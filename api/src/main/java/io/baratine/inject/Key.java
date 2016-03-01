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

package io.baratine.inject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Objects;

import javax.inject.Qualifier;

/**
 * Type and annotation key
 */
public class Key<T>
{
  private static final Class<? extends Annotation> []DEFAULT_ANN_TYPES
    = new Class[] { Bean.class };
  
  private final Type _type;
  private final Class<? extends Annotation> []_annTypes;
  
  protected Key()
  {
    _type = calculateType();
    _annTypes = DEFAULT_ANN_TYPES;
  }
  
  private Key(Type type)
  {
    Objects.requireNonNull(type);
    
    _type = type;
    _annTypes = DEFAULT_ANN_TYPES;
  }
  
  private Key(Type type, Class<? extends Annotation> []annTypes)
  {
    Objects.requireNonNull(type);
    
    _type = type;
    
    if (annTypes.length == 0) {
      annTypes = DEFAULT_ANN_TYPES;
    }
    
    _annTypes = annTypes;
  }
  
  public Key(Type type, Annotation []anns)
  {
    Objects.requireNonNull(type);
    
    _type = type;
    
    if (anns == null || anns.length == 0) {
      _annTypes = DEFAULT_ANN_TYPES;
      return;
    }
    
    ArrayList<Class<? extends Annotation>> annTypeList = new ArrayList<>();
    
    for (Annotation ann : anns) {
      if (isQualifier(ann.annotationType())) {
        annTypeList.add(ann.annotationType());
      }
    }
    
    if (annTypeList.size() == 0) {
      _annTypes = DEFAULT_ANN_TYPES;
      return;
    }
    
    Class<? extends Annotation> []annTypes = new Class[annTypeList.size()];
    annTypeList.toArray(annTypes);
    
    _annTypes = annTypes;
  }

  public Annotation []annotations()
  {
    return new Annotation[0];
  }

  public static <T> Key<T> of(Class<T> type)
  {
    return new Key<>(type);
  }

  public static <T> Key<T> of(Type type)
  {
    return new Key<>(type);
  }
  
  public static <T> Key<T> of(Class<T> type, 
                              Class<? extends Annotation> annType)
  {
    Objects.requireNonNull(type);
    Objects.requireNonNull(annType);
    
    return new Key<>(type, new Class[] { annType });
  }
  
  public static <T> Key<T> of(Class<T> type, 
                              Annotation ann)
  {
    Objects.requireNonNull(type);
    Objects.requireNonNull(ann);
    
    return new Key<>(type, new Class[] { ann.annotationType() });
  }
  
  public static <T> Key<T> of(Method method)
  {
    return new Key(method.getGenericReturnType(), 
                   qualifiers(method.getAnnotations()));
  }
  
  public static <T> Key<T> of(Constructor ctor)
  {
    return new Key(ctor.getDeclaringClass(), 
                   qualifiers(ctor.getAnnotations()));
  }
  
  public static <T> Key<T> of(Field field)
  {
    return new Key(field.getGenericType(), 
                   qualifiers(field.getAnnotations()));
  }

  public static Key<?> of(Parameter parameter)
  {
    return new Key(parameter.getParameterizedType(), 
                   qualifiers(parameter.getAnnotations(),
                              parameter.getDeclaringExecutable().getAnnotations()));
  }
  
  private static Annotation []qualifiers(Annotation []anns)
  {
    ArrayList<Annotation> qualifierList = new ArrayList<>();
    
    for (Annotation ann : anns) {
      if (isQualifier(ann.annotationType())) {
        qualifierList.add(ann);
      }
    }
    
    Annotation []qualifiers = new Annotation[qualifierList.size()];
    qualifierList.toArray(qualifiers);
    
    return qualifiers;
  }
  
  private static Annotation []qualifiers(Annotation []anns,
                                         Annotation []defaultAnns)
  {
    ArrayList<Annotation> qualifierList = new ArrayList<>();
    
    for (Annotation ann : anns) {
      if (isQualifier(ann.annotationType())) {
        qualifierList.add(ann);
      }
    }
    
    if (qualifierList.size() == 0) {
      for (Annotation ann : defaultAnns) {
        if (isQualifier(ann.annotationType())) {
          qualifierList.add(ann);
        }
      }
    }
    
    Annotation []qualifiers = new Annotation[qualifierList.size()];
    qualifierList.toArray(qualifiers);
    
    return qualifiers;
  }
  
  private static boolean isQualifier(Class<? extends Annotation> annType)
  {
    return annType.isAnnotationPresent(Qualifier.class);
  }
  
  public Class<T> rawClass()
  {
    Type type = type();
    
    if (type instanceof Class) {
      return (Class) type;
    }
    else if (type instanceof ParameterizedType) {
      ParameterizedType pType = (ParameterizedType) type;
      
      return (Class) pType.getRawType();
    }
    else {
      throw new UnsupportedOperationException(type + " " + type.getClass().getName());
    }
  }
  
  public Type type()
  {
    return _type;
  }

  public Class[] qualifiers()
  {
    return _annTypes;
  }

  private Type calculateType()
  {
    Type type = getClass().getGenericSuperclass();

    if (type instanceof Class<?>) {
      return type;
    }
    else if (type instanceof ParameterizedType) {
      ParameterizedType pType = (ParameterizedType) type;
      
      return pType.getActualTypeArguments()[0];
    }
    else {
      throw new UnsupportedOperationException(type + " " + type.getClass().getName());
    }
  }

  public boolean isAnnotationPresent(Class<? extends Annotation> annTypeTest)
  {
    for (Class<?> annType : _annTypes) {
      if (annType.equals(annTypeTest)) {
        return true;
      }
    }

    return false;
  }

  public boolean isAssignableFrom(Key<? super T> key)
  {
    Objects.requireNonNull(key);

    for (Class<? extends Annotation> annType : _annTypes) {
      if (! containsType(annType, key._annTypes)) {
        return false;
      }
    }

    return true;
  }
  
  private boolean containsType(Class<? extends Annotation> annType,
                               Class<? extends Annotation> []annTypeSet)
  {
    for (Class<? extends Annotation> annTypeSource : annTypeSet) {
      if (annTypeSource.equals(annType)) {
        return true;
      }
    }
    
    return false;
  }
  
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append(getClass().getSimpleName());
    sb.append("[");
    
    Type type = type();
    if (type instanceof Class<?>) {
      sb.append(((Class<?>) type).getSimpleName());
    }
    else {
      sb.append(type);
    }
    
    for (int i = 0; i < _annTypes.length; i++) {
      sb.append(",@");
      sb.append(_annTypes[i].getSimpleName());
    }
    sb.append("]");
    
    return sb.toString();
  }
}

