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
package com.caucho.v5.config.util;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

import sun.misc.Unsafe;

/**
 * Utilities to manage the sun.misc.Unsafe class.
 */
public class UnsafeUtil
{
  private static final Logger log = Logger.getLogger(UnsafeUtil.class.getName());
  
  private static final boolean _isEnabled;
  private static final Unsafe _unsafe;

  public static boolean isEnabled()
  {
    return _isEnabled;
  }
  
  public static Unsafe getUnsafe()
  {
    return _unsafe;
  }

  static {
    boolean isEnabled = false;
    Unsafe unsafe = null;
    
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
    } catch (Throwable e) {
      log.log(Level.ALL, e.toString(), e);
    }
    
    _unsafe = unsafe;
    _isEnabled = isEnabled;
  }
}
