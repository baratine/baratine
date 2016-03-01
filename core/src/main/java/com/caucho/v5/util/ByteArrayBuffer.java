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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * A variable-length byte buffer, similar to a character buffer.
 *
 * <p>The byte buffer is unsynchronized.
 */
public final class ByteArrayBuffer {
  private static final L10N L = new L10N(ByteArrayBuffer.class);
  
  private byte []_buffer;
  private int _capacity;
  private int _length;

  public ByteArrayBuffer(int minimumCapacity)
  {
    _capacity = 32;
    if (minimumCapacity > 0x1000) {
      _capacity = (minimumCapacity + 0xfff) & ~0xfff;
    } else {
      while (_capacity < minimumCapacity) {
        _capacity += _capacity;
      }
    }

    _buffer = new byte[minimumCapacity];
    _length = 0;
  }

  public ByteArrayBuffer()
  {
    _buffer = new byte[32];
    _capacity = _buffer.length;
    _length = 0;
  }

  /**
   * Returns the actual capacity of the buffer, i.e. how many bytes it
   * can hold.
   */
  public int capacity()
  {
    return _capacity;
  }

  public int hashCode()
  {
    int hash = 17;
    for (int i = _length - 1; i >= 0; i--) {
      hash = 65537 * hash + _buffer[i];
    }

    return hash;
  }

  /**
   * Ensure the buffer can hold at least 'minimumCapacity' bytes.
   */
  public void ensureCapacity(int minimumCapacity)
  {
    if (minimumCapacity <= _capacity)
      return;

    if (minimumCapacity > 0x1000) {
      _capacity = (minimumCapacity + 0xfff) & ~0xfff;
    } else {
      while (_capacity < minimumCapacity) {
        _capacity += _capacity;
      }
    }

    byte []bytes = new byte[_capacity];

    System.arraycopy(_buffer, 0, bytes, 0, _length); 

    _buffer = bytes;
  }

  /**
   * Returns the buffer length
   */
  public int length()
  {
    return _length;
  }

  public int size()
  {
    return _length;
  }
  /**
   * Returns the buffer length
   */
  public int getLength()
  {
    return _length;
  }

  /**
   * Set the buffer.
   */
  public void setLength(int len)
  {
    if (len < 0) {
      throw new IllegalArgumentException(L.l("illegal length: {0}", len));
    }
    else if (len > _capacity) {
      ensureCapacity(len);
    }

    _length = len;
  }

  public void clear()
  {
    _length = 0;
  }
  
  /**
   * Returns the byte array for the buffer.
   */
  public byte []getBuffer() { return _buffer; }

  /**
   * Add a byte to the buffer.
   */
  public void append(int b)
  {
    if (_length + 1 > _capacity)
      ensureCapacity(_length + 1);

    _buffer[_length++] = (byte) b;
  }

  /**
   * Inserts a byte array
   */
  public void add(int i, byte []buffer, int offset, int length)
  {
    if (_length + length > _capacity)
      ensureCapacity(_length + length);

    System.arraycopy(_buffer, i, _buffer, i + length, _length - i);
    System.arraycopy(buffer, offset, _buffer, i, length);

    _length += length;
  }

  public void add(byte []buffer, int offset, int length)
  {
    if (_capacity < _length + length)
      ensureCapacity(_length + length);

    System.arraycopy(buffer, offset, _buffer, _length, length);

    _length += length;
  }

  /**
   * Inserts a byte array
   */
  public void add(int i, int data)
  {
    if (_length + 1 > _capacity)
      ensureCapacity(_length + 1);

    System.arraycopy(_buffer, i, _buffer, i + 1, _length - i);
    _buffer[i] = (byte) data;

    _length += 1;
  }

  public void add(int data)
  {
    if (_capacity < _length + 1)
      ensureCapacity(_length + 1);

    _buffer[_length++] = (byte) data;
  }

  public void set(int i, byte []buffer, int offset, int length)
  {
    System.arraycopy(buffer, offset, _buffer, i, length);
  }

  public void set(int i, int data)
  {
    _buffer[i] = (byte) data;
  }

  public void insert(int i, byte []buffer, int offset, int length)
  {
    if (_length + length > _capacity)
      ensureCapacity(_length + length);

    System.arraycopy(_buffer, i, _buffer, i + length, _length - i);
    System.arraycopy(_buffer, offset, _buffer, i, length);

    _length += length;
  }

  /**
   * Inserts a byte array
   */
  public void replace(int i, byte []buffer, int offset, int length)
  {
    System.arraycopy(buffer, offset, _buffer, i, length);
  }

  /**
   * Inserts a byte array
   */
  public void append(byte []buffer, int offset, int length)
  {
    if (_length + length >= _capacity)
      ensureCapacity(_length + length);

    System.arraycopy(buffer, offset, _buffer, _length, length);

    _length += length;
  }

  public void addByte(int v)
  {
    add(v);
  }

  /**
   * Inserts a short into the buffer
   */
  public void replaceShort(int i, int s)
  {
    _buffer[i]     = (byte) (s >> 8);
    _buffer[i + 1] = (byte) (s);
  }

  /**
   * Appends a short (little endian) in the buffer
   */
  public void appendShort(int s)
  {
    if (_length + 2 > _capacity)
      ensureCapacity(_length + 2);

    replaceShort(_length, s);

    _length += 2;
  }

  public void addShort(int s)
  {
    if (_length + 2 > _capacity)
      ensureCapacity(_length + 2);

    _buffer[_length++] = (byte) (s >> 8);
    _buffer[_length++] = (byte) s;
  }

  public void addShort(int i, int s)
  {
    add(i, (byte) (s >> 8));
    add(i + 1, (byte) (s));
  }

  public void setShort(int i, int s)
  {
    _buffer[i]     = (byte) (s >> 8);
    _buffer[i + 1] = (byte) (s);
  }

  /**
   * Inserts a int (little endian) into the buffer
   */
  public void replaceInt(int i, int v)
  {
    _buffer[i]     = (byte) (v >> 24);
    _buffer[i + 1] = (byte) (v >> 16);
    _buffer[i + 2] = (byte) (v >> 8);
    _buffer[i + 3] = (byte) (v);
  }

  /**
   * Appends an int (little endian) in the buffer
   */
  public void appendInt(int s)
  {
    if (_length + 4 > _capacity)
      ensureCapacity(_length + 4);

    _buffer[_length++] = (byte) (s >> 24);
    _buffer[_length++] = (byte) (s >> 16);
    _buffer[_length++] = (byte) (s >> 8);
    _buffer[_length++] = (byte) s;
  }

  public void addInt(int s)
  {
    if (_capacity < _length + 4)
      ensureCapacity(_length + 4);

    _buffer[_length++] = (byte) (s >> 24);
    _buffer[_length++] = (byte) (s >> 16);
    _buffer[_length++] = (byte) (s >> 8);
    _buffer[_length++] = (byte) s;
  }

  public void addInt(int i, int s)
  {
    add(i + 0, (byte) (s >> 24));
    add(i + 1, (byte) (s >> 16));
    add(i + 2, (byte) (s >> 8));
    add(i + 3, (byte) (s));
  }

  public void setInt(int i, int v)
  {
    _buffer[i]     = (byte) (v >> 24);
    _buffer[i + 1] = (byte) (v >> 16);
    _buffer[i + 2] = (byte) (v >> 8);
    _buffer[i + 3] = (byte) (v);
  }

  public void addLong(long v)
  {
    if (_length + 8 > _capacity)
      ensureCapacity(_length + 8);

    _buffer[_length++] = (byte) (v >> 56L);
    _buffer[_length++] = (byte) (v >> 48L);
    _buffer[_length++] = (byte) (v >> 40L);
    _buffer[_length++] = (byte) (v >> 32L);
    
    _buffer[_length++] = (byte) (v >> 24L);
    _buffer[_length++] = (byte) (v >> 16L);
    _buffer[_length++] = (byte) (v >> 8L);
    _buffer[_length++] = (byte) v;
  }

  public void addFloat(float v)
  {
    if (_length + 4 > _capacity)
      ensureCapacity(_length + 4);

    int bits = Float.floatToIntBits(v);

    _buffer[_length++] = (byte) (bits >> 24);
    _buffer[_length++] = (byte) (bits >> 16);
    _buffer[_length++] = (byte) (bits >> 8);
    _buffer[_length++] = (byte) bits;
  }

  public void addDouble(double v)
  {
    if (_length + 8 > _capacity)
      ensureCapacity(_length + 8);

    long bits = Double.doubleToLongBits(v);

    _buffer[_length++] = (byte) (bits >> 56);
    _buffer[_length++] = (byte) (bits >> 48);
    _buffer[_length++] = (byte) (bits >> 40);
    _buffer[_length++] = (byte) (bits >> 32);
    _buffer[_length++] = (byte) (bits >> 24);
    _buffer[_length++] = (byte) (bits >> 16);
    _buffer[_length++] = (byte) (bits >> 8);
    _buffer[_length++] = (byte) bits;
  }

  public void addString(String s)
  {
    int len = s.length();
    if (len + _length > _capacity)
      ensureCapacity(_length + len);

    for (int i = 0; i < len; i++)
      _buffer[_length++] = (byte) s.charAt(i);
  }

  /**
   * Adds a string with a specified encoding.
   */
  public void addString(String s, String encoding)
  {
    if (encoding == null || encoding.equals("ISO-8859-1")) {
      addString(s);
      return;
    }

    // XXX: special case for utf-8?

    byte []bytes = null;

    try {
      bytes = s.getBytes(encoding);
    } catch (UnsupportedEncodingException e) {
      addString(s);
      return;
    }
    
    int len = bytes.length;
    if (len + _length > _capacity)
      ensureCapacity(_length + len);

    for (int i = 0; i < len; i++)
      _buffer[_length++] = bytes[i];
  }

  public void add(String s)
  {
    int len = s.length();
    if (len + _length > _capacity)
      ensureCapacity(_length + len);

    for (int i = 0; i < len; i++)
      _buffer[_length++] = (byte) s.charAt(i);
  }

  public void add(char []s, int offset, int len)
  {
    if (len + _length > _capacity)
      ensureCapacity(_length + len);

    for (int i = 0; i < len; i++)
      _buffer[_length++] = (byte) s[offset + i];
  }

  /*
  public void add(CharBuffer cb)
  {
    int len = cb.length();
    
    if (len + _length > _capacity)
      ensureCapacity(_length + len);

    char []s = cb.getBuffer();

    for (int i = 0; i < len; i++)
      _buffer[_length++] = (byte) s[i];
  }
  */

  public void remove(int begin, int length)
  {
    System.arraycopy(_buffer, begin + length, _buffer, begin,
                     _capacity - length - begin);

    _length -= length;
  }

  /**
   * Appends an int (little endian) in the buffer
   */
  public void append(String string)
  {
    for (int i = 0; i < string.length(); i++)
      append(string.charAt(i));
  }

  /**
   * Returns the byte at the specified offset.
   */
  public byte byteAt(int i)
  {
    if (i < 0 || i > _length)
      throw new RuntimeException();

    return _buffer[i];
  }
  /**
   * Returns the byte at the specified offset.
   */
  public void setByteAt(int i, int b)
  {
    _buffer[i] = (byte) b;
  }

  public byte get(int i)
  {
    if (i < 0 || i >= _length)
      throw new RuntimeException("out of bounds: " + i + " len: " + _length);

    return _buffer[i];
  }

  public short getShort(int i)
  {
    if (i < 0 || i + 1 >= _length)
      throw new RuntimeException("out of bounds: " + i + " len: " + _length);

    return (short) (((_buffer[i] & 0xff) << 8) + 
                    (_buffer[i + 1] & 0xff));
  }

  public int getInt(int i)
  {
    if (i < 0 || i + 3 >= _length)
      throw new RuntimeException("out of bounds: " + i + " len: " + _length);

    return (((_buffer[i + 0] & 0xff) << 24) +
            ((_buffer[i + 1] & 0xff) << 16) +
            ((_buffer[i + 2] & 0xff) << 8) +
            ((_buffer[i + 3] & 0xff)));
  }

  public void print(int i)
  {
    if (_length + 16 >= _capacity)
      ensureCapacity(_length + 16);

    if (i < 0) {
      _buffer[_length++] = (byte) '-';
      i = -i;
    } else if (i == 0) {
      _buffer[_length++] = (byte) '0';
      return;
    }

    int start = _length;
    while (i > 0) {
      _buffer[_length++] = (byte) ((i % 10) + '0');
      i /= 10;
    }

    for (int j = (_length - start) / 2; j > 0; j--) {
      byte temp = _buffer[_length - j];
      _buffer[_length - j] = _buffer[start + j - 1];
      _buffer[start + j - 1] = temp;
    }
  }

  public int indexOf(byte []buffer, int offset, int length)
  {
    if (length <= 0)
      return -1;
    
    int end = _length - length;
    int first = buffer[offset];

    byte []testBuffer = _buffer;

    for (int i = 0; i <= end; i++) {
      if (testBuffer[i] != first)
        continue;

      int j = length - 1;
      for (; j > 0; j--) {
        if (testBuffer[i + j] != buffer[offset + j])
          break;
      }

      if (j == 0)
        return i;
    }

    return -1;
  }

  /**
   * Clones the buffer
   */
  public Object clone()
  {
    ByteArrayBuffer newBuffer = new ByteArrayBuffer(_length);

    System.arraycopy(_buffer, 0, newBuffer._buffer, 0, _length);

    return newBuffer;
  }

  public boolean equals(Object b)
  {
    if (! (b instanceof ByteArrayBuffer))
      return false;

    ByteArrayBuffer bb = (ByteArrayBuffer) b;
    if (bb._length != _length)
      return false;

    for (int i = _length - 1; i >= 0; i--)
      if (bb._buffer[i] != _buffer[i])
        return false;

    return true;
  }

  public InputStream createInputStream()
  {
    return new BBInputStream(this);
  }

  public OutputStream createOutputStream()
  {
    return new BBOutputStream(this);
  }
  
  /*
  public ReadStream createReadStream()
  {
    return VfsStream.openRead(new BBInputStream(this));
  }
  */

  /**
   * Returns the bytes
   */
  public byte []getByteArray()
  {
    byte []bytes = new byte[_length];

    System.arraycopy(_buffer, 0, bytes, 0, _length);

    return bytes;
  }
  

  /**
   * String representation of the buffer.
   */
  public String toString()
  {
    try {
      return new String(_buffer, 0, _length, "iso-8859-1");
    } catch (Exception e) {
      return new String(_buffer, 0, _length);
    }
  }

  public String toString(String encoding)
  {
    try {
      return new String(_buffer, 0, _length, encoding);
    } catch (Exception e) {
      return new String(_buffer, 0, _length);
    }
  }

  static class BBInputStream extends InputStream {
    ByteArrayBuffer _buf;
    int _index;

    public int available()
    {
      return _buf._length - _index;
    }
    
    public int read() throws IOException
    {
      if (_index >= _buf._length)
        return -1;
      else
        return _buf._buffer[_index++] & 0xff;
    }

    BBInputStream(ByteArrayBuffer buf)
    {
      _buf = buf;
    }
  }

  static class BBOutputStream extends OutputStream {
    ByteArrayBuffer _buf;

    public void write(int ch) throws IOException
    {
      _buf.append(ch);
    }

    BBOutputStream(ByteArrayBuffer buf)
    {
      _buf = buf;
    }
  }
}


