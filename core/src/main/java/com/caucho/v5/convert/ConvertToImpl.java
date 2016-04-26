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

import com.caucho.v5.util.L10N;

import io.baratine.convert.Convert;
import io.baratine.convert.ConvertManager;
import io.baratine.convert.ConvertTo;

/**
 * Converter to a known target
 */
class ConvertToImpl<T> implements ConvertTo<T>
{
  private static final L10N L = new L10N(ConvertToImpl.class);
  
  private final ConvertManager _manager;
  private final Class<T> _target;
  
  private ConvertToMap<T> _convertMap = new ConvertToMap<>();
  
  public ConvertToImpl(ConvertManager manager,
                       Class<T> target)
  {
    _manager = manager;
    _target = target;
  }

  @Override
  public Class<T> targetType()
  {
    return _target;
  }

  @Override
  public <S> Convert<S, T> converter(Class<S> source)
  {
    return (Convert) _convertMap.get(source);
  }
  
  class ConvertToMap<T> extends ClassValue<Convert<?,T>>
  {
    @Override
    protected Convert<?, T> computeValue(Class<?> source)
    {
      return (Convert) _manager.converter(source, _target);
    }
    
  }
}
