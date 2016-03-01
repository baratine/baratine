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
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.h3.OutH3;
import com.caucho.v5.h3.context.ContextH3;
import com.caucho.v5.h3.io.ClassInfoH3;
import com.caucho.v5.h3.io.ConstH3.ClassTypeH3;
import com.caucho.v5.h3.io.FieldInfoH3;
import com.caucho.v5.h3.io.H3ExceptionIn;
import com.caucho.v5.h3.io.InH3Amp;
import com.caucho.v5.h3.io.InRawH3;
import com.caucho.v5.h3.io.OutRawH3;
import com.caucho.v5.h3.query.PathH3Amp;
import com.caucho.v5.util.L10N;

/**
 * H3 typed list serializer.
 */
public class SerializerH3List<T extends List<?>> extends SerializerH3Base<T>
{
  private static final L10N L = new L10N(SerializerH3List.class);
  
  private Class<? extends T> _type;
  private AtomicReference<ClassInfoH3> _infoRef = new AtomicReference<>();
  
  private MethodHandle _ctor;
  private FieldSerBase _item;
  
  SerializerH3List(Class<? extends T> type)
  {
    _type = type;
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
  public void writeObject(OutRawH3 os, int defIndex, T list, OutH3 out)
  {
    os.writeObject(defIndex);
    
    int size = list.size();
    
    os.writeChunk(size, true);
    
    for (Object entry : list) {
      _item.write(os, entry, out);
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
    
    introspectFields();

    FieldInfoH3[] fieldInfo = new FieldInfoH3[1];
    
    fieldInfo[0] = _item.info();

    ClassInfoH3 classInfo = new ClassInfoH3(_type.getName(), ClassTypeH3.LIST, fieldInfo);
    
    _infoRef.compareAndSet(null, classInfo); 
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
  
  private void introspectFields()
  {
    Class<?> keyType = Object.class;

    _item = new FieldSerObject("item", keyType);
  }

  @Override
  public T readObject(InRawH3 is, InH3Amp in)
  {
    List<Object> list = (List) newInstance();

    while (true) {
      long chunk = is.readUnsigned();
      long size = InRawH3.chunkSize(chunk);
      
      for (int i = 0; i < size; i++) {
        Object item = _item.read(is, in);
        
        list.add(item);
      }
      
      if (InRawH3.chunkIsFinal(chunk)) {
        return (T) list;
      }
    }
  }
  
  @Override
  public void scan(InRawH3 is, PathH3Amp path, InH3Amp in, Object []values)
  {
    while (true) {
      long chunk = is.readUnsigned();
      long size = InRawH3.chunkSize(chunk);
      
      for (int i = 0; i < size; i++) {
        Object keyObject = in.readObject();
        
        if (keyObject instanceof String) {
          String key = (String) keyObject;
          
          PathH3Amp subPath = path.field(key);
          
          if (subPath != null) {
            values[subPath.index()] = in.readObject();
          }
          else {
            is.skip(in);
          }
        }
        else {
          is.skip(in);
        }
      }
      
      if (InRawH3.chunkIsFinal(chunk)) {
        return;
      }
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
    private final String _name;
    private final Class<?> _type;
    private final FieldInfoH3 _info;
    
    FieldSerBase(String name, Class<?> type)
    {
      _name = name;
      _type = type;
      _info = new FieldInfoH3(name);
    }
    
    String name()
    {
      return _name;
    }
    
    FieldInfoH3 info()
    {
      return _info;
    }
    
    abstract void write(OutRawH3 os, Object object, OutH3 out);
    
    abstract Object read(InRawH3 is, InH3Amp in);
    
    void skip(InRawH3 is, InH3Amp in)
    {
      is.skip(in);
    }
    
    IllegalStateException error(Throwable exn)
    {
      return new IllegalStateException(L.l("{0}.{1}: {2}", 
                                           "Map",
                                           _name,
                                           exn.toString()),
                                       exn);
    }
  }
  
  private static final class FieldSerObject extends FieldSerBase
  {
    FieldSerObject(String name, Class<?> type)
    {
      super(name, type);
    }
    
    @Override
    void write(OutRawH3 os, Object value, OutH3 out)
    {
      try {
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
    Object read(InRawH3 is, InH3Amp in)
    {
      return is.readObject(in);
    }
  }
}
