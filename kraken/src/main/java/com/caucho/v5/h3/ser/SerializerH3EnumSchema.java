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

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.h3.OutH3;
import com.caucho.v5.h3.context.ContextH3;
import com.caucho.v5.h3.io.ClassInfoH3;
import com.caucho.v5.h3.io.ConstH3.ClassTypeH3;
import com.caucho.v5.h3.io.FieldInfoH3;
import com.caucho.v5.h3.io.InH3Amp;
import com.caucho.v5.h3.io.InRawH3;
import com.caucho.v5.h3.io.OutRawH3;
import com.caucho.v5.h3.query.PathH3Amp;

/**
 * H3 enum serializer.
 */
public class SerializerH3EnumSchema<T extends Enum<?>> extends SerializerH3Base<T>
{
  private Class<T> _type;
  
  private AtomicReference<ClassInfoH3> _infoRef = new AtomicReference<>();
  private int _sequence;
  
  SerializerH3EnumSchema(Class<T> type, int sequence)
  {
    Objects.requireNonNull(type);
    
    if (sequence <= 0) {
      throw new IllegalStateException();
    }
    
    _type = type;
    _sequence = sequence;
  }
  
  @Override
  public Type type()
  {
    return _type;
  }
  
  @Override
  public int typeSequence()
  {
    return _sequence;
  }

  @Override
  public SerializerH3Amp<T> schema(ContextH3 context)
  {
    throw new IllegalStateException();
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
    os.writeLong(value.ordinal());
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
    
    //_ctor = introspectConstructor();

    // XXX: needs to enumerate fields 
    FieldInfoH3[] fieldInfo = new FieldInfoH3[1];
    
    fieldInfo[0] = new FieldInfoH3("ordinal");

    ClassInfoH3 classInfo = new ClassInfoH3(_type.getName(), 
                                            ClassTypeH3.ENUM, 
                                            fieldInfo,
                                            _sequence);
    
    _infoRef.compareAndSet(null, classInfo); 
  }

  @Override
  public T readObject(InRawH3 is, InH3Amp in)
  {
    long index = is.readLong();

    return _type.getEnumConstants()[(int) index];
  }
  
  @Override
  public void scan(InRawH3 is, PathH3Amp path, InH3Amp in, Object []values)
  {
    is.skip(in);
  }
}
