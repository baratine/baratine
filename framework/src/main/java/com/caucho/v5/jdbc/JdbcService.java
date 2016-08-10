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

import java.sql.ResultSet;

import io.baratine.service.Result;
import io.baratine.service.Service;

/**
 * A service to execute SQL queries on a JDBC database.  See
 * {@link JdbcServiceSync} for the synchronous interface.  Before using this
 * service, you must configure the JDBC URL with either of the following two
 * methods:
 *
 * <ol>
 * <p><li> During setup of the server:</p>
 * <pre><code>
 * String jdbcUrl = "jdbc:///foo";
 * Web.property(jdbcUrl + ".url", "jdbc:mysql://localhost/myDb");
 * </code></pre>
 *
 * <p><b>Note</b>: <code>.url</code> is a config field defined in
 * {@link JdbcConfig}.</p>
 * </li>
 *
 * <p><li>Or in a config .yaml file:</p>
 * <pre>
 * "jdbc:///foo.url" : jdbc:mysql://localhost/myDb
 * </pre>
 *
 * <p>Then pass in the file via the command-line:</p>
 *
 * <pre>$ java -jar myapp.jar --conf jdbc.yaml</pre>
 *
 * <p>Where the main() method sends the args to <code>{@link io.baratine.web.Web.start Web.start(args)}</code>:</p>
 *
 * <pre><code>
 * public static void main(String[] args) throws Exception {
 *   Web.include(...);
 *
 *   Web.go(args);
 * }
 * </code></pre>
 *
 * </li>
 *
 * </ol>
 *
 * <p>Once configured, the service is available for injection:
 *
 * <pre><code>
 * {@literal @}{@link javax.inject.Inject Inject} {@literal @}{@link Service}("jdbc:///foo")
 * private JdbcService jdbc;
 * </code></pre>
 *
 * <p>Or programmatically:</p>
 *
 * <pre></code>
 * JdbcService jdbc = Services().{@link io.baratine.service.Services#service service}("jdbc:///foo").as(JdbcService.class);
 * </code></pre>
 */
@Service
public interface JdbcService
{
  /**
   * Executes the SQL with the given params.
   *
   * <pre>
   * <code>
   * query(
   *   (updateCount, e) -> {
   *       System.out.println(updateCount);
   *   },
   *   "UPDATE FROM test WHERE id=111"
   * );
   * </code>
   * </pre>
   *
   * @param result
   * @param sql
   * @param params optional query positional parameters
   */
  void execute(Result<Integer> result, String sql, Object ... params);


  /**
   * Executes the SQL with the given params.
   *
   * <pre>
   * <code>
   * query(
   *   (rs, e) -> {
   *       System.out.println(rs);
   *   },
   *   "SELECT * FROM test"
   * );
   * </code>
   * </pre>
   *
   * @param result
   * @param sql
   * @param params optional query positional parameters
   */
  void query(Result<ResultSet> result, String sql, Object ... params);

  /**
   * Executes on the SQL function on the {@link java.sql.Connection}.  After
   * completion, any opened {@link java.sql.Statement Statement}s are automatically closed.
   *
   * <pre>
   * <code>
   * jdbcService.query(
   *   (rs, e) -> {
   *       System.out.println(rs);
   *   },
   *   (conn) -> {
   *       Statement stmt = conn.createStatement();
   *
   *       stmt.execute("SELECT * FROM test");
   *
   *       return stmt.getUpdateCount();
   *   }
   * );
   * </code>
   * </pre>
   *
   * @param result
   * @param fun query function to run on the {@link java.sql.Connection}
   */
  <T> void query(Result<T> result, SqlFunction<T> fun);

  /**
   * Returns the real-time statistics for this connection.
   *
   * @param result the {@link JdbcStat} for this connection
   */
  void stats(Result<JdbcStat> result);
}
