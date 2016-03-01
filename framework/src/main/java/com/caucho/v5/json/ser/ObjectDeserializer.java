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

package com.caucho.v5.json.ser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.json.io.InJson.Event;
import com.caucho.v5.util.L10N;

public class ObjectDeserializer extends JsonDeserializerBase
{
  private static final L10N L = new L10N(ObjectDeserializer.class);
  
  static final JsonDeserializer DESER = new ObjectDeserializer();
  
  private ObjectDeserializer() {}

  @Override
  public Object read(JsonReader in)
  {
    Event event = in.next();
    
    if (event == null) {
      return null;
    }
    
    switch (event) {
    case VALUE_NULL:
      return null;
      
    case VALUE_TRUE:
      return Boolean.TRUE;
      
    case VALUE_FALSE:
      return Boolean.FALSE;
      
    case VALUE_STRING:
      return in.getString();
      
    case VALUE_LONG:
      if (in.isIntegralNumber()) {
        return in.getLong();
      }
      else {
        return in.getDoubleValue();
      }
      
    case START_OBJECT:
      return parseMap(in);
      
    case START_ARRAY:
      return parseList(in);
      
    default:
      throw new JsonException(L.l("{0} is an unknown object", event));
    }
  }
  
  private Map<?,?> parseMap(JsonReader in)
  {
    Event event;
    
    Map<Object,Object> map = new HashMap<>();
    
    while ((event = in.next()) == Event.KEY_NAME) {
      String key = in.getString();
      
      Object value = read(in);
      
      map.put(key, value);
    }
    
    if (event != Event.END_OBJECT) {
      throw new JsonException(L.l("Expected object end at {0}", event));
    }
    
    return map;
  }
  
  private List<?> parseList(JsonReader in)
  {
    Event event;
    
    List<Object> list = new ArrayList<>();
    
    while ((event = in.peek()) != null && event != Event.END_ARRAY) {
      Object value = read(in);
      
      list.add(value);
    }
    
    event = in.next();
    
    if (event != Event.END_ARRAY) {
      throw new JsonException(L.l("Expected array end at {0}", event));
    }
    
    return list;
  }
}
