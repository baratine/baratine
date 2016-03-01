/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import sun.misc.Unsafe;

import com.caucho.v5.util.CauchoUtil;
import com.caucho.v5.util.L10N;

/**
 * Stream using with JNI.
 */
public class JniMemoryMappedCopy
{
  private static final JniTroubleshoot _jniTroubleshoot;
  private static final boolean _is64Bit;
  
  private static final Unsafe _unsafe;
  private static final Method _copyFromArray;
  private static final Method _copyToArray;
  private static final Method _copyMemory;
  private static final int _byteArrayBaseOffset;
  
  private final long _address;
  private final long _length;
  
  private JniMemoryMappedCopy(long address, long length)
  {
    _address = address;
    _length = length;
  }
  
  public static JniMemoryMappedCopy create(long address, long length)
  {
    if (true)
      return null;
    else if (_copyFromArray != null)
      return new JniMemoryMappedCopy(address, length);
    else
      return null;
  }

  public int read(long pos, byte []buf, int offset, int length)
    throws IOException
  {
    if (buf == null)
      throw new NullPointerException();
    else if (offset < 0 || buf.length < offset + length)
      throw new ArrayIndexOutOfBoundsException();
    else if (_length < pos + length) {
      throw new ArrayIndexOutOfBoundsException("FileLength: 0x" + Long.toHexString(_length)
                                               + " pos: 0x" + Long.toHexString(pos)
                                               + " len: 0x" + Long.toHexString(length));
    }
    
    long mmapOffset = _address + pos;
    
    try {
      _copyToArray.invoke(null, mmapOffset, 
                          buf, _byteArrayBaseOffset, offset,
                          length);
      
      return length;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Writes data to the file.
   */
  public void write(long pos, byte []buf, int offset, int length)
    throws IOException
  {
    if (buf == null)
      throw new NullPointerException();
    else if (offset < 0 || buf.length < offset + length)
      throw new ArrayIndexOutOfBoundsException();
    else if (_length < pos + length) {
      throw new ArrayIndexOutOfBoundsException("FileLength: 0x" + Long.toHexString(_length)
                                               + " pos: 0x" + Long.toHexString(pos)
                                               + " len: 0x" + Long.toHexString(length));
    }

    long mmapOffset = _address + pos;
    
    try {
      _copyFromArray.invoke(null, buf, _byteArrayBaseOffset, offset,
                            mmapOffset, length);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static {
    JniTroubleshoot jniTroubleshoot = null;
    Unsafe unsafe = null;
    Method copyFromArray = null;
    Method copyToArray = null;
    Method copyMemory = null;
    int byteArrayBaseOffset = 0;

    try {
      String path = JniUtil.getLibraryPath("resin");
      
      System.load(path);

      jniTroubleshoot
        = new JniTroubleshoot(JniSharedMemory.class, "resin");
      
      Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
      
      Field theUnsafe = null;
      for (Field field : unsafeClass.getDeclaredFields()) {
        if (field.getName().equals("theUnsafe"))
          theUnsafe = field;
      }
        
      if (theUnsafe != null) {
        theUnsafe.setAccessible(true);
        unsafe = (Unsafe) theUnsafe.get(null);
      }
        
      if (unsafe == null) {
        throw new UnsupportedOperationException(Unsafe.class.getName() + " is unavailable");
      }
      
      for (Method m : unsafeClass.getDeclaredMethods()) {
        if (m.getName().equals("copyMemory")
            && m.getParameterTypes().length == 5) {
          copyMemory = m;
          copyMemory.setAccessible(true);
        }
      }
      
      Class<?> bits = Class.forName("java.nio.Bits");
      
      for (Method m : bits.getDeclaredMethods()) {
        if (m.getName().equals("copyFromArray")
            && m.getParameterTypes().length == 5) {
          copyFromArray = m;
          copyFromArray.setAccessible(true);
        }
        else if (m.getName().equals("copyToArray")
                 && m.getParameterTypes().length == 5) {
          copyToArray = m;
          copyToArray.setAccessible(true);
        }
      }
      
      if (copyFromArray == null || copyToArray == null) {
        throw new UnsupportedOperationException(bits.getName() + " is unavailable");
      }
      
      byteArrayBaseOffset = unsafe.arrayBaseOffset(byte[].class);
    }
    catch (Throwable e) {
      jniTroubleshoot
        = new JniTroubleshoot(JniSharedMemory.class, "resin", e);
    }
    
    _is64Bit = CauchoUtil.is64Bit();
    
    _jniTroubleshoot = jniTroubleshoot;
    
    _unsafe = unsafe;
    _byteArrayBaseOffset = byteArrayBaseOffset;
    _copyMemory = copyMemory;
    _copyFromArray = copyFromArray;
    _copyToArray = copyToArray;
  }
}

