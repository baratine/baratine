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
 * @param <T> ConvertTo target type
 */
public interface ConvertTo<T>
{
  /**
   * Target type of this ConvertTo
   *
   * @return target type
   */
  Class<T> targetType();

  /**
   * Obtains convert capable of converting to &lt;S&gt;
   *
   * @param target coversion target type
   * @param <S>    conversion source type
   * @return Convert instance
   */
  <S> Convert<S,T> converter(Class<S> target);

  /**
   * Synchronously convert source object to an instance of type &lt;T&gt;
   *
   * @param sourceType type of the source
   * @param source     instance to convert
   * @param <S>        source type
   * @return converted instance
   */
  default <S> T convert(Class<S> sourceType, S source)
  {
    return converter(sourceType).convert(source);
  }

  /**
   * Synchronously convert source object to an instance of type &lt;T&gt;
   *
   * @param sourceType type of the source
   * @param source     instance to convert
   * @param result     convertion result holder
   * @param <S>        source type
   */
  default <S> void convert(Class<S> sourceType, S source, Result<T> result)
  {
    converter(sourceType).convert(source, result);
  }

  interface ConvertToBuilder<S>
  {
    <T> ConvertToBuilder<S> add(Convert<S,T> convert);

    ConvertTo<S> get();
  }
}
