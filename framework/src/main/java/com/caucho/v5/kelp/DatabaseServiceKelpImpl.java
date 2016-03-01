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

package com.caucho.v5.kelp;

import io.baratine.service.Direct;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.Result.Fork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.HashKey;

/**
 * btree-based database
 */
@Service
public class DatabaseServiceKelpImpl
{
  private final DatabaseKelp _db;
  
  private ConcurrentHashMap<String,TableKelp> _tableMap = new ConcurrentHashMap<>();
  private HashMap<HashKey,TableKelp> _tableKeyMap = new HashMap<>();
  
  private final HashMap<String,PendingTable> _pendingMap = new HashMap<>();
  
  private final Lifecycle _lifecycle = new Lifecycle();
  
  DatabaseServiceKelpImpl(DatabaseKelp db)
  {
    _db = db;
    
    _lifecycle.toActive();
  }
  
  public void addTable(String name, byte[] tableKey, Row row,
                       Result<TableKelp> result)
  {
    TableKelp table = getTableByKeyDirect(tableKey);

    if (table != null) {
      result.ok(table);
      return;
    }
    
    PendingTable pendingTable = _pendingMap.get(name);
    
    if (pendingTable == null) {
      table = new TableKelp(_db, name, tableKey, row);
      
      pendingTable = new PendingTable(name, table);
      _pendingMap.put(name, pendingTable);
      
      table.start(Result.of(t->afterTable(name, t),
                              e->afterTable(name, e)));
    }
    
    pendingTable.addResult(result);
  }
  
  private void afterTable(String name, TableKelp table)
  {
    _tableMap.put(name, table);
    _tableKeyMap.put(HashKey.create(table.getTableKey()), table);
    
    PendingTable pendingTable = _pendingMap.remove(name);
    
    if (pendingTable != null) {
      pendingTable.complete(table);
    }
  }
  
  private void afterTable(String name, Throwable e)
  {
    PendingTable pendingTable = _pendingMap.remove(name);
    
    if (pendingTable != null) {
      pendingTable.fail(e);
    }
  }
  
  public void loadTable(byte[] tableKey,
                       Result<TableKelp> result)
  {
    TableKelp table = getTableByKeyDirect(tableKey);

    result.ok(table);
  }
  
  @Direct
  public Iterable<TableKelp> getTablesDirect()
  {
    ArrayList<TableKelp> tables = new ArrayList<>(_tableMap.values());
    
    return tables;
  }
  
  @Direct
  public TableKelp getTableByNameDirect(String name)
  {
    return _tableMap.get(name);
  }

  @Direct
  public TableKelp getTableByKeyDirect(byte[] tableKey)
  {
    return _tableKeyMap.get(HashKey.create(tableKey));
  }

  public TableKelp addTableImpl(String name, TableKelp table)
  {
    if (table == null) {
      return null;
    }
    
    if (_tableMap.putIfAbsent(name, table) != null) {
      table = _tableMap.get(name);
      return table;
    }
    
    // _tableMap.put(name, table);
    _tableKeyMap.put(HashKey.create(table.getTableKey()), table);
    
    _db.getSegmentService().addTable(table.getTableKey(),
                                     table.getRow().getLength(),
                                     table.getRow().getKeyOffset(),
                                     table.getRow().getKeyLength());
    
    return table;
  }

  public void close(ShutdownModeAmp mode,
                    Result<Void> result)
  {
    if (! _lifecycle.toDestroy()) {
      result.ok(null);
      return;
    }
    
    ArrayList<TableKelp> tables = new ArrayList<>(); 
            
    for (TableKelp table : _db.getTables()) {
      tables.add(table);
    }
    
    Result<Void> resultJoin = Result.of(x->result.ok(closeImpl()),
                                          e->{ closeImpl(); result.fail(e); });
    
    //Result<Void>[] tableFork = resultJoin.fork(tables.size(), x->null);
    Fork<Void,Void> fork = resultJoin.fork();
    
    for (int i = 0; i < tables.size(); i++) {
      tables.get(i).close(mode, fork.branch()); // tableFork[i]);
    }
    
    fork.join(x->null);
  }
  
  private Void closeImpl()
  {
    _db.closeImpl();
    
    return null;
  }
  
  private class PendingTable {
    private String _name;
    private TableKelp _table;
    private ArrayList<Result<TableKelp>> _resultList = new ArrayList<>();
    
    PendingTable(String name, TableKelp table)
    {
      _name = name;
      _table = table;
    }

    public void addResult(Result<TableKelp> result)
    {
      Objects.requireNonNull(result);
      
      _resultList.add(result);
    }

    public void complete(TableKelp table)
    {
      for (Result<TableKelp> result : _resultList) {
        result.ok(table);
      }
    }

    public void fail(Throwable exn)
    {
      for (Result<TableKelp> result : _resultList) {
        result.fail(exn);
      }
    }
  }
}
