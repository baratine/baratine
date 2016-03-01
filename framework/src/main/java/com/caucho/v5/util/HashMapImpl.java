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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * HashMap which doesn't allocate a new DeployController per item.
 */
public class HashMapImpl<K,V> extends AbstractMap<K,V> {
  // array containing the keys
  private K []_keys;

  // array containing the values
  private V []_values;

  private V _nullValue;

  // maximum allowed entries
  private int _capacity;
  // number of items in the cache
  private int _size;
  private int _mask;

  /**
   * Create the hash map impl with a specific capacity.
   *
   * @param initialCapacity minimum capacity of the cache
   */
  public HashMapImpl()
  {
    this(16);
  }

  /**
   * Create the hash map impl with a specific capacity.
   *
   * @param initialCapacity minimum capacity of the cache
   */
  public HashMapImpl(int initialCapacity)
  {
    int capacity;

    for (capacity = 16; capacity < 2 * initialCapacity; capacity *= 2) {
    }

    _keys = (K []) new Object[capacity];
    _values = (V []) new Object[capacity];
    _mask = capacity - 1;

    _capacity = initialCapacity;
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
    if (_size > 0) {
      final K []keys = _keys;
      final V []values = _values;
      final int length = values.length;

      for (int i = length - 1; i >= 0; i--) {
        keys[i] = null;
        values[i] = null;
      }

      _size = 0;
    }

    _nullValue = null;
  }

  /**
   * Get an item from the cache and make it most recently used.
   *
   * @param key key to lookup the item
   * @return the matching object in the cache
   */
  @Override
  public V get(Object key)
  {
    if (key == null)
      return _nullValue;

    int hash = key.hashCode() & _mask;
    int count = _size + 1;

    K []keys = _keys;

    for (; count > 0; count--) {
      K mapKey = keys[hash];

      if (mapKey == null)
        return null;

      if (key.equals(_keys[hash]))
        return _values[hash];

      hash = (hash + 1) & _mask;
    }

    return null;
  }

  /**
   * Puts a new item in the cache.
   *
   * @param key key to store data
   * @param value value to be stored
   *
   * @return old value stored under the key
   */
  @Override
  public V put(K key, V value)
  {
    if (key == null) {
      V item = _nullValue;

      _nullValue = value;

      return item;
    }

    // forced resizing if 1/2 full
    if (_values.length <= 2 * _size) {
      K []oldKeys = _keys;
      V []oldValues = _values;

      _keys = (K []) new Object[2 * oldKeys.length];
      _values = (V []) new Object[2 * oldValues.length];

      _mask = _values.length - 1;
      _size = 0;

      for (int i = oldValues.length - 1; i >= 0; i--) {
        K oldKey = oldKeys[i];
        V oldValue = oldValues[i];

        if (oldValue != null)
          putImpl(oldKey, oldValue);
      }
    }
    
    V item = putImpl(key, value);

    return item;
  }

  /**
   * Implementation of the put.
   */
  private V putImpl(K key, V value)
  {
    V item = null;

    int hash = key.hashCode() & _mask;
    int count = _values.length;

    for (; count > 0; count--) {
      item = _values[hash];

      // No matching item, so create one
      if (item == null) {
        _keys[hash] = key;
        _values[hash] = value;
        _size++;

        return null;
      }

      // matching item gets replaced
      if (_keys[hash].equals(key)) {
        _values[hash] = value;

        return item;
      }

      hash = (hash + 1) & _mask;
    }

    throw new IllegalStateException();
  }

  /**
   * Removes an item from the cache
   *
   * @param key the key to remove
   *
   * @return the value removed
   */
  @Override
  public V remove(Object key)
  {
    if (key == null) {
      V value = _nullValue;
      _nullValue = null;
      return value;
    }

    int hash = key.hashCode() & _mask;
    int count = _size + 1;

    V item = null;

    for (; count > 0; count--) {
      item = _values[hash];

      if (item == null)
        return null;

      if (_keys[hash].equals(key)) {
        _keys[hash] = null;
        _values[hash] = null;
        _size--;

        refillEntries(hash);
        break;
      }

      hash = (hash + 1) & _mask;
    }

    if (count < 0)
      throw new RuntimeException("internal cache error");

    return item;
  }

  /**
   * Put the item in the best location available in the hash table.
   */
  private void refillEntries(int hash)
  {
    for (int count = _size; count >= 0; count--) {
      hash = (hash + 1) & _mask;

      if (_values[hash] == null)
        return;

      refillEntry(hash);
    }
  }

  /**
   * Put the item in the best location available in the hash table.
   */
  private void refillEntry(int baseHash)
  {
    K key = _keys[baseHash];
    V value = _values[baseHash];

    _keys[baseHash] = null;
    _values[baseHash] = null;

    int hash = key.hashCode() & _mask;

    for (int count = _size; count >= 0; count--) {
      if (_values[hash] == null) {
        _keys[hash] = key;
        _values[hash] = value;
        return;
      }

      hash = (hash + 1) & _mask;
    }
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
    private HashMapImpl<K1,V1> _map;

    KeySet(HashMapImpl<K1,V1> map)
    {
      _map = map;
    }

    /**
     * Returns the size.
     */
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
      if (key == null)
        return _map._nullValue != null;

      K1 []keys = _map._keys;

      for (int i = keys.length - 1 ; i >= 0; i--) {
        K1 testKey = keys[i];

        if (key.equals(testKey))
          return true;
      }

      return false;
    }

    /**
     * Returns the iterator.
     */
    @Override
    public boolean removeAll(Collection<?> keys)
    {
      if (keys == null)
        return false;

      Iterator<?> iter = keys.iterator();
      while (iter.hasNext()) {
        Object key = iter.next();

        _map.remove(key);
      }

      return true;
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
    private HashMapImpl<K1,V1> _map;
    private int _i;

    KeyIterator(HashMapImpl<K1,V1> map)
    {
      init(map);
    }

    void init(HashMapImpl<K1,V1> map)
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

    @Override
    public void remove()
    {
      if (_i > 0)
        _map.remove(_map._keys[_i - 1]);
    }
  }

  /**
   * Returns the entry set of the cache
   */
  @Override
  public Set<Map.Entry<K,V>> entrySet()
  {
    return new EntrySet(this);
  }

  /**
   * Iterator of cache values
   */
  static class EntrySet<K1,V1> extends AbstractSet<Map.Entry<K1,V1>> {
    private HashMapImpl<K1,V1> _map;

    EntrySet(HashMapImpl<K1,V1> map)
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
    public Iterator<Map.Entry<K1,V1>> iterator()
    {
      return new EntryIterator(_map);
    }
  }

  /**
   * Iterator of cache values
   */
  static class EntryIterator<K1,V1> implements Iterator<Map.Entry<K1,V1>> {
    private final Entry<K1,V1> _entry = new Entry<K1,V1>();

    private HashMapImpl<K1,V1> _map;
    private int _i;

    EntryIterator(HashMapImpl<K1,V1> map)
    {
      init(map);
    }

    void init(HashMapImpl<K1,V1> map)
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
    public Map.Entry<K1,V1> next()
    {
      K1 []keys = _map._keys;
      int len = keys.length;

      for (; _i < len; _i++) {
        if (keys[_i] != null) {
          _entry.init(_map, _i++);

          return _entry;
        }
      }

      return null;
    }

    @Override
    public void remove()
    {
      if (_i > 0)
        _map.remove(_map._keys[_i - 1]);
    }
  }

  static class Entry<K1,V1> implements Map.Entry<K1,V1> {
    private HashMapImpl<K1,V1> _map;
    private int _i;

    void init(HashMapImpl<K1,V1> map, int i)
    {
      _map = map;
      _i = i;
    }

    /**
     * Gets the key of the entry.
     */
    public K1 getKey()
    {
      return _map._keys[_i];
    }

    /**
     * Gets the value of the entry.
     */
    public V1 getValue()
    {
      return _map._values[_i];
    }

    /**
     * Sets the value of the entry.
     */
    public V1 setValue(V1 value)
    {
      V1 oldValue = _map._values[_i];

      _map._values[_i] = value;

      return oldValue;
    }
  }
}
