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

package com.caucho.v5.inject;

import java.lang.reflect.Parameter;
import java.util.function.Consumer;

import javax.inject.Provider;

import com.caucho.v5.inject.impl.InjectManagerImpl;

import io.baratine.config.Config;
import io.baratine.convert.Convert;
import io.baratine.inject.Binding;
import io.baratine.inject.InjectManager;
import io.baratine.spi.ServiceManagerProvider;

/**
 * The injection manager for a given environment.
 */
public interface InjectManagerAmp extends InjectManager
{
  public static InjectManagerAmp current()
  {
    return current(Thread.currentThread().getContextClassLoader());
  }

  public static InjectManagerAmp current(ClassLoader classLoader)
  {
    //return ServiceManagerProvider.current().injectCurrent(classLoader);
    return InjectManagerImpl.current(classLoader);
  }

  public static InjectBuilderAmp manager(ClassLoader classLoader)
  {
    return (InjectBuilderAmp) ServiceManagerProvider.current().injectManager(classLoader);
  }

  public static InjectBuilderAmp manager()
  {
    return manager(Thread.currentThread().getContextClassLoader());
  }

  //<T> T instance(Class<T> type, Annotation ...qualifiers);

  //<T> T lookup(Class<T> type, Annotation ...qualifiers);
  
  //<T> T instanceNonService(Class<T> cl, Annotation ...qualifiers);
  
  //Object createByName(String name);

  <T> Iterable<Binding<T>> bindings(Class<T> type);
  
  Config config();
  
  <S,T> Convert<S, T> converter(Class<S> source, Class<T> target);
  
  String property(String key);
  
  //<T> Supplier<T> supplierNew(Class<T> type, Annotation ...qualifiers);
  
  //<T> Provider <T> newProvider(Class<T> type, Annotation ...qualifiers);
  
  //<T> BindingAmp<T> newBinding(Class<T> type);
  
  //void introspectInject(List<InjectProgram> program, Class<?> type);
  
  //void introspectInit(List<InjectProgram> program, Class<?> type);

  //boolean isQualifier(Annotation ann);
  
  /*
  default void scanRoot(ClassLoader classLoader)
  {
  }
  */
  
  public interface InjectBuilderAmp extends InjectBuilder
  {
    InjectBuilderAmp context(boolean isContext);
    
    InjectManagerAmp get();
  }
 
  //
  // XXX: implementation
  //

  public static InjectManagerAmp create(ClassLoader classLoader)
  {
    return InjectManagerImpl.create(classLoader);
  }

  public static InjectManagerAmp create()
  {
    return create(Thread.currentThread().getContextClassLoader());
  }

  Provider<?> []program(Parameter[] parameters);

  <T> Consumer<T> injector(Class<T> type);

}

