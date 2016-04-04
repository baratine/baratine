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

import io.baratine.inject.Injector;

/**
 * Factory for beans from an injector.
 */
interface BeanFactory<T>
{
  T apply(Injector injector);
  
  static class BeanFactoryClass<T> implements BeanFactory<T>
  {
    private Class<? extends T> _type;
    
    BeanFactoryClass(Class<? extends T> type)
    {
      Objects.requireNonNull(type);
      
      _type = type;
    }
    
    @Override
    public T apply(Injector injector)
    {
      return injector.instance(_type);
    }
  }
  
  static class BeanFactoryAnn<T,X> implements BeanFactory<T>
  {
    private Class<? extends T> _type;
    private Annotation _ann;
    
    BeanFactoryAnn(Class<? extends T> type,
                   Annotation ann)
    {
      Objects.requireNonNull(type);
      Objects.requireNonNull(ann);
      
      _type = type;
      _ann = ann;
    }
    
    @Override
    public T apply(Injector injector)
    {
      return injector.instance(_type, _ann);
    }
    
    public String toString()
    {
      return (getClass().getSimpleName()
              + "[" + _type.getSimpleName()
              + "," + _ann.annotationType().getSimpleName() + "]");
    }
  }
}
