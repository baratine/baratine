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

package com.caucho.v5.web.builder;

import java.lang.annotation.Annotation;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import io.baratine.inject.InjectManager.BindingBuilder;
import io.baratine.inject.InjectManager.InjectBuilder;
import io.baratine.inject.InjectManager.ScopeBuilder;
import io.baratine.inject.Key;
import io.baratine.service.ServiceRef;

public interface WebInjectBuilder extends InjectBuilder
{
  WebInjectBuilder inject(Consumer<WebInjectBuilder> injector);
  WebInjectBuilder inject(Class<? extends Consumer<WebInjectBuilder>> type);
  WebInjectBuilder inject(Key<? extends Consumer<WebInjectBuilder>> type);
  WebInjectBuilder inject(String scriptName);
  
  <T> BindingBuilder2<T> bind(Class<T> type);
  
  interface BindingBuilder2<T> extends BindingBuilder<T>
  {
    <U extends T> InitBuilder<U,T> to(Class<U> impl);
    <U extends T> InitBuilder<U,T> to(Key<U> alias);
    //<U extends T> InitBuilder<U,T> to(Supplier<? extends T> factory);
  }
  
  interface InitBuilder<U extends T,T> extends ScopeBuilder2
  {
    <V extends T> InitBuilder<V,T> init(Function<V,U> init);
    
    InitServiceBuilder<U> service();
    <V extends T> InitServiceBuilder<V> service(Class<V> api);
  }
  
  interface InitServiceBuilder<T> extends ScopeBuilder2
  {
    InitServiceBuilder<T> workers();
    InitServiceBuilder<T> workers(int max);
    
    InitServiceBuilder<T> address(String address);
    InitServiceBuilder<T> addressClass();
    InitServiceBuilder<T> addressClass(String prefix);
    
    InitServiceBuilder<T> init(BiConsumer<T,ServiceRef> init);
    
    InitServiceBuilder<T> subscribe(String address);
    InitServiceBuilder<T> subscribe(String prefix, Class<?> type);
    InitServiceBuilder<T> consume(String address);
    InitServiceBuilder<T> consume(String prefix, Class<?> type);
  }
  
  interface ScopeBuilder2 extends ScopeBuilder
  {
    void in(Class<? extends Annotation> type);
    void singleton();
    void dependent();
  }
  
  interface InjectGenerator extends Consumer<WebInjectBuilder>
  {
  }
}
