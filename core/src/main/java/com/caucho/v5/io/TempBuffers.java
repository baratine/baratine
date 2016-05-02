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

package com.caucho.v5.io;

import java.util.logging.Logger;

import com.caucho.v5.util.FreeList;

/**
 * Pooled temporary byte buffer.
 */
public class TempBuffers
{
  private static final Logger log = Logger.getLogger(TempBuffers.class.getName());

  private static final boolean _isSmallmem;
  
  public static final int SMALL_SIZE;
  public static final int LARGE_SIZE;
  public static final int STANDARD_SIZE;
  
  private static final FreeList<TempBufferData> _freeSmall
    = new FreeList<>(256);
  
  private static final FreeList<TempBufferData> _freeStandard
    = new FreeList<>(256);
  
  private static final FreeList<TempBufferData> _freeLarge
    = new FreeList<>(256);

  /**
   * Returns true for a smallmem configuration
   */
  public static boolean isSmallmem()
  {
    return _isSmallmem;
  }
  
  static TempBufferData create()
  {
    TempBufferData data = _freeStandard.allocate();
    
    if (data == null) {
      data = new TempBufferData(STANDARD_SIZE, _freeStandard);
    }
    
    return data;
  }
  
  public static TempBufferData allocate()
  {
    TempBufferData data = create();
    
    data.allocate();
    
    return data;
  }
  
  public static TempBufferData createSmall()
  {
    TempBufferData data = _freeSmall.allocate();
    
    if (data == null) {
      data = new TempBufferData(SMALL_SIZE, _freeSmall);
    }
    
    return data;
  }
  
  public static TempBufferData createLarge()
  {
    TempBufferData data = _freeLarge.allocate();
    
    if (data == null) {
      data = new TempBufferData(LARGE_SIZE, _freeLarge);
    }
    
    return data;
  }

  /**
   * Free data for OOM.
   */
  public static void clearFreeLists()
  {
    while (_freeStandard.allocate() != null) {
    }
    
    while (_freeSmall.allocate() != null) {
    }
    
    while (_freeLarge.allocate() != null) {
    }
  }

  static {
    // the max size needs to be less than JNI code, currently max 16k
    // the min size is 8k because of the JSP spec
    int size = 8 * 1024;
    boolean isSmallmem = false;

    String smallmem = System.getProperty("caucho.smallmem");
    
    if (smallmem != null && ! "false".equals(smallmem)) {
      isSmallmem = true;
      size = 512;
    }

    _isSmallmem = isSmallmem;
    STANDARD_SIZE = size;
    LARGE_SIZE = 8 * 1024;
    SMALL_SIZE = 512;
  }
}
