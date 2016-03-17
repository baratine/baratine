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

import java.util.Map;
import java.util.function.Supplier;

import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.json.io.InJson.Event;
import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.json.io.JsonWriter;
import com.caucho.v5.util.L10N;

public class MapSerializerJson<K,V> 
  extends JsonObjectSerializerBase<Map<K, V>>
{
  private static final L10N L = new L10N(MapSerializerJson.class);
  
  private final SerializerJson<K> _keyDeser;
  private final SerializerJson<V> _valueDeser;
  private final Supplier<? extends Map<K,V>> _factory;
  
  MapSerializerJson(TypeRef typeRef,
                    JsonFactory factory,
                    Supplier<? extends Map<K,V>> supplier)
  {
    TypeRef mapRef = typeRef.to(Map.class);
    
    TypeRef keyRef = mapRef.param(0);
    TypeRef valueRef = mapRef.param(1);
    
    _keyDeser = factory.serializer(keyRef.type());
    _valueDeser = factory.serializer(valueRef.type());
    
    _factory = supplier;
  }

  @Override
  public void write(JsonWriter out, 
                    Map<K, V> value)
  {
    out.writeStartObject();
    
    for (Map.Entry<K,V> entry : value.entrySet()) {
      out.writeKey(String.valueOf(entry.getKey()));
      out.write(entry.getValue());
    }
    
    out.writeEndArray();
  }

  @Override
  public Map<K,V> read(JsonReader in)
  {
    Event event = in.next();
    
    if (event == null || event == Event.VALUE_NULL) {
      return null;
    }
    
    if (event != Event.START_OBJECT) {
      throw new JsonException(L.l("expected object at {0}", event));
    }
    
    Map<K,V> map = (Map) _factory.get();
    
    while ((event = in.peek()) == Event.KEY_NAME) {
      in.next();
      
      String key = in.getString();

      V value = _valueDeser.read(in);
      
      map.put((K) key, value);
    }
    
    in.next();
    
    return map;
  }
}
