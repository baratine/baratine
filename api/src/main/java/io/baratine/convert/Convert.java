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
 * <p>
 * Async converters must be called with the Result api. Converters that could
 * potentially block must true with isAsync method.
 * method.
 * <p>
 * e.g.
 * <p>
 * <pre>
 *   <code>
 *   public class StringToMyBeanConverter implements Convert&lth;String,MyBean> {
 *     public MyBean convert(String value)
 *     {
 *       return new MyBean(value);
 *     }
 *   }
 *
 *   Web.bean(StringToMyBeanConverter.class).to(new Key&lth;Convert&lth;String, MyBean>>());>
 *
 *   @Service
 *   class MyBeanService {
 *     @Get
 *     public void toMyBean(@Query("v") MyBean bean, Result&lth;String> result) {
 *       result.ok(bean.toString());
 *     }
 *   }
 *   </code>
 *
 * </pre>
 * e.g.
 */
public interface Convert<S, T>
{
  /**
   * Converts from &lth;S> to &lth;T>
   * @param source
   * @return
   */
  T convert(S source);

  /**
   * Tests if converter should be invoked by the framework asynchronously
   * @return
   */
  default boolean isAsync()
  {
    return false;
  }

  /**
   * Converts from <S> to <T> asynchronously.
   * @param source
   * @param result
   */
  default void convert(S source, Result<T> result)
  {
    result.ok(convert(source));
  }
}
