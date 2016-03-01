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

package com.caucho.v5.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.util.ModulePrivate;


/**
 * Abstract introspected view of a Bean
 */
@ModulePrivate
public class ReflectUtil
{
  private static final Logger log
    = Logger.getLogger(ReflectUtil.class.getName());
  
  /**
   * Finds any method matching the method name and parameter types.
   */
  public static Method findDeclaredMethod(Class<?> cl, Method testMethod)
  {
    if (cl == null)
      return null;
    
    for (Method method : cl.getDeclaredMethods()) {
      if (isMatch(method, testMethod))
        return method;
    }
    
    return null;
  }
  
  
  /**
   * Finds any method matching the method name and parameter types.
   */
  public static Method findMethod(Method []methods, Method testMethod)
  {
    for (Method method : methods) {
      if (isMatch(method, testMethod))
        return method;
    }
    
    return null;
  }
  
  /**
   * Finds any method matching the method name and parameter types.
   */
  public static Method findMethod(Collection<Method> methods, Method testMethod)
  {
    for (Method method : methods) {
      if (isMatch(method, testMethod))
        return method;
    }
    
    return null;
  }

  /**
   * Tests if two annotated methods are equivalent.
   */
  /*
  public static boolean isMatch(AnnotatedMethod<?> methodA,
                                AnnotatedMethod<?> methodB)
  {
    if (methodA == methodB)
      return true;
    else if (methodA == null || methodB == null)
      return false;
    
    Method javaMethodA = methodA.getJavaMember();
    Method javaMethodB = methodB.getJavaMember();
    
    if (! javaMethodA.getName().equals(javaMethodB.getName()))
      return false;
    
    List<AnnotatedParameter<?>> paramListA = (List) methodA.getParameters();
    List<AnnotatedParameter<?>> paramListB = (List) methodB.getParameters();
    
    if (isMatch(paramListA, paramListB)) {
      return true;
    }
    
    return false;
  }
  */

  /**
   * Tests if an annotated method matches a name and parameter types.
   */
  /*
  public static boolean isMatch(AnnotatedMethod<?> method, 
                                String name, Class<?> []param)
  {
    Method javaMethod = method.getJavaMember();
    
    if (! javaMethod.getName().equals(name))
      return false;

    Class<?> []mparam = javaMethod.getParameterTypes();

    return isMatch(mparam, param);
  }
  */

  /**
   * Tests if an annotated method matches a name and parameter types.
   */
  public static boolean isMatch(Method javaMethod,
                                String name, Class<?> []param)
  {
    if (! javaMethod.getName().equals(name))
      return false;

    Class<?> []mparam = javaMethod.getParameterTypes();

    return isMatch(mparam, param);
  }

  /**
   * Tests if an annotated method matches a name and parameter types.
   */
  public static boolean isMatch(Method methodA, Method methodB)
  {
    if (! methodA.getName().equals(methodB.getName()))
      return false;

    return isMatch(methodA.getParameterTypes(),
                   methodB.getParameterTypes());
  }
  
  /**
   * Tests if parameters match a method's parameter types.
   */
  public static boolean isMatch(Class<?> []paramA, Class<?> []paramB)
  {
    if (paramA.length != paramB.length)
      return false;
    
    for (int i = paramA.length - 1; i >= 0; i--) {
      if (! paramA[i].equals(paramB[i]))
        return false;
    }
    
    return true;
  }

  /**
   * Tests if a method throws a checked exception.
   */
  /*
  public static boolean hasException(AnnotatedMethod<?> method, Class<?> exn)
  {
    Class<?> []methodExceptions = method.getJavaMember().getExceptionTypes();

    for (int j = 0; j < methodExceptions.length; j++) {
      if (methodExceptions[j].isAssignableFrom(exn))
        return true;
    }

    return false;
  }
  */

  public static Field []getDeclaredFields(Class<?> cl)
  {
    try {
      return cl.getDeclaredFields();
    } catch (NoClassDefFoundError e) {
      log.finer(e.toString());
      log.log(Level.FINEST, e.toString(), e);
      
      return new Field[0];
    }
  }

  public static Method []getDeclaredMethods(Class<?> cl)
  {
    try {
      return cl.getDeclaredMethods();
    } catch (NoClassDefFoundError e) {
      log.finer(e.toString());
      log.log(Level.FINEST, e.toString(), e);
      
      return new Method[0];
    }
  }

  public static Constructor<?> []getDeclaredConstructors(Class<?> cl)
  {
    try {
      return cl.getDeclaredConstructors();
    } catch (NoClassDefFoundError e) {
      log.finer(e.toString());
      log.log(Level.FINEST, e.toString(), e);
      
      return new Constructor[0];
    }
  }


  public static Method findAccessibleMethod(Method method)
  {
    method.setAccessible(true);
    
    return method;
  }

}
