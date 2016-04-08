/*
 * Copyright (c) 2001-2016 Caucho Technology, Inc.  All rights reserved.
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.h3.context;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.h3.SerializerH3;
import com.caucho.v5.h3.io.ClassInfoH3;
import com.caucho.v5.h3.io.ConstH3;
import com.caucho.v5.h3.io.H3ExceptionIn;
import com.caucho.v5.h3.ser.SerializerFactoryH3;
import com.caucho.v5.h3.ser.SerializerFactoryH3Impl;
import com.caucho.v5.h3.ser.SerializerH3Amp;
import com.caucho.v5.inject.type.TypeRef;

/**
 * Type context for H3
 */
public class ContextH3Impl implements ContextH3
{
  private ConcurrentHashMap<Type,SerializerH3Amp<?>> _serializerMap
    = new ConcurrentHashMap<>();
  
  private ConcurrentHashMap<String,SerializerH3Amp<?>> _deserMap
    = new ConcurrentHashMap<>();
  
  private ArrayList<SerializerH3Amp<?>> _serArray = new ArrayList<>();
  
  private SerializerFactoryH3 _factory;
  
  private int _typeSequence = ConstH3.PREDEF_TYPE;

  public ContextH3Impl()
  {
    //Objects.requireNonNull(factory);
    
    _factory = new SerializerFactoryH3Impl();
    
    _factory.initSerializers(_serArray);
  }

  @Override
  public void initSerializers(ArrayList<SerializerH3Amp<?>> serArray)
  {
    serArray.clear();
    serArray.addAll(_serArray);
  }
  
  @Override
  public int typeSequence()
  {
    return _typeSequence;
  }
  
  @Override
  public int nextTypeSequence()
  {
    return ++_typeSequence;
  }

  public void schema(Class<?> type)
  {
    SerializerH3Amp<?> ser = serializer(type);
    
    ser = ser.schema(this);
    
    if (ser != null) {
      _serializerMap.put(type, ser);
    }
  }
  
  @Override
  public <T> void register(Class<T> type, SerializerH3Amp<T> ser)
  {
    _serializerMap.putIfAbsent(type, ser);
  }
  
  @Override
  @SuppressWarnings("unchecked")
  public <T> SerializerH3Amp<T> serializer(Class<T> type)
  {
    SerializerH3Amp<T> ser = (SerializerH3Amp<T>) _serializerMap.get(type);
    
    if (ser == null) {
      ser = _factory.serializer(type, this);
      
      register(type, ser);
    }
    
    ser.introspect(this);

    return ser;
  }
  
  @Override
  public <T> SerializerH3Amp<T> serializer(SerializerH3Amp<T> ser)
  {
    SerializerH3Amp<T> oldSer
      = (SerializerH3Amp<T>) _serializerMap.putIfAbsent(ser.type(), ser);
    
    if (oldSer != null) {
      return oldSer;
    }
    else {
      return ser;
    }
  }
  

  @Override
  public <T> SerializerH3<T> serializer(Type type)
  {
    if (type instanceof Class<?>) {
      return (SerializerH3<T>) serializer((Class<?>) type);
    }
    else {
      TypeRef typeRef = TypeRef.of(type);
      
      return (SerializerH3<T>) serializer(typeRef.rawClass());
    }
  }

  @Override
  public SerializerH3Amp<?> define(ClassInfoH3 info)
  {
    String className = info.name();
    
    SerializerH3Amp<?> ser = _deserMap.get(className);
    
    if (ser == null) {
      Class<?> type = deserializerClass(className);

      ser = serializer(type);
      
      _deserMap.putIfAbsent(className, ser);
      
      ser = _deserMap.get(className);
    }
    
    // ser = ser.define(info);
    
    return ser;
  }
  
  private Class<?> deserializerClass(String className)
  {
    validateDeserializer(className);
    
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      
      return Class.forName(className, false, loader);
    } catch (ClassNotFoundException e) {
      throw new H3ExceptionIn(e);
    }
  }
  
  private void validateDeserializer(String className)
  {
  }
}
