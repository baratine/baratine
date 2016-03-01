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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Logger;

import com.caucho.v5.json.JsonName;
import com.caucho.v5.json.JsonTransient;
import com.caucho.v5.json.io.JsonWriter;

public class JavaSerializer extends JsonObjectSerializerBase<Object>
{
  private static final Logger log
    = Logger.getLogger(JavaSerializer.class.getName());

  private Class<?> _type;
  private JsonFieldBase []_fields;

  JavaSerializer(Class<?> type,
                 JsonSerializerFactory factory)
  {
    _type = type;

    introspect(factory);
  }

  void introspect(JsonSerializerFactory factory)
  {
    ArrayList<JsonFieldBase> fields = new ArrayList<>();

    introspectFields(fields, _type, factory);

    Collections.sort(fields, (x,y)->x.name().compareTo(y.name()));

    _fields = new JsonFieldBase[fields.size()];
    fields.toArray(_fields);
  }

  private void introspectFields(ArrayList<JsonFieldBase> fields,
                                Class<?> type,
                                JsonSerializerFactory factory)
  {
    if (type == null) {
      return;
    }

    introspectFields(fields, type.getSuperclass(), factory);

    for (Field field : type.getDeclaredFields()) {
      if (Modifier.isTransient(field.getModifiers())) {
        continue;
      }
      
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      
      if (field.getAnnotation(JsonTransient.class) != null) {
        continue;
      }

      field.setAccessible(true);

      JsonName json = field.getAnnotation(JsonName.class);
      
      JsonFieldBase jsonField;
      
      if (String.class.equals(field.getType())) {
        jsonField = new JsonFieldString(field, json);
      }
      else if (Modifier.isFinal(field.getType().getModifiers())) {
        JsonSerializer<?> ser = factory.serializer(field.getType());
        
        jsonField = new JsonFieldFinal(field, json, ser);
      }
      else {
        jsonField = new JsonField(field, json);
      }
      
      fields.add(jsonField);
    }
  }

  @Override
  public void write(JsonWriter out, Object value)
  {
    out.writeStartObject();
    
    writeFields(out, value);
    
    out.writeEndObject();
  }

  /*
  @Override
  public void write(JsonWriter out, 
                    String name,
                    Object value)
  {
    out.writeStartObject(name);
    
    writeFields(out, value);
    
    out.writeEnd();
  }
  */
  
  private void writeFields(JsonWriter out, Object value)
  {
    for (JsonFieldBase field : _fields) {
      field.write(out, value);
    }
  }
  
  abstract static class JsonFieldBase
  {
    private final Field _field;
    private final String _name;
    private final char []_key;
    
    JsonFieldBase(Field field, JsonName json)
    {
      _field = field;
      
      if (json != null) {
        _name = json.value();
      }
      else {
        _name = _field.getName();
      }
      
      _key = ("\"" + _name + "\":").toCharArray();
    }
    
    abstract Object get(Object value);
    
    void write(JsonWriter out, Object bean)
    {
      Object fieldValue = get(bean);

      if (fieldValue == null) {
        return;
      }

      out.writeKey(key());
      out.writeObjectValue(fieldValue);
    }

    public final Field getField()
    {
      return _field;
    }

    public final String name()
    {
      return _name;
    }

    public final char []key()
    {
      return _key;
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _name + "]";
    }
  }

  static class JsonField extends JsonFieldBase
  {
    private final MethodHandle _fieldHandle;

    JsonField(Field field, JsonName json)
    {
      super(field, json);
      
      try {
        field.setAccessible(true);
        MethodHandle mh = MethodHandles.lookup().unreflectGetter(field); 
        _fieldHandle = mh.asType(MethodType.genericMethodType(1));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public final Object get(Object value)
    {
      try {
        return _fieldHandle.invokeExact(value);
      } catch (Throwable e) {
        log.warning("Cannot get field " + this + " with value " + value);
        
        return null;
      }
    }
  }

  static class JsonFieldFinal<T> extends JsonField
  {
    private final JsonSerializer<T> _ser;
    
    JsonFieldFinal(Field field, JsonName json, JsonSerializer<T> ser)
    {
      super(field, json);
      
      _ser = ser;
    }

    @Override
    void write(JsonWriter out, Object bean)
    {
      Object fieldValue = get(bean);

      if (fieldValue == null) {
        return;
      }
      
      out.writeKey(key());

      _ser.write(out, (T) fieldValue);
      //out.writeObject(name(), fieldValue);
    }
  }

  static final class JsonFieldString extends JsonFieldBase
  {
    private final MethodHandle _fieldHandle;

    JsonFieldString(Field field, JsonName json)
    {
      super(field, json);
      
      try {
        field.setAccessible(true);
        MethodHandle mh = MethodHandles.lookup().unreflectGetter(field); 
        _fieldHandle = mh.asType(MethodType.methodType(String.class, Object.class));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public final String get(Object value)
    {
      try {
        return (String) _fieldHandle.invokeExact(value);
      } catch (Throwable e) {
        log.warning("Cannot get field " + this + " with value " + value);
        
        return null;
      }
    }
    
    @Override
    final void write(JsonWriter out, Object bean)
    {
      String fieldValue = get(bean);

      if (fieldValue != null) {
        out.writeKey(key());
        out.write(fieldValue);
      }
    }
  }
}
