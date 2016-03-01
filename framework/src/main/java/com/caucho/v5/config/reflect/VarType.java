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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Set;

import com.caucho.v5.util.ModulePrivate;

/**
 * class type matching
 */
@ModulePrivate
public class VarType<D extends GenericDeclaration> extends BaseType
  implements TypeVariable<D>
{
  private String _name;
  private BaseType []_bounds;

  public VarType(String name, BaseType []bounds)
  {
    _name = name;
    _bounds = bounds;
  }

  @Override
  public String getName()
  {
    return _name;
  }

  @Override
  public D getGenericDeclaration()
  {
    return (D) new GenericDeclarationImpl();
  }

  @Override
  public boolean isWildcard()
  {
    // ioc/024j vs ioc/024k
    return false;
  }
  
  @Override
  public boolean isGeneric()
  {
    return true;
  }
  
  @Override
  public boolean isVariable()
  {
    return true;
  }

  @Override
  public Type []getBounds()
  {
    Type []bounds = new Type[_bounds.length];

    for (int i = 0; i < bounds.length; i++) {
      bounds[i] = _bounds[i].toType();
    }

    return bounds;
  }
  
  @Override
  protected BaseType []getWildcardBounds()
  {
    return _bounds;
  }
  
  @Override
  public Class<?> getRawClass()
  {
    return Object.class; // technically bounds
  }

  public Type getGenericComponentType()
  {
    return null;
  }

  @Override
  public Type toType()
  {
    return this;
  }

  @Override
  public boolean isAssignableFrom(BaseType type)
  {
    if (type.isWildcard())
      return true;
    
    for (BaseType bound : _bounds) {
      if (! bound.isAssignableFrom(type)) {
        return false;
      }
    }
    
    return true;
  }
  
  @Override
  public boolean isParamAssignableFrom(BaseType type)
  {
    // ioc/0i3m
    return isAssignableFrom(type);
  }

  @Override
  public void fillSyntheticTypes(Set<VarType<?>> varTypeList)
  {
    varTypeList.add(this);
  }

  @Override
  public int hashCode()
  {
    return 17 + 37 * _name.hashCode();
  }

  @Override
  public boolean equals(Object o)
  {
    if (o == this)
      return true;
    else if (o instanceof TypeVariable<?>) {
      // TypeVariable<?> var = (TypeVariable<?>) o;

      return true;
    }
    else
      return false;
  }

  public String toString()
  {
    if (_bounds.length == 0)
      return _name;
    
    StringBuilder sb = new StringBuilder(_name);
    
    for (BaseType type : _bounds) {
      if (! type.getRawClass().equals(Object.class))
        sb.append(" extends ").append(type);
    }
    
    return sb.toString();
  }
  
  static class GenericDeclarationImpl implements GenericDeclaration {
    @Override
    public TypeVariable<?>[] getTypeParameters()
    {
      return null;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass)
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Annotation[] getAnnotations()
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Annotation[] getDeclaredAnnotations()
    {
      // TODO Auto-generated method stub
      return null;
    }
    
  }

  @Override
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Annotation[] getAnnotations()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Annotation[] getDeclaredAnnotations()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public AnnotatedType[] getAnnotatedBounds()
  {
    // TODO Auto-generated method stub
    return null;
  }
}
