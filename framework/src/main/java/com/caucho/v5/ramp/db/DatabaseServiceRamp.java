/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.ramp.db;

import java.util.Arrays;
import java.util.Objects;

import com.caucho.v5.amp.spi.MethodRef;
import com.caucho.v5.io.Vfs;
import com.caucho.v5.kraken.Kraken;
import com.caucho.v5.kraken.KrakenBuilder;
import com.caucho.v5.kraken.KrakenSystem;
import com.caucho.v5.kraken.table.KrakenImpl;

import io.baratine.db.Cursor;
import io.baratine.db.CursorPrepareSync;
import io.baratine.db.DatabaseService;
import io.baratine.db.DatabaseWatch;
import io.baratine.service.Cancel;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.stream.ResultStream;
import io.baratine.stream.ResultStreamBuilder;

/*
 * Entry to the store.
 */
public class DatabaseServiceRamp implements DatabaseService
{
  private KrakenImpl _kraken;
  
  public DatabaseServiceRamp()
  {
    KrakenSystem krakenSystem = KrakenSystem.current();
    
    KrakenImpl kraken;
    
    if (krakenSystem != null) {
      kraken = krakenSystem.getTableManager();
    }
    else {
      String root = System.getProperty("baratine.root");
      Objects.requireNonNull(root);
      
      KrakenBuilder builder = Kraken.newDatabase();
      builder.root(Vfs.path(root).resolve("kraken"));
      kraken = (KrakenImpl) builder.get();
    }
    
    _kraken = kraken;
  }
  
  public DatabaseServiceRamp(String name, String hostName)
  {
    this();
  }
  
  /**
   * Executes a command against the database.
   * 
   * @param sql the query to be executed
   * @param result the result of the command
   * @param args parameters for the query
   */
  @Override
  public void exec(String sql, Result<Object> result, Object ...args)
  {
    _kraken.query(sql).exec(result, args);
  }
  
  /**
   * Prepares for later execution of a command.
   * 
   * @param sql the query to be executed
   * @param result the prepare cursor.
   */
  @Override
  public void prepare(String sql, Result<CursorPrepareSync> result)
  {
    result.ok(_kraken.query(sql).prepare());
  }
  
  /**
   * Queries for a single result in the database.
   * 
   * @param sql the select query
   * @param result holder for the result
   * @param args arguments to the select
   */
  
  @Override
  public void findOne(String sql, Result<Cursor> result, Object ...args)
  {
    _kraken.query(sql).findOne(result, args);
  }
  
  /**
   * Queries the database, returning an iterator of results.
   * 
   * @param sql the select query for the search
   * @param result callback for the result iterator
   * @param args arguments to the sql
   */
  @Override
  public void findAll(String sql, Result<Iterable<Cursor>> result, Object ...args)
  {
    _kraken.query(sql).findAll(result, args);
  }
  
  /**
   * Queries the database, returning an iterator of results.
   * 
   * @param sql the select query for the search
   * @param result callback for the result iterator
   * @param args arguments to the sql
   */
  @Override
  public void findAllLocal(String sql, Result<Iterable<Cursor>> result, Object ...args)
  {
    _kraken.findAllLocal(sql, args, result);
  }
  
  /**
   * Queries the database, returning values to a result sink.
   * 
   * @param sql the select query for the search
   * @param result callback for the result iterator
   * @param args arguments to the sql
   */
  public void find(ResultStream<Cursor> result, String sql, Object ...args)
  {
    _kraken.findStream(sql, args, result);
  }

  @Override
  public ResultStreamBuilder<Cursor> find(String sql, Object... param)
  {
    throw new IllegalStateException(getClass().getName());
  }
  
  /**
   * Queries the database, returning values to a result sink.
   * 
   * @param sql the select query for the search
   * @param result callback for the result iterator
   * @param args arguments to the sql
   */
  public void findLocal(ResultStream<Cursor> result, String sql, Object ...args)
  {
    _kraken.findLocal(sql, args, result);
  }

  @Override
  public ResultStreamBuilder<Cursor> findLocal(String sql, Object... param)
  {
    throw new IllegalStateException(getClass().getName());
  }

  /**
   * Starts a map call on the local node.
   * 
   * @param sql the select query for the search
   * @param result callback for the result iterator
   * @param args arguments to the sql
   */
  @Override
  public void map(MethodRef method,
                  String sql, 
                  Object ...args)
  {
    _kraken.map(method, sql, args);
  }

  /**
   * Registers a watch listener for updates to a row.
   * 
   * @param watch the watch service receiving updates
   * @param sql query specifying the rows to watch
   * @param args parameters to the query
   * 
   * @return true if successful
   */
  @Override
  public void watch(@Service DatabaseWatch watch, String sql, 
                    Result<Cancel> result,
                    Object ...args)
  {
    _kraken.query(sql, 
                        result.then((x,r)->{ x.watch(watch, r, args); }));
  }
  
  /**
   * Unregisters a watch listener for updates to a row.
   * 
   * @param watch the watch service receiving updates
   * @param sql query specifying the rows to watch
   * @param args parameters to the query
   * 
   * @return true if successful
   */
  /*
  @Override
  public void unwatch(@Service DatabaseWatch watch, 
                      String sql, 
                      Result<Boolean> result,
                      Object ...args)
  {
    _tableManager.query(sql).unwatch(watch, args);
    
    result.complete(true);
  }
  */

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
