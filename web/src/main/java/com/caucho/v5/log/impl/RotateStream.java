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
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.types.Bytes;
import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.io.WriteBuffer;

import io.baratine.service.ResultFuture;

/**
 * Automatically-rotating streams.  Normally, clients will call
 * getStream instead of using the StreamImpl interface.
 */
public class RotateStream extends StreamImpl
{
  //private static final RotateStream _stdoutStream;
  //private static final RotateStream _stderrStream;
  
  private static HashMap<Path,WeakReference<RotateStream>> _streams
    = new HashMap<>();
  
  private static HashMap<String,WeakReference<RotateStream>> _formatStreams
    = new HashMap<>();
  private static RotateStreamQueue _stdStreamQueue;

  private final RolloverLogBase _rolloverLog;
  private final WriteBuffer _out;

  private volatile AtomicBoolean _isInit = new AtomicBoolean();

  private RotateStreamQueue _queue;

  /**
   * Create rotate stream.
   *
   * @param path underlying log path
   */
  private RotateStream(String formatPath)
    throws ConfigException
  {
    _rolloverLog = new RolloverLogBase();
    _rolloverLog.setPathFormat(formatPath);
    
    _out = new WriteBuffer(_rolloverLog);

    // _queue = buildQueue(this);
    _queue = new RotateStreamQueue(this);
  }

  /**
   * Create rotate stream.
   *
   * @param path underlying log path
   */
  private RotateStream(Path path)
  {
    _rolloverLog = new RolloverLogBase();
    _rolloverLog.setPath(path);
    
    _out = new WriteBuffer(_rolloverLog);

    _queue = new RotateStreamQueue(this);
  }
  
  private RotateStream(WriteBuffer out)
  {
    _rolloverLog = new RolloverLogBase();
    //_rolloverLog.setPath(out.getPath());
    if (true) throw new UnsupportedOperationException();
    
    _out = out;

    _queue = new RotateStreamQueue(this);
  }
  
  private RotateStream(WriteBuffer out, RotateStreamQueue queue)
  {
    _rolloverLog = new RolloverLogBase();
    //_rolloverLog.setPath(out.getPath());
    
    _out = out;

    _queue = queue;
    queue.addStream(this);
  }

  /**
   * Returns the rotate stream corresponding to this path
   */
  public static RotateStream create(Path path)
  {
    synchronized (_streams) {
      WeakReference<RotateStream> ref = _streams.get(path);
      
      RotateStream stream = ref != null ? ref.get() : null;

      if (stream == null) {
        stream = new RotateStream(path);

        _streams.put(path, new WeakReference<>(stream));
      }

      return stream;
    }
  }

  /**
   * Returns the rotate stream corresponding to this path
   */
  public static RotateStream create(String path)
    throws ConfigException
  {
    synchronized (_formatStreams) {
      WeakReference<RotateStream> ref = _formatStreams.get(path);
      
      RotateStream stream = ref != null ? ref.get() : null;

      if (stream == null) {
        stream = new RotateStream(path);

        _formatStreams.put(path, new WeakReference<RotateStream>(stream));
      }

      return stream;
    }
  }
  
  public static RotateStream getStdoutStream()
  {
    //return _stdoutStream;
    return null;
  }
  
  public static RotateStream getStderrStream()
  {
    //return _stderrStream;
    return null;
  }

  /**
   * Clears the streams.
   */
  public static void clear()
  {
    synchronized (_streams) {
      for (WeakReference<RotateStream> streamRef : _streams.values()) {
        try {
          RotateStream stream = streamRef.get();

          if (stream != null) {
            stream.closeImpl();
          }
        } catch (Throwable e) {
          e.printStackTrace(EnvironmentStream.getOriginalSystemErr());
        }
      }
      
      _streams.clear();
    }
    
    synchronized (_formatStreams) {
      for (WeakReference<RotateStream> streamRef : _formatStreams.values()) {
        try {
          RotateStream stream = streamRef.get();

          if (stream != null) {
            stream.closeImpl();
          }
        } catch (Throwable e) {
          e.printStackTrace(EnvironmentStream.getOriginalSystemErr());
        }
      }
      
      _formatStreams.clear();
    }
  }

  /**
   * Returns the rollover log.
   */
  public RolloverLogBase getRolloverLog()
  {
    return _rolloverLog;
  }

  /*
  public QueueService<?> getQueue()
  {
    return _queue;
  }
  */

  /**
   * Sets the maximum number of rolled logs.
   */
  public void setMaxRolloverCount(int count)
  {
    _rolloverLog.setRolloverCount(count);
  }

  /**
   * Sets the log rollover period, rounded up to the nearest hour.
   *
   * @param period the new rollover period in milliseconds.
   */
  public void setRolloverPeriod(long period)
  {
    _rolloverLog.setRolloverPeriod(Duration.ofMillis(period));
  }

  /**
   * Sets the log rollover size in bytes.
   */
  public void setRolloverSize(long size)
  {
    _rolloverLog.setRolloverSize(new Bytes(size));
  }

  /**
   * Sets the archive format.
   *
   * @param format the archive format.
   */
  public void setArchiveFormat(String format)
  {
    _rolloverLog.setArchiveFormat(format);
  }

  /**
   * Initialize the stream, setting any logStream, System.out and System.err
   * as necessary.
   */
  public void init()
    throws IOException
  {
    if (_isInit.getAndSet(true)) {
      return;
    }

    _rolloverLog.init();
  }

  /**
   * Returns the Path associated with the stream.
   */
  /*
  @Override
  public PathImpl getPath()
  {
    return _rolloverLog.getPath();
  }
  */

  /**
   * True if the stream can write
   */
  @Override
  public boolean canWrite()
  {
    return true;
  }

  /**
   * Writes to the stream
   */
  @Override
  public void write(byte []buffer, int offset, int length, boolean isEnd)
    throws IOException
  {
    _queue.write(this, buffer, offset, length);
  }

  /**
   * Gets the current write stream
   */
  public WriteBuffer getStream()
  {
    return new WriteBuffer(this);
  }
  
  public void waitForFlush(ResultFuture<Boolean> future)
  {
    _queue.waitForFlush(future);
  }

  /**
   * Flushes the underlying stream.
   */
  @Override
  public void flush()
    throws IOException
  {
    /*
    _rolloverLog.flush();
    _rolloverLog.rollover();
    */
  }
  
  //
  // callbacks from RotateStreamQueue
  // 
  
  void onString(String msg)
  {
    try {
      _rolloverLog.rollover();
      _out.print(msg);
    } catch (IOException e) {
    }
  }
  
  void onBinary(byte []buffer, int offset, int length)
  {
    try {
      _rolloverLog.rollover();
      _out.write(buffer, offset, length);

      // _alarm.queue(1000);
    } catch (IOException e) {
    }
  }
  
  void onFlush()
  {
    try {
      _out.flush();
      _rolloverLog.flush();
    } catch (IOException e) {
    }
  }
  
  void onAfterBatch()
  {
    try {
      _out.flush();
      _rolloverLog.flush();
      _rolloverLog.rollover();
    } catch (IOException e) {
    }
  }

  /**
   * The close call does nothing since the rotate stream is shared for
   * many logs.
   */
  @Override
  public void close()
  {
  }
  
  private void closeImpl()
  {
    try {
      _rolloverLog.close();
    } catch (Throwable e) {
    }
  }
  
  @Override
  public void finalize()
    throws Throwable
  {
    super.finalize();
    
    closeImpl();
  }
  
  /*
  static {
    try {
    WriteStream stdOut = EnvironmentStream.getOriginalSystemOutStream();
    WriteStream stdErr = EnvironmentStream.getOriginalSystemErrStream();
    
    _stdStreamQueue = new RotateStreamQueue();
    
    _stdoutStream = new RotateStream(stdOut, _stdStreamQueue);
    _stderrStream = new RotateStream(stdErr, _stdStreamQueue);
    
    _streams.put(VfsOld.lookup("stdout:"), new WeakReference<>(_stdoutStream));
    _streams.put(VfsOld.lookup("stderr:"), new WeakReference<>(_stderrStream));
    } catch (Throwable e) {
      e.printStackTrace();
      throw e;
    }
  }
  */
}
