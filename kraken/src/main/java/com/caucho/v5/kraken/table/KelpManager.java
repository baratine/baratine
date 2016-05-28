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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.kelp.BackupKelp;
import com.caucho.v5.kelp.DatabaseKelp;
import com.caucho.v5.kelp.GetStreamResult;
import com.caucho.v5.kelp.PageServiceSync.PutType;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableBuilderKelp;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.TableListener;
import com.caucho.v5.kraken.query.QueryKraken;
import com.caucho.v5.kraken.query.QueryParserKraken;
import com.caucho.v5.store.temp.TempStore;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;

import io.baratine.db.Cursor;
import io.baratine.service.Result;

/**
 * The local file backing for the store
 */
public class KelpManager
{
  private static final Logger log = Logger.getLogger(KelpManager.class.getName());
  private static final L10N L = new L10N(KelpManager.class);
  
  public static final int STORE_LOW = 2;
  public static final int KEY = 3;
  public static final int KEY_OBJECT_INDEX = 4;
  public static final int VALUE_OBJECT_INDEX = 5;
  public static final int VALUE_HASH = 6;
  public static final int VALUE_LENGTH = 7;
  public static final int VERSION = 8;
  public static final int FLAGS = 9;
  public static final int ACCESSED_TIMEOUT = 10;
  public static final int MODIFIED_TIMEOUT = 11;
  public static final int ACCESSED_TIME = 12;
  public static final int MODIFIED_TIME = 13;
  
  public static final int KEY_LEN = 32;
  public static final int STORE_LOW_LEN = 4;
  
  private static final byte []_zeroKey;
  private static final byte []_onesKey;
  
  private KrakenImpl _tableManager;
  
  private final DatabaseKelp _db;
  //private TableKraken _objectTable;
  
  private TableKelp _metaTable;
  
  private TableKelp _metaUpdateTable;

  private TempStore _tempStore;
  
  public KelpManager(KelpManagerBuilder builder)
  {
    _tableManager = builder.getManager();

    _db = builder.getDatabase();
    
    Objects.requireNonNull(_db);
    
    _tempStore = builder.getTempStore();
    
    _metaUpdateTable = createMetaTableUpdate();
    
    _metaTable = createMetaTable(builder.getPodName());
    Objects.requireNonNull(_metaTable);
    
    // loadMetaTables(_metaTable);
    
    // XXX: check proper serviceRef
    // _metaTable.addListener(new MetaTableListener());
    
  }
  
  public long getMemoryMax()
  {
    return _db.getMemoryMax();
  }
  
  public TempStore getTempStore()
  {
    return _tempStore;
  }
  
  KrakenImpl getManager()
  {
    return _tableManager;
  }
  
  public DatabaseKelp getDatabase()
  {
    return _db;
  }

  /*
  public void start()
  {
    if (_db != null) {
      return;
    }
    
    Path dataDirectory = RootDirectorySystem.getCurrentDataDirectory();
    Path dir = dataDirectory.lookup("kraken");
   
    _builder.path(dir.lookup("store.db"));
    
    _tempStore = _tableManager.getTempStore();
    Objects.requireNonNull(_tempStore);
    
    _builder.tempStore(_tempStore);
    
    _db = _builder.build();
    // createObjectStore();
  }
  */
  
  public TableKelp getMetaTable()
  {
    return _metaTable;
  }
  
  public TableKelp getMetaTableUpdate()
  {
    return _metaUpdateTable;
  }

  Iterable<MetaTableEntry> loadMetaTables()
  {
    TableKelp metaTableKelp = _metaTable;
    
    RowCursor minRow = metaTableKelp.cursor();
    RowCursor maxRow = metaTableKelp.cursor();
    
    minRow.clear();
    
    maxRow.setKeyMax();
    
    ArrayList<MetaTableEntry> entryList = new ArrayList<>();

    for (RowCursor cursor : metaTableKelp.queryRange(minRow, maxRow, null)) {
      byte []key = new byte[32];

      //cursor.getBytes(KEY, key, 0);
      cursor.getBytes(1, key, 0);
      
      String name = cursor.getString(2);
      String sql = cursor.getString(3);
      long version = cursor.getVersion();
      
      if (sql == null || sql.equals("")) {
        log.warning(L.l("Broken meta-table: {0} {1}\n  sql: {2}",
                        Hex.toShortHex(key), name, sql));
        
        continue;
      }
      
      entryList.add(new MetaTableEntry(key, name, sql, version));
    }
    
    return entryList;
    /*
    QueryBuilderKraken queryBuilder
    = QueryParserKraken.parse(_tableManager, name, sql);
  
  CreateQuery query = (CreateQuery) queryBuilder.build(); 

  query.restoreTable();
  */
  }
  
  TableKelp createMetaTable(String podName)
  {
    // String podName = _tableManager.getClusterPodName();
    
    TableBuilderKelp builder = _db.createTable(podName + ".kraken_meta_table");
    
    builder.startKey();
    builder.columnBytes("table_key", TableKelp.TABLE_KEY_SIZE);
    builder.endKey();
    
    builder.columnString("table_name");
    builder.columnString("sql");
    
    TableKelp tableKelp = builder.build();
    
    return tableKelp;
    
    // TableKraken tableKraken = _tableManager.createTableImpl(tableKelp, null);
    
    // return tableKraken;
  }
  
  TableKelp createMetaTableUpdate()
  {
    String podName = "local";
    
    TableBuilderKelp builder = _db.createTable(podName + ".kraken_meta_table_update");
    
    builder.startKey();
    builder.columnBytes("table_key", TableKelp.TABLE_KEY_SIZE);
    builder.endKey();
    
    builder.columnInt64("update_time");
    
    TableKelp tableKelp = builder.build();
    
    return tableKelp;
    
    //TableKraken tableKraken = _tableManager.createTableImpl(tableKelp, null);
    
    //return tableKraken;
  }
  
  /**
   * Table callbacks.
   * @param result 
   */
  public void addTable(TableKelp tableKelp,
                       String sql,
                       BackupKelp backupCb)
  {
    // TableKelp tableKelp = tableKraken.getTableKelp();
   
    RowCursor cursor = _metaTable.cursor();
    
    cursor.setBytes(1, tableKelp.tableKey(), 0);
    cursor.setString(2, tableKelp.getName());
    cursor.setString(3, sql);

    //BackupCallback backupCb = _metaTable.getBackupCallback();
    
    //_metaTable.getTableKelp().put(cursor, backupCb, 
    //                              new CreateTableCompletion(tableKraken, result));
    
    // _metaTable.getTableKelp().put(cursor, backupCb, 
    //                               result.from(v->tableKraken));
    
    _metaTable.put(cursor, backupCb, Result.ignore());
    
    // tableKraken.start();
  }
  
  public Iterable<TableKelp> getTables()
  {
    return _db.getTables();
  }
  
  public boolean waitForFlush()
  {
    // return _objectTable.getTableKelp().waitForComplete();
    
    return true;
  }

  /*
  public void put(byte []tableKey, 
                  StreamSource ss,
                  PutType putType,
                  Result<Boolean> result)
  {
    TableKraken table = _tableManager.getTable(tableKey);

    if (table != null) {
      put(table.getTableKelp(), ss, putType, result);
    }
    else {
      loadTable(tableKey, result.from((t,r)->put(t.getTableKelp(), ss, putType, r)));
    }
  }
  
  private void loadTable(byte []tableKey, Result<TableKraken> result)
  {
    TableKraken table = _tableManager.getTable(tableKey);
    
    if (table != null) {
      Result.complete(result, table);
    }
    else {
      TablePod tablePod = _metaTable.getTablePod();
    
      tablePod.get(tableKey, result.from((v,r)->loadTableLocal(tableKey, r)));
    }
  }
  */
  
  /**
   * Puts data to the local table. Used to update the copy from the remote.
   * 
   * @param table the table to be updated
   * @param ss stream to the row data
   * @param putType distinguishes original puts from replication
   * @param result called on completion
   */
  // XXX: permissions
  public void put(TableKelp table, 
                StreamSource ss, 
                PutType putType,
                Result<Boolean> result)
  {
    if (ss == null) {
      result.ok(false);
      return;
    }

    try (InputStream is = ss.getInputStream()) {
      if (is.available() < 1) {
        System.err.println("BAD_STREAM: " + is + " " + ss + " " + System.identityHashCode(ss) + " " + table); 
        throw new IOException(L.l("unexpected empty stream {0} for {1} [{2}]",
                                  is, table, AmpSystem.currentManager()));
      }
      
      table.put(is, putType, result);
    } catch (Throwable e) {
      e.printStackTrace();
      result.fail(e);
    }
  }
  
  public void remove(byte []tableKey, 
                     byte []rowKey,
                     long version,
                     Result<Boolean> result)
  {
    TableKraken table = _tableManager.getTable(tableKey);

    if (table != null) {
      RowCursor cursor = table.cursor();
      
      cursor.setKey(rowKey, 0);
      cursor.setVersion(version);

      BackupKelp backup = null;
      
      table.getTableKelp().remove(cursor, backup, result);
    }
    else {
      result.ok(false);
    }
  }
  
  /*
  public void get(byte []tableKey, byte []key, 
                   Result<StreamSource> result)
  {
    TableKelp table = _db.findTable(tableKey);
    
    if (table != null) {
      get(table, key, result);
    }
    else {
      // load the table from the system, if missing
      
      TablePod tablePod = _metaTable.getTablePod();

      tablePod.get(tableKey,
                   result.from((TableKraken t, Result<StreamSource> r)->{
                                 get(t.getTableKelp(), key, r);
                               })
                         .from((v,r)->loadTableLocal(key,r)));
    }
  }
  */
  
  public void getLocal(byte []tableKey, 
                       byte []key,
                       long version,
                       Result<GetStreamResult> result)
  {
    TableKelp table = _db.findTable(tableKey);

    if (table != null) {
      getStream(table, key, version, result);
    }
    else {
      _db.loadTable(tableKey, 
                    result.then((t,r)->getLocalImpl(t, tableKey, key, version, r)));
    }
  }
  
  private void getLocalImpl(TableKelp table, byte []tableKey, byte []key,
                            long version,
                            Result<GetStreamResult> result)
  {
    if (table != null) {
      getStream(table, key, version, result);
    }
    else {
      KrakenException exn = new KrakenException(L.l("TableKraken[{0}] is an unknown table for remote get Key[{1}] (server={2})",
                                                    Hex.toShortHex(tableKey),
                                                    Hex.toShortHex(key),
                                                    AmpSystem.currentManager()));
      exn.fillInStackTrace();
      
      // XXX: result needs to distinguish between an authoritative null and
      // a missing file null
      result.fail(exn);
      
      //result.completed(null);
    }
  }
  
  
  private void getStream(TableKelp table, 
                         byte []key, 
                         long version,
                         Result<GetStreamResult> result)
  {
    RowCursor cursor = table.cursor();
    
    cursor.setKey(key, 0);
    cursor.setVersion(version);
    
    table.getStream(cursor, result);
  }

  /**
   * Removes a cache entry
   */
  public void removeExpired()
  {
    /*
    try {
      TableKelp objectTableKelp = _objectTable.getTableKelp();
      
      RowCursor minRow = objectTableKelp.cursor();
      RowCursor maxRow = objectTableKelp.cursor();

      minRow.clear();
      maxRow.setKeyMax();
      
      long now = CurrentTime.getCurrentTime();
      
      IfOldPredicate ifOld = new IfOldPredicate(now);
      
      objectTableKelp.removeRange(minRow, maxRow, ifOld, null);
    } catch (Exception e) {
      e.printStackTrace();
    }
    */
  }

  public void getUpdates(TableKraken tableKraken,
                         int podIndex,
                         long accessTime,
                         Result<Boolean> result)
  {
    /// XXX: problem
    try {
      TableKelp tableKelp = tableKraken.getTableKelp();
      
      RowCursor minRow = tableKelp.cursor();
      RowCursor maxRow = tableKelp.cursor();

      minRow.clear();
      maxRow.setKeyMax();
      
      Predicate<RowCursor> ifNew = new IfUpdatePredicate(tableKraken, podIndex, accessTime);

      BackupKelp replCb = tableKraken.getBackupCallback();
      
      for (RowCursor cursor : tableKelp.queryRangeForUpdate(minRow, maxRow, ifNew)) {
        if (cursor.isRemoved()) {
          // XXX: getVersion()
          
          replCb.onRemove(tableKelp.tableKey(), cursor.getKey(), cursor.getVersion(), Result.ignore());
        }
        else {
          // XXX: update when the backup is fixed to return for a single value
          StreamSource ss = cursor.toStream();
          
          replCb.onPut(tableKelp.tableKey(), cursor.getKey(), ss, Result.ignore());
          
          // XXX: timing issues with result returning before put completes
        }
      }
      
      result.ok(true);
        
      //onLoad.completed();
    } catch (Throwable e) {
      e.printStackTrace();
      //onLoad.failed(e);
      
      result.fail(e);
    }
  }
  
  //
  // SQL
  //
  
  /*
  @Override
  public void createTable(String name, String sql, Result<TableKraken> result)
  {
    String tableName = getTableName(name);
    String podName = getPodName(name);
    
    TableKelp table = _db.getTable(tableName);
    
    if (table != null) {
      result.complete(_tableManager.createTableImpl(table, null));
      
      return;
    }
    
    QueryParserKraken parser = new QueryParserKraken(_tableManager, sql);
    
    parser.setPodName(podName);
    
    QueryBuilderKraken builder = parser.parse();
    
    QueryKraken query = builder.build();
    
    query.exec(result.from(v->_tableManager.getTable(tableName)));
  }
  */
  
  private String getTableName(String name)
  {
    if (name.indexOf('.') > 0) {
      return name;
    }
    
    /*
    String podName = BartenderSystem.getCurrentPod().name();
    String clusterId = BartenderSystem.getCurrentSelfServer().getClusterId();
    */
    // XXX:
    String podName = "pod";
    String clusterId = "cluster";
    
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
    
    /*
    String podName = BartenderSystem.getCurrentPod().name();
    String clusterId = BartenderSystem.getCurrentSelfServer().getClusterId();
    */
    
    // XXX:
    String podName = "pod";
    String clusterId = "cluster";
    
    if (podName.equals(clusterId)) {
      podName = podName + "_hub";
    }
    
    return podName;
  }
                                  
  
  /**
   * Select query returning multiple results.
   */
  /*
  public void findAll(String sql, Object []args, Result<Iterable<Cursor>> result)
  {
    QueryBuilderKraken builder = QueryParserKraken.parse(_tableManager, sql);
    
    if (builder.isTableLoaded()) {
      QueryKraken query = builder.build(); 
      
      findAll(query, args, result);
    }
    else {
      String tableName = builder.getTableName();
      
      loadTableByName(tableName,
                      result.from((t,r)->findAll(builder.build(), args, r)));
      return;
    }
  }
  */
  
  /**
   * Select query returning multiple results.
   */
  /*
  public void findStream(String sql, Object []args, ResultSink<Cursor> result)
  {
    QueryBuilderKraken builder = QueryParserKraken.parse(_tableManager, sql);
    
    if (builder.isTableLoaded()) {
      QueryKraken query = builder.build(); 
      
      findStream(query, args, result);
    }
    else {
      String tableName = builder.getTableName();
      
      loadTableByName(tableName,
                      new FindStreamResult(result, builder, args));
    }
  }
  */
  
  /**
   * Select query returning multiple results.
   */
  /*
  public void findAllLocal(String sql, Object []args, Result<Iterable<Cursor>> result)
  {
    QueryBuilderKraken builder = QueryParserKraken.parse(_tableManager, sql);
    
    QueryKraken query = builder.build(); 
      
    TableKraken table = query.getTable();
    TableKelp tableKelp = table.getTableKelp();

    RowCursor cursor = tableKelp.cursor();

    query.fillKey(cursor, args);
    
    query.findAll(result, args);
  }
  */
  
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

      /*
    RowCursor cursor = tableKelp.cursor();

    query.fillKey(cursor, args);

    tablePod.get(cursor.getKey(), new FindGetResult(result, query, args));
       */
    } catch (Exception e) {
      result.fail(e);
    }
  }
  
  /**
   * Query implementation for multiple result with the parsed query.
   */
  /*
  private void findStream(QueryKraken query, 
                          Object []args, 
                          ResultSink<Cursor> result)
  {
    try {
      TableKraken table = query.getTable();
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
          result.complete(null);
          return;
        }
      }

      query.findStream(result, args);
    } catch (Exception e) {
      Result.fail(result, e);
    }
  }
  */
  
  /**
   * Query implementation for multiple result with the parsed query.
   */
  /*
  private void findStreamLocal(QueryKraken query, 
                               Object []args, 
                               ResultSink<Cursor> result)
  {
    try {
      TableKraken table = query.getTable();
      TableKelp tableKelp = table.getTableKelp();

      RowCursor cursor = tableKelp.cursor();

      query.fillKey(cursor, args);

      query.findStream(result, args);
    } catch (Exception e) {
      result.fail(e);
    }
  }
  */
  
  /*
  private void loadTableByName(String tableName,
                              Result<TableKraken> result)
  {
    TablePod tablePod = _metaTable.getTablePod();
    
    tablePod.findByName(tableName,
                        result.from((key,r)->loadTablePod(key, r)));
  }
  */
  
  /**
   * Loads a table from the current pod.
   */
  /*
  private void loadTablePod(byte []tableKey, Result<TableKraken> result)
  {
    if (tableKey == null) {
      result.complete(null);
      return;
    }
    
    TableKraken table = _tableManager.getTable(tableKey);
    
    if (table != null) {
      result.complete(table);
      return;
    }

    TablePod tablePod = _metaTable.getTablePod();
    
    tablePod.get(tableKey, result.from((x,r)->{ loadTableLocal(tableKey,r); }));
  }
  */
  
  /**
   * Loads and creates a table from the local meta-table.
   */
  public void loadTableLocal(byte []tableKey, Result<TableKraken> result)
  {
    if (tableKey == null) {
      result.ok(null);
      return;
    }
    
    TableKraken table = _tableManager.getTable(tableKey);

    if (table != null) {
      result.ok(table);
      return;
    }

    RowCursor cursor = _metaTable.cursor();
    cursor.setBytes(1, tableKey, 0);
    
    // XXX: direct
    if (_metaTable.getDirect(cursor)) {
      String name = cursor.getString(2);
      String sql = cursor.getString(3);

      if (sql == null || sql.equals("")) {
        throw new IllegalStateException(L.l("Broken table: '{0}'\n  sql: {1} {2}",
                                            name, sql,
                                            AmpSystem.currentManager()));
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

  public long getStartupLastUpdateTime(TableKraken table)
  {
    TableKelp metaUpdateTable = _metaUpdateTable;
    
    if (metaUpdateTable == null) {
      return 0;
    }
    
    RowCursor cursor = metaUpdateTable.cursor();
    
    cursor.setBytes(1, table.getKey(), 0);
    
    if (metaUpdateTable.getDirect(cursor)) {
      return cursor.getLong(2);
    }
    else {
      return 0;
    }
  }
  
  public StreamSource dump(TableKraken table)
  {
    return new SaveTableStreamSource(table);
  }
  
  
  public void saveMetaTableUpdate(byte []tableKey, 
                                  long now)
  {
    RowCursor cursor = _metaUpdateTable.cursor();
    
    cursor.setBytes(1, tableKey, 0);
    cursor.setLong(2, now);
        
    _metaUpdateTable.put(cursor, Result.ignore());
  }

  public void close()
  {
    // saveMetaTableUpdate();
    
    _db.close();
  }
  
  //
  // callbacks
  //
  
  private static class IfUpdatePredicate implements Predicate<RowCursor> {
    private final TableKraken _table;
    private final int _podNode;
    private final long _time;
    
    IfUpdatePredicate(TableKraken table,
                      int podNode,
                      long time)
    {
      _table = table;
      _podNode = podNode;
      _time = time;
    }
    
    @Override
    public boolean test(RowCursor cursor)
    {
      int hash = _table.getPodHash(cursor);

      /*
      //NodePodAmp node = _table.getTablePod().getPodNode(hash);

      if (node == null || node.nodeIndex() != _podNode) {
        return false;
      }
      */
      
      int nodeIndex = _table.getTablePod().nodeIndex();
      
      if (nodeIndex != _podNode) {
        return false;
      }
      
      return (_time <= cursor.getStateTime());
    }
  }
  
  private class MetaTableListener implements TableListener {
    @Override
    public void onPut(byte[] key, TypePut type)
    {
      loadTableLocal(key, null);
    }
    
    @Override
    public void onRemove(byte[] key, TypePut type)
    {
      // loadTable(key, null);
    }
  }
  
  static class MetaTableEntry {
    private byte []_key;
    private String _name;
    private String _sql;
    private long _version;
    
    MetaTableEntry(byte []key, 
                   String name, 
                   String sql,
                   long version)
    {
      _key = key;
      _name = name;
      _sql = sql;
      _version = version;
    }

    public String getName()
    {
      return _name;
    }

    public String getSql()
    {
      return _sql;
    }

    public byte[] getKey()
    {
      return _key;
    }
  }
  
  static {
    _zeroKey = new byte[32];
    Arrays.fill(_zeroKey, (byte) 0);
    
    _onesKey = new byte[32];
    Arrays.fill(_onesKey, (byte) 0xff);
  }
}
