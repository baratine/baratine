/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package com.caucho.v5.util;

/**
 * A fast queue implementation for int primitives.
 * Space efficient because ints are not wrapped and underlying data structure
 * is a ring buffer.
 */
public class IntQueue {
  private int _start;
  private int _end;

  private int _size;

  private int _capacity;
  private int[] _queue;

  public IntQueue() {
    this(32);
  }

  public IntQueue(int initialCapacity)
  {
    _queue = new int[initialCapacity];
    _capacity = initialCapacity;
  }

  public void add(int value)
  {
    if (_size == _capacity) {
      expand(_capacity * 2);
    }

    _queue[_end] = value;

    _end = (_end + 1) % _capacity;
    _size++;
  }

  public int remove()
  {
    if (_size == 0) {
      throw new RuntimeException("queue is empty");
    }

    int value = _queue[_start];

    _start = (_start + 1) % _capacity;
    _size--;

    return value;
  }

  public int size()
  {
    return _size;
  }

  public int capacity()
  {
    return _capacity;
  }

  private void expand(int capacity)
  {
    int[] newQueue = new int[capacity];

    if (_start < _end) {
      System.arraycopy(_queue, _start, newQueue, 0, _size);
    }
    else {
      int partSize = _capacity - _start;

      System.arraycopy(_queue, _start, newQueue, 0, partSize);
      System.arraycopy(_queue, 0, newQueue, partSize, _end);
    }

    _queue = newQueue;

    _capacity = capacity;
    _start = 0;
    _end = _size;
  }
}
