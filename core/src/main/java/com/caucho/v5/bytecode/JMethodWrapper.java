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

import java.lang.annotation.Annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Wrapper around the Java Method for a JMethod.
 */
public class JMethodWrapper extends JMethod {
  private JClassLoader _loader;

  private Method _method;

  public JMethodWrapper(Method method)
  {
    this(method, JClassLoaderWrapper.create());
  }
  
  public JMethodWrapper(Method method, JClassLoader loader)
  {
    if (loader == null)
      throw new NullPointerException();

    _method = method;
    _loader = loader;
  }

  /**
   * Returns the method name.
   */
  public String getName()
  {
    return _method.getName();
  }

  /**
   * Returns true for a static method.
   */
  public boolean isStatic()
  {
    return Modifier.isStatic(_method.getModifiers());
  }

  /**
   * Returns true for a private method
   */
  public boolean isPrivate()
  {
    return Modifier.isPrivate(_method.getModifiers());
  }

  /**
   * Returns true for a public method.
   */
  public boolean isPublic()
  {
    return Modifier.isPublic(_method.getModifiers());
  }

  /**
   * Returns true for a protected method.
   */
  public boolean isProtected()
  {
    return Modifier.isProtected(_method.getModifiers());
  }

  /**
   * Returns true for a final method.
   */
  public boolean isFinal()
  {
    return Modifier.isFinal(_method.getModifiers());
  }

  /**
   * Returns true for an abstract method.
   */
  public boolean isAbstract()
  {
    return Modifier.isAbstract(_method.getModifiers());
  }

  /**
   * Returns the declaring type.
   */
  public JClass getDeclaringClass()
  {
    return _loader.forName(_method.getDeclaringClass().getName());
  }

  /**
   * Returns the return type.
   */
  public JClass getReturnType()
  {
    return _loader.forName(_method.getReturnType().getName());
  }

  /**
   * Returns the return type.
   */
  public JType getGenericReturnType()
  {
    try {
      Type retType = _method.getGenericReturnType();

      if (retType instanceof Class)
  return _loader.forName(((Class) retType).getName());
      else
  return new JTypeWrapper(_loader, (ParameterizedType) retType);
    } catch (NoSuchMethodError e) {
      return getReturnType();
    }

  }

  /**
   * Returns the parameter types.
   */
  public JClass []getParameterTypes()
  {
    Class []types = _method.getParameterTypes();

    JClass []jTypes = new JClass[types.length];

    for (int i = 0; i < types.length; i++) {
      jTypes[i] = _loader.forName(types[i].getName());
    }

    return jTypes;
  }

  /**
   * Returns the exception types.
   */
  public JClass []getExceptionTypes()
  {
    Class []types = _method.getExceptionTypes();

    JClass []jTypes = new JClass[types.length];

    for (int i = 0; i < types.length; i++) {
      jTypes[i] = _loader.forName(types[i].getName());
    }

    return jTypes;
  }
  
  /**
   * Returns the annotation.
   */
  @Override
  public JAnnotation []getDeclaredAnnotations()
  {
    Annotation []ann = _method.getAnnotations();

    JAnnotation []jAnn = new JAnnotation[ann.length];

    for (int i = 0; i < ann.length; i++) {
      jAnn[i] = new JAnnotationWrapper(ann[i]);
    }

    return jAnn;
  }
}
