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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed length cache with a LRU replacement policy.  If cache items
 * implement CacheListener, they will be informed when they're removed
 * from the cache.
 *
 * <p>Null keys are not allowed.  LruCache is synchronized.
 */
public class LruCache<K,V> {
  private static final Object NULL = new Object();
  private static final Object MISMATCH = new Object();

  // maximum allowed entries
  private int _capacity;
  // size 1 capacity is half the actual capacity
  private int _capacity1;

  // hash table containing the entries.  Its size is twice the capacity
  // so it will always remain at least half empty
  private final CacheItem<K,V> []_entries;
  private final Object []_locks;

  // mask for hash mapping
  private int _prime;

  private boolean _isEnableListeners = true;

  //
  // LRU
  //

  private final Object _lruLock = new Object();

  // number of items in the cache seen once
  private int _size1;

  // head of the LRU list
  private CacheItem<K,V> _head1;
  // tail of the LRU list
  private CacheItem<K,V> _tail1;

  // number of items in the cache seen more than once
  private int _size2;

  // head of the LRU list
  private CacheItem<K,V> _head2;
  // tail of the LRU list
  private CacheItem<K,V> _tail2;

  // lru timeout reduces lru updates for the most used items
  private final int _lruTimeout;

  private final AtomicBoolean _isLruTailRemove = new AtomicBoolean();

  // counts group 2 updates, rolling over at 0x3fffffff
  private volatile int _lruCounter;

  //
  // statistics
  //

  private boolean _isEnableStatistics;

  // hit count statistics
  private volatile long _hitCount;
  // miss count statistics
  private volatile long _missCount;

  /**
   * Create the LRU cache with a specific capacity.
   *
   * @param initialCapacity minimum capacity of the cache
   */
  public LruCache(int initialCapacity)
  {
    this(initialCapacity, false);
  }

  /**
   * Create the LRU cache with a specific capacity.
   *
   * @param initialCapacity minimum capacity of the cache
   */
  public LruCache(int initialCapacity, boolean isStatistics)
  {
    int capacity;

    for (capacity = 16; capacity < 2 * initialCapacity; capacity *= 2) {
    }

    _entries = new CacheItem[capacity];
    _prime = Primes.getBiggestPrime(_entries.length);

    _locks = new Object[(_entries.length >> 3) + 1];
    for (int i = 0; i < _locks.length; i++) {
      _locks[i] = new Object();
    }

    _capacity = initialCapacity;
    _capacity1 = _capacity / 2;

    if (_capacity > 32)
      _lruTimeout = _capacity / 8;
    else
      _lruTimeout = 1;

    _isEnableStatistics = isStatistics;

    /*
    if (isStatistics) {
      _hitCount = new AtomicLong();
      _missCount = new AtomicLong();
    }
    */
  }

  /**
   * Disable the listeners
   */
  public void setEnableListeners(boolean isEnable)
  {
    _isEnableListeners = isEnable;
  }

  public void setEnableStatistics(boolean isEnable)
  {
    _isEnableStatistics = isEnable;
  }

  /**
   * Returns the current number of entries in the cache.
   */
  public int size()
  {
    return _size1 + _size2;
  }

  /**
   * Returns the LRU cache capacity
   */
  public int getCapacity()
  {
    return _capacity;
  }

  /**
   * Clears the cache
   */
  public void clear()
  {
    if (_size1 == 0 && _size2 == 0)
      return;

    ArrayList<CacheListener> listeners = null;

    for (int i = _entries.length - 1; i >= 0; i--) {
      Object lock = getLock(i);

      synchronized (lock) {
        CacheItem<K,V> item = _entries[i];
        _entries[i] = null;

        if (_isEnableListeners) {
          for (; item != null; item = item._nextHash) {
            removeLruItem(item);

            if (item._value instanceof CacheListener) {
              if (listeners == null)
                listeners = new ArrayList<CacheListener>();
              listeners.add((CacheListener) item._value);
            }
          }
        }
      }
    }

    for (int i = listeners != null ? listeners.size() - 1 : -1;
         i >= 0;
         i--) {
      CacheListener listener = listeners.get(i);
      listener.removeEvent();
    }
  }

  /**
   * Get an item from the cache and make it most recently used.
   *
   * @param key key to lookup the item
   * @return the matching object in the cache
   */
  public V get(K key)
  {
    Object okey = key;
    if (okey == null)
      okey = NULL;

    int hash = (okey.hashCode() & 0x7fffffff) % _prime;

    CacheItem<K,V> item;

    for (item = _entries[hash];
         item != null;
         item = item._nextHash) {
      Object itemKey = item._key;

      if (itemKey == okey || itemKey.equals(okey)) {
        updateLru(item);

        if (_isEnableStatistics)
          _hitCount++;

        return item._value;
      }
    }

    if (_isEnableStatistics) {
      _missCount++;
    }

    return null;
  }

  /**
   * Puts a new item in the cache.  If the cache is full, remove the
   * LRU item.
   *
   * @param key key to store data
   * @param value value to be stored
   *
   * @return old value stored under the key
   */
  public V put(K key, V value)
  {
    V oldValue = compareAndPut(null, key, value, false);

    return oldValue;
  }

  /**
   * Puts a new item in the cache.  If the cache is full, remove the
   * LRU item.
   *
   * @param key key to store data
   * @param value value to be stored
   *
   * @return the value actually stored
   */
  public V putIfNew(K key, V value)
  {
    V oldValue = compareAndPut(null, key, value, true);

    if (oldValue != null)
      return oldValue;
    else
      return value;
  }

  /**
   * Puts a new item in the cache if the current value matches oldValue.
   *
   * @param key the key
   * @param value the new value
   * @param testValue the value to test against the current
   *
   * @return true if the put succeeds
   */
  public boolean compareAndPut(V testValue, K key, V value)
  {
    V result = compareAndPut(testValue, key, value, true);

    return testValue == result;
  }

  /**
   * Puts a new item in the cache.  If the cache is full, remove the
   * LRU item.
   *
   * @param key key to store data
   * @param value value to be stored
   * @param testValue tests the current value in the cache
   * @param isCompare if true, this is a compare and put
   *
   * @return old value stored under the key
   */
  private V compareAndPut(V testValue, K key, V value, boolean isCompare)
  {
    Object okey = key;

    if (okey == null)
      okey = NULL;

    // remove LRU items until we're below capacity
    removeLru();

    int hash = (okey.hashCode() & 0x7fffffff) % _prime;

    V oldValue = null;

    Object lock = getLock(hash);

    synchronized (lock) {
      CacheItem<K,V> item = _entries[hash];

      for (;
           item != null;
           item = item._nextHash) {
        // matching item gets replaced
        if (okey == item._key || okey.equals(item._key)) {
          updateLru(item);

          oldValue = item._value;

          if (isCompare && testValue != oldValue) {
            return oldValue;
          }

          item._value = value;

          if (value == oldValue)
            oldValue = null;

          break;
        }
      }

      if (isCompare && testValue != oldValue) {
        return null;
      }

      if (item == null) {
        CacheItem<K,V> next = _entries[hash];

        item = new CacheItem<K,V>((K) okey, value);

        // item must be added to lru first, because a get() hit can update
        // the lru, and the item must be in the lru before that happens
        synchronized (_lruLock) {
          assert(item._hitCount == 1);

          // server/1401
          _lruCounter = (_lruCounter + 1) & 0x3fffffff;
          _size1++;

          // server/1406 - the item's lruCounter is not updated
          // because the next hit needs to move it to head2
          item._lruCounter = - (_lruTimeout + 16);

          item._nextLru = _head1;
          if (_head1 != null)
            _head1._prevLru = item;
          _head1 = item;

          if (_tail1 == null)
            _tail1 = item;
        }

        item._nextHash = next;
        _entries[hash] = item;

        return null;
      }

      if (_isEnableListeners
          && oldValue instanceof SyncCacheListener)
        ((SyncCacheListener) oldValue).syncRemoveEvent();
    }

    if (_isEnableListeners && oldValue instanceof CacheListener)
      ((CacheListener) oldValue).removeEvent();

    return oldValue;
  }

  /**
   * Put item at the head of the used-twice lru list.
   * This is always called while synchronized.
   */
  private void updateLru(CacheItem<K,V> item)
  {
    long lruCounter = _lruCounter;
    long itemCounter = item._lruCounter;

    long delta = (lruCounter - itemCounter) & 0x3fffffff;

    if (_lruTimeout < delta || delta < 0) {
      // update LRU only if not used recently
      updateLruImpl(item);
    }
  }

  private void updateLruImpl(CacheItem<K,V> item)
  {
    synchronized (_lruLock) {
      _lruCounter = (_lruCounter + 1) & 0x3fffffff;

      item._lruCounter = _lruCounter;

      CacheItem<K,V> prevLru = item._prevLru;
      CacheItem<K,V> nextLru = item._nextLru;

      if (item._hitCount <= 0) {
        // item deleted before update
        return;
      }
      else if (item._hitCount++ == 1) {
        item._prevLru = null;
        item._nextLru = _head2;

        if (prevLru != null)
          prevLru._nextLru = nextLru;
        else {
          assert(_head1 == item);
          _head1 = nextLru;
        }

        if (nextLru != null)
          nextLru._prevLru = prevLru;
        else {
          assert(_tail1 == item);
          _tail1 = prevLru;
        }

        if (_head2 != null)
          _head2._prevLru = item;
        else {
          assert(_tail2 == null);

          _tail2 = item;
        }

        _head2 = item;

        _size1--;
        _size2++;
      }
      else {
        assert(item._hitCount > 1);

        if (item == _head2)
          return;

        item._prevLru = null;
        item._nextLru = _head2;

        prevLru._nextLru = nextLru;

        _head2._prevLru = item;
        _head2 = item;

        if (nextLru != null)
          nextLru._prevLru = prevLru;
        else {
          assert(_tail2 == item);

          _tail2 = prevLru;
        }
      }
    }
  }

  private void removeLru()
  {
    int overflow = _size1 + _size2 - _capacity;

    if (overflow >= 0) {
      if (_isLruTailRemove.compareAndSet(false, true)) {
        try {
          // remove LRU items until we're below capacity
          for (; overflow >= 0 && removeTail(); overflow--) {
          }
        } finally {
          _isLruTailRemove.set(false);
        }
      }
    }
  }

  /**
   * Remove the last item in the LRU
   */
  public boolean removeTail()
  {
    CacheItem<K,V> tail = null;

    if (_capacity1 <= _size1)
      tail = _tail1;

    if (tail == null) {
      tail = _tail2;

      if (tail == null) {
        tail = _tail1;

        if (tail == null)
          return false;
      }
    }

    V oldValue = tail._value;
    if (oldValue instanceof LruListener) {
      ((LruListener) oldValue).lruEvent();
    }

    V value = remove(tail._key);

    return true;
  }

  /**
   * Remove the last item in the LRU.  In this case, remove from the
   * list with the longest length.
   *
   * For functions like Cache disk space, this is a better solution
   * than the struct LRU removal.
   */
  public boolean removeLongestTail()
  {
    CacheItem<K,V> tail;

    if (_size1 <= _size2)
      tail = _tail2;
    else
      tail = _tail1;

    if (tail == null)
      return false;

    V oldValue = tail._value;
    if (oldValue instanceof LruListener)
      ((LruListener) oldValue).lruEvent();

    V value = remove(tail._key);

    return true;
  }

  /**
   * Removes an item from the cache
   *
   * @param key the key to remove
   *
   * @return the value removed
   */
  public V remove(K key)
  {
    Object okey = key;
    if (okey == null)
      okey = NULL;

    int hash = (okey.hashCode() & 0x7fffffff) % _prime;

    Object lock = getLock(hash);

    V value = null;

    synchronized (lock) {
      CacheItem<K,V> prevItem = null;

      for (CacheItem<K,V> item = _entries[hash];
           item != null;
           item = item._nextHash) {
        if (item._key == okey || item._key.equals(okey)) {
          removeLruItem(item);

          CacheItem<K,V> nextHash = item._nextHash;

          if (prevItem != null)
            prevItem._nextHash = nextHash;
          else {
            assert(_entries[hash] == item);

            _entries[hash] = nextHash;
          }

          value = item._value;
          break;
        }

        prevItem = item;
      }

      if (_isEnableListeners && value instanceof SyncCacheListener)
        ((SyncCacheListener) value).syncRemoveEvent();
    }

    if (_isEnableListeners && value instanceof CacheListener)
      ((CacheListener) value).removeEvent();

    return value;
  }

  private void removeLruItem(CacheItem<K,V> item)
  {
    synchronized (_lruLock) {
      _lruCounter = (_lruCounter + 1) & 0x3fffffff;

      CacheItem<K,V> prevLru = item._prevLru;
      CacheItem<K,V> nextLru = item._nextLru;

      item._prevLru = null;
      item._nextLru = null;

      int hitCount = item._hitCount;
      item._hitCount = -1;

      if (hitCount <= 0)
        return;
      else if (hitCount == 1) {
        _size1--;

        if (prevLru != null)
          prevLru._nextLru = nextLru;
        else {
          assert(_head1 == item);

          _head1 = nextLru;
        }

        if (nextLru != null)
          nextLru._prevLru = prevLru;
        else {
          assert(_tail1 == item);

          _tail1 = prevLru;
        }
      }
      else {
        _size2--;

        if (prevLru != null)
          prevLru._nextLru = nextLru;
        else {
          assert(_head2 == item);

          _head2 = nextLru;
        }

        if (nextLru != null)
          nextLru._prevLru = prevLru;
        else {
          assert(_tail2 == item);

          _tail2 = prevLru;
        }
      }
    }
  }

  private Object getLock(int hash)
  {
    return _locks[hash >> 3];
  }

  /**
   * Returns the keys stored in the cache
   */
  public Iterator<K> keys()
  {
    KeyIterator<K,V> iter = new KeyIterator<K,V>(this);

    iter.init(this);

    return iter;
  }

  /**
   * Returns keys stored in the cache using an old iterator
   */
  public Iterator<K> keys(Iterator<K> oldIter)
  {
    KeyIterator<K,V> iter = (KeyIterator<K,V>) oldIter;

    iter.init(this);

    return oldIter;
  }

  /**
   * Returns the values in the cache
   */
  public Iterable<V> valueIterable()
  {
    return new ValueIterable();
  }

  /**
   * Returns the values in the cache
   */
  public Iterator<V> values()
  {
    ValueIterator<K,V> iter = new ValueIterator<K,V>(this);
    iter.init(this);
    return iter;
  }

  @SuppressWarnings("unchecked")
  public Iterator<V> values(Iterator<V> oldIter)
  {
    ValueIterator<K,V> iter = (ValueIterator<K,V>) oldIter;
    iter.init(this);
    return oldIter;
  }

  /**
   * Returns the entries
   */
  public Iterator<Entry<K,V>> iterator()
  {
    return new EntryIterator();
  }

  /**
   * Returns the hit count.
   */
  public long getHitCount()
  {
    return _hitCount;
  }

  /**
   * Returns the miss count.
   */
  public long getMissCount()
  {
    return _missCount;
  }

  /**
   * A cache item
   */
  static class CacheItem<K,V> {
    volatile CacheItem<K,V> _nextHash;

    CacheItem<K,V> _prevLru;
    CacheItem<K,V> _nextLru;

    volatile int _lruCounter; // LRU only updated after expire time

    final K _key;
    V _value;
    int _index;
    int _hitCount = 1;

    CacheItem(K key, V value)
    {
      if (key == null)
        throw new NullPointerException();

      _key = key;
      _value = value;
    }
  }

  /**
   * Iterator of cache keys
   */
  static class KeyIterator<K,V> implements Iterator<K> {
    private LruCache<K,V> _cache;
    private CacheItem<K,V> _item;
    private boolean _isHead1;

    KeyIterator(LruCache<K,V> cache)
    {
      init(cache);
    }

    void init(LruCache<K,V> cache)
    {
      _cache = cache;

      _item = _cache._head2;
      _isHead1 = false;
      if (_item == null) {
        _item = _cache._head1;
        _isHead1 = true;
      }
    }

    /**
     * Returns the next entry in the cache.
     */
    @Override
    public boolean hasNext()
    {
      return _item != null;
    }

    /**
     * Returns the next key.
     */
    @Override
    public K next()
    {
      CacheItem<K,V> entry = _item;

      if (_item != null)
        _item = _item._nextLru;

      if (_item == null && ! _isHead1) {
        _isHead1 = true;
        _item = _cache._head1;
      }

      if (entry != null)
        return entry._key;
      else
        return null;
    }

    @Override
    public void remove()
    {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Iterator of cache values
   */
  static class ValueIterator<K,V> implements Iterator<V> {
    private LruCache<K,V> _cache;
    private CacheItem<K,V> _item;
    private boolean _isHead1;

    ValueIterator(LruCache<K,V> cache)
    {
      init(cache);
    }

    void init(LruCache<K,V> cache)
    {
      _cache = cache;

      _item = _cache._head2;
      _isHead1 = false;
      if (_item == null) {
        _item = _cache._head1;
        _isHead1 = true;
      }
    }

    /**
     * Returns the next entry in the cache.
     */
    public boolean hasNext()
    {
      return _item != null;
    }

    /**
     * Returns the next value.
     */
    public V next()
    {
      CacheItem<K,V> entry = _item;

      if (_item != null)
        _item = _item._nextLru;

      if (_item == null && ! _isHead1) {
        _isHead1 = true;
        _item = _cache._head1;
      }

      if (entry != null)
        return entry._value;
      else
        return null;
    }

    public void remove()
    {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Interface for entry iterator;
   */
  public interface Entry<K,V> {
    /**
     * Returns the key.
     */
    public K getKey();

    /**
     * Returns the value.
     */
    public V getValue();
  }

  /**
   * Iterator of cache values
   */
  class EntryIterator implements Iterator<Entry<K,V>>, Entry<K,V> {
    private int _i = -1;

    public boolean hasNext()
    {
      int i = _i + 1;
      CacheItem<K,V> []entries = _entries;
      int length = entries.length;

      for (; i < length && entries[i] == null; i++) {
      }

      _i = i - 1;

      return i < length;
    }

    public Entry<K,V> next()
    {
      int i = _i + 1;
      CacheItem<K,V> []entries = _entries;
      int length = entries.length;

      for (; i < length && entries[i] == null; i++) {
      }

      _i = i;

      if (_i < length) {
        return this;
      }
      else
        return null;
    }

    /**
     * Returns the key.
     */
    public K getKey()
    {
      if (_i < _entries.length) {
        CacheItem<K,V> entry = _entries[_i];

        if (entry == null)
          return null;
        else if (entry._key == NULL)
          return null;
        else
          return entry._key;
      }

      return null;
    }

    /**
     * Returns the value.
     */
    public V getValue()
    {
      if (_i < _entries.length) {
        CacheItem<K,V> entry = _entries[_i];

        return entry != null ? entry._value : null;
      }

      return null;
    }

    public void remove()
    {
      if (_i < _entries.length) {
        CacheItem<K,V> entry = _entries[_i];

        if (entry != null)
          LruCache.this.remove(entry._key);
      }
    }
  }
  
  private class ValueIterable implements Iterable<V> {
    @Override
    public Iterator<V> iterator()
    {
      return values();
    }
  }
}
