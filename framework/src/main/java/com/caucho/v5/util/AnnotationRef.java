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

package com.caucho.v5.util;

import java.io.Serializable;
import java.lang.annotation.*;
import java.lang.ref.SoftReference;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convenience API to create runtime Annotations.
 *
 * <code><pre>
 * Annotation current = new AnnotationLiteral&lt;Current>() {}
 * </pre></code>
 *
 * <code><pre>
 * Annotation named = new AnnotationLiteral&lt;Named>() {
 *   public String name() { return "my-name"; }
 * }
 * </pre></code>
 */
public abstract class AnnotationRef<T extends Annotation>
  implements Annotation, Serializable
{
  private static final Logger log = Logger.getLogger(AnnotationRef.class.getName());
  
  private static WeakHashMap<Class<?>,SoftReference<MethodMatch[]>> _annMap
    = new WeakHashMap<Class<?>,SoftReference<MethodMatch[]>>();
  
  private transient Class<T> _annotationType;
  private transient MethodMatch [] _methods;
  private transient int _hashCode;
  
  protected AnnotationRef()
  {
  }
  
  @Override
  public java.lang.Class<? extends java.lang.annotation.Annotation> annotationType()
  {
    if (_annotationType == null) {
      fillAnnotationType(getClass());
    }
    
    return _annotationType;
  }
  
  private void fillAnnotationType(Class<?> cl)
  {
    if (cl == null)
      throw new UnsupportedOperationException(getClass().toString());
      
    Type type = cl.getGenericSuperclass();

    if (type instanceof ParameterizedType) {
      ParameterizedType pType = (ParameterizedType) type;

      _annotationType = (Class) pType.getActualTypeArguments()[0];
    }
    else {
      fillAnnotationType(cl.getSuperclass());
    }
  }
  
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    else if (! (o instanceof Annotation))
      return false;

    Class<?> annTypeA = annotationType();
    Class<?> annTypeB = ((Annotation) o).annotationType();
    
    if (! annTypeA.equals(annTypeB))
      return false;
    
    for (MethodMatch annMethod : getMethods()) {
      try {
        if (! annMethod.invokeMatch(this, o))
          return false;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    
    return true;
  }
  
  @Override
  public int hashCode()
  {
    if (_hashCode != 0)
      return _hashCode;
    
    int hash = 0;
    
    for (MethodMatch annMethod : getMethods()) {
      try {
        hash += (127 * annMethod.getName().hashCode()) ^ annMethod.hashCode(this);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    
    _hashCode = hash;

    return hash;
  }
  
  private MethodMatch []getMethods()
  {
    if (_methods == null) {
      Class<?> annType = annotationType();
      
      SoftReference<MethodMatch[]> matchListRef = _annMap.get(annType);
      
      if (matchListRef != null) {
        MethodMatch []matchArray = matchListRef.get();
        
        if (matchArray != null)
          return matchArray;
      }
      
      ArrayList<MethodMatch> matchList = new ArrayList<MethodMatch>();
      
      for (Method method : annType.getDeclaredMethods()) {
        if (method.getParameterTypes().length > 0 
            || method.getDeclaringClass() == Annotation.class
            || method.getDeclaringClass() == Object.class) {
          continue;
        }
        
        MethodMatch match;
        
        if (method.getReturnType().isArray())
          match = new ArrayMethodMatch(method);
        else
          match = new MethodMatch(method);
        
        matchList.add(match);
      }
      
      MethodMatch []methods = new MethodMatch[matchList.size()];
      matchList.toArray(methods);
      
      if (methods.length > 0 && ! annType.isAssignableFrom(getClass())) {
        throw new IllegalStateException("Annotation literal '" + getClass()
                                        + "' must implement '" + annType.getName()
                                        + "' because it has member values.");
      }
      
      _annMap.put(annType, new SoftReference<MethodMatch[]>(methods));
      
      _methods = methods;
    }
    
    return _methods;
  }

  @Override
  public String toString()
  {
    return "@" + annotationType().getName() + "()";
  }
  
  private static class MethodMatch {
    private final Method _method;
    
    MethodMatch(Method method)
    {
      _method = method;
      method.setAccessible(true);
    }
    
    public String getName()
    {
      return _method.getName();
    }

    public final boolean invokeMatch(Object a, Object b) 
      throws IllegalArgumentException,
             IllegalAccessException,
             InvocationTargetException
    {
      return isMatch(_method.invoke(a), _method.invoke(b));
    }
    
    public boolean isMatch(Object a, Object b)
    {
      return (a == b || a != null && a.equals(b));
    }
    
    public final int hashCode(Object o)
    {
      try {
        Object value = _method.invoke(o);

        if (value != null)
          return valueHashCode(value);
        else
          return 0;
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
        
        return 0;
      }
    }
    
    public int valueHashCode(Object value)
    {
      return value.hashCode();
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _method + "]";
    }
  }
  
  private static class ArrayMethodMatch extends MethodMatch {
    ArrayMethodMatch(Method method)
    {
      super(method);
    }
    
    @Override
    public boolean isMatch(Object a, Object b)
    {
      Object []arrayA = (Object []) a;
      Object []arrayB = (Object []) b;
      
      if (arrayA == arrayB)
        return true;
      else if (arrayA == null || arrayB == null)
        return false;
      
      if (arrayA.length != arrayB.length)
        return false;
      
      for (Object valueA : arrayA) {
        if (! isMatch(valueA, arrayB))
          return false;
      }
      
      return true;
    }
    
    private boolean isMatch(Object aValue, Object []bArray)
    {
      for (Object bValue : bArray) {
        if (aValue == bValue)
          return true;
        else if (aValue != null && aValue.equals(bValue))
          return true;
      }
      
      return false;
    }

    @Override
    public int valueHashCode(Object value)
    {
      Object []array = (Object []) value;
      
      return Arrays.hashCode(array);
    }
  }
}
