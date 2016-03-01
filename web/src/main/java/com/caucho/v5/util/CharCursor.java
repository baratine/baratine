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

import java.text.CharacterIterator;

/* The text cursor is purposely lightweight.  It does not update with the
 * text, nor does is allow changes.
 */
public abstract class CharCursor implements CharacterIterator, CharReader
{
  /** 
   * returns the current location of the cursor
   */
  public abstract int getIndex();

  public abstract int getBeginIndex();
  public abstract int getEndIndex();
  /**
   * sets the cursor to the position
   */
  public abstract char setIndex(int pos);

  public abstract char next();

  public abstract char previous();

  public abstract char current();

  public abstract Object clone();

  public char first()
  {
    return setIndex(getBeginIndex());
  }
  public char last()
  {
    return setIndex(getEndIndex());
  }

  /**
   * our stuff
   */
  public char read()
  {
    char value = current();
    next();
    return value;
  }

  public char prev()
  {
    int pos = getIndex();
    char value = previous();
    setIndex(pos);
    return value;
  }

  /**
   * Skips the next n characters
   */
  public char skip(int n)
  {
    for (int i = 0; i < n; i++)
      next();
    return current();
  }

  public void subseq(CharBuffer cb, int begin, int end)
  {
    int pos = getIndex();

    char ch = setIndex(begin);
    for (int i = begin; i < end; i++) {
      if (ch != DONE)
        cb.append(ch);
      ch = next();
    }

    setIndex(pos);
  }

  public void subseq(CharBuffer cb, int length)
  {
    char ch = current();
    for (int i = 0; i < length; i++) {
      if (ch != DONE)
        cb.append(ch);
      ch = next();
    }
  }

  /**
   * True if the cursor matches the character buffer
   *
   * If match fails, return the pointer to its original.
   */
  public boolean regionMatches(char []cb, int offset, int length)
  {
    int pos = getIndex();

    char ch = current();
    for (int i = 0; i < length; i++) {
      if (cb[i + offset] != ch) {
        setIndex(pos);
        return false;
      }
      ch = next();
    }

    return true;
  }

  /**
   * True if the cursor matches the character buffer
   *
   * If match fails, return the pointer to its original.
   */
  public boolean regionMatchesIgnoreCase(char []cb, int offset, int length)
  {
    int pos = getIndex();

    char ch = current();
    for (int i = 0; i < length; i++) {
      if (ch == DONE) {
        setIndex(pos);
        return false;
      }

      if (Character.toLowerCase(cb[i + offset]) != Character.toLowerCase(ch)) {
        setIndex(pos);
        return false;
      }

      ch = next();
    }

    return true;
  }
}
