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

package com.caucho.v5.web.webapp;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import io.baratine.web.MultiMap;

/**
 * HashMap which doesn't allocate a new DeployController per item.
 */
public class MultiMapImpl<K,V> extends AbstractMap<K,List<V>>
  implements MultiMap<K,V>
{
  public static final MultiMap EMPTY_MAP = new MultiMapImpl<>(8);
  
  private static final Logger log
    = Logger.getLogger(MultiMapImpl.class.getName());
  
  // array containing the keys
  private K []_keys;

  // array containing the values
  private List<V> []_values;

  // maximum allowed entries
  private int _maxCapacity;
  // number of items in the cache
  private int _size;

  /**
   * Create the hash map impl with a specific capacity.
   *
   * @param maxCapacity maximum capacity of the Map
   */
  public MultiMapImpl(int maxCapacity)
  {
    _maxCapacity = maxCapacity;
    
    int size = 8;

    _keys = (K []) new Object[size];
    _values = (List<V> []) new List[size];

    _size = 0;
  }

  /**
   * Returns the current number of entries in the cache.
   */
  @Override
  public int size()
  {
    return _size;
  }

  /**
   * Clears the cache
   */
  @Override
  public void clear()
  {
  }

  /**
   * Get an item from the cache and make it most recently used.
   *
   * @param key key to lookup the item
   * @return the matching object in the cache
   */
  @Override
  public List<V> get(Object key)
  {
    if (key == null) {
      return null;
    }

    K[] keys = _keys;
    int size = _size;
    
    for (int i = size - 1; i >= 0; i--) {
      if (key.equals(keys[i])) {
        return _values[i];
      }
    }
    return null;
  }

  /**
   * Returns true if the map contains the value.
   */
  @Override
  public boolean containsKey(Object key)
  {
    if (key == null) {
      return false;
    }

    K []keys = _keys;

    for (int i = _size - 1 ; i >= 0; i--) {
      K testKey = keys[i];

      if (key.equals(testKey)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Puts a new item in the cache.
   *
   * @param key key to store data
   * @param value value to be stored
   *
   * @return old value stored under the key
   */
  public void add(K key, V value)
  {
    if (key == null) {
      return;
    }

    K []keys = _keys;
    final int size = _size;
    
    for (int i = size - 1; i >= 0; i--) {
      if (key.equals(keys[i])) {
        _values[i].add(value);
        return;
      }
    }

    if (! ensureCapacity(size)) {
      return;
    }
    
    _keys[size] = key;
    _values[size] = new ArrayList<V>();
    _values[size].add(value);
    
    _size = size + 1;
  }

  @Override
  public List<V> put(K key, List<V> value)
  {
    if (key == null)
      throw new IllegalArgumentException();

    K []keys = _keys;
    final int size = _size;

    for (int i = size - 1; i >= 0; i--) {
      if (key.equals(keys[i])) {
        final List<V> values = _values[i];
        _values[i] = value;
        return values;
      }
    }

    if (! ensureCapacity(size)) {
      return null;
    }

    _keys[size] = key;
    _values[size] = value;

    _size = size + 1;

    return null;
  }

  private boolean ensureCapacity(int size)
  {
    if (_keys.length <= size) {
      int newSize = Math.min(2 * size, _maxCapacity);

      if (newSize <= _size) {
        log.warning("Overflow map");

        return false;
      }

      // forced resizing if 1/2 full
      K []newKeys = (K []) new Object[newSize];
      List<V>[]newValues = (List<V> []) new List[newSize];

      System.arraycopy(_keys, 0, newKeys, 0, _keys.length);
      System.arraycopy(_values, 0, newValues, 0, _values.length);

      _keys = newKeys;
      _values = newValues;
    }

    return true;
  }

  /**
   * Returns the entry set of the cache
   */
  @Override
  public Set<K> keySet()
  {
    return new KeySet(this);
  }

  /**
   * Iterator of cache values
   */
  static class KeySet<K1,V1> extends AbstractSet<K1> {
    private MultiMapImpl<K1,V1> _map;

    KeySet(MultiMapImpl<K1,V1> map)
    {
      _map = map;
    }

    /**
     * Returns the size.
     */
    @Override
    public int size()
    {
      return _map.size();
    }

    /**
     * Returns true if the map contains the value.
     */
    @Override
    public boolean contains(Object key)
    {
      return _map.containsKey(key);
    }

    /**
     * Returns the iterator.
     */
    public Iterator<K1> iterator()
    {
      return new KeyIterator<K1,V1>(_map);
    }
  }

  /**
   * Iterator of cache values
   */
  static class KeyIterator<K1,V1> implements Iterator<K1> {
    private MultiMapImpl<K1,V1> _map;
    private int _i;

    KeyIterator(MultiMapImpl<K1,V1> map)
    {
      init(map);
    }

    void init(MultiMapImpl<K1,V1> map)
    {
      _map = map;
      _i = 0;
    }

    @Override
    public boolean hasNext()
    {
      K1 []keys = _map._keys;
      int len = keys.length;

      for (; _i < len; _i++) {
        if (keys[_i] != null)
          return true;
      }

      return false;
    }

    @Override
    public K1 next()
    {
      K1 []keys = _map._keys;
      int len = keys.length;

      for (; _i < len; _i++) {
        K1 key = keys[_i];

        if (key != null) {
          _i++;

          return key;
        }
      }

      return null;
    }
  }

  /**
   * Returns the entry set of the cache
   */
  @Override
  public Set<Map.Entry<K,List<V>>> entrySet()
  {
    return new EntrySet<>(this);
  }

  /**
   * Iterator of cache values
   */
  static class EntrySet<K1,V1> extends AbstractSet<Map.Entry<K1,List<V1>>>
  {
    private MultiMapImpl<K1,V1> _map;

    EntrySet(MultiMapImpl<K1,V1> map)
    {
      _map = map;
    }

    /**
     * Returns the size.
     */
    @Override
    public int size()
    {
      return _map.size();
    }

    /**
     * Returns the iterator.
     */
    @Override
    public Iterator<Map.Entry<K1,List<V1>>> iterator()
    {
      return new EntryIterator<>(_map);
    }
  }

  /**
   * Iterator of cache values
   */
  static class EntryIterator<K1,V1>
    implements Iterator<Map.Entry<K1,List<V1>>>
  {
    private MultiMapImpl<K1,V1> _map;
    private int _i;

    EntryIterator(MultiMapImpl<K1,V1> map)
    {
      init(map);
    }

    void init(MultiMapImpl<K1,V1> map)
    {
      _map = map;
      _i = 0;
    }

    @Override
    public boolean hasNext()
    {
      K1 []keys = _map._keys;
      int len = _map._size;

      for (; _i < len; _i++) {
        if (keys[_i] != null) {
          return true;
        }
      }

      return false;
    }

    @Override
    public Map.Entry<K1,List<V1>> next()
    {
      int i = _i++;
      
      if (i < _map._size) {
        return new Entry<>(_map._keys[i], _map._values[i]);
      }

      return null;
    }
  }

  static class Entry<K1,V1> implements Map.Entry<K1,V1> {
    private K1 _key;
    private V1 _value;

    Entry(K1 key, V1 value)
    {
      _key = key;
      _value = value;
    }

    /**
     * Gets the key of the entry.
     */
    public K1 getKey()
    {
      return _key;
    }

    /**
     * Gets the value of the entry.
     */
    @Override
    public V1 getValue()
    {
      return _value;
    }

    /**
     * Sets the value of the entry.
     */
    public V1 setValue(V1 value)
    {
      return null;
    }
  }
}
