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

package com.caucho.v5.vfs;

import java.io.CharConversionException;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.io.i18n.Encoding;

public class ReaderWriterStream extends StreamImpl {
  private static NullPath nullPath;

  private Reader is;
  private Writer os;
  private boolean flushOnNewline;
  private boolean closeChildOnClose = true;

  private int peek1;
  private int peek2;

  public ReaderWriterStream(Reader is, Writer os)
  {
    init(is, os);
    if (nullPath == null)
      nullPath = new NullPath("stream");
    //setPath(nullPath);
  }

  public ReaderWriterStream(Reader is, Writer os, PathImpl path)
  {
    init(is, os);
    //setPath(path);
  }

  public void init(Reader is, Writer os)
  {
    this.is = is;
    this.os = os;
    //setPath(nullPath);
    peek1 = -1;
    peek2 = -1;
  }

  public String getEncoding()
  {
    if (os instanceof OutputStreamWriter)
      return Encoding.getMimeName(((OutputStreamWriter) os).getEncoding());
    else
      return null;
  }

  public boolean canRead()
  {
    return is != null;
  }

  public int read(byte []buf, int offset, int length) throws IOException
  {
    if (is == null)
      return -1;

    for (int i = 0; i < length; i++) {
      int ch;
      if (peek1 >= 0) {
        buf[offset++] = (byte) peek1;
        peek1 = peek2;
        peek2 = -1;
      }
      else if ((ch = is.read()) < 0) {
        return i == 0 ? -1 : i;
      }
      else if (ch < 0x80) {
        buf[offset++] = (byte) ch;
      }
      else if (ch < 0x800) {
        buf[offset++] = (byte) (0xc0 + (ch >> 6));
        peek1 = 0x80 + (ch & 0x3f);
      }
      else {
        buf[offset++] = (byte) (0xe0 + (ch >> 12));
        peek1 = 0x80 + ((ch >> 6) & 0x3f);
        peek2 = 0x80 + (ch & 0x3f);
      }
    }

    return length;
  }

  public boolean canWrite()
  {
    return os != null;
  }

  public boolean getFlushOnNewline()
  {
    return flushOnNewline;
  }

  public void setFlushOnNewline(boolean value)
  {
    flushOnNewline = value;
  }

  /**
   * Implementation of the writer write.
   *
   * @param buf byte buffer containing the bytes
   * @param offset offset where to start writing
   * @param length number of bytes to write
   * @param isEnd true when the write is flushing a close.
   */
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    int end = offset + length;
    while (offset < end) {
      int ch1 = buf[offset++] & 0xff;

      if (ch1 < 0x80)
        os.write(ch1);
      else if ((ch1 & 0xe0) == 0xc0) {
        if (offset >= end)
          throw new EOFException("unexpected end of file in utf8 character");
        
        int ch2 = buf[offset++] & 0xff;
        if ((ch2 & 0xc0) != 0x80)
          throw new CharConversionException("illegal utf8 encoding");
      
        os.write(((ch1 & 0x1f) << 6) + (ch2 & 0x3f));
      }
      else if ((ch1 & 0xf0) == 0xe0) {
        if (offset + 1 >= end)
          throw new EOFException("unexpected end of file in utf8 character");
        
        int ch2 = buf[offset++] & 0xff;
        int ch3 = buf[offset++] & 0xff;
      
        if ((ch2 & 0xc0) != 0x80)
          throw new CharConversionException("illegal utf8 encoding");
      
        if ((ch3 & 0xc0) != 0x80)
          throw new CharConversionException("illegal utf8 encoding");
      
        os.write(((ch1 & 0x1f) << 12) + ((ch2 & 0x3f) << 6) + (ch3 & 0x3f));
      }
      else
        throw new CharConversionException("illegal utf8 encoding at (" +
                                          (int) ch1 + ")");
    }
  }

  public void flush() throws IOException
  {
    if (os != null)
      os.flush();
  }

  public void setCloseChildOnClose(boolean close)
  {
    closeChildOnClose = close;
  }

  public void close() throws IOException
  {
    if (os != null && closeChildOnClose) {
      os.close();
      os = null;
    }

    if (is != null && closeChildOnClose) {
      is.close();
      is = null;
    }
  }
}
