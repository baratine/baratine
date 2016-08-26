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
 * potentially block must return true with isAsync method.
 * method.
 * <p>
 * e.g.
 * <pre>
 *   <code>
 *   public class StringToMyBeanConverter implements Convert&lt;String,MyBean&gt; {
 *     public MyBean convert(String value)
 *     {
 *       return new MyBean(value);
 *     }
 *   }
 *
 *   Web.bean(StringToMyBeanConverter.class).to(new Key&lt;Convert&lt;String, MyBean&gt;&gt;());&gt;
 *
 *   &#64;Service
 *   class MyBeanService {
 *     &#64;Get
 *     public void toMyBean(@Query("v") MyBean bean, Result&lt;String&gt; result) {
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
   * Converts from &lt;S&gt; to &lt;T&gt;
   * @param source object to convert
   * @return converted to type T instance
   */
  T convert(S source);

  /**
   * Tests if converter should be invoked by the framework asynchronously
   * @return true if converter should be called asynchronously, false otherwise
   */
  default boolean isAsync()
  {
    return false;
  }

  /**
   * Converts from &lt;S&gt; to &lt;T&gt; asynchronously.
   * @param source object to convert
   * @param result converted object
   */
  default void convert(S source, Result<T> result)
  {
    result.ok(convert(source));
  }
}
