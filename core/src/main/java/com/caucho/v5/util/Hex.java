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

package com.caucho.v5.util;

/**
 * hex decoding.
 */
public class Hex {
  /**
   * Convert bytes to hex
   */
  public static String toHex(byte []bytes)
  {
    if (bytes == null)
      return "null";
    
    return toHex(bytes, 0, bytes.length);
  }
  
  /**
   * Convert bytes to hex
   */
  public static String toHex(byte []bytes, int offset, int len)
  {
    if (bytes == null)
      return "null";
    
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < len; i++) {
      int d1 = (bytes[offset + i] >> 4) & 0xf;
      int d2 = (bytes[offset + i]) & 0xf;

      if (d1 < 10)
        sb.append((char) ('0' + d1));
      else
        sb.append((char) ('a' + d1 - 10));

      if (d2 < 10)
        sb.append((char) ('0' + d2));
      else
        sb.append((char) ('a' + d2 - 10));
    }

    return sb.toString();
  }
  
  public static String toShortHex(byte []bytes)
  {
    if (bytes.length <= 4) {
      return toHex(bytes);
    }
    else {
      StringBuilder sb = new StringBuilder();
      
      sb.append(toHex(bytes, 0, 2));
      sb.append("..");
      sb.append(toHex(bytes, bytes.length - 2, 2));
      
      return sb.toString();
    }
  }
  
  public static String toShortHex(byte []bytes, int offset, int length)
  {
    if (length <= 4) {
      return toHex(bytes, offset, length);
    }
    else {
      StringBuilder sb = new StringBuilder();
      
      sb.append(toHex(bytes, offset, 2));
      sb.append("..");
      sb.append(toHex(bytes, offset + length - 2, 2));
      
      return sb.toString();
    }
  }
  /**
   * Convert hex to bytes
   */
  public static byte []toBytes(String hex)
  {
    if (hex == null)
      return null;
    
    int len = hex.length();

    byte []bytes = new byte[len / 2];

    int k = 0;
    for (int i = 0; i < len; i += 2) {
      int digit = 0;

      char ch = hex.charAt(i);

      if ('0' <= ch && ch <= '9')
        digit = ch - '0';
      else if ('a' <= ch && ch <= 'f')
        digit = ch - 'a' + 10;
      else if ('A' <= ch && ch <= 'F')
        digit = ch - 'A' + 10;

      ch = hex.charAt(i + 1);

      if ('0' <= ch && ch <= '9')
        digit = 16 * digit + ch - '0';
      else if ('a' <= ch && ch <= 'f')
        digit = 16 * digit + ch - 'a' + 10;
      else if ('A' <= ch && ch <= 'F')
        digit = 16 * digit + ch - 'A' + 10;

      bytes[k++] = (byte) digit;
    }

    return bytes;
  }
  
  public static long toLong(byte []bytes)
  {
    int length = bytes.length; // Math.min(8, bytes.length);
    
    long value = 0;
    
    for (int i = 0; i < length; i++) {
      value = 65521 * value + bytes[i];
    }
    
    return value;
  }
}
