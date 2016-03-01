/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.bytecode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Wrapper around the Java field for a JField.
 */
public class JFieldWrapper extends JField {
  private JClassLoader _loader;

  private Field _field;

  public JFieldWrapper(Field field, JClassLoader loader)
  {
    _loader = loader;
    _field = field;
  }

  /**
   * Returns the field name.
   */
  public String getName()
  {
    return _field.getName();
  }

  /**
   * Returns the declaring type.
   */
  public JClass getDeclaringClass()
  {
    return _loader.forName(_field.getDeclaringClass().getName());
  }

  /**
   * Returns the return type.
   */
  public JClass getType()
  {
    return _loader.forName(_field.getType().getName());
  }

  /**
   * Returns the return type.
   */
  public JType getGenericType()
  {
    try {
      Type type = _field.getGenericType();

      if (type instanceof Class)
  return getType();
      else
  return new JTypeWrapper(_loader, (ParameterizedType) type);
    } catch (NoSuchMethodError e) {
      return getType();
    }
  }

  /**
   * Returns true for a static field.
   */
  public boolean isStatic()
  {
    return Modifier.isStatic(_field.getModifiers());
  }

  /**
   * Returns true for a private field.
   */
  public boolean isPrivate()
  {
    return Modifier.isPrivate(_field.getModifiers());
  }

  /**
   * Returns true for a transient field.
   */
  public boolean isTransient()
  {
    return Modifier.isTransient(_field.getModifiers());
  }
}
