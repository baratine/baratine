/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

import sun.misc.Unsafe;

import com.caucho.v5.vfs.PathImpl;

/**
 * Stream using with JNI.
 */
public final class UnsafeMemoryMappedFile extends JniMemoryMappedFile
{
  private static final Logger log
    = Logger.getLogger(UnsafeMemoryMappedFile.class.getName());
  private static Unsafe _unsafe;
  private static boolean _isEnabled;
  private static int _byteArrayOffset;
  
  /**
   * Create a new JniStream based on the java.io.* stream.
   */
  protected UnsafeMemoryMappedFile(long file, PathImpl path, long fileLength)
  {
    super(file, path, fileLength);
  }
  
  static boolean isUnsafeEnabled()
  {
    return _isEnabled;
  }

  @Override
  protected final int mmapRead(long file, long pos, 
                         byte []buf, int offset, int length)
    throws IOException
  {
    long mmapAddress = getMmapAddress() + pos;
    
    _unsafe.copyMemory(null, mmapAddress, buf, offset + _byteArrayOffset, length);

    return length;
  }

  static {
    boolean isEnabled = false;
    Unsafe unsafe = null;
    int byteArrayOffset = 0;
    
    try {
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
      
      isEnabled = unsafe != null;
      
      byteArrayOffset = unsafe.arrayBaseOffset(byte[].class);
    } catch (Throwable e) {
      log.log(Level.ALL, e.toString(), e);
    }
    
    _unsafe = unsafe;
    _isEnabled = isEnabled;
    _byteArrayOffset = byteArrayOffset;
  }
}

