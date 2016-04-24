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

package io.baratine.pipe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.baratine.pipe.Message.MessageBuilder;

/**
 * General message type for pipes.
 */
final class MessageImpl<T> implements MessageBuilder<T>
{
  private final T _value;
  private Map<String,Object> _headers;
  
  MessageImpl(T value)
  {
    _value = value;
  }
  
  @Override
  public T value()
  {
    return _value;
  }

  @Override
  public Map<String,Object> headers()
  {
    Map<String, Object> headers = _headers;
    
    if (headers != null) {
      return headers;
    }
    else {
      return Collections.emptyMap();
    }
  }

  @Override
  public Object header(String key)
  {
    Map<String, Object> headers = _headers;
    
    if (headers != null) {
      return headers.get(key);
    }
    else {
      return null;
    }
  }
  
  @Override
  public MessageImpl<T> header(String key, Object value)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);
    
    Map<String,Object> headers = _headers;
    
    if (headers == null) {
      _headers = headers = new LinkedHashMap<>();
    }
    
    headers.put(key, value);
    
    return this;
    
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + value() + "]";
  }
}
