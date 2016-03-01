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

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * A Concurrent array similar to the JDK's CopyOnWriteArrayList but
 * saves a fixed array to avoid extra allocations.
 */
public class ConcurrentArrayList<E> extends AbstractList<E>
{
  private final Class<?> _type;
  private final ArrayList<E> _list = new ArrayList<E>();
  
  private E []_array;
  
  public ConcurrentArrayList(Class<E> type)
  {
    _type = type;

    updateArray();
  }
  
  @Override
  public int size()
  {
    return _array.length;
  }
  
  public E get(int index)
  {
    return _array[index];
  }
  
  @Override
  public boolean contains(Object testValue)
  {
    for (E element : _array) {
      if (element.equals(testValue))
        return true;
    }
    
    return false;
  }
  
  public <K> E find(K key, Match<E,K> match)
  {
    for (E element : _array) {
      if (match.isMatch(element, key))
        return element;
    }
    
    return null;
  }
  
  public <K> int indexOf(K key, Match<E,K> match)
  {
    E []array = _array;
    
    for (int i = 0; i < array.length; i++) {
      if (match.isMatch(array[i], key)) {
        return i;
      }
    }
    
    return -1;
  }
  
  @Override
  public boolean add(E value)
  {
    synchronized (_list) {
      _list.add(value);
      
      updateArray();
      
      return true;
    }
  }
  
  public E addIfAbsent(E value)
  {
    synchronized (_list) {
      int index = _list.indexOf(value);
      
      if (index >= 0)
        return _list.get(index);
      
      _list.add(value);

      updateArray();
      
      return null;
    }
  }
  
  public <K> E addIfAbsent(E value, Match<E,K> match, K key)
  {
    synchronized (_list) {
      E oldValue = find(key, match);
      
      if (oldValue != null)
        return oldValue;
      
      add(value);
      
      return null;
    }
  }
  
  public E set(int index, E value)
  {
    synchronized (_list) {
      while (_list.size() <= index) {
        _list.add(null);
      }
      
      E oldValue = _list.set(index, value);
      
      updateArray();
      
      return oldValue;
    }
  }
  
  @Override
  public boolean remove(Object value)
  {
    synchronized (_list) {
      boolean result  = _list.remove(value);

      updateArray();
      
      return result;
    }
  }
  
  public E remove(int index)
  {
    synchronized (_list) {
      Object value = _list.remove(index);
      
      updateArray();
      
      return (E) value;
    }
  }
  
  public <K> E remove(K key, Match<E,K> match)
  {
    synchronized (_list) {
      int index = indexOf(key, match);
      
      if (index < 0) {
        return null;
      }
        
      E value = _array[index];
      
      _list.remove(index);

      updateArray();
      
      return value;
    }
  }
  
  @Override
  public Iterator<E> iterator()
  {
    return new ArrayIterator<E>(_array);
  }

  @Override
  public E []toArray()
  {
    return _array;
  }
  
  @Override
  public void clear()
  {
    _array = (E[]) new Object[0];
  }
  
  @SuppressWarnings("unchecked")
  private void updateArray()
  {
    E []array = (E []) Array.newInstance(_type, _list.size());
    _list.toArray(array);
  
    _array = array;
  }
  
  public static Class<?> calculateType(Class<?> cl)
  {
    return calculateType(cl, cl);
  }
  
  public static Class<?> calculateType(Class<?> topClass, Class<?> cl)
  {
    if (cl == null)
      throw new UnsupportedOperationException(topClass.toString());
      
    Type type = cl.getGenericSuperclass();

    if (type instanceof ParameterizedType) {
      ParameterizedType pType = (ParameterizedType) type;

      return (Class) pType.getActualTypeArguments()[0];
    }
    else {
      return calculateType(topClass, cl.getSuperclass());
    }
  }

  public interface Match<E,K> {
    public boolean isMatch(E element, K key);
  }
  
  public static class ArrayIterator<E> implements Iterator<E> {
    private final E []_array;
    int _index;
    
    ArrayIterator(E []array)
    {
      _array = array;
    }

    @Override
    public boolean hasNext()
    {
      return _index < _array.length;
    }

    @Override
    public E next()
    {
      if (_index < _array.length)
        return _array[_index++];
      else
        return null;
    }

    @Override
    public void remove()
    {
    }
  }
}
