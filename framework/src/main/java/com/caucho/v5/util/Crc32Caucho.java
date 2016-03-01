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
 * Crc64 hash
 */
public class Crc32Caucho {
  private static final int POLY32REV = 0x04c1_1db7;
  private static final int []CRC_TABLE;

  /**
   * Calculates CRC from a string.
   */
  public static int generate(String value)
  {
    int len = value.length();
    int crc = 0;

    for (int i = 0; i < len; i++) {
      crc = next(crc, value.charAt(i));
    }

    return crc;
  }
  
  /**
   * Calculates CRC from a string.
   */
  public static int generate(int crc, Enum<?> value)
  {
    return generate(crc, value.ordinal());
  }

  /**
   * Calculates CRC from a string.
   */
  public static int generate(int crc, String value)
  {
    if (value == null) {
      return crc;
    }
    
    int len = value.length();

    for (int i = 0; i < len; i++) {
      char ch = value.charAt(i);

      if (ch > 0xff)
        crc = next(crc, (ch >> 8));

      crc = next(crc, ch);
    }

    return crc;
  }

  /**
   * Calculates CRC from a char buffer
   */
  public static int generate(int crc, char []buffer, int offset, int len)
  {
    for (int i = 0; i < len; i++) {
      char ch = buffer[offset + i];

      if (ch > 0xff) {
        crc = next(crc, (ch >> 8));
      }

      crc = next(crc, ch);
    }

    return crc;
  }

  /**
   * Calculates CRC from a char buffer
   */
  public static int generate(int crc, byte []buffer, int offset, int len)
  {
    for (int i = 0; i < len; i++) {
      crc = next(crc, buffer[offset + i]);
    }

    return crc;
  }

  /**
   * Calculates CRC from a buffer
   */
  public static int generate(int crc, byte []buffer)
  {
    return generate(crc, buffer, 0, buffer.length);
  }

  /**
   * Calculates CRC from a long
   */
  public static int generate(int crc, long value)
  {
    crc = next(crc, (byte) (value >> 56));
    crc = next(crc, (byte) (value >> 48));
    crc = next(crc, (byte) (value >> 40));
    crc = next(crc, (byte) (value >> 32));
    
    crc = next(crc, (byte) (value >> 24));
    crc = next(crc, (byte) (value >> 16));
    crc = next(crc, (byte) (value >> 8));
    crc = next(crc, (byte) (value >> 0));

    return crc;
  }

  /**
   * Calculates CRC from an int
   */
  public static int generate(int crc, int value)
  {
    return next(crc, value);
  }

  /**
   * Calculates CRC from an int
   */
  public static int generateInt16(int crc, int value)
  {
    crc = next(crc, (value >> 8));
    crc = next(crc, (value >> 0));

    return crc;
  }

  /**
   * Calculates CRC from an int
   */
  public static int generateInt32(int crc, int value)
  {
    crc = next(crc, (value >> 24));
    crc = next(crc, (value >> 16));
    crc = next(crc, (value >> 8));
    crc = next(crc, (value >> 0));

    return crc;
  }

  /**
   * Calculates the next crc value.
   */
  public static int generate(int crc, byte ch)
  {
    return (crc << 8) ^ CRC_TABLE[((crc >> 24) ^ ch) & 0xff];
  }

  /**
   * Calculates the next crc value.
   */
  public static int next(int crc, int ch)
  {
    return (crc << 8) ^ CRC_TABLE[((crc >> 24) ^ ch) & 0xff];
  }

  static {
    CRC_TABLE = new int[256];

    for (int i = 0; i < 256; i++) {
      int v = i << 24;

      for (int j = 0; j < 8; j++) {
        if ((v & 0x8000_0000L) != 0) {
          v = (v << 1) ^ POLY32REV;
        }
        else {
          v = (v << 1);
        }
      }

      CRC_TABLE[i] = v;
    }
  }
}
