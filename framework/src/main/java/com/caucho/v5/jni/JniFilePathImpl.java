/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.logging.Logger;

import com.caucho.v5.io.SendfileOutputStream;
import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.jni.JniUtil.JniLoad;
import com.caucho.v5.util.CauchoUtil;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.vfs.FilePath;
import com.caucho.v5.vfs.FileStatus;
import com.caucho.v5.vfs.FilesystemPath;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.RandomAccessStream;
import com.caucho.v5.vfs.VfsOld;

/**
 * FilePath implements the native filesystem.
 */
public class JniFilePathImpl extends FilePath {
  private static final Logger log
    = Logger.getLogger(JniFilePathImpl.class.getName());
  
  private static boolean _isEnabled;
  private static boolean _isInit;
  private static final JniTroubleshoot _jniTroubleshoot;

  private byte []_bytes;

  private long _lastStatTime = 0L;
  private FileStatus _lastStat = null;
  private boolean _doLstat = false;

  /**
   * @param path canonical path
   */
  protected JniFilePathImpl(FilesystemPath root, String userPath, String path)
  {
    super(root, userPath, path);

    setWindows(CauchoUtil.isWindows());

    _jniTroubleshoot.checkIsValid();
  }

  /**
   * @param path canonical path
   */
  private JniFilePathImpl(String userPath, String path, boolean isRoot)
  {
    super(null, userPath, path);

    setWindows(CauchoUtil.isWindows());
    _root = this;

    _jniTroubleshoot.checkIsValid();
  }

  public JniFilePathImpl()
  {
    this(null);
  }

  JniFilePathImpl(String path)
  {
    this(VfsOld.getGlobalPwd() != null ? VfsOld.getGlobalPwd().getRoot() : null,
         path, normalizePath("/", initialPath(path),
                             0, CauchoUtil.getFileSeparatorChar()));
    if (_root == null) {
      _root = new JniFilePathImpl("/", "/", true);
    }
  }

  /**
   * Returns true if the JNI file path exists.
   */
  public static boolean isEnabled()
  {
    if (! _isInit) {
      _isInit = true;
      
      try {
        if (_jniTroubleshoot.isEnabled()
            && nativeIsEnabled()
            && JniFileStream.isEnabled()) {
          // 4.0.25 - JNI VFS no longer requires license
          
          /*
          Class<?> cl = Class.forName("com.caucho.license.LicenseCheckImpl");
          LicenseCheck license = (LicenseCheck) cl.newInstance();

          license.requireProfessional(1);
          */

          _isEnabled = true;
        }
      } catch (Throwable e) {
        log.fine(e.toString());
      }
    }

    return _isEnabled;
  }

  public static String getInitMessage()
  {
    if (! _jniTroubleshoot.isEnabled())
      return _jniTroubleshoot.getMessage();
    else
      return null;
  }

  /**
   * Lookup the actual path relative to the filesystem root.
   *
   * @param userPath the user's path to lookup()
   * @param attributes the user's attributes to lookup()
   * @param path the normalized path
   *
   * @return the selected path
   */
  public PathImpl fsWalk(String userPath,
                     Map<String,Object> attributes,
                     String path)
  {
    FilePath newPath = new JniFilePathImpl(_root, userPath, path);

    if (newPath.isWindows() && newPath.isAux())
      return new FilePath(_root, userPath, path);
    else
      return newPath;
  }

  /**
   * Returns true if the path itself is cacheable
   */
  @Override
  protected boolean isPathCacheable()
  {
    return true;
  }

  /**
   * Returns true if the file exists.
   */
  public boolean exists()
  {
    return getStatus() != null;
  }

  /**
   * Returns the last access time of the file.
   *
   * @return 0 for non-files.
   */
  public long getLastAccessTime()
  {
    FileStatus status = getStatus();

    if (status == null)
      return 0;
    else
      return status.getAtime() * 1000L;
  }

  /**
   * Returns the last modified time.
   */
  public long getLastModified()
  {
    FileStatus status = getStatus();

    if (status == null)
      return 0;
    else
      return status.getMtime() * 1000L;
  }

  /**
   * Returns equivalent of struct stat.st_ctime if appropriate.
   */
  public long getLastStatusChangeTime()
  {
    FileStatus status = getStatus();

    if (status == null)
      return 0;
    else
      return status.getCtime() * 1000L;
  }

  /**
   * Returns the length modified time.
   */
  @Override
  public long length()
  {
    FileStatus status = getStatus();

    if (status == null)
      return -1;
    else
      return status.getSize();
  }

  /**
   * Sets the file mode.
   */
  public boolean chmod(int mode)
  {
    byte []bytes = getBytes();
    boolean result = nativeChmod(bytes, bytes.length, mode) >= 0;

    _lastStatTime = 0;

    return result;
  }

  /**
   * Sets the file mode.
   */
  @Override
  public boolean changeOwner(String owner)
  {
    if (owner == null)
      return false;

    byte []bytes = getBytes();

    boolean result = nativeChangeOwner(bytes, bytes.length, owner);

    _lastStatTime = 0;

    return result;
  }

  /**
   * Returns true if the file can be read.
   */
  public boolean canRead()
  {
    byte []bytes = getBytes();

    return nativeCanRead(bytes);
  }

  /**
   * Returns true if the file is a directory.
   */
  @Override
  public boolean isDirectory()
  {
    FileStatus status = getStatus();
    
    if (status == null)
      return false;
    else
      return status.isDirectory();
  }

  /**
   * Returns true if the file is a regular file.
   */
  public boolean isFile()
  {
    FileStatus status = getStatus();

    if (status == null)
      return false;
    else
      return status.isRegularFile();
  }

  /**
   * Returns true if the file is a symbolic link.
   */
  @Override
  public boolean isLink()
  {
    _doLstat = true;
    FileStatus status = getStatus();

    if (status == null)
      return false;
    else
      return status.isLink();
  }

  /**
   * Tests if the path refers to a socket.
   */
  public boolean isSocket()
  {
    FileStatus status = getStatus();

    if (status == null)
      return false;
    else
      return status.isSocket();
  }

  /**
   * Tests if the path refers to a FIFO.
   */
  public boolean isFIFO()
  {
    FileStatus status = getStatus();

    if (status == null)
      return false;
    else
      return status.isFIFO();
  }

  /**
   * Tests if the path refers to a block device.
   */
  public boolean isBlockDevice()
  {
    FileStatus status = getStatus();

    if (status == null)
      return false;
    else
      return status.isBlockDevice();
  }

  /**
   * Tests if the path refers to a block device.
   */
  public boolean isCharacterDevice()
  {
    FileStatus status = getStatus();

    if (status == null)
      return false;
    else
      return status.isCharacterDevice();
  }

  /**
   * Returns the crc64 code.
   */
  @Override
  public long getCrc64()
  {
    byte []bytes = getBytes();

    long crc64 = nativeCrc64(bytes, bytes.length);

    if (crc64 != 0)
      return crc64;
    else
      return super.getCrc64();
  }

  /**
   * Remove the underlying file.
   */
  @Override
  public boolean remove()
  {
    _lastStat = null;
    _lastStatTime = 0;

    return super.remove();
  }

  /**
   * Truncate the file.
   */
  @Override
  public boolean truncate(long length)
    throws IOException
  {
    _lastStat = null;
    _lastStatTime = 0;

    byte []bytes = getBytes();

    nativeTruncate(bytes, bytes.length);

    return true;
  }

  /**
   * Returns the stream implementation for a read stream.
   */
  @Override
  public StreamImpl openReadImpl() throws IOException
  {
    if (_separatorChar == '\\' && isAux()) {
      throw new FileNotFoundException(getNativePath());
    }

    byte []bytes = getBytes();

    JniFileStream stream = JniFileStream.openRead(bytes, bytes.length);

    if (stream == null)
      throw new FileNotFoundException(getNativePath());

    //stream.setPath(this);

    return stream;
  }

  /**
   * Returns the stream implementation for a write stream.
   */
  @Override
  public StreamImpl openWriteImpl() throws IOException
  {
    if (_separatorChar == '\\' && isAux())
      throw new FileNotFoundException(getNativePath());

    byte []bytes = getBytes();

    JniFileStream stream = JniFileStream.openWrite(bytes, bytes.length, false);

    _lastStat = null;
    _lastStatTime = 0;

    if (stream == null)
      throw new FileNotFoundException(getNativePath());

    //stream.setPath(this);

    return stream;
  }

  /**
   * Returns the stream implementation for an append stream.
   */
  @Override
  public StreamImpl openAppendImpl() throws IOException
  {
    if (_separatorChar == '\\' && isAux())
      throw new FileNotFoundException(getNativePath());

    byte []bytes = getBytes();

    JniFileStream stream = JniFileStream.openWrite(bytes, bytes.length, true);

    _lastStat = null;
    _lastStatTime = 0;

    if (stream == null)
      throw new FileNotFoundException(getNativePath());

    //stream.setPath(this);

    return stream;
  }
  
  @Override
  public void sendfile(OutputStream os, long offset, long length)
    throws IOException
  {
    if (! (os instanceof SendfileOutputStream)) {
      super.sendfile(os, offset, length);
      return;
    }
    
    SendfileOutputStream sfOut = (SendfileOutputStream) os;
    
    if (! sfOut.isSendfileEnabled()) {
      super.sendfile(os, offset, length);
      return;
    }
    
    if (_separatorChar == '\\' && isAux()) {
      throw new FileNotFoundException(getNativePath());
    }

    byte []bytes = getBytes();

    /*
    int fd = JniFileStream.openFileDescriptorRead(bytes, bytes.length);
    
    if (fd < 0) {
      super.sendfile(os, offset, length);
      return;
    }
    */
    
    sfOut.writeSendfile(bytes, bytes.length, length);
    
    /*
    try {
      sfOut.writeSendfile(bytes, bytes.length, length);
    } finally {
      JniFileStream.closeFileDescriptor(fd);
    }
    */
  }
  
  @Override
  public boolean isMmapSupported()
  {
    return JniMemoryMappedFile.isEnabled();
  }

  @Override
  public RandomAccessStream openMemoryMappedFile(long fileLength)
    throws IOException
  {
    byte []bytes = getBytes();

    JniMemoryMappedFile stream
      = JniMemoryMappedFile.open(this, bytes, bytes.length, fileLength);

    if (stream != null)
      return stream;
    else
      return super.openMemoryMappedFile(fileLength);
  }
  
  /**
   * Returns the stream implementation for a random-access stream.
   */
  public RandomAccessStream openRandomAccess() throws IOException
  {
    if (isWindows() && isAux()) {
      throw new FileNotFoundException(toString());
    }

    byte []bytes = getBytes();

    JniRandomAccessFile stream
      = JniRandomAccessFile.open(this, bytes, bytes.length);

    if (stream != null)
      return stream;
    else
      return super.openRandomAccess();
  }

  /**
   * Creates a link named by this path to another path.
   *
   * @param target the target of the link
   * @param hardLink true if the link should be a hard link
   */
  public boolean createLink(PathImpl target, boolean hardLink)
    throws IOException
  {
    if (hardLink && ! target.exists())
      throw new FileNotFoundException(target.getNativePath());

    if (exists())
      throw new IOException(getFullPath() + " already exists");

    byte []bytes = getBytes();
    byte []targetBytes = getBytes(target);

    return nativeLink(bytes, targetBytes, hardLink);
  }

  /**
   * Returns the pathname of a link
   */
  @Override
  public String readLink()
  {
    byte []bytes = getBytes();

    return nativeReadLink(bytes);
  }

  /**
   * Returns the pathname of a link
   */
  @Override
  public String realPath()
  {
    byte []bytes = getBytes();

    String realPath = nativeRealPath(bytes);

    if (realPath != null)
      return realPath;
    else
      return super.realPath();
  }

  /**
   * Returns equivalent of struct stat.st_dev if appropriate.
   */
  public long getDevice()
  {
    FileStatus status = getStatus();

    if (status == null)
      return 0;
    else
      return status.getDev();
  }

  /**
   * Returns equivalent of struct stat.st_ino if appropriate.
   */
  public long getInode()
  {
    FileStatus status = getStatus();

    if (status == null)
      return 0;
    else
      return status.getIno();
  }

  /**
   * Returns equivalent of struct stat.st_mode if appropriate.
   */
  public int getMode()
  {
    FileStatus status = getStatus();

    if (status == null)
      return 0;
    else
      return status.getMode();
  }

  /**
   * Returns equivalent of struct stat.st_nlink if appropriate.
   */
  public int getNumberOfLinks()
  {
    FileStatus status = getStatus();

    if (status == null)
      return 0;
    else
      return status.getNlink();
  }

  /**
   * Returns equivalent of struct stat.st_uid if appropriate.
   */
  public int getUser()
  {
    FileStatus status = getStatus();

    if (status == null)
      return 0;
    else
      return status.getUid();
  }

  /**
   * Returns equivalent of struct stat.st_gid if appropriate.
   */
  public int getGroup()
  {
    FileStatus status = getStatus();

    if (status == null)
      return 0;
    else
      return status.getGid();
  }

  /**
   * Returns equivalent of struct stat.st_rdev if appropriate.
   */
  public long getDeviceId()
  {
    FileStatus status = getStatus();

    if (status == null)
      return 0;
    else
      return status.getRdev();
  }

  /**
   * Returns equivalent of struct stat.st_blksize if appropriate.
   */
  public long getBlockSize()
  {
    FileStatus status = getStatus();

    if (status == null)
      return 0;
    else
      return status.getBlksize();
  }

  /**
   * Returns equivalent of struct stat.st_blocks if appropriate.
   */
  public long getBlockCount()
  {
    FileStatus status = getStatus();

    if (status == null)
      return 0;
    else
      return status.getBlocks();
  }

  @Override
  public void clearStatusCache()
  {
    _lastStatTime = 0;
  }

  private FileStatus getStatus()
  {
   long now;

   now = CurrentTime.getCurrentTime();

   // Wait at least 500ms between stats, limited by OS timestamp
   // XXX: This optimization might need to be removed at some point
   if (CurrentTime.isTest()) {
   }
   else if (_lastStat != null && now - _lastStatTime <= 500L) {
     return _lastStat;
   }
   else if (_lastStat == null && now - _lastStatTime <= 100L)
     return null;

    byte []bytes = getBytes();

    FileStatus fileStatus = _lastStat;

    if (fileStatus == null)
      fileStatus = new FileStatus();

    if (nativeStat(fileStatus, bytes, _doLstat)) {
      if (isWindows()) {
        // fix the windows time to avoid windows DST bug
        fileStatus.updateWindowsTime(getFile());
      }
      
      _lastStat = fileStatus;
    }
    else {
      _lastStat = null;
    }

    _lastStatTime = now;

    return _lastStat;
  }

  @Override
  public PathImpl copy()
  {
    return new JniFilePathImpl(getRoot(), getUserPath(), getPath());
  }

  /**
   * Returns the byte representation of the file.
   */
  private byte []getBytes()
  {
    if (_bytes == null) {
      try {
        String encoding = System.getProperty("file.encoding");
        _bytes = getNativePath().getBytes(encoding);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    return _bytes;
  }

  /**
   * Returns the byte representation of the file.
   */
  private byte []getBytes(PathImpl path)
  {
    try {
      String encoding = System.getProperty("file.encoding");
      return path.getNativePath().getBytes(encoding);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static native boolean nativeIsEnabled();
  //private static native int nativeGetLastModified(byte []name, int length);
  //private static native int nativeGetLength(byte []name, int length);
  private static native boolean nativeCanRead(byte []name);
  //private static native boolean nativeIsDirectory(byte []name);
  private static native long nativeCrc64(byte []name, int length);
  private static native int nativeTruncate(byte []name, int length);
  private static native int nativeChmod(byte []name, int length, int mode);
  private static native boolean nativeChangeOwner(byte []name, int length,
                                                  String userName);
  private static native boolean nativeStat(FileStatus status,
                                              byte []name,
                                              boolean doLstat);
  private static native boolean nativeLink(byte []name, byte []target,
                                           boolean hardLink)
    throws IOException;
  private static native String nativeReadLink(byte []name);
  private static native String nativeRealPath(byte []name);

  static {
    _jniTroubleshoot
      = JniUtil.load(JniFilePathImpl.class,
                     new JniLoad() { 
                       public void load(String path) { System.load(path); }},
                     "baratine");
  }
}
