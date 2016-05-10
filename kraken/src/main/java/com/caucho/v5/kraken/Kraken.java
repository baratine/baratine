/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
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
 * @author Scott Ferguson
 */

package com.caucho.v5.kraken;

import com.caucho.v5.kraken.table.DatabaseKraken;
import com.caucho.v5.kraken.table.DatabaseKrakenSync;
import com.caucho.v5.kraken.table.KrakenBuilderImpl;

/**
 * Interface {@Kraken} is a bootstrap class for Kraken database. It provides methods
 * to create {@KrakenBuilder}, start and stop Kraken database.
 * <p>
 * Kraken supports a subset of SQL standard and provides methods with asynchronous
 * execution. Results of executing the statements are obtained via callbacks.
 * <p>
 * Kraken supports most of the regular SQL types and an Object type. Columns of
 * type Object are capable of storing Java Beans using H3 representation. Internally
 * H3 is a Map which allows querying Object type columns using dotted notation e.g.
 * <p>
 * <code> where user.name = ? </code>
 * <p>
 * Other types supported are double, integer, blob, string, object, char, varbinary,
 * binary, mediumtext, longtext, tinyint, smallint, integer, bigint, double, datetime,
 * text and identity.
 * <p>
 * Kraken supports CREATE, INSERT, SELECT, UPDATE, REPLACE and DELETE queries.
 * <p>
 * <code>create table test(id int primary key, data varchar)'</code>
 * <code>insert into test (id, data) values (0, 'Hello World!')</code>
 * <code>select data from test where id = ?</code>
 * <p>
 * E.g.
 * <code>
 * <pre>
 *  public void example()
 *  {
 *    KrakenBuilder krakenBuilder = Kraken.newDatabase();
 *
 *    Kraken kraken = krakenBuilder.get();
 *
 *    DatabaseKraken db = kraken.database();
 *    db.execute(Result.ignore(),
 *             "create table test(id int primary key, data varchar)");
 *    db.execute(Result.ignore(),
 *             "insert into test (id, data) values (0, 'Hello World!')");
 *
 *    db.query((rs, e)->processResult(rs, e), "select id, data from test");
 *  }
 *
 *  private void processResult(ResultSetKraken rs, Throwable e)
 *  {
 *    for (List<Object> row : rs) {
 *      //process row
 *    }
 *   }
 *
 * </pre>
 * </code>
 *
 * In the code above method example doesn't wait for the results of query execution
 * but exits immediately after submitting the queries to {@DatabaseKraken}
 *
 * Method {@processResult} is called asynchronously when the ResultSetKraken
 * becomes available.
 */
public interface Kraken
{
  static KrakenBuilder newDatabase()
  {
    return new KrakenBuilderImpl();
  }

  DatabaseKraken database();

  DatabaseKrakenSync databaseSync();

  void start();

  void close();
}
