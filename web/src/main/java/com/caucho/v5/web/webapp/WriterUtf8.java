/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

package com.caucho.v5.web.webapp;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

import com.caucho.v5.io.OutputStreamWithBuffer;
import com.caucho.v5.util.Utf8Util;


/**
 * writer special-cased for utf-8
 */
public final class WriterUtf8 extends Writer
{
  private final int BUFFER_SIZE = 64;
  
  private OutputStreamWithBuffer _os;
  private char []_cBuf;
  private int _cOffset;
  
  public WriterUtf8(OutputStreamWithBuffer os, char []cBuf)
  {
    Objects.requireNonNull(os);
    Objects.requireNonNull(cBuf);
    
    _os = os;
    _cBuf = cBuf;
  }
  
  @Override
  public void write(int ch)
    throws IOException
  {
    if (ch < 0x80) {
      _os.write(ch);
      return;
    }
    
    int cOffset = _cOffset;
    
    char[] buffer = buffer();
    
    buffer[cOffset++] = (char) ch;
    _cOffset = 0;
    
    write(buffer, 0, cOffset);
  }
  
  @Override
  public void write(char []buffer, int offset, int length)
    throws IOException
  {
    // tailing surrogate pair
    if (_cOffset > 0 && length > 0) {
      _cBuf[1] = buffer[offset];
      _cOffset = 0;
      write(_cBuf, 0, 2);
      offset++;
      length--;
    }
    
    int end = offset + length;
    
    OutputStreamWithBuffer os = _os;
    byte []bBuffer = os.buffer();
    int bOffset = os.offset();

    while (offset < end) {
      int sublen = Math.min(end - offset, (bBuffer.length - bOffset) >> 4);
      int subEnd = offset + sublen;
      char ch;
      
      // ascii
      for (; offset < subEnd && (ch = buffer[offset]) < 0x80; offset++) {
        bBuffer[bOffset++] = (byte) ch;
      }
      
      for (; offset < subEnd; offset++) {
        ch = buffer[offset];
        
        if (ch < 0x80) {
          bBuffer[bOffset++] = (byte) ch;
        }
        else if (ch < 0x800) {
          bBuffer[bOffset++] = (byte) (0xc0 + (ch >> 6));
          bBuffer[bOffset++] = (byte) (0x80 + (ch & 0x3f));
        }
        else if (Character.isSurrogate(ch)) {
          if (offset + 1 < end) {
            char chLow = buffer[++offset];
            int code = Character.toCodePoint(ch, chLow);
            
            bBuffer[bOffset++] = (byte) (0xf0 + (code >> 18));
            bBuffer[bOffset++] = (byte) (0x80 + ((code >> 12) & 0x3f));
            bBuffer[bOffset++] = (byte) (0x80 + ((code >> 6) & 0x3f));
            bBuffer[bOffset++] = (byte) (0x80 + (code & 0x3f));
          }
          else {
            char []cBuf = buffer();
            cBuf[0] = ch;
            _cOffset = 1;
          }
        }
        else {
          bBuffer[bOffset++] = (byte) (0xe0 + (ch >> 12));
          bBuffer[bOffset++] = (byte) (0x80 + ((ch >> 6) & 0x3f));
          bBuffer[bOffset++] = (byte) (0x80 + (ch & 0x3f));
        }
      }
      
      if (end <= offset) {
        os.offset(bOffset);
        return;
      }
      
      if (bBuffer.length - bOffset < 16) {
        bBuffer = os.nextBuffer(bOffset);
        bOffset = os.offset();
      }
    }
  }
  
  @Override
  public void write(String value)
    throws IOException
  {
    write(value, 0, value.length());
  }
  
  @Override
  public void write(String value, int offset, int length)
    throws IOException
  {
    int end = offset + length;
    
    if (length < 16) {
      offset = writeShort(value, offset, end);
    }
    
    char []cBuf = buffer();
    
    while (offset < end) {
      int cOffset = _cOffset;
      int sublen = Math.min(end - offset, cBuf.length - cOffset);
      
      value.getChars(offset, offset + sublen, cBuf, cOffset);
      _cOffset = cOffset;
      
      write(cBuf, 0, sublen);
      
      offset += sublen;
    }
  }
  
  /**
   * Writes a short string.
   */
  private int writeShort(String value, int offset, int end)
    throws IOException
  {
    int ch;
  
    OutputStreamWithBuffer os = _os;
    byte []buffer = os.buffer();
    int bOffset = os.offset();
    
    end = Math.min(end, offset + buffer.length - bOffset);
  
    for (; offset < end && (ch = value.charAt(offset)) < 0x80; offset++) {
      buffer[bOffset++] = (byte) ch;
    }
    
    os.offset(bOffset);
    
    return offset;
  }
  
  private final char []buffer()
  {
    return _cBuf;
  }
  
  @Override
  public void flush()
    throws IOException
  {
    _os.flush();
  }
  
  @Override
  public void close()
    throws IOException
  {
    // XXX: surrogate pair
    _os.close();
    _cBuf = null;
  }
}
