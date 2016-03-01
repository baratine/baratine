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
 *
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
public class IntMap {
  /**
   * Encoding of a null entry.  Since NULL is equal to Integer.MIN_VALUE, 
   * it's impossible to distinguish between the two.
   */
  private final static Object NULL_KEY = new Object();
  
  public final static int NULL = -65536; // Integer.MIN_VALUE + 1;
  
  private static int DELETED = 0x1;
  
  private final Item []_entries;
  private final int _mask;

  private int _size;

  /**
   * Create a new IntMap.  Default size is 256
   */
  public IntMap()
  {
    this(256);
  }

  /**
   * Create a new IntMap.
   */
  public IntMap(int initialCapacity)
  {
    int capacity;

    for (capacity = 16; capacity < 2 * initialCapacity; capacity *= 2) {
    }

    _entries = new Item[capacity];
    _mask = capacity - 1;
  }

  /**
   * Create copy of an IntMap
   */
  private IntMap(Item []entries)
  {
    _entries = new Item[entries.length];
    _mask = _entries.length - 1;

    for (Item item : entries) {
      for (; item != null; item = item._next) {
        put(item._key, item._value);
      }
    }
  }

  /**
   * Clear the hashmap.
   */
  public void clear()
  {
    for (int i = 0; i < _entries.length; i++) {
      _entries[i] = null;
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
  public int get(Object key)
  {
    if (key == null)
      key = NULL_KEY;

    int hash = key.hashCode() & _mask;

    for (Item item = _entries[hash]; item != null; item = item._next) {
      Object itemKey = item._key;
      
      if (itemKey == key || itemKey.equals(key))
        return item._value;
    }

    return NULL;
  }

  /**
   * Puts a new value in the property table with the appropriate flags
   */
  public int put(Object key, int value)
  {
    if (key == null)
      key = NULL_KEY;

    synchronized (this) {
      int hash = key.hashCode() & _mask;

      for (Item item = _entries[hash]; item != null; item = item._next) {
        Object testKey = item._key;

        if (testKey == key || testKey.equals(key)) {
          int oldValue = item._value;

          item._value = value;

          return oldValue;
        }
      }

      Item item = new Item(key, value);
      item._next = _entries[hash];

      _entries[hash] = item;

      _size++;
    }

    return NULL;
  }

  /**
   * Deletes the entry.  Returns true if successful.
   */
  public int remove(Object key)
  {
    if (key == null)
      key = NULL_KEY;

    int hash = key.hashCode() & _mask;

    synchronized (this) {
      Item prev = null;
      for (Item item = _entries[hash]; item != null; item = item._next) {
        Object itemKey = item._key;
      
        if (itemKey == key || itemKey.equals(key)) {
          int oldValue = item._value;

          if (prev != null)
            prev._next = item._next;
          else
            _entries[hash] = item._next;

          _size--;

          return oldValue;
        }

        prev = item;
      }
    }

    return NULL;
  }
    
  /**
   * Returns an iterator of the keys.
   */

  public Iterator iterator()
  {
    return new IntMapIterator();
  }

  public Object clone()
  {
    return new IntMap(_entries);
  }

  public String toString()
  {
    return "IntMap[]";
    /*
    StringBuffer sbuf = new StringBuffer();

    sbuf.append("IntMap[");
    boolean isFirst = true;
    for (int i = 0; i <= _mask; i++) {
      if (_keys[i] != null) {
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
    */
  }

  class IntMapIterator implements Iterator {
    int _index = -1;
    Item _item;

    public boolean hasNext()
    {
      if (_item != null)
        return true;
      
      for (_index++; _index < _entries.length; _index++) {
        _item = _entries[_index];
      
        if (_item != null)
          return true;
      }

      return false;
    }

    public Object next()
    {
      if (_item != null) {
        Object key = _item._key;
        _item = _item._next;

        return key;
      }
      
      for (_index++; _index < _entries.length; _index++) {
        _item = _entries[_index];
      
        if (_item != null) {
          Object key = _item._key;
          _item = _item._next;

          return key;
        }
      }

      return null;
    }

    public void remove()
    {
      throw new RuntimeException();
    }
  }

  static class Item {
    final Object _key;
    int _value;
    
    Item _next;

    Item(Object key, int value)
    {
      _key = key;
      _value = value;
    }
  }
}
