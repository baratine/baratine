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

/**
 * The IntMap provides a simple hashmap from keys to integers.  The API is
 * an abbreviation of the HashMap collection API.
 *
 * <p>The convenience of IntMap is avoiding all the silly wrapping of
 * integers.
 */
public class CaseInsensitiveIntMap {
  /**
   * Encoding of a null entry.  Since NULL is equal to Integer.MIN_VALUE, 
   * it's impossible to distinguish between the two.
   */
  public final static int NULL = 0xdeadbeef; // Integer.MIN_VALUE + 1;

  private char [][]_keys;
  private int []_values;
  private int _size;
  private int _mask;

  /**
   * Create a new IntMap.  Default size is 256;
   */
  public CaseInsensitiveIntMap()
  {
    _keys = new char[256][];
    _values = new int[256];

    _mask = _keys.length - 1;
    _size = 0;
  }

  /**
   * Clear the hashmap.
   */
  public void clear()
  {
    for (int i = 0; i < _values.length; i++) {
      _keys[i] = null;
      _values[i] = 0;
    }
    
    _size = 0;
  }
  /**
   * Returns the current number of entries in the map.
   */
  public int size() 
  { 
    return _size;
  }

  /**
   * Puts a new value in the property table with the appropriate flags
   */
  public int get(char []key, int length)
  {
    if (key == null)
      return NULL;

    int hash = hash(key, length) & _mask;

    while (true) {
      char []mapKey = _keys[hash];

      if (mapKey == null)
        return NULL;
      else if (equals(mapKey, key, length))
        return _values[hash];

      hash = (hash + 1) & _mask;
    }
  }

  /**
   * Puts a new value in the property table with the appropriate flags
   */
  public void put(char []key, int length, int value)
  {
    if (key == null)
      return;

    int hash = hash(key, length) & _mask;

    while (true) {
      char []testKey = _keys[hash];

      if (testKey == null || equals(testKey, key, length)) {
        _keys[hash] = new char[length];

        for (int i = length - 1; i >= 0; i--) {
          char ch = key[i];

          if ('A' <= ch && ch <= 'Z')
            ch += 'a' - 'A';

          _keys[hash][i] = ch;
        }

        _values[hash] = value;

        _size++;

        if (_keys.length <= 4 * _size)
          resize(2 * _keys.length);

        return;
      }
      else if (key != testKey && ! testKey.equals(key)) {
        hash = (hash + 1) & _mask;
        continue;
      }
      else {
        _values[hash] = value;

        return;
      }
    }
  }

  /**
   * Puts a new value in the property table with the appropriate flags
   */
  public void put(String key, int value)
  {
    put(key.toCharArray(), key.length(), value);
  }

  /**
   * Expands the property table
   */
  private void resize(int newSize)
  {
    char [][]newKeys = new char[newSize][];
    int []newValues = new int[newSize];

    _mask = newKeys.length - 1;

    for (int i = 0; i < _keys.length; i++) {
      char []key = _keys[i];
      
      if (key == null)
        continue;

      int hash = hash(key, key.length) & _mask;

      while (true) {
        if (newKeys[hash] == null) {
          newKeys[hash] = _keys[i];
          newValues[hash] = _values[i];
          break;
        }

        hash = (hash + 1) & _mask;
      }
    }

    _keys = newKeys;
    _values = newValues;
  }

  /**
   * Calculate the hash.
   */
  private static int hash(char []key, int length)
  {
    int hash = 17;

    for (int i = length - 1; i >= 0; i--) {
      char a = key[i];

      if ('A' <= a && a <= 'Z')
        a += 'a' - 'A';
      
      hash = 65537 * hash + a;
    }

    return hash;
  }

  /**
   * Calculate equality.
   */
  private static boolean equals(char []lower, char []mixed, int length)
  {
    if (lower.length != length)
      return false;

    for (int i = length - 1; i >= 0; i--) {
      char a = lower[i];
      char b = mixed[i];

      if ('A' <= b && b <= 'Z')
        b += 'a' - 'A';

      if (a != b)
        return false;
    }

    return true;
  }

  public String toString()
  {
    return "CaseInsensitiveIntMap[]";
  }
}
