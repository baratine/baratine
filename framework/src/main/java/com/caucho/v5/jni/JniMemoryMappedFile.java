/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.SendfileOutputStream;
import com.caucho.v5.util.CauchoUtil;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.RandomAccessStream;

/**
 * Stream using with JNI.
 */
public class JniMemoryMappedFile extends RandomAccessStream
{
  private static final Logger log
    = Logger.getLogger(JniMemoryMappedFile.class.getName());
  
  private static final JniTroubleshoot _jniTroubleshoot;
  private static final boolean _is64Bit;
  private static final boolean _isUnsafeEnabled;
  
  private static final Object _openLock = new Object();
  
  // private final JniMemoryMappedCopy _mmapCopy;

  private final AtomicLong _file = new AtomicLong();
  private final long _mmapAddress;
  private final long _fileLength;
  private final PathImpl _path;
  
  // private final AtomicBoolean _isClosed = new AtomicBoolean();

  /**
   * Create a new JniStream based on the java.io.* stream.
   */
  protected JniMemoryMappedFile(long file, PathImpl path, long fileLength)
  {
    _jniTroubleshoot.checkIsValid();

    _file.set(file);
    
    _path = path;
    _fileLength = fileLength;
    _mmapAddress = nativeMmapAddress(file);
    
    // _mmapCopy = JniMemoryMappedCopy.create(_mmapAddress, _fileLength);
  }

  public static boolean isEnabled()
  {
    return _is64Bit && _jniTroubleshoot.isEnabled();
  }

  public static JniMemoryMappedFile open(PathImpl path,
                                         byte []name,
                                         int length,
                                         long fileLength)
  {
    if (! isEnabled())
      return null;
    
    try {
      long currentFileLength = path.length();
      
      if (fileLength < currentFileLength)
        fileLength = currentFileLength;
      
      long file = openImpl(name, length, fileLength);

      if (file != 0)
        return create(file, path, fileLength);
      else
        return null;
    } catch (Error e) {
      log.log(Level.FINE, e.toString(), e);
      
      _jniTroubleshoot.disable(e);
      
      return null;
    }
  }
  
  private static JniMemoryMappedFile create(long file, PathImpl path, long fileLength)
  {
    if (_isUnsafeEnabled) {
      return createUnsafe(file, path, fileLength);
    }
    else {
      return new JniMemoryMappedFile(file, path, fileLength);
    }
  }
  
  private static JniMemoryMappedFile createUnsafe(long file, 
                                                  PathImpl path, 
                                                  long fileLength)
  {
    return new UnsafeMemoryMappedFile(file, path, fileLength);
  }
  
  private static long openImpl(byte []name, int length, long fileLength)
  {
    synchronized (_openLock) {
      return nativeOpen(name, length, fileLength);
    }
  }
  
  /**
   * Returns true for mmap
   */
  @Override
  public boolean isMmap()
  {
    return true;
  }
  
  /**
   * Returns the length.
   */
  @Override
  public long getLength()
    throws IOException
  {
    if (isOpen())
      return _fileLength;
    else
      return -1;
  }

  /**
   * Reads data from the file.
   */
  @Override
  public int read(long pos, byte []buf, int offset, int length)
    throws IOException
  {
    if (buf == null)
      throw new NullPointerException();
    else if (offset < 0 || buf.length < offset + length)
      throw new ArrayIndexOutOfBoundsException();
    else if (_fileLength < pos + length) {
      throw new ArrayIndexOutOfBoundsException("FileLength: 0x" + Long.toHexString(_fileLength)
                                               + " pos: 0x" + Long.toHexString(pos)
                                               + " len: 0x" + Long.toHexString(length));
    }

      /*
      JniMemoryMappedCopy mmapCopy = _mmapCopy;

      if (mmapCopy != null)
        return mmapCopy.read(pos, buf, offset, length);
        */
      
    return mmapRead(_file.get(), pos, buf, offset, length);
  }
  
  protected int mmapRead(long file, long pos, 
                         byte []buf, int offset, int length)
    throws IOException
  {
    return nativeRead(file, pos, buf, offset, length);
  }
  
  @Override
  public long getMmapAddress()
  {
    return _mmapAddress;
  }

  /**
   * Writes data to the file.
   */
  @Override
  public void write(long pos, byte []buf, int offset, int length)
    throws IOException
  {
    if (buf == null)
      throw new NullPointerException();
    else if (offset < 0 || buf.length < offset + length)
      throw new ArrayIndexOutOfBoundsException();
    else if (_fileLength < pos + length) {
      throw new ArrayIndexOutOfBoundsException("FileLength: 0x" + Long.toHexString(_fileLength)
                                               + " pos: 0x" + Long.toHexString(pos)
                                               + " len: 0x" + Long.toHexString(length));
    }

    /*
      JniMemoryMappedCopy mmapCopy = _mmapCopy;
  
      if (mmapCopy != null) {
        mmapCopy.write(pos, buf, offset, length);
        return;
      }
      */
      
    nativeWrite(_file.get(), pos, buf, offset, length);
  }

  /**
   * Writes data to the file.
   */
  @Override
  public boolean writeToStream(SendfileOutputStream os, 
                               long offset, long length,
                               long []blockAddresses, long blockLength)
    throws IOException
  {
    if (os == null)
      throw new NullPointerException();
    else if (blockAddresses == null)
      throw new NullPointerException();
    
    length = Math.min(length, blockLength);

    os.writeMmap(getMmapAddress(), blockAddresses, offset, length);
    
    return true;
  }
  
  @Override
  public void close()
  {
    try {
      fsync();
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    super.close();
  }
  
  @Override
  public void fsync()
    throws IOException
  {
    flushToDisk();
  }

  public void flushToDisk()
    throws IOException
  {
    nativeFlushToDisk(_file.get());
  }

  /**
   * Reads data from the file.
   */
  @Override
  public int read(byte []buf, int offset, int length)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Reads data from the file.
   */
  @Override
  public int read(char []buf, int offset, int length)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Writes data to the file.
   */
  @Override
  public void write(byte []buf, int offset, int length)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Seeks to the given position in the file.
   */
  @Override
  public boolean seek(long position)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Returns an OutputStream for this stream.
   */
  @Override
  public OutputStream getOutputStream()
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Returns an InputStream for this stream.
   */
  @Override
  public InputStream getInputStream()
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Read a byte from the file, advancing the pointer.
   */
  @Override
  public int read()
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Write a byte to the file, advancing the pointer.
   */
  @Override
  public void write(int b)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }


  /**
   * Returns the current position of the file pointer.
   */
  @Override
  public long getFilePointer()
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public boolean allocate()
  {
    return true;
  }

  @Override
  public void free()
  {
  }

  private void finalizeClose()
    throws IOException
  {
    long file = _file.getAndSet(0);
    
    if (file != 0) {
      nativeClose(file);
    }
    else {
      log.warning(this + " double close " + file + " " + getUseCount());
      System.err.println(this + " DOUBLE_CLOSE2: " + Long.toHexString(file) + " " + getUseCount());
    }
  }

  @Override
  protected void finalize()
    throws IOException
  {
    if (_file.get() != 0) {
      finalizeClose();
    }
  }

  /**
   * Native to open a random access file
   */
  private static native long nativeOpen(byte []name, 
                                        int name_length,
                                        long file_length);

  /**
   * Native interface to read bytes from the input.
   */
  private static native int nativeRead(long file, long pos, 
                                       byte []buf, int offset, int length)
    throws IOException;

  /**
   * Native interface to write bytes to the file
   */
  private static native int nativeWrite(long file, long pos, 
                                        byte []buf, int offset, int length)
    throws IOException;

  /**
   * Native interface to return the mmap address.
   */
  private static native long nativeMmapAddress(long file);

  /**
   * Native interface to force data to the disk
   */
  private static native int nativeFlushToDisk(long file)
    throws IOException;

  /**
   * Native interface to read bytes from the input.
   */
  private static native int nativeClose(long file)
    throws IOException;

  /**
   * Returns the debug name for the stream.
   */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _path + "]";
  }

  static {
    JniTroubleshoot jniTroubleshoot = null;

    JniUtil.acquire();
    try {
      boolean isValid = false;
      for (String path : JniUtil.getNativePaths("baratine")) {
        try {
          if (! isValid) {
            System.load(path);
            isValid = true;
            
            jniTroubleshoot = new JniTroubleshoot(JniFilePathImpl.class, "baratine");
          }
        } catch (Exception e) {
          jniTroubleshoot = new JniTroubleshoot(JniFilePathImpl.class, "baratine", e);
        }
      }

      jniTroubleshoot
        = new JniTroubleshoot(JniMemoryMappedFile.class, "resin");
    }
    catch (Throwable e) {
      jniTroubleshoot
        = new JniTroubleshoot(JniMemoryMappedFile.class, "resin", e);
    } finally {
      JniUtil.release();
    }
    
    _is64Bit = CauchoUtil.is64Bit();
    
    _jniTroubleshoot = jniTroubleshoot;
    
    boolean isUnsafeEnabled = false;
    try {
      isUnsafeEnabled = UnsafeMemoryMappedFile.isUnsafeEnabled();
    } catch (Throwable e) {
      
    }
    
    _isUnsafeEnabled = isUnsafeEnabled;
  }
}

