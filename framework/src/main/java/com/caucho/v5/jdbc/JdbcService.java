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

import io.baratine.service.Result;
import io.baratine.service.Service;

@Service
public interface JdbcService
{
  public static String CONFIG_URL = "JDBC_URL";
  public static String CONFIG_USER = "JDBC_USER";
  public static String CONFIG_PASS = "JDBC_PASS";
  public static String CONFIG_POOL_SIZE = "JDBC_POOL_SIZE";

  public static String CONFIG_TEST_QUERY_BEFORE = "JDBC_TEST_QUERY_BEFORE";
  public static String CONFIG_TEST_QUERY_AFTER = "JDBC_TEST_QUERY_AFTER";

  void execute(Result<Integer> result, String sql, Object ... params);
  void executeBatch(Result<Integer[]> result, String sql, Object[] ... params);
  void executeBatch(Result<Integer[]> result, String[] sqlList, Object[] ... params);

  void query(Result<JdbcResultSet> result, String sql, Object ... params);
  void queryBatch(Result<JdbcResultSet[]> result, String sql, Object[] ... paramsList);
  void queryBatch(Result<JdbcResultSet[]> result, String[] sqlList, Object[] ... paramsList);
}
