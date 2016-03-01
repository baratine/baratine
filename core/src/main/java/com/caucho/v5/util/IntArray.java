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
 * The IntArray is a variable array containing integers.  The API follows
 * the JDK 1.2 collection classes.
 */
public class IntArray {
  private int []_data;
  private int _size;

  /**
   * Create an empty int array.
   */
  public IntArray()
  { 
    _data = new int[16];
    _size = 0;
  }

  /**
   * clear the array, i.e. set the size to 0.
   */
  public void clear()
  {
    _size = 0;
  }
  
  /**
   * Returns the current size of the array
   */
  public int size()
  {
    return _size;
  }

  /**
   * Returns the data array.
   */
  public int []getArray()
  {
    return _data;
  }
  
  /**
   * Adds an integer to the array.
   */
  public void add(int i)
  {
    if (_data.length <= _size)
      expand(_size + 1);

    _data[_size++] = i;
  }
  
  /**
   * Appends the integers in array to the end of this array.
   */
  public void add(IntArray array)
  {
    if (_data.length <= array._size)
      expand(_size + array._size);

    for (int i = 0; i < array._size; i++)
      _data[_size++] = array._data[i];
  }
  /**
   * Inserts an integer into the array.
   */
  public void add(int i, int value)
  {
    expand(_size + 1);

    System.arraycopy(_data, i, _data, i + 1, _size - i);
    _data[i] = value;
    _size++;
  }
  
  /**
   * Pops the value off the end of the array.
   */
  public int pop()
  {
    return _data[--_size];
  }
  
  /**
   * Sets the length of the array, filling with zero if necessary.
   */
  public void setLength(int size)
  {
    expand(size);

    for (int i = _size; i < size; i++)
      _data[i] = 0;

    _size = size;
  }
  
  private void expand(int max)
  {
    while (_data.length < max) {
      int []next = new int[_data.length * 2];

      for (int i = 0; i < _data.length; i++)
        next[i] = _data[i];

      _data = next;
    }
  }
  /**
   * Gets the integer value at the given index.
   *
   * @param i index into the array.
   * @return value at the index
   */
  public int get(int i)
  {
    return _data[i];
  }
  /**
   * Returns the last integer in the array.
   */
  public int last()
  {
    return _data[_size - 1];
  }
  /**
   * Sets the value at the given index.
   */
  public void set(int i, int value)
  {
    if (_size <= i)
      throw new IndexOutOfBoundsException(i + " >= " + _size);

    _data[i] = value;
  }
  /**
   * Returns true if the array contains and integer equal to test.
   */
  public boolean contains(int test)
  {
    int []data = _data;
    
    for (int i = _size - 1; i >= 0; i--) {
      if (data[i] == test)
        return true;
    }

    return false;
  }
  /**
   * True if all the integers in subset are contained in the array.
   */
  public boolean isSubset(IntArray subset)
  {
    int []subData = subset._data;
    
    for (int i = subset._size - 1; i >= 0; i--) {
      if (! contains(subData[i]))
        return false;
    }

    return true;
  }
  /**
   * Adds the members of newArray to the list if they are not already
   * members of the array.
   */
  public void union(IntArray newArray)
  {
    for (int i = 0; i < newArray._size; i++) {
      if (! contains(newArray._data[i]))
        add(newArray._data[i]);
    }
  }

  /**
   * Return a new int array with the contents.
   */
  public int []toArray()
  {
    int []value = new int[_size];
    
    System.arraycopy(_data, 0, value, 0, _size);
    
    return value;
  }

  public String toString()
  {
    StringBuilder cb = new StringBuilder();

    cb.append("[");
    for (int i = 0; i < _size; i++) {
      if (i != 0)
        cb.append(", ");
      cb.append(_data[i]);
    }
    cb.append("]");

    return cb.toString();
  }
}
