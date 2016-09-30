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
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Qualifier;

/**
 * Key is used for binding beans and services to injection points.
 * <p>
 * Key is defined by it's associated type and annotations.
 *
 * @param <T> target type, e.g. Key&lt;Foo&gt;
 * @see io.baratine.service.Services.ServicesBuilder#service(Key, Class)
 */
public class Key<T>
{
  private static final Logger log = Logger.getLogger(Key.class.getName());

  private static final Class<? extends Annotation> []DEFAULT_ANN_TYPES
    = new Class[] { Bean.class };
  private static final Annotation []DEFAULT_ANNS
    = new Annotation[0];

  private final Type _type;
  private final Class<? extends Annotation> []_annTypes;
  private final Annotation []_anns;

  protected Key()
  {
    _type = calculateType();
    _annTypes = DEFAULT_ANN_TYPES;
    _anns = DEFAULT_ANNS;
  }

  private Key(Type type)
  {
    Objects.requireNonNull(type);

    _type = type;
    _annTypes = DEFAULT_ANN_TYPES;
    _anns = DEFAULT_ANNS;
  }

  private Key(Type type, Class<? extends Annotation> []annTypes)
  {
    Objects.requireNonNull(type);

    _type = type;

    if (annTypes.length == 0) {
      annTypes = DEFAULT_ANN_TYPES;
    }

    _annTypes = annTypes;
    _anns = DEFAULT_ANNS;
  }

  /**
   * Constructs key with a given type and annotations
   *
   * @param type target type
   * @param anns annotations
   */
  public Key(Type type, Annotation []anns)
  {
    Objects.requireNonNull(type);

    _type = type;
    _anns = anns;

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

  /**
   * Returns Key's annotations.
   *
   * @return array of associated annotations
   */
  public Annotation []annotations()
  {
    return new Annotation[0];
  }

  /**
   * Returns annotation types
   *
   * @return array of associated annotation types
   */
  public Class<? extends Annotation> []annotationTypes()
  {
    return _annTypes;
  }

  /**
   * Builds key from a Class
   *
   * @param type target class
   * @param <T>  target type
   * @return instance of a Key
   */
  public static <T> Key<T> of(Class<T> type)
  {
    return new Key<>(type);
  }

  /**
   * Builds key from a Type
   *
   * @param type target type
   * @param <T>  type
   * @return instance of a Key
   */
  public static <T> Key<T> of(Type type)
  {
    return new Key<>(type);
  }

  /**
   * Builds Key from Type and annotation types
   *
   * @param type     target type
   * @param annTypes associated annotations
   * @param <T>      type
   * @return instance of a Key
   */
  public static <T> Key<T> of(Type type, Class<? extends Annotation> []annTypes)
  {
    return new Key<>(type, annTypes);
  }

  /**
   * Builds Key from Class and annotation type
   *
   * @param type    target type
   * @param annType associated annotation
   * @param <T>     type
   * @return instance of a Key
   */
  public static <T> Key<T> of(Class<T> type,
                              Class<? extends Annotation> annType)
  {
    Objects.requireNonNull(type);
    Objects.requireNonNull(annType);

    return new Key<>(type, new Class[] { annType });
  }

  /**
   * Builds Key from a Class and annotation
   *
   * @param type target class
   * @param ann  associated annotation
   * @param <T>  target type
   * @return instance of a Key
   */
  public static <T> Key<T> of(Class<T> type,
                              Annotation ann)
  {
    Objects.requireNonNull(type);
    Objects.requireNonNull(ann);

    return new Key<>(type, new Annotation[] { ann });
  }

  /**
   * Builds Key from method's generic return type and method's qualifying
   * annotations. A qualifying annotation is marked with a {@code @Qualifier}.
   *
   * @param method method for deriving return type and annotations from
   * @param <T>    target type
   * @return instance of a Key
   */
  public static <T> Key<T> of(Method method)
  {
    return new Key(method.getGenericReturnType(),
                   qualifiers(method.getAnnotations()));
  }

  /**
   * Builds Key from constructor's declaring class and constructor's qualifying
   * annotations. A qualifying annotation is marked with a {@code @Qualifier}.
   *
   * @param ctor constructor for deriving type and annotations from
   * @param <T>  target type
   * @return instance of a Key
   */
  public static <T> Key<T> of(Constructor ctor)
  {
    return new Key(ctor.getDeclaringClass(),
                   qualifiers(ctor.getAnnotations()));
  }

  /**
   * Builds Key from field's generic type and field's qualifying annotations.
   * A qualifying annotation is marked with a {@code @Qualifier}.
   *
   * @param field field for deriving type and annotation from
   * @param <T>   target type
   * @return instance of a Key
   */
  public static <T> Key<T> of(Field field)
  {
    return new Key(field.getGenericType(),
                   qualifiers(field.getAnnotations()));
  }

  /**
   * Builds Key from parameter's parameterized type and and qualifying annotations
   * found on the parameter and its declaring executable (e.g. Method)
   *
   * @param parameter parameter for deriving type and annotations from
   * @return instance of a key
   */
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

  /**
   * Returns raw class of associated type
   *
   * @return raw class
   */
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

  /**
   * Returns associated type
   *
   * @return type
   */
  public Type type()
  {
    return _type;
  }

  /**
   * Returns associated annotation types.
   *
   * @return arary of annotation types
   */
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

  /**
   * Tests if annotation type is present in Key's annotations
   *
   * @param annTypeTest test annotation
   * @return true if present, false otherwise
   */
  public boolean isAnnotationPresent(Class<? extends Annotation> annTypeTest)
  {
    for (Class<?> annType : _annTypes) {
      if (annType.equals(annTypeTest)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Tests if key is assignable. Key is considered assignable if annotation types
   * match, Type matches and annotation instances match.
   *
   * @param key test key
   * @return true if assignable, false otherwise
   */
  public boolean isAssignableFrom(Key<? super T> key)
  {
    Objects.requireNonNull(key);

    for (Class<? extends Annotation> annType : _annTypes) {
      if (! containsType(annType, key._annTypes)) {
        return false;
      }
    }

    if (_type instanceof ParameterizedType) {
      if (! (key._type instanceof ParameterizedType)) {
        return false;
      }

      if (! isAssignableFrom((ParameterizedType) _type,
                             (ParameterizedType) key._type)) {
        return false;
      }
    }

    if (_anns.length > 0 && key._anns.length > 0) {
      return isAssignableFrom(_anns, key._anns);
    }

    return true;
  }

  private boolean isAssignableFrom(ParameterizedType typeA,
                                   ParameterizedType typeB)
  {
    Type []paramA = typeA.getActualTypeArguments();
    Type []paramB = typeB.getActualTypeArguments();

    if (paramA.length != paramB.length) {
      return false;
    }

    for (int i = 0; i < paramA.length; i++) {
      Class<?> classA = rawClass(paramA[i]);
      Class<?> classB = rawClass(paramB[i]);

      if (! classA.equals(classB)
          && ! classA.equals(Object.class)
          && ! classB.equals(Object.class)) {
        return false;
      }
    }

    return true;
  }

  private boolean isAssignableFrom(Annotation []annsA, Annotation []annsB)
  {
    for (Annotation annA : annsA) {
      if (! isAnnotation(annA, annsB)) {
        return false;
      }
    }

    return true;
  }

  private boolean isAnnotation(Annotation annA, Annotation []annsB)
  {
    for (Annotation annB : annsB) {
      if (annB.annotationType().equals(annA.annotationType())) {
        return isMatch(annA, annB);
      }
    }

    return false;
  }

  private boolean isMatch(Annotation annA, Annotation annB)
  {
    for (Method method : annA.annotationType().getMethods()) {
      if (! method.getDeclaringClass().equals(annA.annotationType())) {
        continue;
      }
      else if (method.getParameterTypes().length != 0) {
        continue;
      }
      else if (method.getName().equals("toString")) {
        continue;
      }
      else if (method.getName().equals("hashCode")) {
        continue;
      }

      try {
        Object valueA = method.invoke(annA);
        Object valueB = method.invoke(annB);

        if (valueA == valueB) {
        }
        else if (valueA == null || valueB == null) {
          return false;
        }
        else if (! valueA.equals(valueB)) {
          return false;
        }
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);

        return false;
      }
    }

    return true;
  }

  private Class<?> rawClass(Type type)
  {
    if (type instanceof Class<?>) {
      return (Class<?>) type;
    }
    else if (type instanceof ParameterizedType) {
      ParameterizedType pType = (ParameterizedType) type;

      return (Class) pType.getRawType();
    }
    else if (type instanceof WildcardType) {
      return Object.class;
    }
    else {
      throw new UnsupportedOperationException(String.valueOf(type) + " " + type.getClass().getName());
    }
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
  public int hashCode()
  {
    int hash = _type.hashCode();

    for (Class<?> annType : _annTypes) {
      hash = 65521 * hash + annType.hashCode();
    }

    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (! (obj instanceof Key)) {
      return false;
    }

    Key<?> key = (Key) obj;

    if (! _type.equals(key._type)) {
      return false;
    }

    if (_annTypes.length != key._annTypes.length) {
      return false;
    }

    // XXX: sort issues
    for (int i = 0; i < _annTypes.length; i++) {
      if (! _annTypes[i].equals(key._annTypes[i])) {
        return false;
      }
    }

    return true;
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

    if (false && _anns.length > 0) {
      for (int i = 0; i < _anns.length; i++) {
        sb.append(",");
        sb.append(_anns[i]);
      }
    }
    else {
      for (int i = 0; i < _annTypes.length; i++) {
        sb.append(",@");
        sb.append(_annTypes[i].getSimpleName());
      }
    }
    sb.append("]");

    return sb.toString();
  }
}

