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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.convert.bean.FieldBase;
import com.caucho.v5.convert.bean.FieldBoolean;
import com.caucho.v5.convert.bean.FieldByte;
import com.caucho.v5.convert.bean.FieldChar;
import com.caucho.v5.convert.bean.FieldDouble;
import com.caucho.v5.convert.bean.FieldFloat;
import com.caucho.v5.convert.bean.FieldInt;
import com.caucho.v5.convert.bean.FieldLong;
import com.caucho.v5.convert.bean.FieldObject;
import com.caucho.v5.convert.bean.FieldShort;
import com.caucho.v5.convert.bean.FieldString;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.json.JsonName;
import com.caucho.v5.json.JsonTransient;
import com.caucho.v5.json.io.InJson.Event;
import com.caucho.v5.json.io.JsonReaderImpl;
import com.caucho.v5.json.io.JsonWriterImpl;

public class JavaSerializerJson<T> extends JsonObjectSerializerBase<Object>
{
  private static final Logger log
    = Logger.getLogger(JavaSerializerJson.class.getName());
  
  private static final HashMap<Class<?>,BiFunction<Field,String,JsonField<?,?>>> 
  _fieldFunMap;

  private TypeRef _type;
  
  private JsonField<T,?> []_fields;
  private HashMap<String,JsonField<T,?>> _fieldMap
    = new HashMap<>();
  
  private Constructor<?> _ctor;

  JavaSerializerJson(TypeRef type,
                 JsonFactory factory)
  {
    _type = type;


    introspect(factory);
  }
  
  @Override
  public JavaSerializerJson<T> withType(TypeRef typeRef, JsonFactory factory)
  {
    if (typeRef.equals(_type)) {
      return this;
    }
    
    return new JavaSerializerJson<>(typeRef, factory);
  }

  void introspect(JsonFactory factory)
  {
    try {
      _ctor = introspectConstructor(_type);
      if (_ctor != null) {
        _ctor.setAccessible(true);
      }

      //introspectFields(_type, factory);
      
      ArrayList<JsonField<T,?>> fields = new ArrayList<>();

      introspectFields(fields, _type, factory);

      Collections.sort(fields, (x,y)->x.name().compareTo(y.name()));

      _fields = new JsonField[fields.size()];
      fields.toArray(_fields);
      
      for (JsonField<T,?> field : _fields) {
        _fieldMap.put(field.name(), field);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new JsonException(e);
    }
  }

  private void introspectFields(ArrayList<JsonField<T,?>> fields,
                                TypeRef typeRef,
                                JsonFactory factory)
  {
    if (typeRef == null) {
      return;
    }

    introspectFields(fields, typeRef.superClass(), factory);
    
    for (Field field : typeRef.rawClass().getDeclaredFields()) {
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

      String name = field.getName();
      
      JsonName json = field.getAnnotation(JsonName.class);
      
      if (json != null) {
        name = json.value();
      }
      
      BiFunction<Field,String,JsonField<?,?>> fieldFun;
      fieldFun = _fieldFunMap.get(field.getType());
      
      JsonField<T,?> jsonField;
      
      if (fieldFun != null) {
        jsonField = (JsonField<T,?>) fieldFun.apply(field, name);
      }
      /*
      else if (Modifier.isFinal(field.getType().getModifiers())) {
        JsonSerializer<?> ser = factory.serializer(field.getType());
        
        jsonField = new JsonFieldFinal(field, json, ser);
      }
      */
      else {
        //TypeRef type = _type.child(field.getGenericType());
        TypeRef type = typeRef.child(field.getGenericType());
        
        SerializerJson<?> ser = factory.serializer(type);
        
        jsonField = new JsonFieldObject<>(field, name, ser);
      }
      
      fields.add(jsonField);
    }
  }

  @Override
  public void write(JsonWriterImpl out, Object value)
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
  
  private void writeFields(JsonWriterImpl out, Object value)
  {
    for (JsonField field : _fields) {
      field.write(out, value);
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

    /*
    throw new IllegalStateException(type + " cannot be deserialized because it does not have a zero-arg constructor."
                                    + " JSON deserialization requires zero-arg constructors.");
                                    */
    
    return null;
  }

  @Override
  public Object read(JsonReaderImpl in)
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
  public void readField(JsonReaderImpl in, Object bean, String fieldName)
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
  
  abstract static class JsonField<T,V>
  {
    private final Field _field;
    private final String _name;
    private final char []_key;
    
    JsonField(Field field, String name)
    {
      _field = field;
      
      Objects.requireNonNull(name);
      _name = name;
      
      _key = ("\"" + _name + "\":").toCharArray();
    }

    public final Field field()
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
    
    //abstract V get(T bean);
    
    abstract void write(JsonWriterImpl out, T bean);
    
    void read(JsonReaderImpl in, T bean)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _name + "]";
    }
  }

  /**
   * String field
   */
  static final class JsonFieldString<T> extends JsonField<T,String>
  {
    private final FieldString<T> _fieldRef;

    JsonFieldString(Field field, String name)
    {
      super(field, name);
      
      _fieldRef = new FieldString<>(field);
    }

    /*
    @Override
    public final String get(T bean)
    {
      return _fieldRef.getString(bean);
    }
    */
    
    @Override
    final void write(JsonWriterImpl out, T bean)
    {
      String fieldValue = _fieldRef.getString(bean);

      if (fieldValue != null) {
        out.writeKey(key());
        out.write(fieldValue);
      }
    }

    @Override
    void read(JsonReaderImpl in, T bean)
    {
      String value = in.readString();

      _fieldRef.setString(bean, value);
    }
  }
  
  /**
   * boolean field
   */
  static class JsonFieldBoolean<T> extends JsonField<T,Boolean>
  {
    private final FieldBoolean<T> _fieldRef;

    JsonFieldBoolean(Field field, String name)
    {
      super(field, name);
      
      _fieldRef = new FieldBoolean<>(field);
    }

    /*
    @Override
    public final Boolean get(T bean)
    {
      return _fieldRef.getBoolean(bean);
    }
    */
    
    @Override
    final void write(JsonWriterImpl out, T bean)
    {
      boolean fieldValue = _fieldRef.getBoolean(bean);

      out.writeKey(key());
      out.write(fieldValue);
    }

    @Override
    void read(JsonReaderImpl in, T bean)
    {
      _fieldRef.setBoolean(bean, in.readBoolean());
    }
  }

  static class JsonFieldChar<T> extends JsonField<T,Character>
  {
    private final FieldChar<T> _fieldRef;

    JsonFieldChar(Field field, String name)
    {
      super(field, name);
      
      _fieldRef = new FieldChar<>(field);
    }

    /*
    @Override
    public final Character get(T bean)
    {
      return (char) _fieldRef.getInt(bean);
    }
    */
    
    @Override
    final void write(JsonWriterImpl out, T bean)
    {
      String fieldValue = _fieldRef.getString(bean);

      out.writeKey(key());
      out.write(fieldValue);
    }

    @Override
    void read(JsonReaderImpl in, T bean)
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

        _fieldRef.setChar(bean, ch);
      } catch (Exception e) {
        throw new JsonException(field().getName() + ": " + e, e);
      }
    }
  }

  static class JsonFieldLong<T> extends JsonField<T,Long>
  {
    private final FieldBase<T> _fieldRef;

    JsonFieldLong(Field field, String name)
    {
      super(field, name);
      
      Class<?> fieldType = field.getType();
      
      if (byte.class.equals(fieldType)) {
        _fieldRef = new FieldByte<>(field);
      }
      else if (short.class.equals(fieldType)) {
        _fieldRef = new FieldShort<>(field);
      }
      else if (int.class.equals(fieldType)) {
        _fieldRef = new FieldInt<>(field);
      }
      else if (int.class.equals(fieldType)) {
        _fieldRef = new FieldLong<>(field);
      }
      else {
        throw new UnsupportedOperationException(fieldType.getName());
      }
    }

    /*
    @Override
    public final Long get(T bean)
    {
      return _fieldRef.getLong(bean);
    }
    */
    
    @Override
    final void write(JsonWriterImpl out, T bean)
    {
      long fieldValue = _fieldRef.getLong(bean);

      out.writeKey(key());
      out.write(fieldValue);
    }

    @Override
    void read(JsonReaderImpl in, T bean)
    {
      _fieldRef.setLong(bean, in.readLong());
    }
  }

  static class JsonFieldDouble<T> extends JsonField<T,Double>
  {
    private final FieldBase<T> _fieldRef;

    JsonFieldDouble(Field field, String name)
    {
      super(field, name);
      
      Class<?> fieldType = field.getType();
      
      if (float.class.equals(fieldType)) {
        _fieldRef = new FieldFloat<>(field);
      }
      else if (double.class.equals(fieldType)) {
        _fieldRef = new FieldDouble<>(field);
      }
      else {
        throw new UnsupportedOperationException(fieldType.getName());
      }
    }

    /*
    @Override
    public final Double get(T bean)
    {
      return _fieldRef.getDouble(bean);
    }
    */
    
    @Override
    final void write(JsonWriterImpl out, T bean)
    {
      double fieldValue = _fieldRef.getDouble(bean);

      out.writeKey(key());
      out.write(fieldValue);
    }

    @Override
    void read(JsonReaderImpl in, T bean)
    {
      _fieldRef.setDouble(bean, in.readDouble());
    }
  }

  static class JsonFieldObject<T,V> extends JsonField<T,V>
  {
    private final FieldObject<T,V> _fieldRef;
    private final SerializerJson<V> _ser;

    JsonFieldObject(Field field,
                    String name,
                    SerializerJson<V> ser)
    {
      super(field, name);
      
      _fieldRef = new FieldObject<>(field);
      
      Objects.requireNonNull(ser);
      
      _ser = ser;
    }

    @Override
    void read(JsonReaderImpl in, T bean)
    {
      V value = _ser.read(in);

      _fieldRef.setObject(bean, value);
    }

    @Override
    void write(JsonWriterImpl out, T bean)
    {
      V fieldValue = _fieldRef.getObject(bean);

      if (fieldValue == null) {
        return;
      }
      
      out.writeKey(key());

      // XXX: type check
      if (_ser.rawClass() == fieldValue.getClass()) {
        _ser.write(out, fieldValue);
      }
      else {
        out.write(fieldValue);
      }
    }
  }
  
  static {
    _fieldFunMap = new HashMap<>();
    
    _fieldFunMap.put(boolean.class, JsonFieldBoolean::new);
    _fieldFunMap.put(char.class, JsonFieldChar::new);
    _fieldFunMap.put(byte.class, JsonFieldLong::new);
    _fieldFunMap.put(short.class, JsonFieldLong::new);
    _fieldFunMap.put(int.class, JsonFieldLong::new);
    _fieldFunMap.put(float.class, JsonFieldDouble::new);
    _fieldFunMap.put(double.class, JsonFieldDouble::new);
    _fieldFunMap.put(String.class, JsonFieldString::new);
  }
}
