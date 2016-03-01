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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * An abstract print writer.
 */
public abstract class AbstractWriter extends Writer {
  private static final char []_trueChars = "true".toCharArray();
  private static final char []_falseChars = "false".toCharArray();
  private static final char []_nullChars = "null".toCharArray();

  private final char []_tempCharBuffer;
  
  protected AbstractWriter(char []buffer)
  {
    _tempCharBuffer = buffer;
  }
  
  protected AbstractWriter()
  {
    this(new char[128]);
  }

  /**
   * Writes a character array to the writer.
   *
   * @param buf the buffer to write.
   * @param off the offset into the buffer
   * @param len the number of characters to write
   */
  @Override
  abstract public void write(char []buf, int offset, int length)
    throws IOException;
  
  /**
   * Writes a character to the output.
   *
   * @param buf the buffer to write.
   */
  @Override
  public void write(int ch)
    throws IOException
  {
    char []buffer = _tempCharBuffer;
      
    buffer[0] = (char) ch;
      
    write(buffer, 0, 1);
  }

  /**
   * Writes a subsection of a string to the output.
   */
  @Override
  public void write(String s, int off, int len)
    throws IOException
  {
    char []buffer = _tempCharBuffer;
    int bufferLength = buffer.length;
    
    while (len > 0) {
      int sublen = Math.min(len, bufferLength);
      
      s.getChars(off, off + sublen, buffer, 0);
      
      write(buffer, 0, sublen);
      
      off += sublen;
      len -= sublen;
    }
  }

  /**
   * Writes a char buffer to the output.
   *
   * @param buf the buffer to write.
   */
  @Override
  final public void write(char []buf)
    throws IOException
  {
    write(buf, 0, buf.length);
  }

  /**
   * Writes a string to the output.
   */
  @Override
  final public void write(String s)
    throws IOException
  {
    if (s == null) {
      write(_nullChars, 0, _nullChars.length);
      return;
    }
    
    write(s, 0, s.length());
  }
  
  @Override
  final public Writer append(char ch)
    throws IOException
  {
    char []buffer = _tempCharBuffer;
    
    buffer[0] = (char) ch;
    
    write(buffer, 0, 1);
    
    return this;
  }
  
  @Override
  final public Writer append(CharSequence csq, int start, int end)
    throws IOException
  {
    if (csq == null) {
      write(_nullChars, 0, _nullChars.length);
      return this;
    }
    
    char []buffer = _tempCharBuffer;
    int bufferLength = buffer.length;
      
    while (start < end) {
      int sublen = Math.min(end - start, bufferLength);

      for (int i = sublen - 1; i >= 0; i--) {
        buffer[i] = csq.charAt(i + start);
      }
        
      write(buffer, 0, sublen);
        
      start += sublen;
    }
    
    return this;
  }
  
  @Override
  final public Writer append(CharSequence csq)
    throws IOException
  {
    if (csq == null) {
      write(_nullChars, 0, _nullChars.length);
      return this;
    }
      
    append(csq, 0, csq.length());
    
    return this;
  }
}
