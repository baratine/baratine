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

import com.caucho.v5.json.io.JsonReaderImpl;
import com.caucho.v5.json.io.InJson.Event;
import com.caucho.v5.json.value.JsonArray;
import com.caucho.v5.json.value.JsonArrayImpl;
import com.caucho.v5.json.value.JsonDouble;
import com.caucho.v5.json.value.JsonLong;
import com.caucho.v5.json.value.JsonObject;
import com.caucho.v5.json.value.JsonObjectImpl;
import com.caucho.v5.json.value.JsonStringImpl;
import com.caucho.v5.json.value.JsonValue;
import com.caucho.v5.json.value.JsonValueBase;
import com.caucho.v5.util.L10N;

public class JsonValueDeserializer extends JsonSerializerBase
{
  private static final L10N L = new L10N(JsonValueDeserializer.class);
  
  static final SerializerJson DESER = new JsonValueDeserializer();
  
  private JsonValueDeserializer() {}

  @Override
  public JsonValue read(JsonReaderImpl in)
  {
    Event event = in.next();
    
    if (event == null) {
      return null;
    }
    
    switch (event) {
    case VALUE_NULL:
      return null; // JsonValueBase.NULL;
      
    case VALUE_TRUE:
      return JsonValueBase.TRUE;
      
    case VALUE_FALSE:
      return JsonValueBase.FALSE;
      
    case VALUE_STRING:
      return new JsonStringImpl(in.getString());
      
    case VALUE_LONG:
      if (in.isIntegralNumber()) {
        return new JsonLong(in.getLong());
      }
      else {
        return new JsonDouble(in.getDoubleValue());
      }
      
    case START_OBJECT:
      return parseMap(in);
      
    case START_ARRAY:
      return parseList(in);
      
    default:
      throw new JsonException(L.l("{0} is an unknown object", event));
    }
  }
  
  private JsonObject parseMap(JsonReaderImpl in)
  {
    Event event;
    
    Map<String,JsonValue> map = new HashMap<>();
    
    while ((event = in.next()) == Event.KEY_NAME) {
      String key = in.getString();
      
      JsonValue value = read(in);
      
      map.put(key, value);
    }
    
    if (event != Event.END_OBJECT) {
      throw new JsonException(L.l("Expected object end at {0}", event));
    }
    
    return new JsonObjectImpl(map);
  }
  
  private JsonArray parseList(JsonReaderImpl in)
  {
    Event event;
    
    List<JsonValue> list = new ArrayList<>();
    
    while ((event = in.peek()) != null && event != Event.END_ARRAY) {
      JsonValue value = read(in);
      
      list.add(value);
    }
    
    event = in.next();
    
    if (event != Event.END_ARRAY) {
      throw new JsonException(L.l("Expected array end at {0}", event));
    }
    
    return new JsonArrayImpl(list);
  }
}
