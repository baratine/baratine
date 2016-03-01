/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

package com.caucho.v5.amp.message;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Null header implementation.
 */
final class HeadersCons extends HeadersRampBase
  implements Map.Entry<String, Object>
{
  private final String _key;
  private final Object _value;
  private final HeadersCons _next;
  private final int _size;
  
  HeadersCons(String key, Object value, HeadersCons next)
  {
    _key = key;
    _value = value;
    _next = next;
    
    if (next != null) {
      _size = _next.getSize() + 1;
    }
    else {
      _size = 1;
    }
  }
  
  @Override
  public final HeadersCons add(String key, Object value)
  {
    return new HeadersCons(key, value, this);
  }
  
  @Override
  public final String getKey()
  {
    return _key;
  }
  
  @Override
  public final Object getValue()
  {
    return _value;
  }

  @Override
  public Object setValue(Object value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public final int getSize()
  {
    return _size;
  }
  
  @Override
  public final Object get(String key)
  {
    for (HeadersCons ptr = this; ptr != null; ptr = ptr._next) {
      if (key.equals(ptr.getKey())) {
        return ptr.getValue();
      }
    }
    
    return null;
  }

  @Override
  public Iterator<Entry<String, Object>> iterator()
  {
    return new ConsIterator(this);
  }
  
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append("{");
    
    sb.append(getKey()).append("=").append(getValue());
    
    for (HeadersCons ptr = _next; ptr != null; ptr = ptr._next) {
      sb.append(", ");
      
      sb.append(ptr.getKey()).append("=").append(ptr.getValue());
    }
    
    sb.append("}");
    
    return sb.toString();
  }
  
  private static class ConsIterator 
    implements Iterator<Map.Entry<String, Object>>
  {
    private HeadersCons _ptr;
    
    ConsIterator(HeadersCons ptr)
    {
      _ptr = ptr;
    }
    
    @Override
    public boolean hasNext()
    {
      return _ptr != null;
    }
    
    @Override
    public Map.Entry<String, Object> next()
    {
      HeadersCons entry = _ptr;
      
      if (entry != null) {
        _ptr = entry._next;
      }
      
      return entry;
    }

    @Override
    public void remove()
    {
      throw new UnsupportedOperationException(getClass().getName());
    }
  }
}
