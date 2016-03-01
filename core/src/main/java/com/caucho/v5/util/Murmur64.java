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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.util;

/**
 * Murmer64 hash
 */
public class Murmur64 {
  public static final long SEED = 0xe17a1465;

  /**
   * Calculates hash from a buffer
   */
  public static long generate(long hash, 
                              final byte []buffer, 
                              final int offset, 
                              final int length)
  {
    final long m = 0xc6a4a7935bd1e995L;
    final int r = 47;
    
    hash ^= length * m;
    
    int len8 = length / 8;
    
    for (int i = 0; i < len8; i++) {
      final int index = i * 8 + offset;
      
      long k = ((buffer[index + 0] & 0xffL)
               | ((buffer[index + 1] & 0xffL) << 8)
               | ((buffer[index + 2] & 0xffL) << 16)
               | ((buffer[index + 3] & 0xffL) << 24)
               | ((buffer[index + 4] & 0xffL) << 32)
               | ((buffer[index + 5] & 0xffL) << 40)
               | ((buffer[index + 6] & 0xffL) << 48)
               | ((buffer[index + 7] & 0xffL) << 56));
      
      k *= m;
      k ^= k >>> r;
      k *= m;
      hash ^= k;
      hash *= m;
    }
    
    final int off = offset + (length & ~0x7);
    
    switch (length % 8) {
    case 7: hash ^= (buffer[off + 6] & 0xffL) << 48;
    case 6: hash ^= (buffer[off + 5] & 0xffL) << 40;
    case 5: hash ^= (buffer[off + 4] & 0xffL) << 32;
    case 4: hash ^= (buffer[off + 3] & 0xffL) << 24;
    case 3: hash ^= (buffer[off + 2] & 0xffL) << 16;
    case 2: hash ^= (buffer[off + 1] & 0xffL) << 8;
    case 1: hash ^= (buffer[off + 0] & 0xffL);
            hash *= m;
    }
    
    hash ^= hash >>> r;
    hash *= m;
    hash ^= hash >>> r;
    
    return hash;
  }

  /**
   * Calculates hash from a String, using utf-16 encoding
   */
  public static long generate(long hash, 
                              final CharSequence value)
  {
    final long m = 0xc6a4a7935bd1e995L;
    final int r = 47;
    
    int strlen = value.length();
    int length = 2 * strlen;
    
    hash ^= length * m;
    
    int len4 = strlen / 4;
    int offset = 0;
    
    for (int i = 0; i < len4; i++) {
      final int index = i * 4 + offset;
      
      long k = (value.charAt(index + 0)
               | ((long) value.charAt(index + 1) << 16)
               | ((long) value.charAt(index + 2) << 32)
               | ((long) value.charAt(index + 3) << 48));
      
      k *= m;
      k ^= k >>> r;
      k *= m;
      hash ^= k;
      hash *= m;
    }
    
    final int off = offset + (strlen & ~0x3);
    
    switch (strlen % 4) {
    case 3: hash ^= (long) value.charAt(off + 2) << 48;
    case 2: hash ^= (long) value.charAt(off + 1) << 32;
    case 1: hash ^= (long) value.charAt(off + 0) << 16;
            hash *= m;
    }

    hash ^= hash >>> r;
    hash *= m;
    hash ^= hash >>> r;
    
    return hash;
  }

  /**
   * Calculates hash from a buffer
   */
  public static long generate(long hash, int key)
  {
    final long m = 0xc6a4a7935bd1e995L;
    final int r = 47;

    int length = 4;
    hash ^= length * m;

    hash ^= Integer.reverseBytes(key);
    hash *= m;
    
    hash ^= hash >>> r;
    hash *= m;
    hash ^= hash >>> r;
    
    return hash;
  }

  /**
   * Calculates hash from a buffer
   */
  public static long generate(long hash, 
                              long value)
  {
    final long m = 0xc6a4a7935bd1e995L;
    final int r = 47;
    
    int length = 8;
    
    hash ^= length * m;

    long k = Long.reverseBytes(value);
      
    k *= m;
    k ^= k >>> r;
    k *= m;
    hash ^= k;
    hash *= m;
    
    hash ^= hash >>> r;
    hash *= m;
    hash ^= hash >>> r;
    
    return hash;
  }
}
