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

package com.caucho.v5.io.i18n;

import com.caucho.v5.io.ByteAppendable;
import com.caucho.v5.io.OutputStreamWithBuffer;

import java.io.IOException;

/**
 * Implements the ISO-8859-1 EncodingWriter factory.
 */
public final class ISO8859_1Writer extends EncodingWriter {
  private final static ISO8859_1Writer _writer = new ISO8859_1Writer();

  /**
   * Null-arg constructor for instantiation by com.caucho.v5.vfs.Encoding only.
   */
  public ISO8859_1Writer()
  {
  }
  
  /**
   * Returns the Java encoding for the writer.
   */
  public String getJavaEncoding()
  {
    return "ISO8859_1";
  }

  /**
   * Returns null, since WriteStream handles ISO-8859-1 directly.
   *
   * @return null for ISO-8859-1
   */
  public EncodingWriter create(String javaEncoding)
  {
    return _writer;
  }

  /**
   * Returns null, since WriteStream handles ISO-8859-1 directly.
   *
   * @return null for ISO-8859-1
   */
  public EncodingWriter create()
  {
    return _writer;
  }

  /**
   * Returns the writer.
   */
  public static EncodingWriter getStaticWriter()
  {
    return _writer;
  }
  
  /**
   * Writes a character to the output stream with the correct encoding.
   *
   * @param ch the character to write.
   */
  public void write(ByteAppendable os, char ch)
    throws IOException
  {
    os.write(ch < 0x100 ? ch : 0x3f);
  }

  /**
   * Writes a character buffer using the correct encoding.
   *
   * @param os output stream for data.
   * @param cBuf data.
   * @param cOffset starting offset into the buffer.
   * @param cLength number of characters to write
   */
  @Override
  public int write(OutputStreamWithBuffer os,
                   char []cBuf, int cOffset, int cLength)
    throws IOException
  {
    byte []bBuf = os.getBuffer();
    int bOffset = os.getBufferOffset();
    int bEnd = bBuf.length;

    int cEnd = cOffset + cLength;
    // int cEnd = cOffset + cLength;

    while (cOffset < cEnd) {
      int sublen = Math.min(bEnd - bOffset, cEnd - cOffset);

      for (int i = sublen - 1; i >= 0; i--) {
        char ch = cBuf[cOffset + i];
        
        bBuf[bOffset + i] = (byte) (ch < 0x100 ? ch : 0x3f);
      }

      bOffset += sublen;
      cOffset += sublen;
      
      if (bOffset == bEnd && cLength > 0) {
        bBuf = os.nextBuffer(bOffset);
        bOffset = os.getBufferOffset();
        bEnd = bBuf.length;
      }
    }

    os.setBufferOffset(bOffset);
    
    return cLength;
  }
}
