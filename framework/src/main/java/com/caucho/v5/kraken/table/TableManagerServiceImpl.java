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

package com.caucho.v5.kraken.table;

import io.baratine.service.OnDestroy;
import io.baratine.service.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.Direct;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableBuilderKelp;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.TableListener;
import com.caucho.v5.kraken.query.CreateQuery;
import com.caucho.v5.kraken.query.QueryBuilderKraken;
import com.caucho.v5.kraken.query.QueryKraken;
import com.caucho.v5.kraken.query.QueryParserKraken;
import com.caucho.v5.kraken.query.TableBuilderKraken;
import com.caucho.v5.kraken.table.KelpManager.MetaTableEntry;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.HashKey;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;

/**
 * Manages the distributed cache
 */
public class TableManagerServiceImpl implements TableManagerService
{
  private static final L10N L = new L10N(TableManagerServiceImpl.class);
  private static final Logger log = Logger.getLogger(TableManagerServiceImpl.class.getName());
  
  private final TableManagerKraken _tableManager;
  
  private ConcurrentHashMap<HashKey,TableKraken> _tableMap = new ConcurrentHashMap<>();
  private HashMap<String,TableKraken> _tableNameMap = new HashMap<>();
  
  private HashMap<HashKey,String> _tableSqlMap = new HashMap<>();
  
  private KelpManager _kelpManager;
  
  private TableKraken _metaTable;
  private TableKraken _metaUpdateTable;
  
  private final HashMap<String,PendingTable> _pendingTableMap = new HashMap<>();

  private ClientKrakenImpl _clusterClient;

  private boolean _isClusterStarted;
  
  public TableManagerServiceImpl(TableManagerKraken tableManager)
  {
    _tableManager = tableManager;
    
  }
  
  @Override
  public boolean startLocal()
  {
    _kelpManager = _tableManager.getKelpBacking();
    
    _clusterClient = new ClientKrakenImpl(_tableManager);
    
    TableKelp metaTable = _kelpManager.getMetaTable();
    
    _metaTable = createTableImpl(metaTable, null);
    
    TableKelp metaUpdateTable = _kelpManager.getMetaTableUpdate();
    
    _metaUpdateTable = createTableImpl(metaUpdateTable, null);
    
    for (MetaTableEntry entry : _kelpManager.loadMetaTables()) {
      byte []key = entry.getKey();
      String name = entry.getName();
      String sql = entry.getSql();
      
      restoreTable(key, name, sql);
    }
    
    _metaTable.addListener(new MetaTableListener());
    
    return true;
  }
  
  public void start(Result<Void> result)
  {
    // _kelpManager.start();
    
    result.ok(null);
  }

  @Direct
  public void getTableByKey(byte[] tableKey, Result<TableKraken> result)
  {
    result.ok(getTableByKeyImpl(tableKey));
  }

  private TableKraken getTableByKeyImpl(byte[] tableKey)
  {
    return _tableMap.get(HashKey.create(tableKey));
  }

  @Direct
  public void getTableByName(String name, Result<TableKraken> result)
  {
//    System.out.println("TNN: " + name + " " + _tableNameMap);
    
    result.ok(_tableNameMap.get(name));
  }
   
  @Override
  public void createTable(TableKelp tableKelp, 
                          Result<TableKraken> result)
  {
    TableKraken table = createTableImpl(tableKelp, null);
    
    result.ok(table);
    
    completePending(table.getId(), table);
  }
  
  @Override
  public void createTableSql(String name, String sql,
                             Result<TableKraken> result)
  {
    String tableName = getTableName(name);
    String podName = getPodName(name);
    
    TableKraken tableKraken = _tableNameMap.get(tableName);
    
    if (tableKraken != null) {
      result.ok(tableKraken);
      return;
    }
    
    QueryParserKraken parser = new QueryParserKraken(_tableManager, sql);
    
    parser.setPodName(podName);
    
    QueryBuilderKraken builder = parser.parse();
    
    QueryKraken query = builder.build();
    
    query.exec(result.of(v->_tableNameMap.get(tableName)));
  }
  
  private String getTableName(String name)
  {
    if (name.indexOf('.') > 0) {
      return name;
    }
    
    String podName = BartenderSystem.getCurrentPod().name();
    String clusterId = BartenderSystem.getCurrentSelfServer().getClusterId();
    
    if (podName.equals(clusterId)) {
      podName = podName + "_hub";
    }
    
    //return name + '.' + podName;
    return podName + '.' + name;
  }
  
  private String getPodName(String name)
  {
    int p = name.indexOf('.');
    
    if (p > 0) {
      return name.substring(0, p);
    }
    
    String podName = BartenderSystem.getCurrentPod().name();
    String clusterId = BartenderSystem.getCurrentSelfServer().getClusterId();
    
    if (podName.equals(clusterId)) {
      podName = podName + "_hub";
    }
    
    return podName;
  }

  @Override
  public void loadTable(String name,
                        Result<TableKraken> result)
  {
    PendingTable pending = _pendingTableMap.get(name);
    
    if (pending != null) {
      pending.addPending(result);
    }
    else {
      loadTableImpl(name, result);
    }
  }
  
  @Override
  public void buildTable(String sql,
                         TableBuilderKraken builderKraken, 
                         TableBuilderKelp builderKelp, 
                         Result<TableKraken> result)
  {
    String id = builderKraken.getId();
    
    PendingTable pending = new PendingTable(id);
    
    if (_pendingTableMap.get(id) == null) {
      _pendingTableMap.put(id, pending);
    }
    
    builderKelp.build(Result.of(table->addTable(sql, builderKraken, table, result),
                                  exn->failTable(exn, id, result)));
  }
  
  private void addTable(String sql,
                        TableBuilderKraken builder,
                        TableKelp tableKelp,
                        Result<TableKraken> result)
  {
    TableKraken tableKraken = null;
    
    if (tableKelp != null) {
      tableKraken = createTableImpl(tableKelp, builder);

      HashKey hKey = HashKey.create(tableKelp.getTableKey());
      
      if (_tableSqlMap.get(hKey) == null) {
        _tableSqlMap.put(hKey, sql);
        
        KelpManager kelpManager = _tableManager.getKelpBacking();

        kelpManager.addTable(tableKelp, sql, _metaTable.getBackupCallback());
      }
    }
    
    result.ok(tableKraken);
    
    completePending(builder.getId(), tableKraken);
  }
  
  private void completePending(String id, TableKraken tableKraken)
  {
    PendingTable pending = _pendingTableMap.remove(id);
    
    if (pending != null) {
      pending.complete(tableKraken);
    }
  }
  
  private void failTable(Throwable exn, String id, Result<TableKraken> result)
  {
    result.fail(exn);
    
    PendingTable pending = _pendingTableMap.remove(id);
    
    if (pending != null) {
      pending.fail(exn);
    }
  }

  private TableKraken createTableImpl(TableKelp tableKelp,
                                      TableBuilderKraken builder)
  {
    HashKey tableKey = HashKey.create(tableKelp.getTableKey());
    
    TableKraken tableKraken = _tableMap.get(tableKey);

    if (tableKraken == null) {
      String tableNameKelp = tableKelp.getName();
      int p = tableNameKelp.lastIndexOf('.');
      
      String podName;
      String tableName;
      
      if (p > 0) {
        podName = tableNameKelp.substring(0, p);
        tableName = tableNameKelp.substring(p + 1);
      }
      else {
        podName = QueryParserKraken.getCurrentPodName();
        tableName = tableNameKelp;
      }
      
      PodKraken podManager = _clusterClient.getPod(podName);
      
      tableKraken = new TableKraken(_tableManager, tableName, 
                                    tableKelp, builder,
                                    podManager, getKelpBacking());
    
      _tableMap.put(tableKey, tableKraken);
      _tableNameMap.put(tableKelp.getName(), tableKraken);
      
      if (_isClusterStarted) {
        tableKraken.start();
      }
    }
    
    return tableKraken;
  }
  
  private KelpManager getKelpBacking()
  {
    return _tableManager.getKelpBacking();
  }

  void loadTableImpl(String name, Result<TableKraken> result)
  {
    TableKraken table = _tableNameMap.get(name);
    
    if (table != null) {
      result.ok(table);
      return;
    }

    //ServiceFuture<TableKraken> future = new ServiceFuture<>();
    
    loadTableByName(name, result);
  }
  
  public void loadTableByName(String tableName,
                               Result<TableKraken> result)
  {
    TablePod tablePod = _metaTable.getTablePod();

    tablePod.findByName(tableName,
                        result.of((key,r)->loadTableByKey(key, r)));
  }

  @Override
  public void loadTableByKey(byte []tableKey,
                             Result<TableKraken> result)
  {
    if (tableKey == null) {
      result.ok(null);
      return;
    }
    
    TableKraken table = getTableByKeyImpl(tableKey);
    
    if (table != null) {
      result.ok(table);
      return;
    }

    TablePod tablePod = _metaTable.getTablePod();
    
    tablePod.get(tableKey, result.of((x,r)->loadTableLocal(tableKey,r)));
  }
  
  /**
   * Loads and creates a table from the local meta-table.
   */
  public void loadTableLocal(byte []tableKey, Result<TableKraken> result)
  {
    if (tableKey == null) {
      result.ok(null);
      return;
    }
    
    TableKraken table = getTableByKeyImpl(tableKey);

    if (table != null) {
      result.ok(table);
      return;
    }

    RowCursor cursor = _metaTable.getTableKelp().cursor();
    cursor.setBytes(1, tableKey, 0);
    
    _metaTable.getTableKelp().get(cursor, result.of((v,r)->loadTableLocalImpl(tableKey, cursor, v, r)));
  }
  
  private void loadTableLocalImpl(byte []tableKey,
                                  RowCursor cursor,
                                  boolean isComplete,
                                  Result<TableKraken> result)
  {
    if (isComplete) {
      String name = cursor.getString(2);
      String sql = cursor.getString(3);

      if (sql == null || sql.equals("")) {
        throw new IllegalStateException(L.l("Broken table: '{0}'\n  sql: {1} {2}",
                                            name, sql,
                                            BartenderSystem.getCurrentSelfServer()));
      }
        
      QueryKraken query = QueryParserKraken.parse(_tableManager, sql).build();

      // ServiceFuture<Object> future = new ServiceFuture<>();
      
      query.exec((Result) result);

      //future.get(1, TimeUnit.SECONDS);
    }
    else {
      KrakenException exn = new KrakenException(L.l("Failed to load table {0}",
                                                    Hex.toShortHex(tableKey)));
      exn.fillInStackTrace();
      
      result.fail(exn);
    }
  }

  public Iterable<TableKraken> getTables()
  {
    return _tableMap.values();
  }
  
  //
  // lifecycle
  //

  /**
   * Called when a peer server starts, to enable/retry any missed sync.
   */
  @Override
  public void onServerStart()
  {
    if (! _isClusterStarted) {
      return;
    }
    
    for (TableKraken table : getTables()) {
      table.start();
    }
  }

  @Override
  public void startCluster()
  {
    _isClusterStarted = true;
  }
  
  @Override
  public void startRequestUpdates()
  {
    for (TableKraken table : getTables()) {
      table.getTablePod().startRequestUpdates();
    }
  }

  @OnDestroy
  public void shutdown()
  {
    saveMetaTableUpdate();
    
    TreeSet<String> tableNames = new TreeSet<>(_tableNameMap.keySet());
      
    for (String tableName : tableNames) {
      TableKraken table = _tableNameMap.get(tableName);
        
      table.shutdown();
    }
  }
  
  private void saveMetaTableUpdate()
  {
    long now = CurrentTime.currentTime();
    
    for (TableKraken table : getTables()) {
      if (table.isStartupComplete()) {
        getKelpBacking().saveMetaTableUpdate(table.getKey(), now);
      }
    }
    
    _metaUpdateTable.getTableKelp().waitForComplete();
  }
  
  private void onMetaTable(boolean isLoad, RowCursor cursor)
  {
    if (! isLoad) {
      return;
    }
    
    byte []key = cursor.getBytes(1);
    String name = cursor.getString(2);
    String sql = cursor.getString(3);

    restoreTable(key, name, sql);
  }
  
  private void restoreTable(byte []key, String name, String sql)
  {
    try {
      _tableSqlMap.put(HashKey.create(key), sql);
  
      QueryBuilderKraken queryBuilder
        = QueryParserKraken.parse(_tableManager, name, sql);
  
      CreateQuery query = (CreateQuery) queryBuilder.build(); 

      query.restoreTable();
    } catch (Exception e) {
      log.log(Level.FINE, "Failed to restore table '" + name + "' with sql='" + sql + "'\n" + e, e);
      
      throw e;
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  private class MetaTableListener implements TableListener {
    @Override
    public void onPut(byte[] key, TypePut type)
    {
      if (type == TypePut.REMOTE) {
        RowCursor cursor = _metaTable.cursor();
      
        cursor.setBytes(1, key, 0);
      
        _metaTable.get(cursor, (x,e)->onMetaTable(x, cursor));
      }
    }

    @Override
    public void onRemove(byte[] key, TypePut type)
    {
    }
  }
  
  private static class PendingTable {
    private ArrayList<Result<TableKraken>> _pendingList = new ArrayList<>();
    
    PendingTable(String name)
    {
    }
    
    public void addPending(Result<TableKraken> result)
    {
      _pendingList.add(result);
    }
    
    public void complete(TableKraken table)
    {
      for (Result<TableKraken> result : _pendingList) {
        result.ok(table);
      }
    }
    
    public void fail(Throwable exn)
    {
      for (Result<TableKraken> result : _pendingList) {
        result.fail(exn);
      }
    }
  }
}
