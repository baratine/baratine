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

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.caucho.v5.h3.SerializerH3;
import com.caucho.v5.h3.context.ContextH3;
import com.caucho.v5.h3.io.ConstH3;
import com.caucho.v5.h3.io.StreamSourceH3;

/**
 * H3 typed serializer.
 */
public class SerializerFactoryH3Impl implements SerializerFactoryH3
{
  private static final SerializerH3Amp<?> MAP;
  private static final SerializerH3Amp<?> LIST;
  private static final SerializerH3Amp<?> SET;

  private static final HashMap<Class<?>,SerializerH3Amp<?>> _serMap
    = new HashMap<>();

  private static final ArrayList<SerializerH3Amp<?>> _serArray
    = new ArrayList<>();

  @Override
  public void initSerializers(ArrayList<SerializerH3Amp<?>> serArray)
  {
    serArray.clear();
    serArray.addAll(_serArray);
  }

  @Override
  public <T> SerializerH3Amp<T> serializer(Class<T> type, ContextH3 context)
  {
    SerializerH3Amp<?> ser = _serMap.get(type);
    System.out.println("SER: " + type);
    if (ser != null) {
      return (SerializerH3Amp) ser;
    }
    else if (Map.class.isAssignableFrom(type)) {
      return (SerializerH3Amp) MAP;
    }
    else if (Enum.class.isAssignableFrom(type)) {
      Class<?> parent = type.getEnclosingClass();
      
      if (parent != null && Enum.class.isAssignableFrom(parent)) {
        return new SerializerH3Enum(parent);
      }
      else {
        return new SerializerH3Enum(type);
      }
    }
    else if (StreamSourceH3.class.isAssignableFrom(type)) {
      return new SerializerH3StreamSource();
    }
    else if (type.isArray()) {
      throw new UnsupportedOperationException(String.valueOf(type));
    }
    else if (Modifier.isAbstract(type.getModifiers())) {
      throw new UnsupportedOperationException(String.valueOf(type));
    }
    else {
      return new SerializerH3Java<>(type, context);
    }
  }

  @Override
  public <T> SerializerH3<T> get(Type type)
  {
    throw new UnsupportedOperationException(String.valueOf(type));
  }

  private static void ser(Class<?> type, SerializerH3Amp<?> ser)
  {
    _serMap.put(type, ser);

    while (_serArray.size() <= ser.typeSequence()) {
      _serArray.add(null);
    }

    _serArray.set(ser.typeSequence(), ser);
  }

  static {
    MAP = new SerializerH3MapPredef(HashMap.class, ConstH3.DEF_MAP);
    LIST = new SerializerH3ListPredef(ArrayList.class, ConstH3.DEF_LIST);
    SET = new SerializerH3SetPredef(HashSet.class, ConstH3.DEF_SET);

    ser(Boolean.class, new SerializerH3Boolean());

    ser(String.class, new SerializerH3String());
    ser(Byte.class, new SerializerH3Byte());
    ser(Character.class, new SerializerH3Character());
    ser(Short.class, new SerializerH3Short());
    ser(Integer.class, new SerializerH3Integer());
    ser(Long.class, new SerializerH3Long());
    ser(Float.class, new SerializerH3Float());
    ser(Double.class, new SerializerH3Double());

    ser(HashMap.class, MAP);
    ser(ArrayList.class, LIST);
    ser(HashSet.class, SET);

    ser(byte[].class, new SerializerH3ArrayByte());
    ser(char[].class, new SerializerH3ArrayChar());
    ser(Object[].class, new SerializerH3ArrayObject());
    ser(String[].class, new SerializerH3ArrayString());

    //time
    ser(Instant.class, new SerializerH3Instant());
    ser(LocalDate.class, new SerializerH3LocalDate());
    ser(LocalTime.class, new SerializerH3LocalTime());
    ser(LocalDateTime.class, new SerializerH3LocalDateTime());
    ser(ZonedDateTime.class, new SerializerH3ZonedDateTime());

    //regex Pattern
    ser(Pattern.class, new SerializerH3RegexPattern());

    //BigInteger, BigDecimal
    ser(BigInteger.class, new SerializerH3BigInteger());
    ser(BigDecimal.class, new SerializerH3BigDecimal());
    
    ser(Class.class, new SerializerH3Class());

    ser(UUID.class, new SerializerH3UUID());
    ser(URI.class, new SerializerH3URI());
    ser(URL.class, new SerializerH3URL());

  }
}
