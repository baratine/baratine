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

package com.caucho.v5.kraken.cluster;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.ServerOnUpdate;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.bartender.pod.PodOnUpdate;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.kelp.GetStreamResult;
import com.caucho.v5.kelp.PageServiceSync.PutType;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kraken.query.CursorKraken;
import com.caucho.v5.kraken.query.QueryBuilderKraken;
import com.caucho.v5.kraken.query.QueryException;
import com.caucho.v5.kraken.query.QueryKraken;
import com.caucho.v5.kraken.query.QueryParserKraken;
import com.caucho.v5.kraken.query.UpdateQuery;
import com.caucho.v5.kraken.table.ClusterServiceKraken;
import com.caucho.v5.kraken.table.KelpManager;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.KrakenImpl;
import com.caucho.v5.kraken.table.TablePod;
import com.caucho.v5.store.temp.TempStore;
import com.caucho.v5.store.temp.TempWriter;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;

import io.baratine.db.Cursor;
import io.baratine.event.EventsSync;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.Services;
import io.baratine.service.ServiceRef;

/**
 * Service for handling the distributed cache
 */
@Service("/cluster-kraken")
public class ClusterServiceKrakenImpl implements ClusterServiceKraken
{
  private static final L10N L = new L10N(ClusterServiceKrakenImpl.class);
  
  private static final Logger log
    = Logger.getLogger(ClusterServiceKrakenImpl.class.getName());

  private final KrakenImpl _tableManager;
  
  private final ClientKrakenImpl _clientKraken;
  
  private HashMap<Long,ChunkedPut> _chunkMap = new HashMap<>();

  ClusterServiceKrakenImpl(KrakenImpl tableManager,
                           ClientKrakenImpl clientKraken)
  {
    _tableManager = tableManager;
    
    _clientKraken = clientKraken;
  }
  
  private KelpManager getLocalBacking()
  {
    return _tableManager.getKelpBacking();
  }
  
  //
  //
  //
  
  //@OnInit
  public void start()
  {
    Services manager = AmpSystem.currentManager();
    
    EventsSync events = manager.service(EventsSync.class);
    /*
    manager.service("event:///" + ServerOnUpdate.class.getName())
           .subscribe(new ServerListener());
           */
    events.subscriber(ServerOnUpdate.class, new ServerListener());
    
    ServiceRef selfRef = ServiceRef.current();
    
    PodOnUpdate listener = selfRef.pin(new PodUpdateListener())
                                  .as(PodOnUpdate.class);
    /*
    manager.service("event:///" + PodOnUpdate.class.getName())
           .subscribe(listener);
           */
    events.subscriber(PodOnUpdate.class, listener);
  }
  
  private void updatePod(PodBartender pod)
  {
    
  }

  //
  // messages
  //
  
  /**
   * Get a row from a table.
   * 
   * The version is for a conditional get, where the caller has an existing
   * row, and the full data should only be returned if the version has changed.
   * 
   *  @param tableKey - the key for the table
   *  @param key - the key for the row to be returned
   *  @param version - the version for the caller's current copy of the row
   */
  
  @Override
  public void get(byte []tableKey, 
                  byte []key,
                  long version,
                  Result<GetStreamResult> result)
  {
    _tableManager.getKelpBacking().getLocal(tableKey, key, version, 
                                            result.of(gs->getImpl(gs)));
  }
  
  private GetStreamResult getImpl(GetStreamResult getStream)
  {
    StreamSource ss = getStream.getStreamSource();
    
    if (ss != null) {
      return new GetStreamResult(getStream.isFound(), new StreamSource(ss));
    }
    else {
      return null;
    }
  }

  //
  // put methods
  //
  
  /**
   * Puts the row for replication.
   * 
   * Put is only called for the owning servers. Each owner and its backups
   * get a copy.
   */
  @Override
  public void put(byte []tableKey, 
                  StreamSource rowSource,
                  Result<Boolean> result)
  {
    putImpl(tableKey, rowSource, PutType.PUT, result);
  }
  
  
  @Override
  public void putChunk(byte[] tableKey,
                       long putId,
                       long length, int index, int chunkSize,
                       StreamSource source,
                       Result<Boolean> result)
  {
    ChunkedPut chunkedPut = _chunkMap.get(putId);
    
    if (chunkedPut == null) {
      TempStore store = getLocalBacking().getTempStore();
      TempWriter out = store.openWriter();
      
      chunkedPut = new ChunkedPut(length, chunkSize, out);
      
      _chunkMap.put(putId, chunkedPut);
    }
    
    chunkedPut.put(index, source);
    
    if (chunkedPut.isComplete()) {
      _chunkMap.remove(putId);
      
      putImpl(tableKey, chunkedPut.openRead(), PutType.PUT, Result.ignore());
    }
    
    result.ok(true);;
  }
  
  private void putImpl(byte []tableKey,
                       StreamSource ss,
                       PutType putType,
                       Result<Boolean> result)
  {
    TableKraken table = _tableManager.getTable(tableKey);
    
    KelpManager kelpManager = _tableManager.getKelpBacking();

    if (table != null) {
      kelpManager.put(table.getTableKelp(), ss, putType, result);
    }
    else {
      _tableManager.loadTable(tableKey, result.of((t,r)->kelpManager.put(t.getTableKelp(), ss, putType, r)));
    }
  }
  
  @Override
  public void update(Result<Integer> result, int node, String sql, Object[] args)
  {
    try {
      QueryBuilderKraken builder = QueryParserKraken.parse(_tableManager, sql);
      
      builder.setLocal(node);
      
      UpdateQuery query = (UpdateQuery) builder.build();
      
      query.execLocal((Result) result, args);
    } catch (Throwable e) {
      result.fail(e);
    }
  }

  /**
   * Removes a row identified by its key. If the current value has a later
   * version than the request, the request is ignored.
   * 
   * @param tableKey key for the table
   * @param rowKey key for the row
   * @param version version for the delete
   * @param result true if deleted
   */
  @Override
  public void remove(byte []tableKey, byte []rowKey,
                     long version,
                     Result<Boolean> result)
  {
    _tableManager.getKelpBacking().remove(tableKey, rowKey, version, result);
  }
  
  //
  // query
  //
  
  /**
   * Find a table by its name.
   */
  @Override
  public void find(byte []tableKey, 
                   Object arg, 
                   Result<byte[]> result)
  {
    TableKraken table = _tableManager.getTable(tableKey);
    
    if (table == null) {
      throw new QueryException(L.l("'{0}' is an unknown table.",
                                   Hex.toShortHex(tableKey)));
    }
      
    String sql = "select_local table_key from kraken_meta_table where table_name=?";
      
    QueryBuilderKraken builder = QueryParserKraken.parse(_tableManager, sql);
      
    QueryKraken query = builder.build();
    
    query.findOne(result.of(cursor->findKeyResult(cursor)), arg);
  }

  private byte []findKeyResult(Cursor value)
  {
    if (value instanceof CursorKraken) {
      CursorKraken cursor = (CursorKraken) value;
      RowCursor rowCursor = cursor.getRowCursor();
      
      return rowCursor.getKey();
    }
    else {
      return null;
    }
  }
  
  @Override
  public void findOne(Result<byte[]> result,
                      byte []tableKey,
                      String sql, 
                      Object[] args)
  {
    TableKraken table = _tableManager.getTable(tableKey);
      
    if (table == null) {
      throw new QueryException(L.l("'{0}' is an unknown table.",
                                   Hex.toShortHex(tableKey)));
    }
      
    QueryBuilderKraken builder = QueryParserKraken.parse(_tableManager, sql);
      
    QueryKraken query = builder.build();
      
    query.findOne(new FindKeyResult(result), args);
  }
  
  /**
   * Find all select values using a map/reduce.
   */
  @Override
  public void findAll(Result<Iterable<byte[]>> result,
                      byte []tableKey,
                      String sql, 
                      Object[] args)
  {
    try {
      TableKraken table = _tableManager.getTable(tableKey);
      
      if (table == null) {
        throw new QueryException(L.l("'{0}' is an unknown table.",
                                     Hex.toShortHex(tableKey)));
      }
      
      QueryBuilderKraken builder = QueryParserKraken.parse(_tableManager, sql);
      
      QueryKraken query = builder.build();
      
      query.findAllLocalKeys(result, args);
    } catch (Throwable e) {
      result.fail(e);
    }
  }
  
  //
  // watch
  //
  
  @Override
  public void addWatch(byte []tableKey,
                       byte []key, 
                       String serverId)
  {
    _tableManager.loadTable(tableKey, 
                            Result.onOk(table->addWatchImpl(table, tableKey, key, serverId)));
  }
  
  private void addWatchImpl(TableKraken table,
                            byte []tableKey,
                            byte []key,
                            String serverId)
  {
    if (table == null) {
      throw new QueryException(L.l("'{0}' is an unknown table.",
                                   Hex.toShortHex(tableKey)));
    }
    
    table.addForeignWatch(key, serverId);
  }
  
  @Override
  public void notifyWatch(byte []tableKey,
                          byte []key)
  {
    TableKraken table = _tableManager.getTable(tableKey);

    if (table == null) {
      return;
    }
    
    _tableManager.notifyWatch(table, key);
  }
  
  @Override
  public void notifyLocalWatch(byte []tableKey,
                                 byte []key)
  {
    TableKraken table = _tableManager.getTable(tableKey);
    
    if (table == null) {
      return;
    }
    
    _tableManager.notifyLocalWatch(table, key);
  }

  //
  // startup
  //

  /**
   * Asks for updates from the message
   */
  @Override
  public void requestStartupUpdates(String from,
                                    byte []tableKey,
                                    int podIndex,
                                    long deltaTime,
                                    Result<Boolean> cont)
  {
    if (log.isLoggable(Level.FINEST)) {
      log.finest("CacheRequestUpdates " + from + " shard=" + podIndex
                 + " delta=" + deltaTime);
    }
    
    // ChampCloudManager cloudManager = ChampCloudManager.create();
    
    //ServiceManagerAmp rampManager = AmpSystem.getCurrentManager();
    
    //String address = "champ://" + from + ClusterServiceKraken.UID;
    
    //ClusterServiceKraken peerService = rampManager.lookup(address).as(ClusterServiceKraken.class);

    // ArrayList<CacheData> entryList = null;
    long accessTime = CurrentTime.currentTime() + deltaTime;

    // int count = 0;
    
    TablePod tablePod = _clientKraken.getTable(tableKey);
    
    if (tablePod == null) {
      // This server does not have any information about the table. 
      if (log.isLoggable(Level.FINEST)) {
        log.finest(L.l("{0} is an unknown table key ({1})",
                      Hex.toShortHex(tableKey),
                      BartenderSystem.getCurrentSelfServer()));
      }
      
      cont.ok(true);
      return;
    }
    
    tablePod.getUpdatesFromLocal(podIndex, accessTime, cont);
    
                                 // new ResultUpdate(peerService, cont));

    // start reciprocating update request
    // tablePod.startRequestUpdates();
  }
  
  //
  // time update
  //
  
  private class ChunkedPut {
    private final long _length;
    private final int _chunkSize;
    
    private final TempWriter _out;
    
    private final boolean []_completed;
    
    public ChunkedPut(long length, 
                      int chunkSize,
                      TempWriter out)
    {
      _length = length;
      _chunkSize = chunkSize;
      _out = out;
      
      int items = (int) ((length + chunkSize - 1) / chunkSize);
      
      _completed = new boolean[items];
    }

    public void put(int index, StreamSource source)
    {
      int offset = index * _chunkSize;
      int length = (int) Math.min(_length - offset, _chunkSize);

      try (InputStream is = source.getInputStream()) {
        _out.writeStream(offset, is, length);
      } catch (IOException e) {
        e.printStackTrace();
      }
      
      _completed[index] = true;
    }
    
    public boolean isComplete()
    {
      for (int i = 0; i < _completed.length; i++) {
        if (! _completed[i]) {
          return false;
        }
      }
      
      return true;
    }
    
    StreamSource openRead()
    {
      //return _out.getStreamSource();
      return _out.getStreamSource();
    }
  }
  
  private class ServerListener implements ServerOnUpdate {
    //
    // callbacks from the server start/stop
    //

    /**
     * Called when a server starts.
     */
    @Override
    public void onServerUpdate(ServerBartender server)
    {
      if (server.isUp()) {
        _tableManager.onServerStart();
      }
      /*
      if (server.isHub()) {
        _tableManager.clearLeases();
      }
      */
    }
  }
  
  private static class FindKeyResult extends Result.Wrapper<Cursor,byte[]>
  {
    FindKeyResult(Result<byte[]> result)
    {
      super(result);
    }

    @Override
    public void ok(Cursor value)
    {
      if (value instanceof CursorKraken) {
        CursorKraken cursor = (CursorKraken) value;
        RowCursor rowCursor = cursor.getRowCursor();
        
        delegate().ok(rowCursor.getKey());
      }
      else {
        delegate().ok(null);
      }
    }
  }
  
  private class PodUpdateListener implements PodOnUpdate {
    @Override
    public void onUpdate(PodBartender pod)
    {
      updatePod(pod);
    }
  }
}
