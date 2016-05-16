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
 */

package io.baratine.db;

import io.baratine.service.Cancel;
import io.baratine.service.MethodRef;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.stream.ResultStreamBuilder;


/**
 * Experimental API to the internal Kelp/Kraken database.
 *
 * <pre><code>
 * &#64;Inject &#64;Lookup("bardb:///")
 * DatabaseServce _db;
 * 
 * serviceManager.lookup("bardb:///").as(DatabaseService.class);
 * </code></pre>
 */
@Service("bardb:///")
public interface DatabaseService
{
  /**
   * Async execution for database statements.
   * 
   * @param sql the query to execute
   * @param result callback for the query result
   * @param param optional parameters for the query
   */
  void exec(String sql, Result<Object> result, Object ...param);
  
  /**
   * Prepare for later execution. The returned cursor is not thread safe.
   * @param sql the query to execute
   * @param result holder for the query result
   */
  void prepare(String sql, Result<CursorPrepareSync> result);
  
  /**
   * Async select query that returns a single result.
   *   
   * @param sql the select query to execute
   * @param result a callback to contain the result
   * @param param optional parameters for the select query
   */
  void findOne(String sql, Result<Cursor> result, Object ...param);
  
  void findAll(String sql, Result<Iterable<Cursor>> cursor, Object ...param);
  
  ResultStreamBuilder<Cursor> find(String sql, Object ...param);
  
  ResultStreamBuilder<Cursor> findLocal(String sql, Object ...param);
  
  void findAllLocal(String sql, Result<Iterable<Cursor>> cursor, Object ...param);
  
  void map(MethodRef method, String sql, Object ...param);
  
  /**
   * Set a watch callback on a database to be notified when the row changes.
   * 
   * @param watch callback for change events to the row
   * @param sql the watch query to select rows to watch
   * @param result holder for the result
   * @param param parameters for the query
   */
  void watch(@Service DatabaseWatch watch, 
             String sql, 
             Result<Cancel> result,
             Object ...param);
}
