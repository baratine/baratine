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

package com.caucho.v5.h3.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import com.caucho.v5.io.TempBuffer;

/**
 * H3 output interface
 */
public class OutRawH3Impl implements OutRawH3
{
  private OutputStream _os;
  
  private byte []_buffer;
  private int _offset;
  private int _length;
  
  private char []_charBuffer = new char[256];

  private TempBuffer _tBuf;
  
  public OutRawH3Impl(OutputStream os)
  {
    Objects.requireNonNull(os);
    _os = os;
    
    _tBuf = TempBuffer.create();
    _buffer = _tBuf.buffer();
    _length = _buffer.length;
  }
  
  /**
   * null
   */
  @Override
  public void writeNull()
  {
    require(1);
    
    _buffer[_offset++] = (byte) ConstH3.NULL; 
  }

  /**
   * boolean
   */
  @Override
  public void writeBoolean(boolean value)
  {
    require(1);
    
    _buffer[_offset++] = (byte) (value ? ConstH3.TRUE : ConstH3.FALSE); 
  }
  
  /**
   * long
   */
  @Override
  public void writeLong(long value)
  {
    if (value < 0) {
      value = ((- value - 1) << 1) + 1;
    }
    else {
      value = value << 1;
    }
    
    writeLong(ConstH3.INTEGER, ConstH3.INTEGER_BITS, value);
  }
  
  /**
   * double
   */
  @Override
  public void writeDouble(double value)
  {
    long bits = Double.doubleToRawLongBits(value);
    
    require(9);
    
    byte []buffer = _buffer;
    int offset = _offset;
    
    buffer[offset++] = (byte) ConstH3.DOUBLE;
    buffer[offset++] = (byte) (bits >> 56);
    buffer[offset++] = (byte) (bits >> 48);
    buffer[offset++] = (byte) (bits >> 40);
    buffer[offset++] = (byte) (bits >> 32);
    buffer[offset++] = (byte) (bits >> 24);
    buffer[offset++] = (byte) (bits >> 16);
    buffer[offset++] = (byte) (bits >> 8);
    buffer[offset++] = (byte) (bits);
    
    _offset = offset;
  }
  
  /**
   * float
   */
  @Override
  public void writeFloat(float value)
  {
    int bits = Float.floatToRawIntBits(value);
    
    require(5);
    
    byte []buffer = _buffer;
    int offset = _offset;
    
    buffer[offset++] = (byte) ConstH3.FLOAT;
    buffer[offset++] = (byte) (bits >> 24);
    buffer[offset++] = (byte) (bits >> 16);
    buffer[offset++] = (byte) (bits >> 8);
    buffer[offset++] = (byte) (bits);
    
    _offset = offset;
  }
  
  /**
   * string
   */
  @Override
  public void writeString(String value)
  {
    if (value == null) {
      writeNull();
      return;
    }

    int strlen = value.length();
    
    writeLong(ConstH3.STRING, ConstH3.STRING_BITS, strlen);
    
    writeStringData(value, 0, strlen);
  }
  
  /**
   * binary
   */
  @Override
  public void writeBinary(byte []buffer, int offset, int length)
  {
    Objects.requireNonNull(buffer);

    writeLong(ConstH3.BINARY, ConstH3.BINARY_BITS, length);
    
    writeBinaryData(buffer, offset, length);
  }
  
  //
  // untyped or partial
  //
  // These do not write a complete opcode on their own, but write parts
  // of more complex opcodes
  //
  
  /**
   * long encoded without type or sign
   */
  @Override
  public void writeUnsigned(long value)
  {
    int tail = (int) (value & 0x7f);
    
    writeLong(0, 8, tail);
  }
  
  /**
   * chunked header. LSB 
   */
  @Override
  public void writeChunk(long length, boolean isFinal)
  {
    long chunk;
    
    if (isFinal) {
      chunk = length << 1;
    }
    else {
      chunk = (length << 1) + 1;
    }
    
    writeUnsigned(chunk);
  }
  
  /**
   * string data without length
   */
  @Override
  public void writeStringData(String value, int offset, int length)
  {
    char []cBuf = _charBuffer;
    int cBufLength = cBuf.length;
    
    for (int i = 0; i < length; i += cBufLength) {
      int sublen = Math.min(length - i, cBufLength);
      
      value.getChars(offset + i, offset + i + sublen, cBuf, 0);
      
      writeStringChunk(cBuf, 0, sublen);
    }
  }
  
  private void writeStringChunk(char []cBuf, int offset, int length)
  {
    int end = offset + length;
    
    byte []bBuf = _buffer;
    int bOffset = _offset;
    int bLength = bBuf.length;
    
    while (offset < end) {
      if (bLength - bOffset < 4) {
        bOffset = flush(bOffset);
      }
      
      int sublen = Math.min(end - offset, (bLength - bOffset) >> 2);
      
      int subEnd = offset + sublen;
      
      for (; offset < subEnd; offset++) {
        char ch = cBuf[offset];
        
        if (ch < 0x80) {
          bBuf[bOffset++] = (byte) ch;
        }
        else if (ch < 0x800) {
          bBuf[bOffset++] = (byte) (0xc0 + (ch >> 6));
          bBuf[bOffset++] = (byte) (0x80 + (ch & 0x3f));
        }
        else {
          bBuf[bOffset++] = (byte) (0xe0 + ch >> 12);
          bBuf[bOffset++] = (byte) (0x80 + ((ch >> 6) & 0x3f));
          bBuf[bOffset++] = (byte) (0x80 + (ch & 0x3f));
        }
      }
    }
    
    _offset = bOffset;
  }
  
  /**
   * binary data without type or length
   */
  @Override
  public void writeBinaryData(byte []sBuf, int sOffset, int sLength)
  {
    byte []tBuf = _buffer;
    int tOffset = _offset;
    int tLength = tBuf.length;
    
    int end = sOffset + sLength;
    
    while (sOffset < end) {
      if (tLength - tOffset < 1) {
        tOffset = flush(tOffset);
      }
      
      int sublen = Math.min(tLength - tOffset, end - sOffset);
      
      System.arraycopy(sBuf, sOffset, tBuf, tOffset, sublen);
      
      tOffset += sublen;
      sOffset += sublen;
    }
    
    _offset = tOffset;
  }
  
  /**
   * Require empty space in the output buffer.
   * 
   * If not enough space is available, flush the buffer.
   */
  private void require(int len)
  {
    int offset = _offset;
    int length = _length;
    
    if (offset + len < length) {
      return;
    }
    
    flush(_offset);
  }
  
  /**
   * Flush the buffer and set the offset to zero.
   */
  private int flush(int offset)
  {
    try {
      _os.write(_buffer, 0, offset);
      _offset = 0;
    
      return 0;
    } catch (IOException e) {
      throw new H3ExceptionOut(e);
    }
  }
  public void flush()
  {
    try {
      if (_tBuf == null) {
        return;
      }
    
      if (_offset == 0) {
        return;
      }
    
      _os.write(_buffer, 0, _offset);
      _offset = 0;
    } catch (Exception e) {
      throw new H3ExceptionOut(e);
    }
  }

  @Override
  public void writeObjectDefinition(int defIndex, 
                                    ClassInfoH3 objectInfo)
  {
    Objects.requireNonNull(objectInfo);
    
    require(1);
    _buffer[_offset++] = ConstH3.OBJECT_DEF;
    
    writeUnsigned(defIndex);
    writeString(objectInfo.name());
    writeUnsigned(objectInfo.type().ordinal());
    
    FieldInfoH3[] fields = objectInfo.fields();
    
    writeUnsigned(fields.length);
    for (int i = 0; i < fields.length; i++) {
      writeString(fields[i].name());
      writeUnsigned(1); // object
    }
  }

  @Override
  public void writeObject(int defIndex)
  {
    writeLong(ConstH3.OBJECT, ConstH3.OBJECT_BITS, defIndex);
  }
  
  private void writeLong(int op, int bits, long value)
  {
    bits -= 1;
    int tail = (int) (value & ((1 << bits) - 1));
    value = value >>> bits;
    
    if (value == 0) {
      require(1);
      _buffer[_offset++] = (byte) (op + tail);
      return;
    }
    

    require(9);
      
    byte []buffer = _buffer;
    int offset = _offset;
      
    buffer[offset++] = (byte) (op + tail + (1 << bits));
      
    while (true) {
      tail = (int) (value & 0x7f);
      value >>>= 7;
    
      if (value != 0) {
        buffer[offset++] = (byte) (0x80 + tail);
      }
      else {
        buffer[offset++] = (byte) (tail);
        _offset = offset;
        return;
      }
    }
  }

  @Override
  public void close()
  {
    flush();
    
    TempBuffer tBuf = _tBuf;
    _tBuf = null;
    
    _buffer = null;
    
    if (tBuf != null) {
      tBuf.freeSelf();
    }
  }
}
