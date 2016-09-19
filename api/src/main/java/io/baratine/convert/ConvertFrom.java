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
public interface ConvertFrom<S>
{
  /**
   * Returns type of the source object
   *
   * @return class
   */
  Class<S> sourceType();

  /**
   * Returns converter supporting conversion to type &lt;T&gt;
   *
   * @param target target conversion type
   * @param <T> target type
   * @return converter for &lt;S&gt; to &lt;T&gt; convertion
   */
  <T> Convert<S, T> converter(Class<T> target);
  
  /**
   * Synchronously converts source object to an instance of a target type
   * 
   * @param targetType the expected type of the target
   * @param source object to convert
   * @param <T> target type
   * @return the converted value
   */
  default <T> T convert(Class<T> targetType, S source)
  {
    return converter(targetType).convert(source);
  }
  
  /**
   * Asynchronously converts source object to an instance of a target type.
   *
   * @param targetType target type
   * @param source object to convert
   * @param result conversion result holder
   * @param <T> target type
   */
  default <T> void convert(Class<T> targetType, S source, Result<T> result)
  {
    converter(targetType).convert(source, result);
  }

  /**
   * Interface ConvertFromBuilder adds support for converting from type specified
   * as &lt;S&gt; to &lt;T&gt; type
   *
   * @param <S> type of an object to convert from
   */
  interface ConvertFromBuilder<S>
  {
    /**
     * Adds Convert from type &lt;S&gt; to &lt;T&gt;
     *
     * @param convert converter instance
     * @param <T>
     * @return this instance of ConvertFromBuilder for call chaining
     */
    <T> ConvertFromBuilder<S> add(Convert<S,T> convert);

    /**
     * Obtains instance of ConvertFrom of type &lt;S&gt;.
     *
     * @return instance of ConvertFrom
     */
    ConvertFrom<S> get();
  }
}
