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

package com.caucho.v5.ramp.jamp;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parameter parsing.
 */
abstract class JampMarshal
{
  private static final Logger log
    = Logger.getLogger(JampMarshal.class.getName());

  private static final HashMap<Type,JampMarshal> _marshalMap
    = new HashMap<>();

  abstract Object toObject(String stringValue);

  static JampMarshal create(Type type)
  {
    JampMarshal marshal = _marshalMap.get(type);

    if (marshal != null) {
      return marshal;
    }

    if (type instanceof Class<?>) {
      Class<?> cl = (Class<?>) type;

      if (Enum.class.isAssignableFrom(cl)) {
        return new JampEnumMarshal(cl);
      }

      try {
        Method method = cl.getMethod("valueOf", String.class);

        if (method != null) {
          return new JampValueOfMarshal(method);
        }
      } catch (Exception e) {
        log.log(Level.FINEST, e.toString(), e);
      }
    }
    else if (type instanceof TypeVariable) {
      return new JampTypeVariableMarshal();
    }
    else if (type instanceof GenericArrayType) {
      return new JampGenericArrayTypeMarshal();
    }

    return new JampUnknownMarshal(type);
  }

  static class JampStringMarshal extends JampMarshal
  {
    @Override
    protected Object toObject(String string)
    {
      return string;
    }
  }

  static class JampBooleanMarshal extends JampMarshal
  {
    @Override
    protected Object toObject(String string)
    {
      return Boolean.valueOf(string);
    }
  }

  static class JampByteMarshal extends JampMarshal
  {
    @Override
    protected Object toObject(String string)
    {
      if (string != null) {
        return Byte.valueOf(string);
      }
      else {
        return new Byte((byte) 0);
      }
    }
  }

  static class JampShortMarshal extends JampMarshal
  {
    @Override
    protected Object toObject(String string)
    {
      if (string != null) {
        return Short.valueOf(string);
      }
      else {
        return new Short((short) 0);
      }
    }
  }

  static class JampIntegerMarshal extends JampMarshal
  {
    @Override
    protected Object toObject(String string)
    {
      if (string != null) {
        return Integer.valueOf(string);
      }
      else {
        return new Integer(0);
      }
    }
  }

  static class JampLongMarshal extends JampMarshal
  {
    @Override
    protected Object toObject(String string)
    {
      if (string != null) {
        return Long.valueOf(string);
      }
      else {
        return new Long(0);
      }
    }
  }

  static class JampCharacterMarshal extends JampMarshal
  {
    @Override
    protected Object toObject(String string)
    {
      if (string == null || string.length() == 0) {
        return null;
      }
      else {
        return new Character(string.charAt(0));
      }
    }
  }

  static class JampFloatMarshal extends JampMarshal
  {
    @Override
    protected Object toObject(String string)
    {
      if (string != null) {
        return Float.valueOf(string);
      }
      else {
        return new Float(0);
      }
    }
  }

  static class JampDoubleMarshal extends JampMarshal
  {
    @Override
    protected Object toObject(String string)
    {
      if (string != null) {
        return Double.valueOf(string);
      }
      else {
        return new Double(0);
      }
    }
  }

  static class JampEnumMarshal<T extends Enum<T>> extends JampMarshal
  {
    private Class<T> _type;

    JampEnumMarshal(Class<T> type)
    {
      _type = type;
    }

    @Override
    protected Object toObject(String string)
    {
      return Enum.valueOf(_type, string);
    }
  }

  static class JampTypeVariableMarshal extends JampMarshal
  {
    @Override
    protected Object toObject(String string)
    {
      return string;
    }
  }

  static class JampGenericArrayTypeMarshal extends JampMarshal
  {
    @Override
    protected Object toObject(String string)
    {
      // XXX:
      return new String[] { string };
    }
  }

  static class JampValueOfMarshal extends JampMarshal
  {
    private Method _method;

    JampValueOfMarshal(Method method)
    {
      _method = method;
    }

    @Override
    protected Object toObject(String string)
    {
      if (string == null) {
        return null;
      }

      try {
        return _method.invoke(null, string);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  static class JampUnknownMarshal extends JampMarshal
  {
    private Type _type;

    JampUnknownMarshal(Type type)
    {
      _type = type;
    }

    @Override
    protected Object toObject(String string)
    {
      throw new UnsupportedOperationException(String.valueOf(_type));
    }
  }

  static {
    _marshalMap.put(boolean.class, new JampBooleanMarshal());
    _marshalMap.put(Boolean.class, new JampBooleanMarshal());

    _marshalMap.put(char.class, new JampCharacterMarshal());
    _marshalMap.put(Character.class, new JampCharacterMarshal());

    _marshalMap.put(byte.class, new JampByteMarshal());
    _marshalMap.put(Byte.class, new JampByteMarshal());

    _marshalMap.put(short.class, new JampShortMarshal());
    _marshalMap.put(Short.class, new JampShortMarshal());

    _marshalMap.put(int.class, new JampIntegerMarshal());
    _marshalMap.put(Integer.class, new JampIntegerMarshal());

    _marshalMap.put(long.class, new JampLongMarshal());
    _marshalMap.put(Long.class, new JampLongMarshal());

    _marshalMap.put(float.class, new JampFloatMarshal());
    _marshalMap.put(Float.class, new JampFloatMarshal());

    _marshalMap.put(double.class, new JampDoubleMarshal());
    _marshalMap.put(Double.class, new JampDoubleMarshal());

    _marshalMap.put(String.class, new JampStringMarshal());
    _marshalMap.put(Object.class, new JampStringMarshal());
  }
}
