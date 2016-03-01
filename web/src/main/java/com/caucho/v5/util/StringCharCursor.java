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

/* The text cursor is purposely lightweight.  It does not update with the
 * text, nor does is allow changes.
 */
public class StringCharCursor extends CharCursor {
  private CharSequence _string;
  private int _length;
  private int _pos;

  public StringCharCursor(CharSequence string)
  {
    _string = string;
    _length = string.length();
    _pos = 0;
  }

  public StringCharCursor(CharSequence string, int offset)
  {
    _string = string;
    _length = string.length();
    _pos = offset;
  }

  /** 
   * returns the current location of the cursor
   */
  public int getIndex() { return _pos; }

  public int getBeginIndex() { return 0; }
  public int getEndIndex() { return _length; }
  /**
   * sets the cursor to the position
   */
  public char setIndex(int pos) 
  { 
    if (pos < 0) {
      _pos = 0;
      return DONE;
    }
    else if (_length <= pos) {
      _pos = _length;
      return DONE;
    }
    else {
      _pos = pos; 
      return _string.charAt(pos);
    }
  }

  /**
   * reads a character from the cursor
   *
   * @return -1 on EOF
   */
  public char next() 
  { 
    if (_length <= ++_pos) {
      _pos = _length;
      return DONE;
    }
    else
      return _string.charAt(_pos);
  }

  /**
   * reads a character from the cursor
   *
   * @return -1 on EOF
   */
  public char previous() 
  { 
    if (--_pos < 0) {
      _pos = 0;
      return DONE;
    }
    else
      return _string.charAt(_pos);
  }

  public char current() 
  { 
    if (_length <= _pos)
      return DONE;
    else
      return _string.charAt(_pos);
  }

  /**
   * Skips the next n characters
   */
  public char skip(int n)
  {
    _pos += n;
    
    if (_length <= _pos) {
      _pos = _string.length();
      return DONE;
    } else
      return _string.charAt(_pos);
  }

  public void init(CharSequence string)
  {
    _string = string;
    _length = string.length();
    
    _pos = 0;
  }

  public Object clone()
  {
    return new StringCharCursor(_string);
  }
}
