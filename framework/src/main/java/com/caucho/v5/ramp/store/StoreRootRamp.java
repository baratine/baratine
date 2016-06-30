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

package com.caucho.v5.ramp.store;

import io.baratine.db.Cursor;
import io.baratine.db.DatabaseService;
import io.baratine.service.OnInit;
import io.baratine.service.OnLoad;
import io.baratine.service.OnLookup;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import io.baratine.stream.ResultStream;

import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.kraken.KrakenSystem;
import com.caucho.v5.kraken.query.QueryKraken;
import com.caucho.v5.kraken.table.PodHashGenerator;
import com.caucho.v5.kraken.table.PodHashGeneratorColumn;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.KrakenImpl;
import com.caucho.v5.ramp.keyvalue.StoreSync;

/*
 * Entry to the store.
 */
@Service
public class StoreRootRamp
{
  private final StoreSchemeRamp _scheme;
  private final String _podName;
  private final String _tableName;
  
  private DatabaseService _db;
  
  private TableKraken _table;
  
  private String _insertSql;
  private String _selectSql;
  private String _removeSql;
  private KrakenImpl _tableManager;
  private QueryKraken _insertQuery;
  private QueryKraken _removeQuery;
  private QueryKraken _selectQuery;
  private ServiceRef _self;
  
  public StoreRootRamp(StoreSchemeRamp scheme,
                       String podName)
  {
    _scheme = scheme;
    
    if (podName == null || podName.isEmpty()) {
      PodBartender pod = BartenderSystem.getCurrentPod();
      
      podName = pod.name();
    }
    
    
    _podName = podName;
    
    //_tableName = podName + ".baratine_store";
    _tableName = podName + ".baratine_store";
  }
  
  @OnInit
  private void onInit()
  {
    _self = ServiceRef.current();
  }
  
  @OnLookup
  StoreRamp onLookup(String path)
  {
    return new StoreRamp(this, path);
  }
  
  StoreSync<?> lookup(String path)
  {
    return _self.service(_scheme + "://" + _podName + path).as(StoreSync.class);
  }
  
  @OnLoad
  void onLoad(Result<Boolean> result)
  {
    if (_table != null) {
      result.ok(true);
      return;
    }
    
    _tableManager = KrakenSystem.current().getTableManager();
    
    QueryKraken query = _tableManager.query("create table " + _tableName + " ("
                       + "  id string,"
                       + "  hash int16,"
                       + "  value object,"
                       + " primary key (id, hash)"
                       + ") with hash '" + PodHashGeneratorColumn.class.getName() + "'");
    
    query.exec(result.then(table->completeCreate((TableKraken) table)));
    
    // _db = db;
  }
  
  private boolean completeCreate(TableKraken table)
  {
    _table = table;
    String insertSql = "insert into " + _tableName + " (id,hash,value) values (?,?,?)";
    _insertQuery = _tableManager.query(insertSql);
    
    String removeSql = "delete from " + _tableName + " where id=? and hash=?";
    
    _removeQuery = _tableManager.query(removeSql);
    
    String selectSql = "select value from " + _tableName + " where id=? and hash=?";
    
    _selectQuery = _tableManager.query(selectSql);
    
    return true;
  }
  
  public void get(String key, Result<Object> result)
  {    
    /*
    System.out.println("XPL: " + _selectSql);
    _db.exec("explain " + _selectSql, 
             Result.make(v -> { System.out.println("V: " + v); }, e->e.printStackTrace()),
        key);
        */
    
    _selectQuery.findOne(result.then(cursor -> cursor != null ? cursor.getObject(1) : null),
                         key, hash(key));
  }
  
  public void put(String key, Object value, Result<Void> result)
  {
    _insertQuery.exec(result.then(x->null),
                      key, hash(key), value);
  }
  
  public void remove(String key, Result<Void> result)
  {
    _removeQuery.exec(result.then(x->null),
                      key, hash(key));
  }

  public void findOne(String sql, 
                      Result<Object> result,
                      Object[] args)
  {
    findOneImpl("", sql, result, args);
  }

  public void findOneImpl(String path, 
                          String query,
                          Result<Object> result,
                          Object[] args)
  {
    String name = _table.getName();
    
    String objExprSql = _tableManager.parseSubQuery(name, "value", query);
    
    String sql = ("SELECT id, value FROM " + _tableName
                 //+ " WHERE id_store=?"
                  + " WHERE " + objExprSql);
    
    /*
    Object []extArgs = new Object[args.length + 1];
    extArgs[0] = path;
    System.arraycopy(args, 0, extArgs, 1, args.length);
    */
    
    QueryKraken queryKraken = _tableManager.query(sql);
    
    queryKraken.findOne(result.then(cursor->findOneResult(cursor)),
                        args);
    
    // result.complete(null);
  }
  
  private Object findOneResult(Cursor cursor)
  {
    if (cursor == null) {
      return null;
    }
    else {
      return cursor.getObject(2);
    }
  }

  public void find(ResultStream result, String sql, Object ...args)
  {
    findImpl("", sql, args, result);
  }

  public void findImpl(String path, 
                       String query,
                       Object[] args,
                       ResultStream<Object> result)
  {
    String name = _table.getName();
    
    String objExprSql = _tableManager.parseSubQuery(name, "value", query);
    
    String sql = ("SELECT id, value FROM " + _tableName
                 //+ " WHERE id_store=?"
                  + " WHERE " + objExprSql);

    /*
    Object []extArgs = new Object[args.length + 1];
    extArgs[0] = path;
    System.arraycopy(args, 0, extArgs, 1, args.length);
    */
    
    //QueryKraken queryKraken = _tableManager.query(sql);
    
    _table.flush(Result.of(x->{ _tableManager.findStream(sql, args,
                                                          new ResultSinkChain(result));
                             },
                             e->result.fail(e)));
  }

  public void findLocal(ResultStream result, String sql, Object ...args)
  {
    findLocalImpl("", sql, args, result);
  }

  public void findLocalImpl(String path, 
                            String query,
                            Object[] args,
                            ResultStream<Object> result)
  {
    String name = _table.getName();
    
    String objExprSql = _tableManager.parseSubQuery(name, "value", query);
    
    String sql = ("SELECT id, value FROM " + _tableName
                 //+ " WHERE id_store=?"
                  + " WHERE " + objExprSql);
    
    /*
    Object []extArgs = new Object[args.length + 1];
    extArgs[0] = path;
    System.arraycopy(args, 0, extArgs, 1, args.length);
    */
    
    //QueryKraken queryKraken = _tableManager.query(sql);
    
    _table.flush(Result.of(x->{ _tableManager.findLocal(sql, args,
                                                          new ResultSinkChain(result));
                             },
                             e->result.fail(e)));
 
    
  }
  
  private int hash(String key)
  {
    int p = key.indexOf('/');
    
    if (p < 0) {
      return PodHashGenerator.Base.getPodHash(key);
    }
    
    int q = key.indexOf('/', p + 1);
    
    if (q < 0) {
      return PodHashGenerator.Base.getPodHash(key);
    }
    else {
      String subKey = key.substring(0, q);
      
      return PodHashGenerator.Base.getPodHash(subKey);
    }
  }

  public void shutdown()
  {
    // XXX: flush query
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _podName + "]";
  }
  
  private static class ResultSinkChain implements ResultStream<Cursor> {
    private ResultStream<Object> _result;
    
    ResultSinkChain(ResultStream<Object> result)
    {
      _result = result;
    }

    @Override
    public void accept(Cursor value)
    {
      _result.accept(value.getObject(2));
    }

    @Override
    public void ok()
    {
      _result.ok();
    }

    @Override
    public void fail(Throwable e)
    {
      _result.fail(e);
    }
    
    @Override
    public void handle(Cursor value, Throwable e, boolean isOk)
    {
      if (isOk) {
        ok();
      }
      else if (e != null) {
        fail(e);
      }
      else {
        accept(value);
      }
    }
  }
}
