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

package com.caucho.v5.h3.ser;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.h3.OutH3;
import com.caucho.v5.h3.context.ContextH3;
import com.caucho.v5.h3.io.ClassInfoH3;
import com.caucho.v5.h3.io.FieldInfoH3;
import com.caucho.v5.h3.io.H3ExceptionIn;
import com.caucho.v5.h3.io.InH3Amp;
import com.caucho.v5.h3.io.InRawH3;
import com.caucho.v5.h3.io.OutRawH3;
import com.caucho.v5.h3.query.PathH3Amp;
import com.caucho.v5.util.L10N;

/**
 * H3 typed serializer.
 */
public class SerializerH3Java<T> extends SerializerH3Base<T>
{
  private static final L10N L = new L10N(SerializerH3Java.class);
  
  private static final HashMap<Class<?>,FieldProvider> _fieldMap
    = new HashMap<>();
  
  private Class<T> _type;
  private AtomicReference<ClassInfoH3> _infoRef = new AtomicReference<>();
  
  private MethodHandle _ctor;
  private FieldSerBase[] _fields;
  
  SerializerH3Java(Class<T> type, ContextH3 context)
  {
    _type = type;
    
    context.register(type, this);
  }
  
  @Override
  public Type type()
  {
    return _type;
  }
  
  @Override
  public int typeSequence()
  {
    return _infoRef.get().sequence();
  }

  @Override
  public SerializerH3Amp<T> schema(ContextH3 context)
  {
    ClassInfoH3 info = _infoRef.get();
    
    if (info.sequence() == 0) {
      ClassInfoH3 infoSeq = info.sequence(context.nextTypeSequence());

      _infoRef.compareAndSet(info, infoSeq);
    }
    
    return null;
  }
  
  @Override
  public void writeDefinition(OutRawH3 os, int defIndex)
  {
    os.writeObjectDefinition(defIndex, _infoRef.get());
  }

  @Override
  public void writeObject(OutRawH3 os, int defIndex, T object, OutH3 out)
  {
    os.writeObject(defIndex);
    
    for (FieldSerBase field : _fields) {
      field.write(os, object, out);
    }
  }
  
  /**
   * Introspect the class.
   */
  @Override
  public void introspect(ContextH3 context)
  {
    if (_infoRef.get() != null) {
      return;
    }
    
    _ctor = introspectConstructor();

    TreeMap<String,FieldSerBase> fieldMap = new TreeMap<>();

    try {
      introspectFields(fieldMap, _type);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }

    _fields = new FieldSerBase[fieldMap.size()];
    fieldMap.values().toArray(_fields);

    FieldInfoH3[] fieldInfo = new FieldInfoH3[_fields.length];

    for (int i = 0; i < _fields.length; i++) {
      fieldInfo[i] = _fields[i].info(); 
    }

    _infoRef.compareAndSet(null, new ClassInfoH3(_type.getName(), fieldInfo));
  }
  
  private MethodHandle introspectConstructor()
  {
    try {
      for (Constructor<?> ctor : _type.getDeclaredConstructors()) {
        if (ctor.getParameterTypes().length == 0) {
          ctor.setAccessible(true);
        
          MethodHandle mh = MethodHandles.lookup().unreflectConstructor(ctor);
        
          mh = mh.asType(MethodType.genericMethodType(0));
        
          return mh;
        }
      }
    } catch (Exception e) {
      throw new H3ExceptionIn(_type.getName() + ": " + e.getMessage(), e);
    }
    
    throw new H3ExceptionIn(L.l("Zero-arg constructor required for {0}",
                                _type.getName()));
  }
  
  private void introspectFields(Map<String,FieldSerBase> fieldMap,
                                Class<?> type)
    throws IllegalAccessException
  {
    if (type == null) {
      return;
    }
    
    introspectFields(fieldMap, type.getSuperclass());
    
    for (Field field : type.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      
      if (Modifier.isTransient(field.getModifiers())) {
        continue;
      }
      
      FieldSerBase fieldSer;
      
      Class<?> fieldType = field.getType();
      
      field.setAccessible(true);
      MethodHandle fieldGet = MethodHandles.lookup().unreflectGetter(field);
      MethodHandle fieldSet = MethodHandles.lookup().unreflectSetter(field);
      
      FieldProvider provider = _fieldMap.get(fieldType);
      
      if (provider != null) {
        fieldSer = provider.apply(field, fieldGet, fieldSet);
      }
      else {
        fieldSer = new FieldSerObject(field, fieldGet, fieldSet);
      }

      fieldMap.put(fieldSer.name(), fieldSer);
    }
  }

  /**
   * Reads the bean from the stream.
   */
  @Override
  public T readObject(InRawH3 is, InH3Amp in)
  {
    T bean = newInstance();
    
    in.ref(bean);
    
    FieldSerBase[] fields = _fields;
    int size = fields.length;
    
    for (int i = 0; i < size; i++) {
      fields[i].read(bean, is, in);
    }
    
    return bean;
  }
  
  @Override
  public void scan(InRawH3 is, PathH3Amp path, InH3Amp in, Object []values)
  {
    FieldSerBase[] fields = _fields;
    int size = fields.length;
    
    for (int i = 0; i < size; i++) {
      FieldSerBase field = fields[i];
      
      PathH3Amp subPath = path.field(field.name());

      if (subPath == null) {
        field.skip(is, in);
      }
      else {
        values[subPath.index()] = field.read(is, in);
      }
    }
  }

  @Override
  public void skip(InRawH3 is, InH3Amp in)
  {
    FieldSerBase[] fields = _fields;
    int size = fields.length;

    for (int i = 0; i < size; i++) {
      FieldSerBase field = fields[i];

      field.skip(is, in);
    }
  }

  @SuppressWarnings("unchecked")
  private T newInstance()
  {
    try {
      Object value = _ctor.invokeExact(); 
      
      return (T) value;
    } catch (Throwable e) {
      throw new H3ExceptionIn(e);
    }
  }
  
  abstract private static class FieldSerBase
  {
    private final Field _field;
    private final FieldInfoH3 _info;
    
    FieldSerBase(Field field)
    {
      _field = field;
      _info = new FieldInfoH3(field.getName());
    }
    
    String name()
    {
      return _field.getName();
    }
    
    FieldInfoH3 info()
    {
      return _info;
    }
    
    abstract void write(OutRawH3 os, Object object, OutH3 out);
    
    abstract void read(Object bean, InRawH3 is, InH3Amp in);
    
    abstract Object read(InRawH3 is, InH3Amp in);
    
    void skip(InRawH3 is, InH3Amp in)
    {
      is.skip(in);
    }
    
    IllegalStateException error(Throwable exn)
    {
      return new IllegalStateException(L.l("{0}.{1}: {2}", 
                                           _field.getDeclaringClass().getSimpleName(),
                                           _field.getName(),
                                           exn.toString()),
                                       exn);
    }
    
    @Override
    public String toString()
    {
      return (getClass().getSimpleName()
             + "[" + _field.getDeclaringClass().getSimpleName()
             + "." + name()
             + "]");
    }
  }

  /**
   * String field
   */
  private static final class FieldSerString extends FieldSerBase
  {
    private final MethodHandle _fieldGet;
    private final MethodHandle _fieldSet;
    
    FieldSerString(Field field, 
                   MethodHandle fieldGet,
                   MethodHandle fieldSet)
    {
      super(field);
      
      _fieldGet = fieldGet.asType(MethodType.methodType(String.class, Object.class));
      _fieldSet = fieldSet.asType(MethodType.methodType(void.class, Object.class, String.class));
    }
    
    @Override
    void write(OutRawH3 os, Object object, OutH3 out)
    {
      try {
        String value = (String) _fieldGet.invokeExact(object);
        
        os.writeString(value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
      
    @Override
    void read(Object bean, InRawH3 is, InH3Amp in)
    {
      try {
        String value = is.readString();
        
        _fieldSet.invokeExact(bean, value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
    
    @Override
    Object read(InRawH3 is, InH3Amp in)
    {
      return is.readString();
    }
  }
  
  /**
   * Byte field
   */
  private static final class FieldSerByte extends FieldSerBase
  {
    private final MethodHandle _fieldGet;
    private final MethodHandle _fieldSet;
    
    FieldSerByte(Field field, 
                MethodHandle fieldGet,
                MethodHandle fieldSet)
    {
      super(field);
      
      _fieldGet = fieldGet.asType(MethodType.methodType(byte.class, Object.class));
      _fieldSet = fieldSet.asType(MethodType.methodType(void.class, Object.class, byte.class));
    }
    
    @Override
    void write(OutRawH3 os, Object object, OutH3 out)
    {
      try {
        byte value = (byte) _fieldGet.invokeExact(object);
        
        os.writeLong(value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
      
    @Override
    void read(Object bean, InRawH3 is, InH3Amp in)
    {
      try {
        byte value = (byte) is.readLong();
        
        _fieldSet.invokeExact(bean, value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
    
    @Override
    Object read(InRawH3 is, InH3Amp in)
    {
      return (byte) is.readLong();
    }
  }
  
  /**
   * short field
   */
  private static final class FieldSerShort extends FieldSerBase
  {
    private final MethodHandle _fieldGet;
    private final MethodHandle _fieldSet;
    
    FieldSerShort(Field field, 
                  MethodHandle fieldGet,
                  MethodHandle fieldSet)
    {
      super(field);
      
      _fieldGet = fieldGet.asType(MethodType.methodType(short.class, Object.class));
      _fieldSet = fieldSet.asType(MethodType.methodType(void.class, Object.class, short.class));
    }
    
    @Override
    void write(OutRawH3 os, Object object, OutH3 out)
    {
      try {
        short value = (short) _fieldGet.invokeExact(object);
        
        os.writeLong(value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
      
    @Override
    void read(Object bean, InRawH3 is, InH3Amp in)
    {
      try {
        short value = (short) is.readLong();
        
        _fieldSet.invokeExact(bean, value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
    
    @Override
    Object read(InRawH3 is, InH3Amp in)
    {
      return (short) is.readLong();
    }
  }
  
  /**
   * Int field
   */
  private static final class FieldSerInt extends FieldSerBase
  {
    private final MethodHandle _fieldGet;
    private final MethodHandle _fieldSet;
    
    FieldSerInt(Field field, 
                MethodHandle fieldGet,
                MethodHandle fieldSet)
    {
      super(field);
      
      _fieldGet = fieldGet.asType(MethodType.methodType(int.class, Object.class));
      _fieldSet = fieldSet.asType(MethodType.methodType(void.class, Object.class, int.class));
    }
    
    @Override
    void write(OutRawH3 os, Object object, OutH3 out)
    {
      try {
        int value = (int) _fieldGet.invokeExact(object);
        
        os.writeLong(value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
      
    @Override
    void read(Object bean, InRawH3 is, InH3Amp in)
    {
      try {
        int value = (int) is.readLong();
        
        _fieldSet.invokeExact(bean, value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
    
    @Override
    Object read(InRawH3 is, InH3Amp in)
    {
      return (int) is.readLong();
    }
  }
  
  /**
   * Long field
   */
  private static final class FieldSerLong extends FieldSerBase
  {
    private final MethodHandle _fieldGet;
    private final MethodHandle _fieldSet;
    
    FieldSerLong(Field field, 
                 MethodHandle fieldGet,
                 MethodHandle fieldSet)
    {
      super(field);
      
      _fieldGet = fieldGet.asType(MethodType.methodType(long.class, Object.class));
      _fieldSet = fieldSet.asType(MethodType.methodType(void.class, Object.class, long.class));
    }
    
    @Override
    void write(OutRawH3 os, Object object, OutH3 out)
    {
      try {
        long value = (long) _fieldGet.invokeExact(object);
        
        os.writeLong(value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
      
    @Override
    void read(Object bean, InRawH3 is, InH3Amp in)
    {
      try {
        long value = is.readLong();
        
        _fieldSet.invokeExact(bean, value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
    
    @Override
    Object read(InRawH3 is, InH3Amp in)
    {
      return is.readLong();
    }
  }
  
  /**
   * boolean field
   */
  private static final class FieldSerBoolean extends FieldSerBase
  {
    private final MethodHandle _fieldGet;
    private final MethodHandle _fieldSet;
    
    FieldSerBoolean(Field field, 
                MethodHandle fieldGet,
                MethodHandle fieldSet)
    {
      super(field);
      
      _fieldGet = fieldGet.asType(MethodType.methodType(boolean.class, Object.class));
      _fieldSet = fieldSet.asType(MethodType.methodType(void.class, Object.class, boolean.class));
    }
    
    @Override
    void write(OutRawH3 os, Object object, OutH3 out)
    {
      try {
        boolean value = (boolean) _fieldGet.invokeExact(object);
        
        os.writeBoolean(value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
      
    @Override
    void read(Object bean, InRawH3 is, InH3Amp in)
    {
      try {
        boolean value = (boolean) is.readBoolean();
        
        _fieldSet.invokeExact(bean, value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
    
    @Override
    Object read(InRawH3 is, InH3Amp in)
    {
      return (boolean) is.readBoolean();
    }
  }

  /**
   * float field
   */
  private static final class FieldSerFloat extends FieldSerBase
  {
    private final MethodHandle _fieldGet;
    private final MethodHandle _fieldSet;
    
    FieldSerFloat(Field field, 
                  MethodHandle fieldGet,
                  MethodHandle fieldSet)
    {
      super(field);
      
      _fieldGet = fieldGet.asType(MethodType.methodType(float.class, Object.class));
      _fieldSet = fieldSet.asType(MethodType.methodType(void.class, Object.class, float.class));
    }
    
    @Override
    void write(OutRawH3 os, Object object, OutH3 out)
    {
      try {
        float value = (float) _fieldGet.invokeExact(object);
        
        os.writeFloat(value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
      
    @Override
    void read(Object bean, InRawH3 is, InH3Amp in)
    {
      try {
        float value = is.readFloat();
        
        _fieldSet.invokeExact(bean, value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
    
    @Override
    Object read(InRawH3 is, InH3Amp in)
    {
      return is.readFloat();
    }
  }

  /**
   * double field
   */
  private static final class FieldSerDouble extends FieldSerBase
  {
    private final MethodHandle _fieldGet;
    private final MethodHandle _fieldSet;
    
    FieldSerDouble(Field field, 
                   MethodHandle fieldGet,
                   MethodHandle fieldSet)
    {
      super(field);
      
      _fieldGet = fieldGet.asType(MethodType.methodType(double.class, Object.class));
      _fieldSet = fieldSet.asType(MethodType.methodType(void.class, Object.class, double.class));
    }
    
    @Override
    void write(OutRawH3 os, Object object, OutH3 out)
    {
      try {
        double value = (double) _fieldGet.invokeExact(object);
        
        os.writeDouble(value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
      
    @Override
    void read(Object bean, InRawH3 is, InH3Amp in)
    {
      try {
        double value = is.readDouble();
        
        _fieldSet.invokeExact(bean, value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
    
    @Override
    Object read(InRawH3 is, InH3Amp in)
    {
      return is.readDouble();
    }
  }
  
  /**
   * Object field
   */
  private static final class FieldSerObject extends FieldSerBase
  {
    private final MethodHandle _fieldGet;
    private final MethodHandle _fieldSet;
    
    FieldSerObject(Field field, 
                   MethodHandle fieldGet,
                   MethodHandle fieldSet)
    {
      super(field);
      
      _fieldGet = fieldGet.asType(MethodType.methodType(Object.class, Object.class));
      _fieldSet = fieldSet.asType(MethodType.methodType(void.class, Object.class, Object.class));
    }
    
    @Override
    void write(OutRawH3 os, Object object, OutH3 out)
    {
      try {
        Object value = _fieldGet.invokeExact(object);
        
        if (value == null) {
          os.writeNull();
        }
        else {
          //ser = out.serializer(value.getClass());
          out.writeObject(value);
        }
      } catch (Throwable e) {
        throw error(e);
      }
    }
      
    @Override
    void read(Object bean, InRawH3 is, InH3Amp in)
    {
      try {
        Object value = in.readObject();
        
        _fieldSet.invokeExact(bean, value);
      } catch (Throwable e) {
        throw error(e);
      }
    }
    
    @Override
    Object read(InRawH3 is, InH3Amp in)
    {
      return is.readObject(in);
    }
  }
  
  private interface FieldProvider
  {
    FieldSerBase apply(Field field, MethodHandle getter, MethodHandle setter); 
  }
  
  static {
    _fieldMap.put(String.class, FieldSerString::new);
    _fieldMap.put(boolean.class, FieldSerBoolean::new);
    _fieldMap.put(byte.class, FieldSerByte::new);
    _fieldMap.put(short.class, FieldSerShort::new);
    _fieldMap.put(int.class, FieldSerInt::new);
    _fieldMap.put(long.class, FieldSerLong::new);
    _fieldMap.put(float.class, FieldSerFloat::new);
    _fieldMap.put(double.class, FieldSerDouble::new);
  }
}
