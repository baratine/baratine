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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Wrapper around the java Class for a JClass.
 */
public class JTypeWrapper implements JType {
  private JClassLoader _loader;
  
  private ParameterizedType _type;

  public JTypeWrapper(JClassLoader loader, ParameterizedType type)
  {
    _loader = loader;
    
    _type = type;
  }

  public JTypeWrapper(ParameterizedType type, ClassLoader loader)
  {
    _loader = JClassLoaderWrapper.create(loader);
    
    _type = type;
  }
  
  public static JType create(Type type, ClassLoader loader)
  {
    if (type instanceof ParameterizedType) {
      return new JTypeWrapper((ParameterizedType) type, loader);
    }
    else {
      return new JClassWrapper((Class) type);
    }
  }

  /**
   * Returns the class name.
   */
  public String getName()
  {
    return ((Class) _type.getRawType()).getName();
  }

  /**
   * Returns the print name.
   */
  public String getPrintName()
  {
    JType []typeArgs = getActualTypeArguments();
    
    if (typeArgs.length == 0)
      return getRawClass().getPrintName();

    StringBuilder cb = new StringBuilder();
    cb.append(getRawClass().getPrintName());
    cb.append('<');
    for (int i = 0; i < typeArgs.length; i++) {
      if (i != 0)
        cb.append(',');
      
      cb.append(typeArgs[i].getPrintName());
    }
    
    cb.append('>');

    return cb.toString();
  }

  /**
   * Returns the actual type arguments.
   */
  public JType []getActualTypeArguments()
  {
    Type []rawArgs = _type.getActualTypeArguments();

    JType []args = new JType[rawArgs.length];
    for (int i = 0; i < args.length; i++) {
      Type type = rawArgs[i];

      if (type instanceof Class) {
        args[i] = _loader.forName(((Class) type).getName());
      }
      else if (type instanceof ParameterizedType)
        args[i] = new JTypeWrapper(_loader, (ParameterizedType) type);
      else {
        args[i] = _loader.forName("java.lang.Object");
        // jpa/0gg0
        // throw new IllegalStateException(type.toString());
      }
    }

    return args;
  }

  /**
   * Returns the actual type arguments.
   */
  public JClass getRawType()
  {
    return _loader.forName(((Class) _type.getRawType()).getName());
  }

  
  /**
   * Returns true for a primitive class.
   */
  public boolean isPrimitive()
  {
    return getRawClass().isPrimitive();
  }
  
  /**
   * Returns true for a public class.
   */
  public boolean isPublic()
  {
    return getRawClass().isPublic();
  }
  
  /**
   * Returns true for an abstract class
   */
  public boolean isAbstract()
  {
    return getRawClass().isAbstract();
  }
  
  /**
   * Returns true for a final class
   */
  public boolean isFinal()
  {
    return getRawClass().isFinal();
  }
  
  /**
   * Returns true for an interface
   */
  public boolean isInterface()
  {
    return getRawClass().isAbstract();
  }

  /**
   * Returns the superclass.
   */
  public JClass getSuperClass()
  {
    return getRawClass().getSuperClass();
  }

  /**
   * Returns the interfaces.
   */
  public JClass []getInterfaces()
  {
    return getRawClass().getInterfaces();
  }

  /**
   * Returns true for an array class.
   */
  public boolean isArray()
  {
    return getRawClass().isArray();
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
    return getRawClass().isAssignableTo(cl);
  }

  /**
   * Returns true if the jClass is assignable to the class.
   */
  public boolean isAssignableFrom(Class cl)
  {
    return getRawClass().isAssignableFrom(cl);
  }

  /**
   * Returns true if the jClass is assignable to the class.
   */
  public boolean isAssignableFrom(JClass cl)
  {
    return getRawClass().isAssignableFrom(cl);
  }

  /**
   * Returns the declared methods
   */
  public JMethod []getDeclaredMethods()
  {
    return getRawClass().getDeclaredMethods();
  }

  /**
   * Returns the public methods
   */
  public JMethod []getMethods()
  {
    return getRawClass().getMethods();
  }

  /**
   * Returns the matching method.
   */
  public JMethod getMethod(String name, JClass []param)
  {
    return getRawClass().getMethod(name, param);
  }

  /**
   * Returns the declared fields
   */
  public JField []getDeclaredFields()
  {
    return getRawClass().getDeclaredFields();
  }

  /**
   * Returns the fields
   */
  public JField []getFields()
  {
    return getRawClass().getFields();
  }

  private JClass getRawClass()
  {
    return _loader.forName(((Class) _type.getRawType()).getName());
  }
}
