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

import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Wrapper around the java Class for a JClass.
 */
public class JClassWrapper extends JClass {
  private JClassLoader _loader;
  
  private Class _class;

  private SoftReference<JMethod[]> _declaredMethodsRef;

  public JClassWrapper(Class cl, JClassLoader loader)
  {
    _loader = loader;
    
    _class = cl;
  }

  public static JClassWrapper create(Class cl)
  {
    return new JClassWrapper(cl, JClassLoaderWrapper.create());
  }

  JClassWrapper(Class cl)
  {
    _class = cl;
  }

  /**
   * Returns the class name.
   */
  public String getName()
  {
    return _class.getName();
  }

  /**
   * Returns the Java class.
   */
  @Override
  public Class getJavaClass()
  {
    return _class;
  }

  /**
   * Returns the class.
   */
  public Class getWrappedClass()
  {
    return _class;
  }

  /**
   * Returns the loader.
   */
  public JClassLoader getClassLoader()
  {
    if (_loader != null)
      return _loader;
    else
      return JClassLoader.getSystemClassLoader();
  }
  
  /**
   * Returns true for a primitive class.
   */
  public boolean isPrimitive()
  {
    return _class.isPrimitive();
  }
  
  /**
   * Returns true for a primitive class.
   */
  public boolean isArray()
  {
    return _class.isArray();
  }
  
  /**
   * Returns the component type for a primitive class.
   */
  public JClass getComponentType()
  {
    return getClassLoader().forName(_class.getComponentType().getName());
  }
  
  /**
   * Returns true for a public class.
   */
  public boolean isPublic()
  {
    return Modifier.isPublic(_class.getModifiers());
  }
  
  /**
   * Returns true for an abstract class
   */
  public boolean isAbstract()
  {
    return Modifier.isAbstract(_class.getModifiers());
  }
  
  /**
   * Returns true for a final class
   */
  public boolean isFinal()
  {
    return Modifier.isFinal(_class.getModifiers());
  }
  
  /**
   * Returns true for an interface
   */
  public boolean isInterface()
  {
    return _class.isInterface();
  }

  /**
   * Returns true for assignability.
   */
  public boolean isAssignableTo(Class cl)
  {
    return cl.isAssignableFrom(_class);
  }

  /**
   * Returns true for assignability.
   */
  public boolean isAssignableFrom(Class cl)
  {
    return _class.isAssignableFrom(cl);
  }

  /**
   * Returns true for assignability.
   */
  public boolean isAssignableFrom(JClass cl)
  {
    return cl.isAssignableTo(_class);
  }
  
  /**
   * Returns the superclass
   */
  public JClass getSuperClass()
  {
    Class cl = _class.getSuperclass();

    if (cl != null)
      return _loader.forName(cl.getName());
    else
      return null;
  }
  
  /**
   * Returns the superclass
   */
  public JClass []getInterfaces()
  {
    Class []cl = _class.getInterfaces();

    JClass []clList = new JClass[cl.length];

    for (int i = 0; i < cl.length; i++)
      clList[i] = _loader.forName(cl[i].getName());

    return clList;
  }
    
  /**
   * Returns the declared methods.
   */
  public JMethod []getDeclaredMethods()
  {
    SoftReference<JMethod[]> jMethodsRef = _declaredMethodsRef;
    JMethod []jMethods = null;

    if (jMethodsRef != null) {
      jMethods = jMethodsRef.get();
      if (jMethods != null)
        return jMethods;
    }

    Method []methods = _class.getDeclaredMethods();
    
    jMethods = new JMethod[methods.length];

    for (int i = 0; i < methods.length; i++) {
      jMethods[i] = new JMethodWrapper(methods[i], getClassLoader());
    }

    _declaredMethodsRef = new SoftReference<JMethod[]>(jMethods);

    return jMethods;
  }
    
  /**
   * Returns the public methods.
   */
  public JMethod []getMethods()
  {
    Method []methods = _class.getMethods();
    
    JMethod []jMethods = new JMethod[methods.length];

    for (int i = 0; i < methods.length; i++) {
      jMethods[i] = new JMethodWrapper(methods[i], getClassLoader());
    }

    return jMethods;
  }

  /**
   * Returns the matching methods.
   */
  public JMethod getMethod(String name, JClass []types)
  {
    JClassLoader jClassLoader = getClassLoader();
    
    return getMethod(_class, name, types, jClassLoader);
  }

  private static JMethod getMethod(Class cl, String name, JClass []types,
                                   JClassLoader jClassLoader)
  {
    if (cl == null)
      return null;
    
    loop:
    for (Method method : cl.getDeclaredMethods()) {
      if (! method.getName().equals(name))
        continue;

      Class []paramTypes = method.getParameterTypes();
      if (types.length != paramTypes.length)
        continue;

      for (int i = 0; i < types.length; i++) {
        if (! types[i].getName().equals(paramTypes[i].getName()))
          continue loop;
      }

      return new JMethodWrapper(method, jClassLoader);
    }

    for (Class ifc : cl.getInterfaces()) {
      JMethod method = getMethod(ifc, name, types, jClassLoader);

      if (method != null)
        return method;
    }

    return getMethod(cl.getSuperclass(), name, types, jClassLoader);
  }
    
  /**
   * Returns the public constructors.
   */
  public JMethod []getConstructors()
  {
    Constructor []methods = _class.getConstructors();
    
    JMethod []jMethods = new JMethod[methods.length];

    for (int i = 0; i < methods.length; i++) {
      jMethods[i] = new JConstructorWrapper(methods[i], getClassLoader());
    }

    return jMethods;
  }
    
  /**
   * Returns the declared methods.
   */
  public JField []getDeclaredFields()
  {
    Field []fields = _class.getDeclaredFields();
    
    JField []jFields = new JField[fields.length];

    for (int i = 0; i < fields.length; i++) {
      jFields[i] = new JFieldWrapper(fields[i], getClassLoader());
    }

    return jFields;
  }
    
  /**
   * Returns the declared methods.
   */
  public JField []getFields()
  {
    Field []fields = _class.getFields();
    
    JField []jFields = new JField[fields.length];

    for (int i = 0; i < fields.length; i++) {
      jFields[i] = new JFieldWrapper(fields[i], getClassLoader());
    }

    return jFields;
  }
  
  /**
   * Returns the annotation.
   */
  @Override
  public JAnnotation getAnnotation(String name)
  {
    Annotation []ann = _class.getAnnotations();

    for (int i = 0; i < ann.length; i++) {
      if (ann[i].annotationType().getName().equals(name))
        return new JAnnotationWrapper(ann[i]);
    }

    return null;
  }
  
  /**
   * Returns the annotation.
   */
  @Override
  public JAnnotation []getDeclaredAnnotations()
  {
    Annotation []ann = _class.getAnnotations();

    JAnnotation []jAnn = new JAnnotation[ann.length];

    for (int i = 0; i < ann.length; i++) {
      jAnn[i] = new JAnnotationWrapper(ann[i]);
    }

    return jAnn;
  }
  
  public String toString()
  {
    return "JClassWrapper[" + getName() + "]";
  }
}
