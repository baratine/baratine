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

import java.io.InputStream;

/**
 * CharBuffer is an unsynchronized version of StringBuffer.
 */
public final class CharBuffer extends CharSegment
{
  private static final int MIN_CAPACITY = 64;

  /**
   * Constructs a char buffer with no characters.
   */
  public CharBuffer()
  {
    init(new char[MIN_CAPACITY], 0, 0);
  }

  /**
   * Constructs a char buffer with the given initial capacity
   *
   * @param capacity initial capacity
   */
  public CharBuffer(int capacity)
  {
    if (capacity < 0)
      throw new IllegalArgumentException();
    
    capacity = Math.max(capacity, MIN_CAPACITY);
    
    init(new char[capacity], 0, 0);
  }

  /**
   * Constructs a char buffer with the given initial string
   *
   * @param string initial string
   */
  public CharBuffer(String string)
  {
    int length = string.length();
    int capacity = length + MIN_CAPACITY;

    init(new char[capacity], 0, length);

    string.getChars(0, length, buffer(), 0);
  }

  /**
   * Constructs a char buffer with the given initial string
   *
   * @param string initial string
   */
  public CharBuffer(String string, int offset, int length)
  {
    int capacity = Math.max(length, MIN_CAPACITY);

    init(new char[capacity], 0, length);

    string.getChars(offset, length, buffer(), 0);
  }

  public static CharBuffer allocate()
  {
    return new CharBuffer();
  }

  public void free()
  {
  }

  /**
   * Set the length of the buffer.
   */
  public final void length(int newLength)
  {
    if (newLength < 0)
      throw new IndexOutOfBoundsException("illegal argument");
    
    ensureCapacity(newLength);

    super.length(newLength);
  }

  /**
   * Returns the char at the specified offset.
   */
  /*
  public char charAt(int i)
  {
    if (i < 0 || _length <= i)
      throw new IndexOutOfBoundsException();

    return _buffer[i];
  }
  */

  /**
   * Returns the last character of the buffer
   *
   * @throws IndexOutOfBoundsException for an empty buffer
   */
  /*
  public char lastChar()
  {
    if (_length == 0)
      throw new IndexOutOfBoundsException();

    return _buffer[_length - 1];
  }
  */
 
  /**
   * Copies characters to the destination buffer.
   */
  /*
  public void getChars(int srcBegin, int srcEnd, char []dst, int dstBegin)
  {
    char []buffer = buffer();
    
    while (srcBegin < srcEnd)
      dst[dstBegin++] = buffer[srcBegin++];
  }
  */

  /**
   * Sets the character at the given index.
   */
  public void setCharAt(int index, char ch)
  {
    if (index < 0 || length() <= index)
      throw new IndexOutOfBoundsException();

    buffer()[index] = ch;
  }

  /**
   * Appends the string representation of the object to the buffer.
   */
  public CharBuffer append(Object obj)
  {
    return append(String.valueOf(obj));
  }

  /**
   * Appends the string representation of the object to the buffer.
   */
  public CharBuffer append(CharBuffer cb)
  {
    return append(cb.buffer(), 0, cb.length());
  }

  /**
   * Appends the string.
   */
  public CharBuffer append(String string)
  {
    if (string == null){
      string = "null";
    }

    int len = string.length();
    int length = length();
    
    int newLength = length + len;
    
    ensureCapacity(newLength);

    string.getChars(0, len, buffer(), length);

    length(newLength);

    return this;
  }

  public CharBuffer append(String string, int offset, int len)
  {
    int length = length();

    ensureCapacity(len + length);

    string.getChars(offset, offset + len, buffer(), length);

    length(length + len);

    return this;
  }
  
  /**
   * Appends the characters to the buffer.
   */
  public CharBuffer append(char []buffer)
  {
    return append(buffer, 0, buffer.length);
  }

  /**
   * Appends the characters to the buffer.
   */
  public CharBuffer append(char []buffer, int offset, int len)
  {
    int length = length();

    ensureCapacity(length + len);

    System.arraycopy(buffer, offset, buffer(), length, len);

    length(length + len);

    return this;
  }

  /**
   * Appends the boolean representation to the buffer
   */
  public final CharBuffer append(boolean b)
  {
    return append(String.valueOf(b));
  }
  
  /**
   * Appends the character to the buffer
   */
  public final CharBuffer append(char ch)
  {
    int length = length();

    ensureCapacity(length + 1);

    buffer()[length] = ch;
    
    length(length + 1);

    return this;
  }
  
  /**
   * Add an int to the buffer.
   */
  public CharBuffer append(int i)
  {
    if (i == 0x80000000) {
      return append("-2147483648");
    }
    
    int length = length();
    
    ensureCapacity(length + 16);

    char []buffer = buffer();

    if (i < 0) {
      buffer[length++] = '-';
      i = -i;
    }
    else if (i == 0) {
      buffer[length] = '0';
      length(length + 1);
      return this;
    }

    int start = length;
    while (i > 0) {
      buffer[length++] = (char) ((i % 10) + '0');
      i /= 10;
    }

    for (int j = (length - start) / 2; j > 0; j--) {
      char temp = buffer[length - j];
      buffer[length - j] = buffer[start + j - 1];
      buffer[start + j - 1] = temp;
    }

    length(length);

    return this;
  }
  
  /**
   * Add a long to the buffer.
   */
  public CharBuffer append(long i)
  {
    if (i == 0x8000000000000000L) {
      return append("-9223372036854775808");
    }
    
    int length = length();
    
    ensureCapacity(length + 32);

    char []buffer = buffer();

    if (i < 0) {
      buffer[length++] = '-';
      i = -i;
    }
    else if (i == 0) {
      buffer[length] = '0';
      length(length + 1);
      return this;
    }

    int start = length;
    while (i > 0) {
      buffer[length++] = (char) ((i % 10) + '0');
      i /= 10;
    }

    for (int j = (length - start) / 2; j > 0; j--) {
      char temp = buffer[length - j];
      buffer[length - j] = buffer[start + j - 1];
      buffer[start + j - 1] = temp;
    }

    length(length);

    return this;
  }

  /**
   * Add a float to the buffer.
   */
  public CharBuffer append(float f)
  {
    return append(String.valueOf(f));
  }

  /**
   * Add a double to the buffer.
   */
  public CharBuffer append(double d)
  {
    return append(String.valueOf(d));
  }
  
  /**
   * Appends iso-8859-1 bytes to the buffer
   */
  public final CharBuffer append(byte []buf, int offset, int len)
  {
    int length = length();
    
    ensureCapacity(length + len);

    char []buffer = buffer();
    
    for (; len > 0; len--) {
      buffer[length++] = (char) (buf[offset++] & 0xff);
    }

    length(length);

    return this;
  }

  /**
   * Deletes characters from the buffer.
   */
  public CharBuffer delete(int start, int end)
  {
    int length = length();
    
    if (start < 0 || end < start || length < start)
      throw new StringIndexOutOfBoundsException();

    end = Math.min(length, end);
    
    int tail = length - end;
    char []buffer = buffer();
    
    for (int i = 0; i < tail; i++) {
      buffer[start + i] = buffer[end + i];
    }

    length(length - (end - start));

    return this;
  }

  /**
   * Deletes a character from the buffer.
   */
  public CharBuffer deleteCharAt(int index)
  {
    int length = length();
    
    if (index < 0 || length < index)
      throw new StringIndexOutOfBoundsException();

    if (index == length) {
      return this;
    }
    
    int tail = length - index + 1;
    char []buffer = buffer();

    for (int i = 0; i < tail; i++)
      buffer[index + i] = buffer[index + i + 1];

    length(length - 1);

    return this;
  }

  /**
   * Replaces a range with a string
   */
  public CharBuffer replace(int start, int end, String string)
  {
    int length = length();
    
    if (start < 0 || end < start || length < start)
      throw new StringIndexOutOfBoundsException();

    int len = string.length();

    ensureCapacity(len + length - (end - start));

    char []buffer = buffer();

    if (len < end - start) {
      int tail = length - end;
      for (int i = 0; i < tail; i++) {
        buffer[start + len + i] = buffer[end + i];
      }
    }
    else {
      int tail = length - end;
      
      for (int i = tail - 1; i >= 0; i--) {
        buffer[end + i] = buffer[start + len + i];
      }
    }

    string.getChars(0, len, buffer, start);

    length(length + len - (end - start));

    return this;
  }

  /**
   * Replaces a range with a character array
   */
  public CharBuffer replace(int start, int end,
                            char []buffer, int offset, int len)
  {
    int length = length();
    
    if (start < 0 || end < start || length < start)
      throw new StringIndexOutOfBoundsException();

    ensureCapacity(len + length - (end - start));

    char []thisBuffer = buffer();

    if (len < end - start) {
      int tail = length - end;
      for (int i = 0; i < tail; i++)
        thisBuffer[start + len + i] = thisBuffer[end + i];
    }
    else {
      int tail = length - end;
      for (int i = tail - 1; i >= 0; i--)
        thisBuffer[end + i] = thisBuffer[start + len + i];
    }

    System.arraycopy(buffer, offset, thisBuffer, start, len);

    length(length + len - (end - start));

    return this;
  }

  /**
   * Returns a substring
   */
  /*
  public String substring(int start)
  {
    if (_length < start || start < 0)
      throw new StringIndexOutOfBoundsException();

    return new String(_buffer, start, _length - start);
  }
  */

  /**
   * Returns a substring
   */
  /*
  public String substring(int start, int end)
  {
    if (_length < start || start < 0 || end < start)
      throw new StringIndexOutOfBoundsException();

    return new String(_buffer, start, end - start);
  }
  */
  /**
   * Inserts a string.
   */
  public CharBuffer insert(int index, String string)
  {
    if (string == null) {
      string = "null";
    }
    
    int length = length();

    if (index < 0 || length < index)
      throw new StringIndexOutOfBoundsException();

    int len = string.length();
    
    ensureCapacity(len + length);

    int tail = length() - index;
    char []buffer = buffer();
    
    for (int i = tail - 1; i >= 0; i--)
      buffer[index + len + i] = buffer[index + i];

    string.getChars(0, len, buffer, index);
    length(length + len);

    return this;
  }

  /**
   * Inserts a character buffer.
   */
  public CharBuffer insert(int index, char []buffer, int offset, int len)
  {
    int length = length();
    
    if (index < 0 || length < index)
      throw new StringIndexOutOfBoundsException();

    ensureCapacity(len + length);

    int tail = length - index;
    char []thisBuffer = buffer;
    for (int i = tail - 1; i >= 0; i--)
      buffer[index + len + i] = thisBuffer[index + i];

    System.arraycopy(buffer, offset, thisBuffer, index, len);
    length(length + len);

    return this;
  }

  /**
   * Inserts an object at a given offset.
   */
  public CharBuffer insert(int offset, Object o)
  {
    return insert(offset, String.valueOf(o));
  }

  /**
   * Inserts a character at a given offset.
   */
  public CharBuffer insert(int offset, char ch)
  {
    return insert(offset, String.valueOf(ch));
  }

  /**
   * Inserts an integer at a given offset.
   */
  public CharBuffer insert(int offset, int i)
  {
    return insert(offset, String.valueOf(i));
  }

  /**
   * Inserts a long at a given offset.
   */
  public CharBuffer insert(int offset, long l)
  {
    return insert(offset, String.valueOf(l));
  }

  /**
   * Inserts a float at a given offset.
   */
  public CharBuffer insert(int offset, float f)
  {
    return insert(offset, String.valueOf(f));
  }

  /**
   * Inserts a double at a given offset.
   */
  public CharBuffer insert(int offset, double d)
  {
    return insert(offset, String.valueOf(d));
  }

  public int indexOf(char ch)
  {
    return indexOf(ch, 0);
  }

  /**
   * Clones the buffer
   */
  public Object clone()
  {
    CharBuffer newBuffer = new CharBuffer();

    newBuffer.length(length());

    System.arraycopy(buffer(), 0, newBuffer.buffer(), 0, length());

    return newBuffer;
  }

  /**
   * String representation of the buffer.
   */
  /*
  public String toString()
  {
    return new String(_buffer, 0, _length);
  }
  */

  public String close()
  {
    String string = new String(buffer(), 0, length());
    free();
    return string;
  }

  class CBInputStream extends InputStream {
    int _index = 0;

    public int read()
    {
      if (length() <= _index)
        return -1;

      return buffer()[_index++];
    }
  }

  public InputStream getInputStream()
  {
    return new CBInputStream();
  }
}
