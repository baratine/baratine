/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import sun.misc.Unsafe;

import com.caucho.v5.util.CauchoUtil;
import com.caucho.v5.util.L10N;

/**
 * Stream using with JNI.
 */
public final class JniSharedMemory
{
  private static final L10N L = new L10N(JniSharedMemory.class);
  
  private static final Logger log
    = Logger.getLogger(JniSharedMemory.class.getName());
  
  private static final int HEAD_OFFER = 0;
  private static final int HEAD = 8;
  private static final int HEAD_LIMIT = 16;
  
  private static final int TAIL = 24;
  
  private static final int FOOTER_SIZE = 1024;
  
  
  private static final JniTroubleshoot _jniTroubleshoot;
  private static final boolean _is64Bit;
  
  private static final Unsafe _unsafe;
  private static final Method _copyFromArray;
  private static final Method _copyToArray;
  private static final Method _copyMemory;
  private static final int _byteArrayBaseOffset;
  
  private static final Object _openLock = new Object();
  
  private final String _name;
  private final int _dataLength;
  private final boolean _isReader;
  
  private final long _mmapAddress;
  private final int _mmapLength;
  
  
  private final long _mmapHeadOffer;
  private final long _mmapHead;
  private final long _mmapTail;

  private final AtomicBoolean _isClosed = new AtomicBoolean();

  /**
   * Create a new JniStream based on the java.io.* stream.
   */
  private JniSharedMemory(String name, int dataLength,
                          long mmapAddress, int mmapLength,
                          boolean isReader)
  {
    _jniTroubleshoot.checkIsValid();

    _name = name;
    _dataLength = dataLength;
    _isReader = isReader;
    
    _mmapAddress = mmapAddress;
    _mmapLength = mmapLength;
    
    long footer = _mmapAddress + _mmapLength;
    
    _mmapHeadOffer = footer + HEAD_OFFER;;
    _mmapHead = footer + HEAD;
    _mmapTail = footer + TAIL;
  }

  public static boolean isEnabled()
  {
    return _is64Bit && _jniTroubleshoot.isEnabled();
  }

  public static JniSharedMemory open(String name, 
                                     int dataLength,
                                     boolean isRead)
  {
    if (! isEnabled()) {
      return null;
    }
    
    if (Integer.bitCount(dataLength) != 1) {
      throw new IllegalArgumentException(L.l("0x{0} is an invalid length because it's not a power of 2.",
                                             dataLength));
    }
    
    try {
      int mmapLength = dataLength + FOOTER_SIZE;
      long mmapAddress = nativeOpen(name, mmapLength, isRead);
      

      if (mmapAddress != 0)
        return new JniSharedMemory(name, dataLength,
                                   mmapAddress, mmapLength,
                                   isRead);
      else
        return null;
    } catch (Error e) {
      log.log(Level.FINE, e.toString(), e);
      
      _jniTroubleshoot.disable(e);
      
      return null;
    }
  }
  
  public long getMmapAddress()
  {
    return _mmapAddress;
  }
  
  public int write(byte []buffer, int offset, int length)
      throws IOException
  {
    if (_isClosed.get()) {
      return 0;
    }

    long headStart = beginOffer(length);
    long headTail = headStart + length;
    
    long mmapOffset = _mmapAddress + headStart;
    
    try {
      _copyFromArray.invoke(null, buffer, _byteArrayBaseOffset, offset,
                            mmapOffset, length);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    completeOffer(headStart, headTail);
    
    return 1;
  }
  
  private long beginOffer(int length)
  {
    long headStart;
    long headEnd;
    
    do {
      headStart = _unsafe.getLongVolatile(null, _mmapHeadOffer);
      
      headEnd = headStart + length;
    } while (! _unsafe.compareAndSwapLong(null, _mmapHeadOffer, 
                                          headStart, headEnd));
    System.out.println("HS: " + headStart);
    return headStart;
  }
  
  private void completeOffer(long headStart, long headEnd)
  {
    long head = _unsafe.getLongVolatile(null, _mmapHead);

    while (! _unsafe.compareAndSwapLong(null, _mmapHead, 
                                        headStart, headEnd)) {
    }
    
    System.out.println("COMP:" + Long.toHexString(_mmapHead) + " " + headEnd);
  }
  
  public int read(byte []buffer, int offset, int length)
      throws IOException
  {
    if (_isClosed.get()) {
      return -1;
    }

    long head = _unsafe.getLongVolatile(null, _mmapHead);
    long tail = _unsafe.getLongVolatile(null, _mmapTail);
    System.out.println("HEAD: " + head + " " + tail);
    if (head <= tail) {
      return -1;
    }
    
    int sublen = Math.min(length, (int) (head - tail));
    
    long mmapOffset = _mmapAddress + tail;
    
    try {
      _copyToArray.invoke(null, mmapOffset, 
                          buffer, _byteArrayBaseOffset, offset,
                          sublen);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    _unsafe.putLongVolatile(null, _mmapTail , tail + sublen);
    
    return sublen;
  }
  
  public void close()
    throws IOException
  {
    if (_isClosed.getAndSet(true)) {
      return;
    }
    nativeClose(_mmapAddress, _mmapLength);
  }

      
  @Override
  protected void finalize()
    throws IOException
  {
    close();
  }

  /**
   * Returns the debug name for the stream.
   */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _name + "]";
  }

  /**
   * Native to open a shm
   */
  private static native long nativeOpen(String name,
                                        long bufferLength,
                                        boolean isRead);
  
  private static native int nativeClose(long mmapAddress,
                                        int bufferLength);

  static {
    JniTroubleshoot jniTroubleshoot = null;
    Unsafe unsafe = null;
    Method copyFromArray = null;
    Method copyToArray = null;
    Method copyMemory = null;
    int byteArrayBaseOffset = 0;

    JniUtil.acquire();
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
    } finally {
      JniUtil.release();
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

