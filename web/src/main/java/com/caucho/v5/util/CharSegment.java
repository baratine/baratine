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

import java.util.Objects;

/**
 * CharSegment is a section of a character buffer
 */
public class CharSegment implements CharSequence
{
  private char []_buffer;
  private int _offset;
  private int _length;

  public CharSegment()
  {
  }

  /**
   * Constructs a char segment based on a char array.
   */
  public CharSegment(char []buffer, int offset, int length)
  {
    _buffer = buffer;
    _offset = offset;
    _length = length;
  }

  /**
   * Sets the char segment to a new buffer triple.
   */
  public void init(char []buffer, int offset, int length)
  {
    _buffer = buffer;
    _offset = offset;
    _length = length;
  }

  /**
   * Clears the buffer.  Equivalent to setLength(0)
   */
  public final void clear()
  {
    _length = 0;
  }

  /**
   * Returns the character count of the buffer's contents.
   */
  @Override
  public final int length()
  {
    return _length;
  }
  
  protected void length(int length)
  {
    _length = length;
  }

  public final int offset()
  {
    return _offset;
  }

  /**
   * Returns the char at the specified offset.
   */
  @Override
  public final char charAt(int i)
  {
    if (i < 0 || _length <= i)
      throw new IndexOutOfBoundsException();

    return _buffer[i + _offset];
  }

  /**
   * Returns the last character of the buffer
   *
   * @throws IndexOutOfBoundsException for an empty buffer
   */
  public final char lastChar()
  {
    if (_length == 0)
      throw new IndexOutOfBoundsException();

    return _buffer[_offset + _length - 1];
  }

  /**
   * Returns the buffer's char array.
   */
  public final char []buffer()
  {
    return _buffer;
  }
  
  /**
   * Returns the buffer's char array.
   */
  protected final void buffer(char []buffer)
  {
    Objects.requireNonNull(buffer);
    
    _buffer = buffer;
  }

  /**
   * Returns the capacity of the buffer, i.e. how many chars it
   * can hold.
   */
  public final int capacity()
  {
    return _buffer.length;
  }

  /**
   * Ensure the buffer can hold at least 'minimumCapacity' chars.
   */
  public final void ensureCapacity(int minimumCapacity)
  {
    if (minimumCapacity <= _buffer.length) {
      return;
    }

    expandCapacity(minimumCapacity);
  }

  /**
   * Expands the capacity to a new value.
   */
  protected final void expandCapacity(int minimumCapacity)
  {
    int newCapacity = Math.max(2 * _buffer.length, minimumCapacity);

    char []chars = new char[newCapacity];
    
    System.arraycopy(buffer(), 0, chars, 0, _buffer.length); 

    _buffer = chars;
  }

  /**
   * Copies characters to the destination buffer.
   */
  public void getChars(int srcBegin, int srcEnd, char []dst, int dstBegin)
  {
    srcBegin += _offset;
    srcEnd += _offset;

    char []buffer = _buffer;
    while (srcBegin < srcEnd)
      dst[dstBegin++] = buffer[srcBegin++];
  }

  /**
   * Returns a substring
   */
  public String substring(int start)
  {
    if (_length < start || start < 0)
      throw new StringIndexOutOfBoundsException();

    return new String(_buffer, _offset + start, _length - start);
  }

  /**
   * Returns a substring
   */
  public String substring(int start, int end)
  {
    if (_length < start || start < 0 || end < start)
      throw new StringIndexOutOfBoundsException();

    return new String(_buffer, _offset + start, end - start);
  }

  /**
   * Returns a subsequence
   */
  public CharSequence subSequence(int start, int end)
  {
    if (_length < start || start < 0 || end < start)
      throw new StringIndexOutOfBoundsException();

    return new String(_buffer, _offset + start, end - start);
  }

  /**
   * Returns the index of a character in the CharSegment.
   */
  public int indexOf(char ch)
  {
    return indexOf(ch, 0);
  }

  /**
   * Returns the index of a character in the CharSegment starting
   * from an offset.
   */
  public final int indexOf(char ch, int start)
  {
    if (start < 0)
      start = 0;

    int end = _offset + _length;
    start += _offset;

    char []buffer = _buffer;
    for (; start < end; start++) {
      if (buffer[start] == ch) {
        return start - _offset;
      }
    }

    return -1;
  }

  /**
   * Returns the last index of a character in the CharSegment.
   */
  public final int lastIndexOf(char ch)
  {
    return lastIndexOf(ch, _length - 1);
  }
  /**
   * Returns the last index of a character in the CharSegment starting
   * from an offset.
   */

  public final int lastIndexOf(char ch, int start)
  {
    if (_length <= start)
      start = _length - 1;

    char []buffer = _buffer;
    int offset = _offset;
    for (; start >= 0; start--) {
      if (buffer[start + offset] == ch)
        return start;
    }

    return -1;
  }

  public int indexOf(String s)
  {
    return indexOf(s, 0);
  }

  public int indexOf(String s, int start)
  {
    int slen = s.length();

    if (start < 0)
      start = 0;

    int end = _offset + _length - slen + 1;
    start += _offset;

    char []buffer = _buffer;
    for (; start < end; start++) {
      int i = 0;
      for (; i < slen; i++) {
        if (buffer[start+i] != s.charAt(i))
          break;
      }
      if (i == slen)
        return start - _offset;
    }

    return -1;
  }

  /**
   * Returns the buffer's hash code
   */
  public int hashCode()
  {
    int hash = 0;
    char []buffer = _buffer;
    int begin = _offset;
    int end = begin + _length;

    for (; begin < end; begin++)
      hash = 65521 * hash + buffer[begin] * 251 + 1021;

    return hash;
  }

  /* 
   * Predicate testing if two char segments are equal
   */
  public final boolean equals(Object a)
  {
    if (this == a) {
      return true;
    }
    else if (a instanceof CharSegment) {
      CharSegment cb = (CharSegment) a;

      int length = _length;
      if (length != cb._length)
        return false;

      char []buffer = _buffer;
      char []aBuffer = cb._buffer;

      int offset = _offset;
      int aOffset = cb._offset;

      for (int i = length - 1; i >= 0; i--) {
        if (buffer[offset + i] != aBuffer[aOffset + i]) {
          return false;
        }
      }

      return true;
    }
    else if (a instanceof CharSequence) {
      CharSequence seq = (CharSequence) a;

      int length = seq.length();

      if (_length != length) {
        return false;
      }

      for (int i = length - 1; i >= 0; i--) {
        if (_buffer[i] != seq.charAt(i)) {
          return false;
        }
      }

      return true;
    }
    else
      return false;
  }

  /**
   * Returns true if the two char segments are equal.
   */
  public boolean equals(CharSegment cb)
  {
    int length = _length;
    
    if (length != cb._length) {
      return false;
    }

    char []buffer = _buffer;
    char []aBuffer = cb._buffer;

    int offset = _offset;
    int aOffset = cb._offset;

    for (int i = length - 1; i >= 0; i--) {
      if (buffer[offset + i] != aBuffer[aOffset + i]) {
        return false;
      }
    }

    return true;
  }

  /**
   * Returns true if the CharSegment equals the char array.
   */
  public final boolean equals(char []cb, int length)
  {
    if (length != _length)
      return false;

    int offset = _offset;
    char []buffer = _buffer;

    for (int i = _length - 1; i >= 0; i--)
      if (buffer[offset + i] != cb[i])
        return false;

    return true;
  }

  /**
   * Returns true if the CharSegment equals the char array.
   */
  public final boolean equals(char []cb, int offset, int length)
  {
    if (length != _length)
      return false;

    int selfOffset = _offset;
    char []buffer = _buffer;

    for (int i = _length - 1; i >= 0; i--)
      if (buffer[selfOffset + i] != cb[i + offset])
        return false;

    return true;
  }

  /**
   * Returns true if the CharSegment equals the string.
   */
  public final boolean equalsIgnoreCase(String a)
  {
    int len = a.length();
    if (_length != len)
      return false;

    int offset = _offset;
    char []buffer = _buffer;

    for (int i = 0; i < len; i++) {
      char ca = buffer[offset + i];
      char cb = a.charAt(i);

      if (ca == cb) {
      }

      else if (Character.toLowerCase(ca) != Character.toLowerCase(cb))
        return false;
    }

    return true;
  }

  /**
   * Returns true if the two CharSegments are equivalent ignoring the case.
   */
  public final boolean equalsIgnoreCase(CharSegment b)
  {
    int length = _length;
    if (length != b._length)
      return false;

    char []buffer = _buffer;
    char []bBuffer = b._buffer;

    int offset = _offset;
    int bOffset = b._offset;

    for (int i = length - 1; i >= 0; i--) {
      char ca = buffer[offset + i];
      char cb = bBuffer[bOffset + i];

      if (ca != cb &&
          Character.toLowerCase(ca) != Character.toLowerCase(cb))
        return false;
    }
    return true;
  }

  public final static boolean isMatch(char []buffer, int offset, int length,
                                      String s)
  {
    int strlen = s.length();
    
    if (length != strlen) {
      return false;
    }
    
    for (int i = length - 1; i >= 0; i--) {
      if (buffer[offset + i] != s.charAt(i)) {
        return false;
      }
    }
    
    return true;
  }
  
  /* 
   * XXX: unsure is this is legit
   */
  public final boolean matches(Object a)
  {
    if (a instanceof CharSegment) {
      CharSegment cb = (CharSegment) a;
      if (_length != cb._length)
        return false;

      int offset = _offset;
      int bOffset = cb._offset;

      char []buffer = _buffer;
      char []cbBuffer = cb._buffer;

      for (int i = _length - 1; i >= 0; i--) {
        if (buffer[offset + i] != cbBuffer[bOffset + i])
          return false;
      }

      return true;
    } else if (a instanceof String) {
      String sa = (String) a;

      if (_length != sa.length())
        return false;

      int offset = _offset;
      char []buffer = _buffer;

      for (int i = _length - 1; i >= 0; i--)
        if (buffer[i + offset] != sa.charAt(i))
          return false;

      return true;
    } else
      return false;
  }
  
  /**
   * Returns true if the charSegment matches the string.
   */
  public boolean matches(String sa)
  {
    if (_length != sa.length())
      return false;

    char []buffer = _buffer;
    int offset = _offset;

    for (int i = _length - 1; i >= 0; i--)
      if (_buffer[_offset + i] != sa.charAt(i))
        return false;

    return true;
  }

  /**
   * Returns true if the CharSegment matches the string, ignoring the case.
   */
  public boolean matchesIgnoreCase(String sa)
  {
    if (_length != sa.length())
      return false;

    char []buffer = _buffer;
    int offset = _offset;
    for (int i = _length - 1; i >= 0; i--) {
      char ca = buffer[offset + i];
      char cb = sa.charAt(i);

      if (ca != cb && Character.toLowerCase(ca) != Character.toLowerCase(cb))
        return false;
    }

    return true;
  }

  public boolean regionMatches(int off1, CharSegment buf, int off2, int len)
  {
    if (_length < off1 + len || buf._length < off2 + len)
      return false;

    char []buffer = _buffer;
    char []bufBuffer = buf._buffer;
    for (int i = len - 1; i >= 0; i--) {
      if (buffer[off1 + i] != bufBuffer[off2 + i])
        return false;
    }

    return true;
  }

  public boolean regionMatches(int off1, String buf, int off2, int len)
  {
    if (_length < off1 + len || buf.length() < off2 + len)
      return false;

    char []buffer = _buffer;
    for (int i = 0; i < len; i++) {
      if (buffer[off1 + i] != buf.charAt(off2 + i))
        return false;
    }

    return true;
  }

  public boolean regionMatchesIgnoreCase(int off1, CharSegment buf,
                                         int off2, int len)
  {
    if (_length < off1 + len || buf._length < off2 + len)
      return false;

    char []buffer = _buffer;
    char []bufBuffer = buf._buffer;
    for (int i = len -1; i >= 0; i--) {
      if (Character.toLowerCase(buffer[off1 + i]) !=
          Character.toLowerCase(bufBuffer[off2 + i]))
        return false;
    }

    return true;
  }

  /**
   * Returns true if the CharSegment starts with the string.
   */
  public boolean startsWith(String string)
  {
    if (string == null)
      return false;

    int strlen = string.length();
    if (_length < strlen)
      return false;

    char []buffer = _buffer;
    int offset = _offset;

    while (--strlen >= 0) {
      if (buffer[offset + strlen] != string.charAt(strlen))
        return false;
    }

    return true;
  }

  /**
   * Returns true if the CharSegment ends with the string.
   */
  public boolean endsWith(String string)
  {
    if (string == null)
      return false;

    int strlen = string.length();
    if (_length < strlen)
      return false;

    char []buffer = _buffer;
    int offset = _offset + _length - strlen;

    while (--strlen >= 0) {
      if (buffer[offset + strlen] != string.charAt(strlen))
        return false;
    }

    return true;
  }

  /**
   * Returns true if the CharSegment ends with the char segment.
   */
  public boolean endsWith(CharSegment cb)
  {
    if (cb == null)
      return false;

    int strlen = cb._length;
    if (_length < strlen)
      return false;

    char []buffer = _buffer;
    int offset = _offset + _length - strlen;

    char []cbBuffer = cb._buffer;
    int cbOffset = cb._offset;

    while (--strlen >= 0) {
      if (buffer[offset + strlen] != cbBuffer[cbOffset + strlen])
        return false;
    }

    return true;
  }

  /**
   * Converts the contents of the segment to lower case.
   */
  public CharSegment toLowerCase()
  {
    char []buffer = _buffer;
    int len = _length;
    int offset = _offset;

    while (--len >= 0)
      buffer[offset + len] = Character.toLowerCase(buffer[offset + len]);

    return this;
  }

  /**
   * String representation of the buffer.
   */
  public String toString()
  {
    return new String(_buffer, _offset, _length);
  }
}
