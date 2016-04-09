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
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Objects;
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

/**
 * H3 enum serializer.
 */
public class SerializerH3Enum<T extends Enum<T>> extends SerializerH3Base<T>
{
  private Class<T> _type;
  private MethodHandle _ctor;
  
  private AtomicReference<ClassInfoH3> _infoRef = new AtomicReference<>();
  
  SerializerH3Enum(Class<T> type)
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
    int seq = context.nextTypeSequence();
    
    SerializerH3EnumSchema<T> serSchema = new SerializerH3EnumSchema(_type, seq);
    
    //context.register(_type, serSchema);
    
    serSchema.introspect(context);
    
    return serSchema;
  }
  
  @Override
  public void writeDefinition(OutRawH3 os, int defIndex)
  {
    os.writeObjectDefinition(defIndex, _infoRef.get());
  }

  @Override
  public void writeObject(OutRawH3 os, int defIndex, T value, OutH3 out)
  {
    os.writeObject(defIndex);
    os.writeString(value.name());
  }

  @Override
  public void skip(InRawH3 is, InH3Amp in)
  {
    is.skip(in);
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

    FieldInfoH3[] fieldInfo = new FieldInfoH3[1];
    
    fieldInfo[0] = new FieldInfoH3("name");

    ClassInfoH3 classInfo = new ClassInfoH3(_type.getName(), ClassTypeH3.CLASS, fieldInfo);
    
    _infoRef.compareAndSet(null, classInfo); 
  }
  
  private MethodHandle introspectConstructor()
  {
    try {
      Method m = _type.getMethod("valueOf", String.class);
      
      Objects.requireNonNull(m);
      
      m.setAccessible(true);
        
      MethodHandle mh = MethodHandles.lookup().unreflect(m);
          
      mh = mh.asType(MethodType.methodType(Object.class, String.class));
        
      return mh;
    } catch (Exception e) {
      throw new H3ExceptionIn(_type.getName() + ": " + e.getMessage(), e);
    }
  }

  @Override
  public T readObject(InRawH3 is, InH3Amp in)
  {
    String name = is.readString();

    return Enum.valueOf(_type, name);
  }
  
  @Override
  public void scan(InRawH3 is, PathH3Amp path, InH3Amp in, Object []values)
  {
    is.skip(in);
  }
}
