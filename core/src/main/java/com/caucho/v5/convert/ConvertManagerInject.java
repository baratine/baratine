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

package com.caucho.v5.convert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.config.Priorities;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.L10N;

import io.baratine.convert.Convert;
import io.baratine.convert.ConvertManager;
import io.baratine.convert.ConvertManagerType;
import io.baratine.inject.Binding;
import io.baratine.inject.Injector;
import io.baratine.inject.Key;

/**
 * Default converter.
 */
public class ConvertManagerInject implements ConvertManager
{
  private static final L10N L = new L10N(ConvertManagerInject.class);
  
  private static ConverterIdentity<?> IDENTITY = new ConverterIdentity<>();
  
  private HashMap<Class<?>,ConvertManagerTypeImpl<?>> _convertMap
    = new HashMap<>();
  
  private Injector _injector;
  
  public ConvertManagerInject(Injector injector)
  {
    _injector = injector;
    
    init();
  }
  
  private void init()
  {
    for (ConvertManagerType<?> convertManager
        : ConvertManagerImpl.convertManagers()) {
      add(convertManager);
    }
    
    for (Binding<ConvertManagerType<?>> binding
        : _injector.bindings(new Key<ConvertManagerType<?>>() {})) {
      add(binding.provider().get());
    }
    
    for (Binding<Convert<?,?>> binding
        : _injector.bindings(new Key<Convert<?,?>>() {})) {
      TypeRef convertRef = TypeRef.of(binding.key().type())
                                  .to(Convert.class);
      Class<?> sourceType = convertRef.param(0).rawClass();
      Class<?> targetType = convertRef.param(1).rawClass();
      
      add(sourceType, targetType, (Convert) binding.provider().get());
    }
  }
  
  private <S> void add(ConvertManagerType<S> convertManager)
  {
    Class<S> sourceType = convertManager.sourceType();
    
    ConvertManagerTypeImpl<S> typeImpl = getOrCreate(sourceType);
    
    typeImpl.add(convertManager);
  }
  
  private <S,T> void add(Class<S> sourceType,
                         Class<T> targetType,
                         Convert<S,T> converter)
  {
    Objects.requireNonNull(converter);
    
    ConvertManagerTypeImpl<S> typeImpl = getOrCreate(sourceType);
    
    typeImpl.add(targetType, converter);
  }

  /**
   * Returns the converter for a given source type and target type.
   */
  @Override
  public <S, T> Convert<S, T> converter(Class<S> source, Class<T> target)
  {
    ConvertManagerType<S> convertType = getOrCreate(source);
    
    return convertType.converter(target);
  }

  /**
   * Returns the ConvertManagerTypeImpl for a given source type.
   */
  private <S> ConvertManagerTypeImpl<S> getOrCreate(Class<S> sourceType)
  {
    ConvertManagerTypeImpl<S> convertType
    = (ConvertManagerTypeImpl<S>) _convertMap.get(sourceType);
  
    if (convertType != null) {
      return convertType;
    }
    
    convertType = new ConvertManagerTypeImpl<>(sourceType);
    
    _convertMap.putIfAbsent(sourceType, convertType);

    return (ConvertManagerTypeImpl<S>) _convertMap.get(sourceType);
  }
  
  static final class ConvertManagerTypeImpl<S> implements ConvertManagerType<S>
  {
    private Class<S> _sourceType;
    private ArrayList<ConvertManagerType<S>> _delegates = new ArrayList<>();
    
    private ConcurrentHashMap<Class<?>,Convert<?,?>> _converterMap
      = new ConcurrentHashMap<>();
    
    ConvertManagerTypeImpl(Class<S> sourceType)
    {
      _sourceType = sourceType;
    }
    
    public Class<S> sourceType()
    {
      return _sourceType;
    }
    
    void add(ConvertManagerType<S> delegate)
    {
      Objects.requireNonNull(delegate);
      
      _delegates.add(delegate);
      Collections.sort(_delegates, (x,y)->Priorities.compare(x,y));
    }
    
    <T> void add(Class<T> target, Convert<S,T> converter)
    {
      Objects.requireNonNull(converter);
      
      _converterMap.put(target, converter);
    }

    @Override
    public <T> Convert<S,T> converter(Class<T> target)
    {
      Convert<S,T> converter = (Convert<S,T>) _converterMap.get(target);
      
      if (converter != null) {
        return converter;
      }
      else {
        converter = findConverter(target);
        
        _converterMap.putIfAbsent(target, converter);
        
        return (Convert<S,T>) _converterMap.get(target);
      }
    }
    
    private <T> Convert<S,T> findConverter(Class<T> target)
    {
      for (ConvertManagerType<S> delegate : _delegates) {
        Convert<S, T> convert = delegate.converter(target);
        
        if (convert != null) {
          return convert;
        }
      }
      
      if (target.isAssignableFrom(_sourceType)) {
        return (Convert) IDENTITY;
      }
      
      ConvertException exn
        = new ConvertException(L.l("Can't convert {0} to {1}",
                                   _sourceType.getName(),
                                   target.getName()));
      
      return (Convert) new ConvertWithException(exn);
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + sourceType().getSimpleName() + "]";
    }
  }
  
  static final class ConverterIdentity<T> implements Convert<T,T>
  {
    @Override
    public T convert(T source)
    {
      return source;
    }
  }
}
