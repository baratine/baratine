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
import java.util.Collection;
import java.util.Objects;

import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.json.io.InJson.Event;
import com.caucho.v5.json.io.JsonReaderImpl;
import com.caucho.v5.json.io.JsonWriterImpl;
import com.caucho.v5.util.L10N;

public class IterableSerializer<V>
  extends JsonSerializerBase<Iterable<V>>
{
  private static final L10N L = new L10N(IterableSerializer.class);
  
  private SerializerJson<V> _ser;
  
  IterableSerializer(TypeRef typeRef,
                       JsonFactory factory)
  {
    Objects.requireNonNull(typeRef);
    
    _ser = factory.serializer(typeRef.to(Iterable.class).param(0));
  }
  
  @Override
  public IterableSerializer<V> withType(TypeRef type, JsonFactory factory)
  {
    if (getClass() != IterableSerializer.class) {
      throw new UnsupportedOperationException(getClass().getName());
    }
    
    return new IterableSerializer<>(type, factory);
  }

  @Override
  public void write(JsonWriterImpl out, Iterable<V> value)
  {
    out.writeStartArray();
    
    for (V child : value) {
      _ser.write(out, child);
    }
    
    out.writeEndArray();
  }

  @Override
  public Iterable<V> read(JsonReaderImpl in)
  {
    Event event = in.next();
    
    if (event == null || event == Event.VALUE_NULL) {
      return null;
    }
    
    if (event != Event.START_ARRAY) {
      throw new JsonException(L.l("expected array at {0}", event));
    }
    
    ArrayList<V> values = new ArrayList<>();
    
    while ((event = in.peek()) != Event.END_ARRAY && event != null) {
      values.add(_ser.read(in));
    }
    
    in.next();
    
    return values;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _ser + "]";
  }
}
