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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.h3.H3;
import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.kelp.BackupKelp;
import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.Row;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.TableListener;
import com.caucho.v5.kelp.query.ColumnExprKelp;
import com.caucho.v5.kelp.query.ExprKelp;
import com.caucho.v5.kraken.archive.ArchiveKraken;
import com.caucho.v5.kraken.query.TableBuilderKraken;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.Murmur64;

import io.baratine.db.DatabaseWatch;
import io.baratine.service.Cancel;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;


/**
 * Interface to a store instance.
 */
public class TableKraken
{
  private TableKelp _tableKelp;
  private KelpManager _kelpBacking;
  private TablePod _tablePod;
  private ExprKelp []_selectExprs;
  private String _tableName;
  private PodHashGenerator _hashGen;
  private KrakenImpl _tableManager;
  private String _sql;
  private OutFactoryH3 _serializerFactory;
  
  public TableKraken(KrakenImpl tableManager,
                     String tableName,
                     TableKelp tableKelp,
                     TableBuilderKraken builder,
                     PodKrakenAmp podManager,
                     KelpManager kelpBacking)
  {
    Objects.requireNonNull(tableManager);    
    Objects.requireNonNull(tableKelp);    
    Objects.requireNonNull(podManager);
    
    _tableName = tableName;

    _tableKelp = tableKelp;
    _kelpBacking = kelpBacking;
    //_tablePod = new TablePodImpl(tableManager, this, podManager);
    _tablePod = new TablePodLocal(tableManager, this, podManager);
    
    _tableManager = tableManager;
    
    _selectExprs = createSelectExpr();
    
    if (builder != null) {
      _sql = builder.getSql();
    }
    
    PodHashGenerator hashGen = null;
    
    if (builder != null) {
      hashGen = builder.buildHashGenerator(tableKelp);
    }
    
    if (hashGen == null) {
      hashGen = new PodHashGenerator.Base();
    }
    
    _hashGen = hashGen;
    
    _serializerFactory = H3.newOutFactory().get();
  }
  
  public String getName()
  {
    return _tableName;
  }

  public String getId()
  {
    return getPodName() + "." + _tableName;
  }
  
  public String getPodName()
  {
    return _tablePod.getPodName();
  }
  
  public byte []getTableKey()
  {
    return getTableKelp().tableKey();
  }
  
  public TableKelp getTableKelp()
  {
    return _tableKelp;
  }

  public OutFactoryH3 serializer()
  {
    return _serializerFactory;
  }

  public long getStartupLastUpdateTime()
  {
    return _kelpBacking.getStartupLastUpdateTime(this);
  }
  
  public String getSql()
  {
    return _sql;
  }

  public KrakenImpl tableManager()
  {
    return _tableManager;
  }

  public ServicesAmp getManager()
  {
    return _tableManager.services();
  }

  public Column getColumn(String name)
  {
    return getTableKelp().getColumn(name);
  }

  public boolean isKeyColumn(Column col)
  {
    if (getKeyColumns().contains(col)) {
      return true;
    }
    else if (getColumn(":key_" + col.name()) != null) {
      return true;
    }
    else if (col.name().startsWith(":")) {
      return true;
    }
    else {
      return false;
    }
  }
  
  public ArrayList<Column> getKeyColumns()
  {
    ArrayList<Column> keys = new ArrayList<>();
    
    TableKelp tableKelp = getTableKelp();
    int keyOffset = tableKelp.getKeyOffset();
    int keyLength = tableKelp.getKeyLength();
    
    for (Column column : tableKelp.getColumns()) {
      if (keyOffset <= column.offset()
          && column.offset() < keyOffset + keyLength) {
        keys.add(column);
      }
    }
    
    return keys;
  }

  public KelpManager getKelpBacking()
  {
    return _kelpBacking;
  }
  
  /**
   * Convenience for calculating the pod hash for a string.
   */
  public static int getPodHash(String key)
  {
    byte []buffer = new byte[16];
    
    int sublen = Math.min(key.length(), 8);
    
    for (int i = 0; i < sublen; i++) {
      buffer[i] = (byte) key.charAt(i);
    }
    
    long hash = Murmur64.generate(Murmur64.SEED, key);
    
    BitsUtil.writeLong(buffer, 8, hash);
    
    return calculatePodHash(buffer, 0, 16);
  }

  /**
   * Returns the hash for the pod. The hash is the top bytes, bit-reversed.
   * 
   * The bit-reversal is used to match the database key indexing, so keys from
   * a node are together in the btree. 
   */
  public int getPodHash(byte[] key)
  {
    return getPodHash(key, 0, _tableKelp.getKeyLength());
  }

  public int getPodHash(RowCursor cursor)
  {
    return getPodHash(cursor.buffer(),
                      _tableKelp.getKeyOffset(),
                      _tableKelp.getKeyLength());
                               
    /*
    return getPodHash(cursor.getBuffer(),
                      _tableKelp.getKeyOffset(),
                      _tableKelp.getKeyLength());
                      */
  }

  public PodHashGenerator getHashGenerator()
  {
    return _hashGen;
  }

  public int getPodHash(byte[] buffer, int offset, int length)
  {
    return _hashGen.getPodHash(buffer, offset, length, _tablePod);
  }
  
  private static int calculatePodHash(byte []buffer, int offset, int length)
  {
    long hash = Murmur64.generate(Murmur64.SEED, buffer, offset, length);

    return (int) (hash & 0xffff);
  }

  public boolean isKeyLocalCopy(byte[] key)
  {
    int hash = calculatePodHash(key, 0, key.length);
    
    return _tablePod.getNode(hash).isSelfCopy();
  }

  public boolean isKeyLocalOwner(byte[] key)
  {
    int hash = calculatePodHash(key, 0, key.length);
    
    return _tablePod.getNode(hash).isSelfOwner();
  }

  public boolean isKeyLocalPrimary(byte[] key)
  {
    int hash = calculatePodHash(key, 0, key.length);
    
    return _tablePod.getNode(hash).isSelfPrimary();
  }

  public void fillHashKey(RowCursor rowCursor, Column keyColumn, String value)
  {
    int offset = keyColumn.offset();
    int length = keyColumn.length();
    
    int sublen = Math.min(value.length(), 8);
    
    byte []buffer = rowCursor.buffer();
    
    Arrays.fill(buffer, offset, offset + length, (byte) 0);
    
    for (int i = 0; i < sublen; i++) {
      buffer[offset + i] = (byte) value.charAt(i);
    }
    
    long hash = Murmur64.generate(Murmur64.SEED, value);
    
    BitsUtil.writeLong(buffer, offset + 8, hash);
  }

  public TablePod getTablePod()
  {
    return _tablePod;
  }

  public boolean isStartupComplete()
  {
    TablePod tablePod = getTablePod();
    
    int count = tablePod.getNodeCount();
    
    for (int i = 0; i < count; i++) {
      TablePodNodeAmp node = tablePod.getNode(i);
      
      if (node.isSelf() && ! node.isStartComplete()) {
        return false;
      }
    }
    
    return true;
  }

  public BackupKelp getBackupCallback()
  {
    return getTablePod().getReplicationCallback();
  }
  
  public ExprKelp []getSelectExprs()
  {
    return _selectExprs;
  }

  public void addListener(TableListener listener)
  {
    _tableKelp.addListener(listener);
  }

  public void removeListener(TableListener listener)
  {
    _tableKelp.removeListener(listener);
  }
  
  public void addWatch(DatabaseWatch watch, 
                       byte []key,
                       Result<Cancel> result)
  {
    _tableManager.addWatch(watch, this, key, result);
  }
  
  public void removeWatch(DatabaseWatch watch, byte []key)
  {
    _tableManager.removeWatch(watch, this, key);
  }

  public void notifyWatch(byte[] key)
  {
    _tableManager.notifyWatch(this, key);
  }

  public void notifyOwner(byte[] key)
  {
    _tableManager.notifyWatchOwner(this, key);
  }

  public void notifyLocal(byte[] key)
  {
    _tableManager.notifyLocalWatch(this, key);
  }
  
  public void addRemoteWatch(byte[] key)
  {
    int hash = getPodHash(key);
    
    getTablePod().addRemoteWatch(key, hash);
  }

  public void addForeignWatch(byte[] key, String serverId)
  {
    _tableManager.addForeignWatch(this, key, serverId);
  }

  public void notifyForeignWatch(byte[] key, String serverId)
  {
    getTablePod().notifyForeignWatch(key, serverId);
  }

  public byte[] getKey()
  {
    return _tableKelp.tableKey();
  }

  public RowCursor cursor()
  {
    RowCursor cursor = _tableKelp.cursor();
    
    cursor.serializer(serializer());

    return cursor;
  }

  /**
   * Gets a row from the table when the primary key is known.
   * 
   * @param cursor the request cursor containing the key
   * @param result return true when the row exists.
   */
  public void get(RowCursor cursor, Result<Boolean> result)
  {
    TablePod tablePod = getTablePod();
    
    int hash = getPodHash(cursor);
    
    if (tablePod.getNode(hash).isSelfCopy()) {
      _tableKelp.getDirect(cursor, result);
    }
    else {
      getTablePod().get(cursor.getKey(),
                        result.then((v,r)->getResult(v, cursor, r)));
    }
  }
  
  public boolean get(RowCursor cursor)
  {
    ResultFuture<Boolean> future = new ResultFuture<>();
    
    get(cursor, future);
    
    return future.get(60, TimeUnit.SECONDS);
  }
  
  private void getResult(Boolean value, 
                         RowCursor cursor, 
                         Result<Boolean> result)
  {
    if (Boolean.TRUE.equals(value)) {
      _tableKelp.getDirect(cursor, (Result) result);
    }
    else {
      result.ok(false);
    }
  }
  
  public void flush(Result<Boolean> result)
  {
    _tableKelp.flush(result);
  }
  
  public void checkpoint(Result<Boolean> result)
  {
    _tableKelp.checkpoint(result);
  }

  public void put(RowCursor cursor, Result<Boolean> result)
  {
    _tableKelp.put(cursor, getBackupCallback(), result);
  }

  public void putLocal(RowCursor cursor, Result<Boolean> result)
  {
    _tableKelp.put(cursor, null, result);
  }

  public void start()
  {
    _tablePod.startRequestUpdates();
  }
  
  private ExprKelp []createSelectExpr()
  {
    Row row = _tableKelp.row();
    Column []columns = row.columns();
    
    ExprKelp []exprs = new ExprKelp[columns.length - 1];
    
    for (int i = 0; i < exprs.length; i++) {
      exprs[i] = new ColumnExprKelp(columns[i + 1]);
    }
    
    return exprs;
  }
  
  public ArchiveKraken archive(Path path)
  {
    return new ArchiveKraken(this, path);
  }
  
  public RestoreKrakenTable restore(Path path)
  {
    return new RestoreKrakenTable(this, path);
  }

  public void shutdown()
  {
    _tableKelp.close(ShutdownModeAmp.GRACEFUL, Result.ignore());
  }

  public boolean isClosed()
  {
    return _tableKelp.isClosed();
  }
  
  @Override
  public String toString()
  {
    TablePod tablePod = _tablePod;
    
    return (getClass().getSimpleName() + "[" + getName()
            + "," + (tablePod != null ? tablePod.getPodName() : "unknown")
            + "," + Hex.toShortHex(getTableKey()) + "]");
  }

  /*
  private class TableWatchImpl implements TableListener {
    private EnvKelp _envKelp;
    private TableKraken _table;
    private DatabaseWatch _watch;
    
    TableWatchImpl(TableKraken table,
                   DatabaseWatch watch)
    {
      _table = table;
      _watch = watch;
    }

    @Override
    public void onPut(byte[] key, TypePut type)
    {
      TableKelp tableKelp = _table.getTableKelp();
      
      RowCursor cursor = tableKelp.cursor();
      
      cursor.setKey(key, 0);
      
      if (tableKelp.getDirect(cursor)) {
        _envKelp.test(cursor);
        _watch.onChange(new CursorKraken(_envKelp, cursor, _selectExprs));
      }
    }
    
    @Override
    public void onRemove(byte[] key, TypePut type)
    {
      
    }
  }
  */

  /*
  private class GetResult extends Result.Wrapper<Boolean,Boolean> {
    private RowCursor _cursor;
    
    GetResult(Result<Boolean> result, RowCursor cursor)
    {
      super(result);
      
      _cursor = cursor;
    }
    
    @Override
    public void complete(Boolean value)
    {
      if (Boolean.TRUE.equals(value)) {
        _tableKelp.getDirect(_cursor, (Result) getNext());
      }
      else {
        getNext().complete(false);
      }
    }
  }
  */
}
