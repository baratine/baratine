/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package io.baratine.jdbc;

import java.sql.Connection;
import java.util.function.BiFunction;

import io.baratine.service.Result;

/**
 * Represents a function to apply to a connection and array of parameters using
 * JdbcService#query(Result, SqlBiFunction, Object...) method.
 *
 * @param <R> type of function result
 * @see JdbcService#query(Result, SqlBiFunction, Object...)
 */

@FunctionalInterface
public interface SqlBiFunction<R> extends BiFunction<Connection, Object[],R>
{
  /**
   * Function method to implement.
   *
   * @param t JDBC Connection
   * @param params parameters passed from JdbcService.query() method
   * @return value of type R (result of applying the function)
   * @throws Exception relay exception thrown during function call
   */
  R applyWithException(Connection t, Object ... params) throws Exception;

  default R apply(Connection t, Object[] params)
  {
    try {
      return applyWithException(t, params);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  default void close()
  {
  }
}
