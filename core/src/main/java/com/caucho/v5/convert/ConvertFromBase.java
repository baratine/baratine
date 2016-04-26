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

import io.baratine.convert.Convert;
import io.baratine.convert.ConvertFrom;
import io.baratine.service.Result;

/**
 * Default string converter.
 * read-only properties map.
 */
public class ConvertFromBase<S> implements ConvertFrom<S>
{
  private final Map<Class<?>,Convert<S,?>> _converterMap
    = new HashMap<>();
  private Class<S> _sourceType;
  
  ConvertFromBase(Class<S> sourceType, Map<Class<?>,Convert<S,?>> map)
  {
    Objects.requireNonNull(sourceType);
    
    _sourceType = sourceType;
    
    _converterMap.putAll(map);
  }
  
  ConvertFromBase(ConvertFromBuilderImpl<S> builder)
  {
    _sourceType = builder.sourceType();
    
    _converterMap.putAll(builder.map());
  }
  
  @Override
  public Class<S> sourceType()
  {
    return _sourceType;
  }
  
  public Map<Class<?>,Convert<S,?>> map()
  {
    return Collections.unmodifiableMap(_converterMap);
  }

  @Override
  public <T> Convert<S,T> converter(Class<T> targetType)
  {
    return (Convert) _converterMap.get(targetType);
  }

  @Override
  public <T> T convert(Class<T> targetType, S source)
  {
    Convert<S,T> convert = converter(targetType);
    
    if (convert != null) {
      return convert.convert(source);
    }
    else {
      return null;
    }
  }

  @Override
  public <T> void convert(Class<T> targetType, S source, Result<T> result)
  {
    result.ok(convert(targetType, source));
  }
}
