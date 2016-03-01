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

package io.baratine.convert;

import io.baratine.service.Result;

/**
 * Convert from source to target.
 * 
 * Async converters must be called with the Result api.
 */
public interface ConvertManager
{
  <S,T> Convert<S,T> converter(Class<S> source, Class<T> target);
  
  /**
   * Convert using only sync converters
   * 
   * @param targetType the expected type of the target
   * @param source the source value
   * 
   * @return the converted value
   */
  default <S,T> T convert(Class<T> targetType, S source)
  {
    if (source == null) {
      return null;
    }
    else {
      Class<S> sourceType = (Class<S>) source.getClass();
    
      return converter(sourceType, targetType).convert(source);
    }
  }
  
  /**
   * Convert using sync or async converters.
   */
  default <S,T> void convert(Class<T> targetType, S source, Result<T> result)
  {
    if (source == null) {
      result.ok(null);
    }
    else {
      Class<S> sourceType = (Class<S>) source.getClass();
  
      converter(sourceType, targetType).convert(source, result);
    }
  }
  
  default <S> ConvertManagerType<S> to(Class<S> type)
  {
    throw new UnsupportedOperationException(); 
  }
}
