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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.json.io.InJson.Event;
import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.reflect.TypeFactoryReflect;
import com.caucho.v5.reflect.TypeImpl;

public class JavaDeserializer extends JsonDeserializerBase
{
  private static final Logger log
    = Logger.getLogger(JavaDeserializer.class.getName());

  private TypeRef _type;
  private Constructor<?> _ctor;
  private HashMap<String,JsonField> _fieldMap
    = new HashMap<>();

  JavaDeserializer(TypeRef type)
  {
    _type = type;
  }

  void introspect(JsonSerializerFactory factory)
  {
    try {
      _ctor = introspectConstructor(_type);
      _ctor.setAccessible(true);

      introspectFields(_type, factory);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new JsonException(e);
    }
  }

  private Constructor<?> introspectConstructor(TypeRef type)
  {
    for (Constructor<?> ctor : type.rawClass().getDeclaredConstructors()) {
      if (ctor.getParameterTypes().length == 0)
        return ctor;
    }
    
    if (type.rawClass().isInterface()) {
      throw new JsonException(type + " cannot be deserialized because it is an interface."
                              + " JSON deserialization requires concrete types.");
    }
    else if (Modifier.isAbstract(type.rawClass().getModifiers())) {
      throw new JsonException(type + " cannot be deserialized because it is abstract."
                              + " JSON deserialization requires concrete types.");
    }

    throw new IllegalStateException(type + " cannot be deserialized because it does not have a zero-arg constructor."
                                    + " JSON deserialization requires zero-arg constructors.");
  }

  private void introspectFields(TypeRef type,
                                JsonSerializerFactory factory)
  {
    if (type == null) {
      return;
    }

    introspectFields(type.superClass(), factory);

    for (Field field : type.rawClass().getDeclaredFields()) {
      if (Modifier.isTransient(field.getModifiers())) {
        continue;
      }

      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }

      field.setAccessible(true);

      MethodHandle mh;

      try {
        mh = MethodHandles.lookup().unreflectSetter(field);
      } catch (Exception e) {
        e.printStackTrace();
        continue;
      }

      TypeFactoryReflect typeFactory = factory.getTypeFactory();

      TypeRef genType = TypeRef.of(field.getGenericType());

      Class<?> fieldType = genType.rawClass();

      if (fieldType == boolean.class) {
        _fieldMap.put(field.getName(), new JsonBooleanField(field));
      }
      else if (fieldType == char.class) {
        _fieldMap.put(field.getName(), new JsonCharField(field));
      }
      else if (fieldType == byte.class) {
        _fieldMap.put(field.getName(), new JsonByteField(field));
      }
      else if (fieldType == short.class) {
        _fieldMap.put(field.getName(), new JsonShortField(field));
      }
      else if (fieldType == int.class) {
        _fieldMap.put(field.getName(), new JsonIntField(field));
      }
      else if (fieldType == long.class) {
        _fieldMap.put(field.getName(), new JsonLongField(field));
      }
      else if (fieldType == double.class) {
        _fieldMap.put(field.getName(), new JsonDoubleField(field));
      }
      else if (fieldType == float.class) {
        _fieldMap.put(field.getName(), new JsonFloatField(field));
      }
      else if (fieldType == String.class) {
        _fieldMap.put(field.getName(), new JsonStringField(field));
      }
      else {
        JsonDeserializer deser = factory.deserializer(genType.type());

        _fieldMap.put(field.getName(),
                      new JsonObjectField(field.getDeclaringClass().getName(),
                                          field.getName(),
                                          mh,
                                          deser));
      }
    }
  }

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

    case START_OBJECT:
    {
      Object bean = create();

      in.parseBeanMap(bean, this);

      return bean;
    }

    default:
      throw error("unexpected token: {0} while parsing {1}", 
                  event, _type.rawClass()); 
    }
  }

  public Object create()
  {
    try {
      return _ctor.newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void readField(JsonReader in, Object bean, String fieldName)
  {
    JsonField jsonField = _fieldMap.get(fieldName);

    if (jsonField != null) {
      jsonField.read(in, bean);
    }
    else {
      // skip
      try {
        in.readObject();
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }
  }

  public Object complete(Object bean)
  {
    return bean;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _type + "]";
  }

  abstract static class JsonField
  {
    void read(JsonReader in, Object bean)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }
  }

  static class JsonBooleanField extends JsonField {
    private final Field _field;

    JsonBooleanField(Field field)
    {
      _field = field;
    }

    @Override
    void read(JsonReader in, Object bean)
    {
      try {
        _field.setBoolean(bean, in.readBoolean());
      } catch (Exception e) {
        throw new JsonException(_field.getName() + ": " + e, e);
      }
    }
  }

  static class JsonCharField extends JsonField {
    private final Field _field;

    JsonCharField(Field field)
    {
      _field = field;
    }

    @Override
    void read(JsonReader in, Object bean)
    {
      try {
        String v = in.readString();

        char ch;

        if (v == null || v.equals("")) {
          ch = 0;
        }
        else {
          ch = v.charAt(0);
        }

        _field.setChar(bean, ch);
      } catch (Exception e) {
        throw new JsonException(_field.getName() + ": " + e, e);
      }
    }
  }

  static class JsonByteField extends JsonField {
    private final Field _field;

    JsonByteField(Field field)
    {
      _field = field;
    }

    @Override
    void read(JsonReader in, Object bean)
    {
      try {
        _field.setByte(bean, (byte) in.readLong());
      } catch (Exception e) {
        throw new JsonException(_field.getName() + ": " + e, e);
      }
    }
  }

  static class JsonShortField extends JsonField {
    private final Field _field;

    JsonShortField(Field field)
    {
      _field = field;
    }

    @Override
    void read(JsonReader in, Object bean)
    {
      try {
        _field.setShort(bean, (short) in.readLong());
      } catch (Exception e) {
        throw new JsonException(_field.getName() + ": " + e, e);
      }
    }
  }

  static class JsonIntField extends JsonField {
    private final Field _field;

    JsonIntField(Field field)
    {
      _field = field;
    }

    @Override
    void read(JsonReader in, Object bean)
    {
      try {
        _field.setInt(bean, (int) in.readLong());
      } catch (Exception e) {
        throw new JsonException(_field.getName() + ": " + e, e);
      }
    }
  }

  static class JsonLongField extends JsonField {
    private final Field _field;

    JsonLongField(Field field)
    {
      _field = field;
    }

    @Override
    void read(JsonReader in, Object bean)
    {
      try {
        _field.setLong(bean, in.readLong());
      } catch (Exception e) {
        throw new JsonException(_field.getName() + ": " + e, e);
      }
    }
  }

  static class JsonFloatField extends JsonField {
    private final Field _field;

    JsonFloatField(Field field)
    {
      _field = field;
    }

    @Override
    void read(JsonReader in, Object bean)
    {
      float value = (float) in.readDouble();

      try {
        _field.setFloat(bean, value);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  static class JsonDoubleField extends JsonField {
    private final Field _field;

    JsonDoubleField(Field field)
    {
      _field = field;
    }

    @Override
    void read(JsonReader in, Object bean)
    {
      double value = in.readDouble();

      try {
        _field.setDouble(bean, value);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  static class JsonStringField extends JsonField {
    private final Field _field;

    JsonStringField(Field field)
    {
      _field = field;
    }

    @Override
    void read(JsonReader in, Object bean)
    {
      String value = in.readString();

      try {
        _field.set(bean, value);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  static class JsonObjectField extends JsonField {
    private final String _className;
    private final String _fieldName;
    private final MethodHandle _field;
    private final JsonDeserializer _deser;

    JsonObjectField(String className,
                    String fieldName,
                    MethodHandle field,
                    JsonDeserializer deser)
    {
      _className = className;
      _fieldName = fieldName;
      field = field.asType(MethodType.methodType(void.class,
                                                 Object.class,
                                                 Object.class));

      _field = field;
      _deser = deser;
    }

    @Override
    void read(JsonReader in, Object bean)
    {
      Object value = _deser.read(in);

      /*
      if (value == null) {
        return;
      }
      */

      try {
        _field.invokeExact(bean, value);
      } catch (Throwable e) {
        throw new JsonException(value.getClass() + " is an illegal value for "
                                + _className + "." + _fieldName,
                                e);
      }
    }
  }
}
