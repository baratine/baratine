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

package com.caucho.v5.config.types;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.util.L10N;

/**
 * Returns a number of bytes, allowing EL expressions.  Default is bytes.
 *
 * <pre>
 * b  : bytes
 * k  : kilobytes
 * kb : kilobytes
 * m  : megabytes
 * mb : megabytes
 * g  : gigabytes
 * </pre>
 */
public class Bytes {
  private static final L10N L = new L10N(Bytes.class);

  public static final long BYTE = 1L;
  public static final long KILOBYTE = 1024;
  public static final long MEGABYTE = 1024 * 1024;
  public static final long GIGABYTE = 1024 * 1024 * 1024;

  public static final long INFINITE = Long.MAX_VALUE / 2;

  private long _bytes;

  public Bytes()
  {
  }

  public Bytes(long bytes)
  {
    _bytes = bytes;
  }

  /**
   * Sets the text.
   */
  public void addText(String text)
    throws ConfigException
  {
    _bytes = toBytes(text);
  }

  /**
   * Replace with the real bytes.
   */
  public long getBytes()
  {
    return _bytes;
  }

  /**
   * Converts a byte string to a number of bytes.  Default is bytes.
   *
   * <pre>
   * b  : bytes
   * k  : kilobytes
   * kb : kilobytes
   * m  : megabytes
   * mb : megabytes
   * g  : gigabytes
   * </pre>
   */
  public static long toBytes(String bytes)
    throws ConfigException
  {
    if (bytes == null)
      return -1;

    long value = 0;
    long sign = 1;
    int i = 0;
    int length = bytes.length();

    if (length == 0)
      return -1;

    if (bytes.charAt(i) == '-') {
      sign = -1;
      i++;
    }
    else if (bytes.charAt(i) == '+') {
      i++;
    }

    if (length <= i)
      return -1;

    int ch;
    for (; i < length && (ch = bytes.charAt(i)) >= '0' && ch <= '9'; i++)
      value = 10 * value + ch - '0';

    value = sign * value;

    if (bytes.endsWith("gb") || bytes.endsWith("g") || bytes.endsWith("G")) {
      return value * 1024L * 1024L * 1024L;
    }
    else if (bytes.endsWith("mb") || bytes.endsWith("m") || bytes.endsWith("M")) {
      return value * 1024L * 1024L;
    }
    else if (bytes.endsWith("kb") || bytes.endsWith("k") || bytes.endsWith("K")) {
      return value * 1024L;
    }
    else if (bytes.endsWith("b") || bytes.endsWith("B")) {
      return value;
    }
    else if (value < 0)
      return value;
    else {
      throw new ConfigException(L.l("byte-valued expression `{0}' must have units.  '16B' for bytes, '16K' for kilobytes, '16M' for megabytes, '16G' for gigabytes.", bytes));
    }
  }
}
