/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Baratine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Baratine; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.log.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.caucho.v5.amp.thread.WorkerThreadPoolBase;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.types.Bytes;
import com.caucho.v5.config.types.CronType;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.util.Alarm;
import com.caucho.v5.util.AlarmListener;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.PeriodUtil;
import com.caucho.v5.util.WeakAlarm;

/**
 * Abstract class for a log that rolls over based on size or period.
 */
public class RolloverLogBase extends OutputStream // implements Closeable
{
  private static final L10N L = new L10N(RolloverLogBase.class);
  private static final Logger log
    = Logger.getLogger(RolloverLogBase.class.getName());

  // Milliseconds in an hour
  private static final long HOUR = 3600L * 1000L;
  // Milliseconds in a day
  private static final long DAY = 24L * 3600L * 1000L;

  // Default maximum log size = 2G
  private static final long DEFAULT_ROLLOVER_SIZE = Bytes.INFINITE;
  // How often to check size
  private static final long DEFAULT_ROLLOVER_CHECK_PERIOD = 600L * 1000L;

  private static final long ROLLOVER_OVERFLOW_MAX = 64 * 1024 * 1024;
  
  private static final long INFINITE = Integer.MAX_VALUE;

  // prefix for the rollover
  private String _rolloverPrefix;

  // template for the archived files
  private String _archiveFormat;
  // .gz or .zip
  private String _archiveSuffix = "";

  // Cron description of the rollover
  private CronType _rolloverCron;
  // How often the logs are rolled over.
  private long _rolloverPeriod = INFINITE;

  // Maximum size of the log.
  private long _rolloverSize = DEFAULT_ROLLOVER_SIZE;

  // How often the rolloverSize should be checked
  private long _rolloverCheckPeriod = DEFAULT_ROLLOVER_CHECK_PERIOD;

  // How many archives are allowed.
  private int _rolloverCount;

  private Path _pwd = Paths.get(".");

  private Path _path;

  protected String _pathFormat;

  // private String _format;
  
  private volatile boolean _isInit;

  // The time of the next period-based rollover
  private long _nextPeriodEnd = -1;
  private final AtomicLong _nextRolloverCheckTime = new AtomicLong();

  // private long _lastTime;

  private final RolloverWorker _rolloverWorker = new RolloverWorker();
  private final FlushWorker _flushWorker = new FlushWorker();
  private final Object _logLock = new Object();

  private volatile boolean _isRollingOver;
  //private TempWriter _tempStream;
  private long _tempStreamSize;

  private WriteBuffer _os;
  private WriteBuffer _zipOut;

  private volatile boolean _isClosed;
  private final RolloverAlarm _rolloverListener;
  private WeakAlarm _rolloverAlarm;
  
  protected RolloverLogBase()
  {
    _rolloverListener = new RolloverAlarm();
    _rolloverAlarm = new WeakAlarm(_rolloverListener);
    
    EnvLoader.addCloseListener(this);
  }


  /**
   * Returns the access-log's path.
   */
  public Path getPath()
  {
    return _path;
  }

  /**
   * Sets the access-log's path.
   */
  public void setPath(Path path)
  {
    _path = path;
  }

  /**
   * Returns the pwd for the rollover log
   */
  public Path getPwd()
  {
    return _pwd;
  }

  /**
   * Returns the formatted path
   */
  public String getPathFormat()
  {
    return _pathFormat;
  }

  /**
   * Sets the formatted path.
   */
  public void setPathFormat(String pathFormat)
    throws ConfigException
  {
    _pathFormat = pathFormat;

    if (pathFormat.endsWith(".zip")) {
      throw new ConfigException(L.l(".zip extension to path-format is not supported."));
    }
  }

  /**
   * Sets the archive name format
   */
  public void setArchiveFormat(String format)
  {
    if (format.endsWith(".gz")) {
      _archiveFormat = format.substring(0, format.length() - ".gz".length());
      _archiveSuffix = ".gz";
    }
    else if (format.endsWith(".zip")) {
      _archiveFormat = format.substring(0, format.length() - ".zip".length());
      _archiveSuffix = ".zip";
    }
    else {
      _archiveFormat = format;
      _archiveSuffix = "";
    }
  }

  /**
   * Sets the archive name format
   */
  public String getArchiveFormat()
  {
    if (_archiveFormat == null)
      return _rolloverPrefix + ".%Y%m%d.%H%M";
    else
      return _archiveFormat;
  }

  /**
   * Sets the log rollover cron specification
   */
  public void setRolloverCron(CronType cron)
  {
    _rolloverCron = cron;
  }

  /**
   * Sets the log rollover period, rounded up to the nearest hour.
   *
   * @param period the new rollover period in milliseconds.
   */
  public void setRolloverPeriod(Duration period)
  {
    _rolloverPeriod = period.toMillis();

    if (_rolloverPeriod > 0) {
      _rolloverPeriod += 3600000L - 1;
      _rolloverPeriod -= _rolloverPeriod % 3600000L;
    }
    else
      _rolloverPeriod = Integer.MAX_VALUE; // Period.INFINITE;
  }

  /**
   * Sets the log rollover period, rounded up to the nearest hour.
   *
   * @return the new period in milliseconds.
   */
  public long getRolloverPeriod()
  {
    return _rolloverPeriod;
  }

  /**
   * Sets the log rollover size, rounded up to the megabyte.
   *
   * @param bytes maximum size of the log file
   */
  public void setRolloverSize(Bytes bytes)
  {
    setRolloverSizeBytes(bytes.getBytes());
  }

  public void setRolloverSizeBytes(long size)
  {
    if (size < 0)
      _rolloverSize = Bytes.INFINITE;
    else
      _rolloverSize = size;
  }

  /**
   * Sets the log rollover size, rounded up to the megabyte.
   *
   * @return maximum size of the log file
   */
  public long getRolloverSize()
  {
    return _rolloverSize;
  }

  /**
   * Sets how often the log rollover will be checked.
   *
   * @param period how often the log rollover will be checked.
   */
  public void setRolloverCheckPeriod(long period)
  {
    if (period > 1000)
      _rolloverCheckPeriod = period;
    else if (period > 0)
      _rolloverCheckPeriod = 1000;
    
    if (DAY < _rolloverCheckPeriod) {
      log.info(this + " rollover-check-period "
               + _rolloverCheckPeriod + "ms is longer than 24h");
      
      _rolloverCheckPeriod = DAY;
    }
  }

  /**
   * Sets how often the log rollover will be checked.
   *
   * @return how often the log rollover will be checked.
   */
  public long getRolloverCheckPeriod()
  {
    return _rolloverCheckPeriod;
  }

  /**
   * Sets the max rollover files.
   */
  public void setRolloverCount(int count)
  {
    _rolloverCount = count;
  }

  public void setLastTime(long lastTime)
  {
    // _lastTime = lastTime;
  }
  
  protected boolean isClosed()
  {
    return _isClosed;
  }

  /**
   * Initialize the log.
   */
  public void init()
    throws IOException
  {
    long now = CurrentTime.getExactTime();

    // server/0263
    // _nextRolloverCheckTime = now + _rolloverCheckPeriod;

    Path path = getPath();

    if (path != null) {
      Files.createDirectories(path.getParent());

      _rolloverPrefix = path.getName(path.getNameCount() - 1).toString();

      long lastModified = Files.getLastModifiedTime(path).toMillis();
      
      if (lastModified <= 0 || now < lastModified) {
        lastModified = now;
      }

      // _calendar.setGMTTime(lastModified);

      _nextPeriodEnd = nextRolloverTime(lastModified);
    }
    else {
      _nextPeriodEnd = nextRolloverTime(now);
    }
    
    if (_archiveFormat != null || getRolloverPeriod() <= 0) {
    }
    else if (_rolloverCron != null)
      _archiveFormat = _rolloverPrefix + ".%Y%m%d.%H";
    else if (getRolloverPeriod() % DAY == 0)
      _archiveFormat = _rolloverPrefix + ".%Y%m%d";
    else if (getRolloverPeriod() % HOUR == 0)
      _archiveFormat = _rolloverPrefix + ".%Y%m%d.%H";
    else
      _archiveFormat = _rolloverPrefix + ".%Y%m%d.%H%M";
    
    _isInit = true;

    _rolloverListener.requeue(_rolloverAlarm);
    rollover();
  }

  public boolean rollover()
  {
    long now = CurrentTime.getCurrentTime();

    if (_nextPeriodEnd <= now || _nextRolloverCheckTime.get() <= now) {
      _nextRolloverCheckTime.set(now + _rolloverCheckPeriod);
      _rolloverWorker.wake();

      return true;
    }
    else
      return false;
  }

  @Override
  public void write(int v)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  /**
   * Writes to the underlying log.
   */
  @Override
  public void write(byte []buffer, int offset, int length)
      throws IOException
  {
    synchronized (_logLock) {
      if (_isRollingOver && getTempStreamMax() < _tempStreamSize) {
        try {
          _logLock.wait();
        } catch (Exception e) {
        }
      }

      if (! _isRollingOver) {
        if (_os == null)
          openLog();

        if (_os != null)
          _os.write(buffer, offset, length);
      }
      else {
        // XXX:
        
        throw new UnsupportedOperationException(getClass().getName());
        /*
        if (_tempStream == null) {
          _tempStream = createTempStream();
          _tempStreamSize = 0;
        }

        _tempStreamSize += length;
        _tempStream.write(buffer, offset, length);
        */
      }
    }
  }
  
  //protected TempWriter createTempStream()
  protected OutputStream createTempStream()
  {
    // return new TempStream();
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  protected long getTempStreamMax()
  {
    return ROLLOVER_OVERFLOW_MAX;
  }

  /**
   * Writes to the underlying log.
   */
  @Override
  public void flush()
    throws IOException
  {
    _flushWorker.wake();
  }

  protected void flushStream()
    throws IOException
  {
    synchronized (_logLock) {
      if (_os != null)
        _os.flush();

      if (_zipOut != null)
        _zipOut.flush();
    }
  }

  /**
   * Called from rollover worker
   */
  private void rolloverLogTask()
  {
    try {
      if (_isInit) {
        flush();
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }

    _isRollingOver = true;
    
    try {
      if (! _isInit)
        return;
      
      Path savedPath = null;

      long now = CurrentTime.getCurrentTime();

      long lastPeriodEnd = _nextPeriodEnd;

      _nextPeriodEnd = nextRolloverTime(now);

      Path path = getPath();

      synchronized (_logLock) {
        flushTempStream();

        long length = Files.size(path);
        
        if (lastPeriodEnd <= now && lastPeriodEnd > 0) {
          closeLogStream();

          savedPath = getSavedPath(lastPeriodEnd - 1);
        }
        else if (path != null && getRolloverSize() <= length) {
          closeLogStream();

          savedPath = getSavedPath(now);
        }
      }
      
      // archiving of path is outside of the synchronized block to
      // avoid freezing during archive
      if (savedPath != null) {
        movePathToArchive(savedPath);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      synchronized (_logLock) {
        _isRollingOver = false;
        flushTempStream();
      }
      
      _rolloverListener.requeue(_rolloverAlarm);
    }
  }
  
  private Path getSavedPath(long time)
  {
    if (getPathFormat() == null)
      return getArchivePath(time);
    else
      return null;
  }

  /**
   * Tries to open the log.  Called from inside _logLock
   */
  private void openLog()
  {
    closeLogStream();

    WriteBuffer os = _os;
    _os = null;

    IoUtil.close(os);

    Path path = getPath();

    if (path == null) {
      path = getPath(CurrentTime.getCurrentTime());
    }

    Path parent = path.getParent();

    try {
      if (! Files.isDirectory(parent)) {
        Files.createDirectory(parent);
      }
    } catch (Exception e) {
      logWarning(L.l("Can't create log directory {0}.\n  Exception={1}",
                     parent, e), e);
    }

    Exception exn = null;

    for (int i = 0; i < 3 && _os == null; i++) {
      try {
        OutputStream out = Files.newOutputStream(path, StandardOpenOption.APPEND);
        _os = new WriteBuffer(out);
      } catch (IOException e) {
        exn = e;
      }
    }

    String pathName = path.toString();

    try {
      if (pathName.endsWith(".gz")) {
        _zipOut = _os;
        _os = new WriteBuffer(new GZIPOutputStream(_zipOut));
      }
      else if (pathName.endsWith(".zip")) {
        throw new ConfigException("Can't support .zip in path-format");
      }
    } catch (Exception e) {
      if (exn == null)
        exn = e;
    }

    if (exn != null)
      logWarning(L.l("Can't create log for {0}.\n  User={1} Exception={2}",
                     path, System.getProperty("user.name"), exn), exn);
  }

  private void movePathToArchive(Path savedPath)
  {
    if (savedPath == null)
      return;

    synchronized (_logLock) {
      closeLogStream();
    }

    Path path = getPath();

    String savedName = savedPath.getFileName().toString();

    try {
      if (! Files.isDirectory(savedPath.getParent())) {
        Files.createDirectory(savedPath.getParent());
      }
    } catch (Exception e) {
      logWarning(L.l("Can't open archive directory {0}",
                     savedPath.getParent()),
                 e);
    }

    try {
      if (Files.exists(path)) {
        WriteBuffer os = null;
        OutputStream out = null;

        // *.gz and *.zip are copied.  Others are just renamed
        if (savedName.endsWith(".gz")) {
          // XXX: buffer
          out = Files.newOutputStream(savedPath);
          out = new GZIPOutputStream(out);
        }
        else if (savedName.endsWith(".zip")) {
          out = Files.newOutputStream(savedPath);
          //os = savedPath.openWrite();

          ZipOutputStream zip = new ZipOutputStream(out);
          String entryName = savedName.substring(0, savedName.length() - 4);
          ZipEntry entry = new ZipEntry(entryName);
          zip.putNextEntry(entry);

          out = zip;
        }

        if (out != null) {
          try {
            Files.copy(path, out);
          } finally {
            try {
              out.close();
            } catch (Exception e) {
              // can't log in log rotation routines
            }

            try {
              if (out != os)
                os.close();
            } catch (Exception e) {
              // can't log in log rotation routines
            }
          }
        }
        else {
          Files.move(path, savedPath);
        }
      }
    } catch (Exception e) {
      logWarning(L.l("Error rotating logs: {0}", e.toString()), e);
    }

    try {
      Files.delete(path);
      /*
      try {
        if (! path.truncate())
          path.remove();
      } catch (IOException e) {
        path.remove();

        throw e;
      }
      */
    } catch (Exception e) {
      logWarning(L.l("Error truncating logs"), e);
    }

    if (_rolloverCount > 0)
      removeOldLogs();
  }

  /**
   * Removes logs passing the rollover count.
   */
  private void removeOldLogs()
  {
    try {
      Path path = getPath();
      Path parent = path.getParent();

      ArrayList<String> matchList = new ArrayList<String>();

      Pattern archiveRegexp = getArchiveRegexp();
      Files.list(parent).forEach(child->{
        String subPath = child.getFileName().toString();
        
        Matcher matcher = archiveRegexp.matcher(subPath);

        if (matcher.matches())
          matchList.add(subPath);
      });

      Collections.sort(matchList);

      if (_rolloverCount <= 0 || matchList.size() < _rolloverCount)
        return;

      for (int i = 0; i + _rolloverCount < matchList.size(); i++) {
        try {
          Files.delete(parent.resolve(matchList.get(i)));
        } catch (Throwable e) {
        }
      }
    } catch (Throwable e) {
    }
  }

  private Pattern getArchiveRegexp()
  {
    StringBuilder sb = new StringBuilder();

    String archiveFormat = getArchiveFormat();

    for (int i = 0; i < archiveFormat.length(); i++) {
      char ch = archiveFormat.charAt(i);

      switch (ch) {
      case '.':  case '\\': case '*': case '?': case '+':
      case '(': case ')': case '{': case '}': case '|':
        sb.append("\\");
        sb.append(ch);
        break;
      case '%':
        sb.append(".+");
        i++;
        break;
      default:
        sb.append(ch);
        break;
      }
    }

    return Pattern.compile(sb.toString());
  }

  /**
   * Returns the path of the format file
   *
   * @param time the archive date
   */
  protected Path getPath(long time)
  {
    String formatString = getPathFormat();

    if (formatString == null)
      throw new IllegalStateException(L.l("getPath requires a format path"));

    String pathString = getFormatName(formatString, time);

    return getPwd().resolve(pathString);
  }

  /**
   * Returns the name of the archived file
   *
   * @param time the archive date
   */
  protected Path getArchivePath(long time)
  {
    Path path = getPath();

    String archiveFormat = getArchiveFormat();

    String name = getFormatName(archiveFormat + _archiveSuffix, time);
    Path newPath = path.resolveSibling(name);

    if (Files.exists(newPath)) {
      if (archiveFormat.indexOf("%H") < 0)
        archiveFormat = archiveFormat + ".%H%M";
      else if (archiveFormat.indexOf("%M") < 0)
        archiveFormat = archiveFormat + ".%M";

      for (int i = 0; i < 100; i++) {
        String suffix;

        if (i == 0)
          suffix = _archiveSuffix;
        else
          suffix = "." + i + _archiveSuffix;

        name = getFormatName(archiveFormat + suffix, time);

        newPath = path.resolveSibling(name);

        if (! Files.exists(newPath)) {
          break;
        }
      }
    }

    return newPath;
  }

  /**
   * Returns the name of the archived file
   *
   * @param time the archive date
   */
  protected String getFormatName(String format, long time)
  {
    if (time <= 0) {
      time = CurrentTime.getCurrentTime();
    }
    
    if (true) throw new UnsupportedOperationException();

    /*
    if (format != null)
      return QDate.formatLocal(time, format);
    else if (_rolloverCron != null)
      return _rolloverPrefix + "." + QDate.formatLocal(time, "%Y%m%d.%H");
    else if (getRolloverPeriod() % (24 * 3600 * 1000L) == 0)
      return _rolloverPrefix + "." + QDate.formatLocal(time, "%Y%m%d");
    else
      return _rolloverPrefix + "." + QDate.formatLocal(time, "%Y%m%d.%H");
      */
    
    return _rolloverPrefix + ".date";
  }

  /**
   * error messages from the log itself
   */
  private void logWarning(String msg, Throwable e)
  {
    EnvironmentStream.logStderr(msg, e);
  }
  
  private long nextRolloverTime(long time)
  {
    if (_rolloverCron != null)
      return _rolloverCron.nextTime(time);
    else
      return PeriodUtil.periodEnd(time, getRolloverPeriod());
  }

  /**
   * Closes the log, flushing the results.
   */
  public void close()
    throws IOException
  {
    _isClosed = true;
    
    _rolloverWorker.wake();
    
    _rolloverWorker.close();

    synchronized (_logLock) {
      closeLogStream();
    }
    
    Alarm alarm = _rolloverAlarm;
    _rolloverAlarm = null;
    
    if (alarm != null)
      alarm.dequeue();
  }

  /**
   * Tries to close the log.
   */
  private void closeLogStream()
  {
    try {
      WriteBuffer os = _os;
      _os = null;

      if (os != null)
        os.close();
    } catch (Throwable e) {
      // can't log in log routines
    }

    try {
      WriteBuffer zipOut = _zipOut;
      _zipOut = null;

      if (zipOut != null)
        zipOut.close();
    } catch (Throwable e) {
      // can't log in log routines
    }
  }

  /**
   * Called from inside _logLock
   */
  private void flushTempStream()
  {
    /* XXX:
    TempWriter ts = _tempStream;
    _tempStream = null;
    _tempStreamSize = 0;

    try {
      if (ts != null) {
        if (_os == null)
          openLog();

        try {
          try (InputStream is = ts.openRead().getInputStream()) {
            _os.writeStream(is);
          }
        } catch (IOException e) {
          e.printStackTrace();
        } finally {
          // ts.close();
        }
      }
    } finally {
      _logLock.notifyAll();
    }
    */
  }
  
  @Override
  public String toString()
  {
    Path path = _path;
    
    return getClass().getSimpleName() + "[" + path + "]";
  }

  class RolloverWorker extends WorkerThreadPoolBase {
    @Override
    public long runTask()
    {
      rolloverLogTask();
      
      return -1;
    }
  }

  class FlushWorker extends WorkerThreadPoolBase {
    @Override
    public long runTask()
    {
      try {
        flushStream();
      } catch (IOException e) {
        log.log(Level.FINER, e.toString(), e);
      }
      
      return -1;
    }
  }
  
  class RolloverAlarm implements AlarmListener {
    @Override
    public void handleAlarm(Alarm alarm)
    {
      if (isClosed())
        return;

      try {
        _rolloverWorker.wake();
      } finally {
        alarm.runAfter(_rolloverCheckPeriod);
      }
    }
    
    void requeue(Alarm alarm)
    {
      if (isClosed() || alarm == null)
        return;
      
      long now = CurrentTime.getCurrentTime();
      
      long nextCheckTime;
      
      if (getRolloverSize() <= 0 || _rolloverCheckPeriod <= 0)
        nextCheckTime = now + DAY;
      else
        nextCheckTime = now + _rolloverCheckPeriod;

      if (_nextPeriodEnd <= nextCheckTime) {
        alarm.queueAt(_nextPeriodEnd);
      }
      else {
        alarm.queueAt(nextCheckTime);
      }
    }
  }
}
