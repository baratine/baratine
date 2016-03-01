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
 * A timed LRU cache.  Items remain valid until they expire.
 * TimedCache can simplify database caching.
 *
 * <pre><code>
 * TimedCache storyCache = new TimedCache(30, 60000);
 *
 * public Story getCurrentStory(String id)
 * {
 *   Story story = (Story) storyCache.get(id);
 *
 *   if (story == null) {
 *     story = DB.queryStoryDatabase(id);
 *     storyCache.put(id, story);
 *   }
 *
 *   return story;
 * }
 * </code></pre>
 */
public class TimedCache<K,V>
{
  private LruCache<K,TimedCache.Entry<V>> _cache;
  private long _expireInterval;

  /**
   * Creates a new timed LRU cache.
   *
   * @param capacity the maximum size of the LRU cache
   * @param expireInterval the time an entry remains valid
   */
  public TimedCache(int capacity, long expireInterval)
  {
    _cache = new LruCache<K,Entry<V>>(capacity);
    _expireInterval = expireInterval;
  }

  /**
   * Put a new item in the cache.
   */
  public V put(K key, V value)
  {
    Entry<V> oldValue = _cache.put(key, new Entry<V>(_expireInterval, value));

    if (oldValue != null)
      return oldValue.getValue();
    else
      return null;
  }

  /**
   * Gets an item from the cache, returning null if expired.
   */
  public V get(K key)
  {
    Entry<V> entry = _cache.get(key);

    if (entry == null)
      return null;

    if (entry.isValid())
      return entry.getValue();
    else {
      _cache.remove(key);

      return null;
    }
  }

  /**
   * Class representing a cached entry.
   */
  static class Entry<V> implements CacheListener {
    private long _expireInterval;
    private long _checkTime;
    private V _value;

    Entry(long expireInterval, V value)
    {
      _expireInterval = expireInterval;
      _value = value;

      _checkTime = CurrentTime.getCurrentTime();
    }

    boolean isValid()
    {
      return CurrentTime.getCurrentTime() < _checkTime + _expireInterval;
    }

    V getValue()
    {
      return _value;
    }

    public void removeEvent()
    {
      if (_value instanceof CacheListener)
        ((CacheListener) _value).removeEvent();
    }
  }
}

