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

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kraken.Kraken;
import com.caucho.v5.kraken.archive.ArchiveKrakenManager;
import com.caucho.v5.kraken.archive.ArchiveServiceImpl;
import com.caucho.v5.kraken.archive.ArchiveServiceSync;
import com.caucho.v5.kraken.query.QueryBuilderKraken;
import com.caucho.v5.kraken.query.QueryKraken;
import com.caucho.v5.kraken.query.QueryParserKraken;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.store.temp.TempStore;
import com.caucho.v5.util.L10N;

import io.baratine.db.Cursor;
import io.baratine.db.DatabaseWatch;
import io.baratine.service.Cancel;
import io.baratine.service.MethodRef;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;
import io.baratine.service.ServiceRef;
import io.baratine.stream.ResultStream;

/**
 * Kraken manager
 */
public final class KrakenImpl implements Kraken
{
  private static final L10N L = new L10N(KrakenImpl.class);

  private final Path _root;
  private final ServicesAmp _services;

  
  // private final ClientKraken _clusterClient;
  private KelpManagerBuilder _kelpManagerBuilder;
  private KelpManager _kelpBacking;
  
  private final Lifecycle _lifecycle = new Lifecycle();
  
  private ServiceRefAmp _serviceRef;
  private TableManagerServiceSync _tableService;
  private WatchService _watchService;
  
  private boolean _isClosed;
  
  //private AdminCacheStore _admin = new AdminCacheStore(this);

  //private ReplicationCallback _replicationCallback;

  private TempStore _tempStore;

  private ServerBartender _serverSelf;

  private ArchiveServiceSync _archiveService;

  private boolean _isCluster;

  private DatabaseKraken _database;

  private DatabaseKrakenSync _databaseSync;

  // private boolean _isClusterStarted;
  
  public KrakenImpl(KrakenBuilderImpl builder)
  {
    _root = builder.root();
    Objects.requireNonNull(_root);
    
    _serverSelf = builder.serverSelf();
    _services = builder.services();
    // new AdminPersistentStore(this);
    
    _tempStore = builder.tempStore();
    
    
    _kelpManagerBuilder = new KelpManagerBuilder(this);
    
    //_isCluster = BartenderSystem.current() != null;
    _isCluster = false;
    
    ServiceRefAmp databaseRef
      = _services.newService(new DatabaseKrakenImpl(this)).ref();
  
    _database = databaseRef.as(DatabaseKraken.class); 
    _databaseSync = databaseRef.as(DatabaseKrakenSync.class);
  }

  public Path root()
  {
    return _root;
  }

  /*
  public ReplicationCallback getTableReplicationCallback()
  {
    return _replicationCallback;
  }
  */
  
  public ServerBartender serverSelf()
  {
    return _serverSelf;
  }
  
  public long getMemoryMax()
  {
    return _kelpBacking.getMemoryMax();
  }
  
  public void setMemoryMax(long memoryMax)
  {
    _kelpBacking.getMemoryMax();
  }

  public boolean isCluster()
  {
    return _isCluster;
  }
  
  @Override
  public DatabaseKraken database()
  {
    return _database;
  }
  
  @Override
  public DatabaseKrakenSync databaseSync()
  {
    return _databaseSync;
  }
  
  /*
  public void setClusterClient(ClientKraken clusterEngine)
  {
  }
  */
  
  /*
  public ClientKraken getClusterEngine()
  {
    return _clusterClient;
  }
  */
  
  public ServiceRefAmp getStoreServiceRef()
  {
    return _serviceRef;
  }

  public TableManagerServiceSync getTableService()
  {
    return _tableService;
  }

  public ServicesAmp services()
  {
    return _services;
  }

  public ArchiveServiceSync getArchiveService()
  {
    return _archiveService;
  }

  public ServiceRef getDatabaseServiceRef()
  {
    // XXX:
    return _serviceRef;
  }

  public KelpManager getKelpBacking()
  {
    return _kelpBacking;
  }
  
  public Path getStorePath()
  {
    return getKelpBacking().getDatabase().getPath();
  }

  public String getClusterPodName()
  {
    String clusterId = _serverSelf.getClusterId();
    
    String podName = "cluster_hub";
    //podName = podName.replace('-', '_');
    //podName = podName.replace('.', '_');

    return podName;
  }

  public TableKraken getTable(byte[] tableKey)
  {
    return _tableService.getTableByKey(tableKey);
  }

  public TableKraken getTable(String name)
  {
    return _tableService.getTableByName(name);
  }

  public Iterable<TableKraken> getTables()
  {
    return _tableService.getTables();
  }
  
  public TempStore tempStore()
  {
    return _tempStore;
  }
  
  //@Override
  public void start()
  {
    if (! _lifecycle.toActive()) {
      return;
    }
    
    _kelpBacking = _kelpManagerBuilder.build();
    
    TableManagerServiceImpl tableService = new TableManagerServiceImpl(this);
    
    _serviceRef = _services.newService(tableService).ref();
    _tableService = _serviceRef.as(TableManagerServiceSync.class);
    
    WatchServiceImpl watchService = new WatchServiceImpl(this);
    _watchService = _services.newService(watchService).as(WatchService.class);
    
    ArchiveServiceImpl archiveService = new ArchiveServiceImpl(this);
    _archiveService = _services.newService(archiveService)
                                 .as(ArchiveServiceSync.class);

    _tableService.startLocal();
  }
  
  public void startCluster()
  {
    _tableService.startCluster();
  }

  public void startRequestUpdates()
  {
    _tableService.startRequestUpdates();
  }
  
  public void stop()
  {
    // _admin.unregister();
  }

  /**
   * Called when a peer server starts, to enable/retry any missed sync.
   */
  public void onServerStart()
  {
    _tableService.onServerStart();
  }

  /**
   * Closes the manager.
   */
  public void close()
  {
    if (_isClosed) {
      return;
    }
    
    _isClosed = true;

    KelpManager backing = _kelpBacking;
    // _localBacking = null;
    
    if (backing != null) {
      backing.close();
    }
  }

  public TableKraken createTable(String name, String sql)
  {
    return _tableService.createTableSql(name, sql);
  }

  public void createTable(String name, String sql, Result<TableKraken> result)
  {
    _tableService.createTableSql(name, sql, result);
  }
  
  /*
  public TableKraken createTable(TableKelp tableKelp)
  {
    return _tableService.createTable(tableKelp);
  }
  */

  public TableKraken loadTable(String name)
  {
    ResultFuture<TableKraken> future = new ResultFuture<>();
    
    loadTable(name, future);
    
    TableKraken table = future.get(10, TimeUnit.SECONDS);
    
    return table;
  }
  
  public void loadTable(String name, Result<TableKraken> result)
  {
    _tableService.loadTable(name, result);
  }
  
  public void loadTable(byte []tableKey, Result<TableKraken> result)
  {
    TableKraken table = getTable(tableKey);
    
    if (table != null) {
      result.ok(table);
    }
    else {
      _tableService.loadTableByKey(tableKey, result);
    }
  }
  
  public QueryKraken query(String sql)
  {
    return QueryParserKraken.parse(this, sql).build();
  }
  
  public void query(String sql, Result<QueryKraken> result)
  {
    QueryParserKraken.parse(this, sql).build(result);
  }
  

  public String parseSubQuery(String tableName, String objectColumn, String query)
  {
    return QueryParserKraken.parse(this, tableName, objectColumn, query);
  }

  /**
   * Parse and execute SQL.
   */
  public void exec(String sql, Object []params, Result<Object> result)
  {
    QueryBuilderKraken query = QueryParserKraken.parse(this, sql);
      
    query.build(result.then((q,r)->q.exec(r, params)));
  }

  /**
   * Parse and execute SQL.
   */
  public Object execSync(String sql, Object []params)
  {
    QueryBuilderKraken query = QueryParserKraken.parse(this, sql);
      
    return _services.run(10, TimeUnit.SECONDS,
                    result->query.build(result.then((q,r)->q.exec(r, params))));
  }

  public void findOne(String sql, Object []args, Result<Cursor> result)
  {
    QueryBuilderKraken builder = QueryParserKraken.parse(this, sql);

    if (builder.isTableLoaded()) {
      QueryKraken query = builder.build(); 
      
      query.findOne(result, args);
    }
    else {
      String tableName = builder.getTableName();
      
      // if table is not loaded, load it first, then execute query
      _tableService.loadTable(tableName,
                              result.then((t,r)->buildAndFindOne(builder, args, r)));
      
      return;
    }
    
    // findOne(query, args, result);
  }
  
  private void buildAndFindOne(QueryBuilderKraken builder,
                               Object []args,
                               Result<Cursor> result)
  {
    builder.build(result.then((query,r)->query.findOne(r, args)));
  }
  
  /**
   * Select query returning multiple results.
   */
  public void findAll(String sql, Object []args, Result<Iterable<Cursor>> result)
  {
    QueryBuilderKraken builder = QueryParserKraken.parse(this, sql);
    
    if (builder.isTableLoaded()) {
      QueryKraken query = builder.build(); 
      
      findAll(query, args, result);
    }
    else {
      String tableName = builder.getTableName();
      
      _tableService.loadTable(tableName,
                              result.then((t,r)->findAll(builder.build(), args, r)));
      return;
    }
  }
  
  /**
   * Query implementation for multiple result with the parsed query.
   */
  private void findAll(QueryKraken query, 
                       Object []args, 
                       Result<Iterable<Cursor>> result)
  {
    try {
      TableKraken table = query.table();
      TableKelp tableKelp = table.getTableKelp();
      TablePod tablePod = table.getTablePod();

      if (query.isStaticNode()) {
        RowCursor cursor = tableKelp.cursor();

        query.fillKey(cursor, args);

        int hash = query.calculateHash(cursor);

        //ShardPod node = tablePod.getPodNode(hash);

        if (tablePod.getNode(hash).isSelfCopy() || true) {
          query.findAll(result, args);
          return;
        }
        else {
          //tablePod.get(cursor.getKey(), new FindGetResult(result, query, args));
          result.ok(null);
          return;
        }
      }

      query.findAll(result, args);
    } catch (Exception e) {
      result.fail(e);
    }
  }
  
  /**
   * Select query returning multiple results.
   */
  public void findAllLocal(String sql, Object []args, Result<Iterable<Cursor>> result)
  {
    QueryBuilderKraken builder = QueryParserKraken.parse(this, sql);
    
    QueryKraken query = builder.build(); 
      
    TableKraken table = query.table();
    TableKelp tableKelp = table.getTableKelp();

    RowCursor cursor = tableKelp.cursor();

    query.fillKey(cursor, args);
    
    query.findAllLocal(result, args);
  }

  public void findLocal(String sql, Object[] args, ResultStream<Cursor> result)
  {
    findStream(sql, args, result);
  }
  
  /**
   * Select query returning multiple results.
   */
  public void findStream(String sql, Object []args, ResultStream<Cursor> result)
  {
    QueryBuilderKraken builder = QueryParserKraken.parse(this, sql);
    
    if (builder.isTableLoaded()) {
      QueryKraken query = builder.build(); 
      
      findStream(query, args, result);
    }
    else {
      String tableName = builder.getTableName();
      
      _tableService.loadTable(tableName,
                              result.of((x,r)->{
                                findStream(builder.build(), args, r); 
                              }));
                      //new FindStreamResult(result, builder, args));
    }
  }
  
  /**
   * Query implementation for multiple result with the parsed query.
   */
  private void findStream(QueryKraken query, 
                          Object []args, 
                          ResultStream<Cursor> result)
  {
    try {
      TableKraken table = query.table();
      TableKelp tableKelp = table.getTableKelp();
      TablePod tablePod = table.getTablePod();

      if (query.isStaticNode()) {
        RowCursor cursor = tableKelp.cursor();

        query.fillKey(cursor, args);

        int hash = query.calculateHash(cursor);

        //ShardPod node = tablePod.getPodNode(hash);

        if (tablePod.getNode(hash).isSelfCopy() || true) {
          query.findStream(result, args);
          return;
        }
        else {
          //tablePod.get(cursor.getKey(), new FindGetResult(result, query, args));
          result.ok();
          return;
        }
      }

      query.findStream(result, args);
    } catch (Exception e) {
      result.fail(e);
    }
  }
  
  /**
   * Query implementation for multiple result with the parsed query.
   */
  private void findStreamLocal(QueryKraken query, 
                               Object []args, 
                               ResultStream<Cursor> result)
  {
    try {
      TableKraken table = query.table();
      TableKelp tableKelp = table.getTableKelp();

      RowCursor cursor = tableKelp.cursor();

      query.fillKey(cursor, args);

      query.findStream(result, args);
    } catch (Exception e) {
      result.fail(e);
    }
  }

  /**
   * Maps results to a method callback.
   */
  public void map(MethodRef method, String sql, Object []args)
  {
    QueryBuilderKraken builder = QueryParserKraken.parse(this, sql);
    
    if (builder.isTableLoaded()) {
      QueryKraken query = builder.build(); 
      
      query.map(method, args);
    }
    else {
      String tableName = builder.getTableName();
      
      _tableService.loadTable(tableName,
                              Result.on(t->builder.build().map(method, args)));
    }
  }
  
  //
  // watch methods

  /*
  public void addWatch(String tableName, DatabaseWatch watch)
  {
    TableKraken table = _tableNameMap.get(tableName);
    
    if (table == null) {
      throw new IllegalStateException(L.l("'{0}' is an unknown table.",
                                          tableName));
    }
    
    // table.addWatch(watch);
  }
  */
  
  public void addWatch(DatabaseWatch watch, 
                       TableKraken table, 
                       byte[] key,
                       Result<Cancel> result)
  {
    _watchService.addWatch(watch, table, key, result);
  }

  public void removeWatch(DatabaseWatch watch, TableKraken table, byte[] key)
  {

  }
  
  public void addForeignWatch(TableKraken table, byte[] key, String serverId)
  {
    _watchService.addForeignWatch(table, key, serverId);
  }
  
  public void notifyWatch(TableKraken table, byte[] key)
  {
    _watchService.notifyWatch(table, key);
  }
  
  public void notifyLocalWatch(TableKraken table, byte[] key)
  {
    _watchService.notifyLocalWatch(table, key);
  }
  
  public void notifyWatchOwner(TableKraken table, byte[] key)
  {
    table.getTablePod().notifyWatch(key);
  }
  
  //
  // archive/restore
  //
  
  public ArchiveKrakenManager archive(Path path)
  {
    return new ArchiveKrakenManager(this, path);
  }
  
  public RestoreKraken restore(String tableName, Path path)
  {
    return new RestoreKraken(this, tableName, path);
  }
  
  public RestoreKrakenManager restore(Path path)
  {
    return new RestoreKrakenManager(this, path);
  }

  public boolean isClosed()
  {
    return _isClosed;
  }

  /*
  public void shutdown()
  {
    _tableService.shutdown();
  }
  */
  
  //
  // QA
  //
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _root + "]";
  }
}
