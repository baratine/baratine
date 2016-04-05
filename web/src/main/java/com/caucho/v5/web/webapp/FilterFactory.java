/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

package com.caucho.v5.web.webapp;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.TriFunction;
import com.caucho.v5.web.builder.WebBuilderAmp;

import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Injector;
import io.baratine.inject.Key;
import io.baratine.web.RouteBuilder;

/**
 * Factory for beans from an injector.
 */
interface FilterFactory<T>
{
  T apply(RouteBuilderAmp builder);
  
  static class BeanFactoryClass<T> implements FilterFactory<T>
  {
    private Class<? extends T> _type;
    
    BeanFactoryClass(Class<? extends T> type)
    {
      Objects.requireNonNull(type);
      
      _type = type;
    }
    
    @Override
    public T apply(RouteBuilderAmp builder)
    {
      return builder.webBuilder().injector().instance(_type);
    }
  }
  
  static class BeanFactoryAnn<T,X extends Annotation>
    implements FilterFactory<T>
  {
    private Class<T> _type;
    private X _annotation;
    private InjectionPoint<?> _ip;
    
    BeanFactoryAnn(Class<T> type,
                   X annotation,
                   InjectionPoint<?> ip)
    {
      Objects.requireNonNull(type);
      Objects.requireNonNull(annotation);
      Objects.requireNonNull(ip);
      
      _type = type;
      _annotation = annotation;
      _ip = ip;
      
    }
    
    @Override
    public T apply(RouteBuilderAmp builder)
    {
      Injector injector = builder.webBuilder().injector();
      
      TypeRef triFunRef = TypeRef.of(TriFunction.class, 
                                     _annotation.annotationType(),
                                     InjectionPoint.class,
                                     RouteBuilderAmp.class,
                                     _type);
      
      Key<?> triFunKey = Key.of(triFunRef.type());
      
      TriFunction<X,InjectionPoint<?>,RouteBuilderAmp,T> triFun
        = (TriFunction) injector.instance(triFunKey);
      
      if (triFun != null) {
        return triFun.apply(_annotation, _ip, builder);
      }
      
      TypeRef biFunRef = TypeRef.of(BiFunction.class, 
                                  _annotation.annotationType(),
                                  InjectionPoint.class,
                                  _type);
      
      Key<?> biFunKey = Key.of(biFunRef.type());
      
      BiFunction<X,InjectionPoint<?>,T> biFun
        = (BiFunction) injector.instance(biFunKey);
      
      if (biFun != null) {
        return biFun.apply(_annotation, _ip);
      }
      
      TypeRef funRef = TypeRef.of(Function.class, 
                                  _annotation.annotationType(),
                                  _type);
      
      Key<?> key = Key.of(funRef.type());
      
      Function<X,T> fun = (Function) injector.instance(key);
      
      if (fun != null) {
        return fun.apply(_annotation);
      }
      
      return null;
    }
    
    public String toString()
    {
      return (getClass().getSimpleName()
              + "[" + _ip + "]");
    }
  }
}
