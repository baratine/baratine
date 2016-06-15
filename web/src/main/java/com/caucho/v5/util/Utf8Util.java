/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.caucho.v5.io.TempBuffer;

/**
 * Utf8 decoding.
 */
public final class Utf8Util
{
  public static void write(OutputStream os, char ch)
    throws IOException
  {
    if (ch < 0x80)
      os.write(ch);
    else if (ch < 0x800) {
      os.write(0xc0 + (ch >> 6));
      os.write(0x80 + (ch & 0x3f));
    }
    else {
      os.write(0xe0 + (ch >> 12));
      os.write(0x80 + ((ch >> 6) & 0x3f));
      os.write(0x80 + (ch & 0x3f));
    }
  }

  public static void write(OutputStream os, String s)
    throws IOException
  {
    int len = s.length();

    for (int i = 0; i < len; i++) {
      write(os, s.charAt(i));
    }
  }

  public static void write(OutputStream os, char []buffer, int offset, int len)
    throws IOException
  {
    for (int i = 0; i < len; i++) {
      write(os, buffer[offset + i]);
    }
  }

  public static int write(byte []buffer, int offset, char ch)
  {
    if (ch < 0x80) {
      buffer[offset] = (byte) ch;

      return offset + 1;
    }
    else if (ch < 0x800) {
      buffer[offset + 0] = (byte) (0xc0 + (ch >> 6));
      buffer[offset + 1] = (byte) (0x80 + (ch & 0x3f));

      return offset + 2;
    }
    else {
      buffer[offset + 0] = (byte) (0xe0 + (ch >> 12));
      buffer[offset + 1] = (byte) (0x80 + ((ch >> 6) & 0x3f));
      buffer[offset + 2] = (byte) (0x80 + (ch & 0x3f));

      return offset + 3;
    }
  }

  public static String readString(InputStream is)
    throws IOException
  {
    StringBuilder sb = new StringBuilder();

    int ch;

    while ((ch = read(is)) >= 0) {
      sb.append((char) ch);
    }

    return sb.toString();
  }

  public static int read(InputStream is)
    throws IOException
  {
    int ch1 = is.read();

    if (ch1 < 0x80)
      return ch1;

    if ((ch1 & 0xe0) == 0xc0) {
      int ch2 = is.read();

      if (ch2 < 0)
        return -1;
      else if ((ch2 & 0x80) != 0x80)
        return 0xfdff;

      return (((ch1 & 0x1f) << 6)
              | (ch2 & 0x3f));
    }
    else if ((ch1 & 0xf0) == 0xe0) {
      int ch2 = is.read();
      int ch3 = is.read();

      if (ch2 < 0)
        return -1;
      else if ((ch2 & 0x80) != 0x80)
        return 0xfdff;
      else if ((ch3 & 0x80) != 0x80)
        return 0xfdff;

      return (((ch1 & 0xf) << 12)
              | ((ch2 & 0x3f) << 6)
              | ((ch3 & 0x3f) << 6));
    }
    else if (ch1 == 0xff)
      return 0xffff;
    else
      return 0xfdff;
  }

  public static void read(final StringBuilder sb,
                          final byte[] buffer, int offset, int length)
  {
    int tail = offset + length;

    while (offset < tail) {
      int ch = buffer[offset++] & 0xff;

      if (ch < 0x80) {
        sb.append((char) ch);
      }
      else if (ch < 0xe0) {
        if (offset < tail) {
          int ch2 = buffer[offset++] & 0xff;

          sb.append((char) ((ch & 0x1f) << 6 | ch2 & 0x3f));
        }
      }
      else if (ch < 0xf0) {
        if (offset + 1 < tail) {
          int ch2 = buffer[offset++] & 0xff;
          int ch3 = buffer[offset++] & 0xff;

          sb.append((char) ((ch & 0x0f) << 12 | (ch2 & 0x3f) << 6 | ch3 & 0x3f));
        }
      }
      else {
        if (offset + 2 < tail) {
          int ch2 = buffer[offset++] & 0xff;
          int ch3 = buffer[offset++] & 0xff;
          int ch4 = buffer[offset++] & 0xff;

          int codePoint = (ch & 0x07) << 18 | (ch2 & 0x3f) << 12 | (ch3 & 0x3f) << 6 | ch4 & 0x3f;

          if (Character.isSupplementaryCodePoint(codePoint)) {
            sb.append(Character.highSurrogate(codePoint));
            sb.append(Character.lowSurrogate(codePoint));
          }
          else {
            sb.append((char) codePoint);
          }
        }
      }
    }
  }

  /**
   * @return number of characters able to be written to the buffer, which may
   *         be less than the number passed in
   */
  public static int write(TempBuffer out,
                         final char []cBuf, int cOffset, int cLength)
  {
    byte []buffer = out.buffer();
    int bOffset = out.length();
    int bLength = buffer.length;

    int charsWritten = 0;

    while (cOffset < cLength) {
      int sublen = Math.min(cLength - charsWritten, bLength - bOffset);
      char ch;
      int i = 0;

      for (; i < sublen && (ch = cBuf[cOffset + i]) < 0x80; i++) {
        buffer[bOffset + i] = (byte) ch;
      }

      cOffset += i;
      bOffset += i;
      charsWritten += i;

      if (charsWritten == cLength) {
        out.length(bOffset);

        return charsWritten;
      }

      sublen = Math.min(cLength - charsWritten, (bLength - bOffset) >> 2);

      for (i = 0; i < sublen; i++) {
        ch = cBuf[cOffset + i];

        if (ch < 0x80) {
          buffer[bOffset++] = (byte) ch;
        }
        else if (ch < 0x800) {
          buffer[bOffset++] = (byte) (0xc0 + (ch >> 6));
          buffer[bOffset++] = (byte) (0x80 + (ch & 0x3f));
        }
        else if ('\ud800' <= ch && ch <= '\udbff') {
          // surrogate pair

          i++;
          if (cOffset + i >= sublen) {
            continue;
          }

          char ch2 = cBuf[cOffset + i];

          if ('\udc00' <= ch2 && ch2 <= '\udfff') {
            int codePoint = Character.toCodePoint(ch, ch2);

            bOffset += encodeUtf8(buffer, bOffset, codePoint);
          }
        }
        else {
          buffer[bOffset++] = (byte) (0xe0 + (ch >> 12));
          buffer[bOffset++] = (byte) (0x80 + ((ch >> 6) & 0x3f));
          buffer[bOffset++] = (byte) (0x80 + (ch & 0x3f));
        }
      }

      cOffset += i;
      charsWritten += i;

      if (bLength - bOffset < 4) {
        out.length(bOffset);
        return charsWritten;
      }
    }

    out.length(bOffset);

    return charsWritten;
  }

  private static int encodeUtf8(byte[] buffer, int offset, int codePoint)
  {
    if (codePoint <= 0x7f) {
      buffer[offset] = (byte) codePoint;

      return 1;
    }
    else if (codePoint <= 0x7ff) {
      buffer[offset] = (byte) (0xc0 + ((codePoint >> 6) & 0x1f));
      buffer[offset + 1] = (byte) (0x80 + (codePoint & 0x3f));

      return 2;
    }
    else if (codePoint <= 0xffff) {
      buffer[offset] = (byte) (0xe0 + ((codePoint >> 12) & 0x0f));
      buffer[offset + 1] = (byte) (0x80 + ((codePoint >> 6) & 0x3f));
      buffer[offset + 2] = (byte) (0x80 + (codePoint & 0x3f));

      return 3;
    }
    else {
      buffer[offset] = (byte) (0xf0 + ((codePoint >> 18) & 0x07));
      buffer[offset + 1] = (byte) (0x80 + ((codePoint >> 12) & 0x3f));
      buffer[offset + 2] = (byte) (0x80 + ((codePoint >> 6) & 0x3f));
      buffer[offset + 3] = (byte) (0x80 + (codePoint & 0x3f));

      return 4;
    }
  }
}
