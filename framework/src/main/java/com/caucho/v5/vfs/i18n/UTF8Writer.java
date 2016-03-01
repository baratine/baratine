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

package com.caucho.v5.vfs.i18n;

import java.io.IOException;

import com.caucho.v5.io.ByteAppendable;
import com.caucho.v5.io.OutputStreamWithBuffer;
import com.caucho.v5.io.i18n.EncodingWriter;

/**
 * Implements an encoding char-to-byte writer for UTF8 and the associated
 * factory.
 */
public class UTF8Writer extends EncodingWriter {
  private final static UTF8Writer _writer = new UTF8Writer();

  /**
   * Null-arg constructor for instantiation by com.caucho.v5.vfs.Encoding only.
   */
  public UTF8Writer()
  {
  }
  
  /**
   * Returns the Java encoding for the writer.
   */
  @Override
  public String getJavaEncoding()
  {
    return "UTF8";
  }

  /**
   * Returns the UTF8_Writer
   *
   * @return the UTF8_Writer
   */
  @Override
  public EncodingWriter create(String javaEncoding)
  {
    return _writer;
  }

  /**
   * Returns the UTF8_Writer
   *
   * @return the UTF8_Writer
   */
  public EncodingWriter create()
  {
    return _writer;
  }

  /**
   * Writes a character to the output stream with the correct encoding.
   *
   * @param ch the character to write.
   */
  @Override
  public void write(ByteAppendable os, char ch)
    throws IOException
  {
    if (ch < 0x80) {
      os.write(ch);
    }
    else if (ch < 0x800) {
      os.write((0xc0 + (ch >> 6)));
      os.write((0x80 + (ch & 0x3f)));
    }
    else {
      os.write((0xe0 + (ch >> 12)));
      os.write((0x80 + ((ch >> 6) & 0x3f)));
      os.write((0x80 + (ch & 0x3f)));
    }
  }

  /**
   * Writes into a character buffer using the correct encoding.
   *
   * @param cbuf character array with the data to write.
   * @param off starting offset into the character array.
   * @param len the number of characters to write.
   */
  @Override
  public int write(OutputStreamWithBuffer os, char []cbuf, int off, int len)
    throws IOException
  {
    byte []buffer = os.getBuffer();
    int length = os.getBufferOffset();
    int capacity = buffer.length;
    int tail = off + len;
    int head = off;

    while (off < tail) {
      while (capacity - length <= 4) {
        buffer = os.nextBuffer(length);
        length = os.getBufferOffset();
      }
      
      char ch = cbuf[off++];

      if (ch < 0x80) {
        buffer[length++] = (byte) ch;
      }
      else if (ch < 0x800) {
        buffer[length++] = (byte) (0xc0 + (ch >> 6));
        buffer[length++] = (byte) (0x80 + (ch & 0x3f));
      }
      else if (ch < 0xd800 || 0xdfff < ch) {
        // server/0815
        buffer[length++] = (byte) (0xe0 + (ch >> 12));
        buffer[length++] = (byte) (0x80 + ((ch >> 6) & 0x3f));
        buffer[length++] = (byte) (0x80 + (ch & 0x3f));
      }
      else if (off == tail) {
        off--;
        break;
      }
      else {
        char ch2 = cbuf[off++];
        
        int v = 0x10000 + (ch & 0x3ff) * 0x400 + (ch2 & 0x3ff);
          
        buffer[length++] = (byte) (0xf0 + (v >> 18));
        buffer[length++] = (byte) (0x80 + ((v >> 12) & 0x3f));
        buffer[length++] = (byte) (0x80 + ((v >> 6) & 0x3f));
        buffer[length++] = (byte) (0x80 + (v & 0x3f));
      }
    }

    os.setBufferOffset(length);
    
    return off - head;
  }
}


