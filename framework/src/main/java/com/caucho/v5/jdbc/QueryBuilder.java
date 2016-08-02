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

package com.caucho.v5.jdbc;

import java.util.function.Function;

/**
 * Builds chainable queries under one or more transactions.
 *
 */
public interface QueryBuilder
{
  /**
   * Creates a new chainable query.
   *
   * @param query
   * @return
   */
  public static QueryBuilder query(String query)
  {
    QueryBuilderImpl builder = new QueryBuilderImpl(query);

    return builder;
  }

  /**
   * Specifies the query parameters for the current query.
   *
   * @param params
   * @return this
   */
  QueryBuilder withParams(Object ... params);

  /**
   * Specifies the query parameters for the current query.
   *
   * @param params
   * @return this
   */
  QueryBuilder withParams(Function<JdbcResultSet,Object[]> fun);

  /**
   * Appends a new query to this transaction.
   *
   * @param params
   * @return this
   */
  QueryBuilder then(String sql);

  /**
   * Appends a new query to this transaction.
   *
   * @param params
   * @return this
   */
  QueryBuilder then(Function<JdbcResultSet,String> fun);

  /**
   * Commits the transaction then creates a new query.
   *
   * @param sql
   * @return this
   */
  QueryBuilder commitThen(String sql);

  /**
   * Commits the transaction then creates a new query.
   *
   * @param fun
   * @return this
   */
  QueryBuilder commitThen(Function<JdbcResultSet,String> fun);
}
