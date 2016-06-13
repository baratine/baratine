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

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.convert.bean.FieldBase;
import com.caucho.v5.convert.bean.FieldBeanFactory;
import com.caucho.v5.h3.OutH3;
import com.caucho.v5.h3.context.ContextH3;
import com.caucho.v5.h3.io.ClassInfoH3;
import com.caucho.v5.h3.io.ConstH3;
import com.caucho.v5.h3.io.InH3Amp;
import com.caucho.v5.h3.io.InRawH3;
import com.caucho.v5.h3.io.OutRawH3;
import com.caucho.v5.h3.query.PathH3Amp;

/**
 * H3 enum serializer.
 */
public class SerializerH3EnumSet<T extends EnumSet<?>> extends SerializerH3Base<T>
{
  private static final FieldBase<EnumSet<?>> _elementType;
  
  private AtomicReference<ClassInfoH3> _infoRef = new AtomicReference<>();
  
  SerializerH3EnumSet()
  {
  }
  
  @Override
  public Type type()
  {
    return EnumSet.class;
  }
  
  @Override
  public int typeSequence()
  {
    return ConstH3.DEF_ENUMSET;
  }

  @Override
  public SerializerH3Amp<T> schema(ContextH3 context)
  {
    return null;
  }
  
  @Override
  public void writeDefinition(OutRawH3 os, int defIndex)
  {
  //  os.writeObjectDefinition(defIndex, _infoRef.get());
  }

  @Override
  public void writeObject(OutRawH3 os, int defIndex, T value, OutH3 out)
  {
    os.writeObject(defIndex);
    
    Class<?> type = (Class<?>) _elementType.getObject(value);
    
    Object []values = new Object[value.size()];
    
    int i = 0;
    
    for (Enum<?> item : value) {
      values[i++] = item;
    }
    
    out.writeObject(type);
    out.writeObject(values);
  }

  @Override
  public void skip(InRawH3 is, InH3Amp in)
  {
    long chunk = is.readUnsigned();
    long len = InRawH3.chunkSize(chunk);
    boolean isFinal = InRawH3.chunkIsFinal(chunk);
    
    for (long i = 0; i < len; i++) {
      is.skip(in);
    }
  }

  /**
   * Introspect the class.
   */
  @Override
  public void introspect(ContextH3 context)
  {
  }

  @Override
  public T readObject(InRawH3 is, InH3Amp in)
  {
    Class type = in.readObject(Class.class);
    
    Object []values = (Object[]) in.readObject();
    
    EnumSet set = EnumSet.noneOf(type);
    
    for (Object value : values) {
      set.add((Enum) value);
    }

    return (T) set;
  }
  
  @Override
  public void scan(InRawH3 is, PathH3Amp path, InH3Amp in, Object []values)
  {
    is.skip(in);
    is.skip(in);
  }
  
  static {
    Field elementType = null;
    
    for (Field field : EnumSet.class.getDeclaredFields()) {
      if (field.getName().equals("elementType")) {
        elementType = field;
      }
    }
    
    elementType.setAccessible(true);
    
    _elementType = (FieldBase) FieldBeanFactory.get(elementType);
  }
}
