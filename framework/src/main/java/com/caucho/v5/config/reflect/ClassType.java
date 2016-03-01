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

package com.caucho.v5.config.reflect;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.caucho.v5.util.ModulePrivate;

/**
 * class type matching
 */
@ModulePrivate
public class ClassType extends BaseType
{
  public static final ObjectType OBJECT_TYPE;
  
  private static final HashMap<Class<?>,ClassType> _classTypeMap
    = new HashMap<Class<?>,ClassType>();
  
  private static final HashSet<Class<?>> _classTypeIgnoreSet
    = new HashSet<Class<?>>();
    
  private Class<?> _type;

  protected ClassType(Class<?> type)
  {
    // ioc/0706
    /*
    Class<?> boxType = _boxTypeMap.get(type);

    if (boxType != null)
      type = boxType;
      */
    
    _type = type;
  }
  
  public static ClassType create(Class<?> type)
  {
    ClassType classType = _classTypeMap.get(type);
    
    if (classType != null)
      return classType;
    else
      return new ClassType(type);
  }
  
  @Override
  public Class<?> getRawClass()
  {
    return _type;
  }
  
  @Override
  public Type toType()
  {
    return _type;
  }
 
  @Override
  public boolean isAssignableFrom(BaseType type)
  {
    if (type.isWildcard() || type.isVariable()) {
      for (BaseType bound : type.getWildcardBounds()) {
        if (! isAssignableFrom(bound))
          return false;
      }
      
      return true;
    }
    else if (! _type.isAssignableFrom(type.getRawClass()))
      return false;
    else if (type.getParameters().length > 0) {
      for (BaseType param : type.getParameters()) {
        if (! OBJECT_TYPE.isParamAssignableFrom(param))
          return false;
      }

      return true;
    }
    else
      return true;
  }

  @Override
  public boolean isParamAssignableFrom(BaseType type)
  {
    // ioc/024d
    if (type.isWildcard() || type.isVariable()) {
      // ioc/0i3q
      boolean isMatchBound = true; // false;

      for (BaseType bound : type.getWildcardBounds()) {
        // ioc/024d
        if (bound.isObject())
          continue;
        
        // if (! isAssignableFrom(bound))
        //  return false;
        
        // ioc/2601
        if (! bound.isAssignableFrom(this))
          return false;
        
        // ioc/024n        
        
        isMatchBound = true;
      }
      
      return isMatchBound;
    }
    else if (_type.equals(type.getRawClass()))
      return true;
    else
      return false;
  }

  /**
   * Fills in a parameter with a given name.
   */
  @Override
  public BaseType fill(BaseType ... baseType)
  {
    // ioc/1238
    return this;
  }
   
  @Override
  public void fillTypeClosure(BaseTypeFactory manager, Set<Type> typeSet)
  {
    Type ownType = toType();
    
    if (_classTypeIgnoreSet.contains(ownType))
      return;
    
    typeSet.add(ownType);
    
    for (Type type : _type.getGenericInterfaces()) {
      BaseType ifaceType = manager.createForSource(type);
      
      ifaceType.fillTypeClosure(manager, typeSet);
    }

    Type superclass = _type.getGenericSuperclass();

    if (superclass != null) {
      BaseType superType = manager.createForSource(superclass);
      
      superType.fillTypeClosure(manager, typeSet);
    }
    else if (_type.isInterface()) {
      typeSet.add(Object.class);
    }
  }

  @Override
  public int hashCode()
  {
    return _type.hashCode();
  }

  @Override
  public boolean equals(Object o)
  {
    if (o == this)
      return true;
    else if (! (o instanceof ClassType))
      return false;

    ClassType type = (ClassType) o;

    return _type.equals(type._type);
  }

  @Override
  public String toString()
  {
    return getRawClass().toString();
  }

  static {
    _classTypeMap.put(boolean.class, new BoxType(boolean.class, Boolean.class));
    _classTypeMap.put(Boolean.class, new ClassType(Boolean.class));
    
    _classTypeMap.put(char.class, new BoxType(char.class, Character.class));
    _classTypeMap.put(Character.class, new ClassType(Character.class));
    
    _classTypeMap.put(byte.class, new BoxType(byte.class, Byte.class));
    _classTypeMap.put(Byte.class, new ClassType(Byte.class));
    
    _classTypeMap.put(short.class, new BoxType(short.class, Short.class));
    _classTypeMap.put(Short.class, new ClassType(Short.class));
    
    _classTypeMap.put(int.class, new BoxType(int.class, Integer.class));
    _classTypeMap.put(Integer.class, new ClassType(Integer.class));
    
    _classTypeMap.put(long.class, new BoxType(long.class, Long.class));
    _classTypeMap.put(Long.class, new ClassType(Long.class));
    
    _classTypeMap.put(float.class, new BoxType(float.class, Float.class));
    _classTypeMap.put(Float.class, new ClassType(Float.class));
    
    _classTypeMap.put(double.class, new BoxType(double.class, Double.class));
    _classTypeMap.put(Double.class, new ClassType(Double.class));
    
    _classTypeMap.put(String.class, new ClassType(String.class));
    
    OBJECT_TYPE = ObjectType.OBJECT_TYPE;
    _classTypeMap.put(Object.class, OBJECT_TYPE);
    
    //_classTypeIgnoreSet.add(Serializable.class);
    _classTypeIgnoreSet.add(Cloneable.class);
  }
}
