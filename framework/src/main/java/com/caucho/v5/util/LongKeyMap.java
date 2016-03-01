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
 * The LongKeyMap provides a simple hashmap from longs to values.  The API is
 * an abbreviation of the HashMap collection API.
 *
 * <p>The convenience of IntMap is avoiding all the silly wrapping of
 * integers.
 */
public class LongKeyMap {
  /**
   * Encoding of a null entry.  Since NULL is equal to Integer.MIN_VALUE, 
   * it's impossible to distinguish between the two.
   */
  private final static int DELETED = 0x1;
  private final static long DEAD_KEY = 0xdeadbeeffeedcafeL;
  private long []keys;
  private Object []values;
  private byte []flags;
  private int size;
  private int mask;

  /**
   * Create a new LongKeyMap.  Default size is 16.
   */
  public LongKeyMap()
  {
    keys = new long[16];
    values = new Object[16];
    flags = new byte[16];

    mask = keys.length - 1;
    size = 0;

    clear();
  }

  /**
   * Create a new IntMap for cloning.
   */
  private LongKeyMap(boolean dummy)
  {
  }

  /**
   * Clear the hashmap.
   */
  public void clear()
  {
    for (int i = 0; i < values.length; i++) {
      keys[i] = DEAD_KEY;
      flags[i] = 0;
      values[i] = null;
    }
    size = 0;
  }
  /**
   * Returns the current number of entries in the map.
   */
  public int size() 
  { 
    return size;
  }

  /**
   * Puts a new value in the property table with the appropriate flags
   */
  public Object get(long key)
  {
    int hash = (int) (key & mask);

    while (true) {
      long mapKey = keys[hash];

      if (mapKey == key)
        return values[hash];
      else if (mapKey == DEAD_KEY) {
        if ((flags[hash] & DELETED) == 0)
          return null;
      }

      hash = (hash + 1) & mask;
    }
  }

  /**
   * Expands the property table
   */
  private void resize(int newSize)
  {
    long []newKeys = new long[newSize];
    Object []newValues = new Object[newSize];
    byte []newFlags = new byte[newSize];

    for (int i = 0; i < newSize; i++)
      newKeys[i] = DEAD_KEY;

    mask = newKeys.length - 1;

    for (int i = 0; i < keys.length; i++) {
      if (keys[i] == DEAD_KEY || (flags[i] & DELETED) != 0)
        continue;

      int hash = (int) keys[i] & mask;

      while (true) {
        if (newKeys[hash] == DEAD_KEY) {
          newKeys[hash] = keys[i];
          newValues[hash] = values[i];
          newFlags[hash] = flags[i];
          break;
        }
        hash = (hash + 1) & mask;
      }
    }

    keys = newKeys;
    values = newValues;
    flags = newFlags;
  }

  /**
   * Puts a new value in the property table with the appropriate flags
   */
  public Object put(long key, Object value)
  {
    int hash = (int) (key & mask);
    int count = size;

    while (count-- >= 0) {
      long testKey = keys[hash];

      if (testKey == DEAD_KEY || (flags[hash] & DELETED) != 0) {
        keys[hash] = key;
        values[hash] = value;
        flags[hash] = 0;

        size++;

        if (keys.length <= 2 * size)
          resize(2 * keys.length);

        return null;
      }
      else if (key != testKey) {
        hash = (hash + 1) & mask;
        continue;
      }
      else {
        Object old = values[hash];

        values[hash] = value;

        return old;
      }
    }

    return null;
  }

  /**
   * Deletes the entry.  Returns true if successful.
   */
  public Object remove(long key)
  {
    int hash = (int) (key & mask);

    while (true) {
      long mapKey = keys[hash];

      if (mapKey == DEAD_KEY)
        return null;
      else if (mapKey == key) {
        flags[hash] |= DELETED;

        size--;

        keys[hash] = DEAD_KEY;

        return values[hash];
      }

      hash = (hash + 1) & mask;
    }
  }
  /**
   * Returns an iterator of the keys.
   */
  public Iterator iterator()
  {
    return new LongKeyMapIterator();
  }

  public Object clone()
  {
    LongKeyMap clone = new LongKeyMap(true);

    clone.keys = new long[keys.length];
    System.arraycopy(keys, 0, clone.keys, 0, keys.length);
    
    clone.values = new Object[values.length];
    System.arraycopy(values, 0, clone.values, 0, values.length);
    
    clone.flags = new byte[flags.length];
    System.arraycopy(flags, 0, clone.flags, 0, flags.length);

    clone.mask = mask;
    clone.size = size;

    return clone;
  }

  public String toString()
  {
    StringBuffer sbuf = new StringBuffer();

    sbuf.append("LongKeyMap[");
    boolean isFirst = true;
    for (int i = 0; i <= mask; i++) {
      if ((flags[i] & DELETED) == 0 && keys[i] != DEAD_KEY) {
        if (! isFirst)
          sbuf.append(", ");
        isFirst = false;
        sbuf.append(keys[i]);
        sbuf.append(":");
        sbuf.append(values[i]);
      }
    }
    sbuf.append("]");
    return sbuf.toString();
  }

  class LongKeyMapIterator implements Iterator {
    int index;

    public boolean hasNext()
    {
      for (; index < keys.length; index++)
        if (keys[index] != DEAD_KEY && (flags[index] & DELETED) == 0)
          return true;

      return false;
    }

    public Object next()
    {
      for (; index < keys.length; index++)
        if (keys[index] != DEAD_KEY && (flags[index] & DELETED) == 0)
          return new Long(keys[index++]);

      return null;
    }

    public void remove()
    {
      throw new RuntimeException();
    }
  }
}
