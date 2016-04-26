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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.caucho.v5.inject.type.TypeRef;

import io.baratine.convert.Convert;
import io.baratine.convert.ConvertFrom;
import io.baratine.convert.ConvertFrom.ConvertFromBuilder;

/**
 * Default string converter.
 * read-only properties map.
 */
public class ConvertFromBuilderImpl<S> implements ConvertFromBuilder<S>
{
  private Class<S> _sourceType;
  private Map<Class<?>,Convert<S,?>> _converterMap
    = new HashMap<>();
  
  ConvertFromBuilderImpl(Class<S> sourceType)
  {
    _sourceType = sourceType;
  }

  public Class<S> sourceType()
  {
    return _sourceType;
  }

  public void add(ConvertFromBase<S> base)
  {
    _converterMap.putAll(base.map());
  }
  
  @Override
  public <T> ConvertFromBuilder<S> add(Convert<S,T> convert)
  {
    Objects.requireNonNull(convert);
    
    TypeRef typeRef = TypeRef.of(convert.getClass());
    TypeRef convertRef = typeRef.to(Convert.class);
    TypeRef targetRef = convertRef.param(0);
    
    _converterMap.put(targetRef.rawClass(), convert);

    return this;
  }

  @Override
  public ConvertFrom<S> get()
  {
    return new ConvertFromBase<>(this);
  }

  public Map<Class<?>, Convert<S,?>> map()
  {
    return Collections.unmodifiableMap(_converterMap);
  }
}
