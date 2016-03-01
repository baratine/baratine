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

/**
 * Represents an introspected java class.
 */
public class JavaParameterizedType implements JType {
  private JClassLoader _loader;
  private JClass _rawClass;
  private JType []_typeArgs;

  JavaParameterizedType(JClassLoader loader, JClass rawClass, JType []args)
  {
    _loader = loader;
    _rawClass = rawClass;
    _typeArgs = args;
  }

  /**
   * Returns the type name.
   */
  public String getName()
  {
    return _rawClass.getName();
  }

  /**
   * Returns the print name.
   */
  public String getPrintName()
  {
    if (_typeArgs.length == 0)
      return _rawClass.getPrintName();

    StringBuilder cb = new StringBuilder();
    cb.append(_rawClass.getPrintName());
    cb.append('<');
    for (int i = 0; i < _typeArgs.length; i++) {
      if (i != 0)
        cb.append(',');
      
      cb.append(_typeArgs[i].getPrintName());
    }
    
    cb.append('>');

    return cb.toString();
  }

  /**
   * Returns the parameter types.
   */
  public JType []getActualTypeArguments()
  {
    return _typeArgs;
  }

  /**
   * Returns the raw type.
   */
  public JClass getRawType()
  {
    return _rawClass;
  }
  
  /**
   * Returns true for a primitive class.
   */
  public boolean isPrimitive()
  {
    return _rawClass.isPrimitive();
  }
  
  /**
   * Returns true for a public class.
   */
  public boolean isPublic()
  {
    return _rawClass.isPublic();
  }
  
  /**
   * Returns true for an abstract class
   */
  public boolean isAbstract()
  {
    return _rawClass.isAbstract();
  }
  
  /**
   * Returns true for a final class
   */
  public boolean isFinal()
  {
    return _rawClass.isFinal();
  }
  
  /**
   * Returns true for an interface
   */
  public boolean isInterface()
  {
    return _rawClass.isAbstract();
  }

  /**
   * Returns the superclass.
   */
  public JClass getSuperClass()
  {
    return _rawClass.getSuperClass();
  }

  /**
   * Returns the interfaces.
   */
  public JClass []getInterfaces()
  {
    return _rawClass.getInterfaces();
  }

  /**
   * Returns true for an array class.
   */
  public boolean isArray()
  {
    return _rawClass.isArray();
  }

  /**
   * Returns the component for a class.
   */
  public JClass getComponentType()
  {
    return null;
  }

  /**
   * Returns true if the jClass is assignable to the class.
   */
  public boolean isAssignableTo(Class cl)
  {
    return _rawClass.isAssignableTo(cl);
  }

  /**
   * Returns true if the jClass is assignable to the class.
   */
  public boolean isAssignableFrom(Class cl)
  {
    return _rawClass.isAssignableFrom(cl);
  }

  /**
   * Returns true if the jClass is assignable to the class.
   */
  public boolean isAssignableFrom(JClass cl)
  {
    return _rawClass.isAssignableFrom(cl);
  }

  /**
   * Returns the declared methods
   */
  public JMethod []getDeclaredMethods()
  {
    return _rawClass.getDeclaredMethods();
  }

  /**
   * Returns the public methods
   */
  public JMethod []getMethods()
  {
    return _rawClass.getMethods();
  }

  /**
   * Returns the matching method.
   */
  public JMethod getMethod(String name, JClass []param)
  {
    return _rawClass.getMethod(name, param);
  }

  /**
   * Returns the declared fields
   */
  public JField []getDeclaredFields()
  {
    return _rawClass.getDeclaredFields();
  }

  /**
   * Returns the fields
   */
  public JField []getFields()
  {
    return _rawClass.getFields();
  }
}
