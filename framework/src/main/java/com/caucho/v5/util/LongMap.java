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

import java.util.Iterator;
/**
 * The IntMap provides a simple hashmap from keys to integers.  The API is
 * an abbreviation of the HashMap collection API.
 *
 * <p>The convenience of IntMap is avoiding all the silly wrapping of
 * integers.
 */
public class LongMap<K> {
  /**
   * Encoding of a null entry.  Since NULL is equal to Integer.MIN_VALUE, 
   * it's impossible to distinguish between the two.
   */
  public final static long NULL = 0xffffffffffffffedL;
  private static int DELETED = 0x1;
  private K []_keys;
  private long _nullValue;
  private long []_values;
  private byte []_flags;
  private int _size;
  private int _mask;

  /**
   * Create a new LongMap.  Default size is 16.
   */
  public LongMap()
  {
    _keys = (K []) new Object[16];
    _values = new long[16];
    _flags = new byte[16];

    _mask = _keys.length - 1;
    _size = 0;

    _nullValue = NULL;
  }

  /**
   * Create a new IntMap for cloning.
   */
  private LongMap(boolean dummy)
  {
  }

  /**
   * Clear the hashmap.
   */
  public void clear()
  {
    _nullValue = NULL;
    
    for (int i = 0; i < _values.length; i++) {
      _keys[i] = null;
      _flags[i] = 0;
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
  public long get(K key)
  {
    if (key == null)
      return _nullValue;

    int hash = key.hashCode() & _mask;

    while (true) {
      K mapKey = _keys[hash];

      if (mapKey == key)
        return _values[hash];
      else if (mapKey == null) {
        if ((_flags[hash] & DELETED) == 0)
          return NULL;
      }
      else if (mapKey.equals(key))
        return _values[hash];

      hash = (hash + 1) & _mask;
    }
  }

  /**
   * Expands the property table
   */
  private void resize(int newSize)
  {
    K []newKeys = (K []) new Object[newSize];
    long []newValues = new long[newSize];
    byte []newFlags = new byte[newSize];

    _mask = newKeys.length - 1;

    for (int i = 0; i < _keys.length; i++) {
      if (_keys[i] == null || (_flags[i] & DELETED) != 0)
        continue;

      int hash = _keys[i].hashCode() & _mask;

      while (true) {
        if (newKeys[hash] == null) {
          newKeys[hash] = _keys[i];
          newValues[hash] = _values[i];
          newFlags[hash] = _flags[i];
          break;
        }
        hash = (hash + 1) & _mask;
      }
    }

    _keys = newKeys;
    _values = newValues;
    _flags = newFlags;
  }

  /**
   * Puts a new value in the property table with the appropriate flags
   */
  public long put(K key, long value)
  {
    return put(key, value, false);
  }

  /**
   * Puts a new value in the property table with the appropriate flags
   */
  public long putIfNew(K key, long value)
  {
    return put(key, value, true);
  }

  /**
   * Puts a new value in the property table with the appropriate flags
   */
  private long put(K key, long value, boolean ifNew)
  {
    if (key == null) {
      long old = _nullValue;
      _nullValue = value;
      return old;
    }

    int hash = key.hashCode() & _mask;

    while (true) {
      K testKey = _keys[hash];

      if (testKey == null || (_flags[hash] & DELETED) != 0) {
        _keys[hash] = key;
        _values[hash] = value;
        _flags[hash] = 0;

        _size++;

        if (_keys.length <= 2 * _size)
          resize(2 * _keys.length);

        return NULL;
      }
      else if (key != testKey && ! testKey.equals(key)) {
        hash = (hash + 1) & _mask;
        continue;
      }
      else if (ifNew) {
        return _values[hash];
      }
      else {
        long old = _values[hash];

        _values[hash] = value;

        return old;
      }
    }
  }

  /**
   * Deletes the entry.  Returns true if successful.
   */
  public long remove(K key)
  {
    if (key == null) {
      long old = _nullValue;
      _nullValue = NULL;
      return old;
    }

    int hash = key.hashCode() & _mask;

    while (true) {
      Object mapKey = _keys[hash];

      if (mapKey == null)
        return NULL;
      else if (mapKey.equals(key)) {
        _flags[hash] |= DELETED;

        _size--;

        _keys[hash] = null;

        return _values[hash];
      }

      hash = (hash + 1) & _mask;
    }
  }
  /**
   * Returns an iterator of the keys.
   */
  public Iterator iterator()
  {
    return new LongMapIterator();
  }

  public Object clone()
  {
    LongMap clone = new LongMap(true);

    clone._keys = new Object[_keys.length];
    System.arraycopy(_keys, 0, clone._keys, 0, _keys.length);
    
    clone._values = new long[_values.length];
    System.arraycopy(_values, 0, clone._values, 0, _values.length);
    
    clone._flags = new byte[_flags.length];
    System.arraycopy(_flags, 0, clone._flags, 0, _flags.length);

    clone._mask = _mask;
    clone._size = _size;

    clone._nullValue = _nullValue;

    return clone;
  }

  public String toString()
  {
    StringBuffer sbuf = new StringBuffer();

    sbuf.append("LongMap[");
    
    boolean isFirst = true;
    
    for (int i = 0; i <= _mask; i++) {
      if ((_flags[i] & DELETED) == 0 && _keys[i] != null) {
        if (! isFirst)
          sbuf.append(", ");
        isFirst = false;
        sbuf.append(_keys[i]);
        sbuf.append(":");
        sbuf.append(_values[i]);
      }
    }
    sbuf.append("]");
    
    return sbuf.toString();
  }

  class LongMapIterator implements Iterator {
    int _index;

    public boolean hasNext()
    {
      for (; _index < _keys.length; _index++)
        if (_keys[_index] != null && (_flags[_index] & DELETED) == 0)
          return true;

      return false;
    }

    public Object next()
    {
      for (; _index < _keys.length; _index++)
        if (_keys[_index] != null && (_flags[_index] & DELETED) == 0)
          return _keys[_index++];

      return null;
    }

    public void remove()
    {
      throw new RuntimeException();
    }
  }
}
