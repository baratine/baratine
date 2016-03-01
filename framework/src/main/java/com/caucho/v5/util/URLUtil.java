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
 * URL utilities.
 */
public class URLUtil {
  /**
   * Encode the url with '%' encoding.
   */
  public static String encodeURL(String uri)
  {
    CharBuffer cb = CharBuffer.allocate();

    for (int i = 0; i < uri.length(); i++) {
      char ch = uri.charAt(i);

      switch (ch) {
      case '<':
      case '>':
      case ' ':
      case '%':
      case '\'':
      case '\"':
        cb.append('%');
        cb.append(encodeHex(ch >> 4));
        cb.append(encodeHex(ch));
        break;

      default:
        cb.append(ch);
      }
    }

    return cb.close();
  }

  public static String byteToHex(int b)
  {
    CharBuffer cb = CharBuffer.allocate();

    cb.append(encodeHex((b & 0xf0) >> 4));
    cb.append(encodeHex(b & 0xf));

    return cb.close();
  }

  public static char encodeHex(int ch)
  {
    ch &= 0xf;

    if (ch < 10)
      return (char) (ch + '0');
    else
      return (char) (ch + 'a' - 10);
  }
}
