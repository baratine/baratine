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

import io.baratine.db.DatabaseWatch;
import io.baratine.event.EventsSync;
import io.baratine.service.Cancel;
import io.baratine.service.OnInit;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.ServerOnUpdate;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.bartender.pod.PodOnUpdate;
import com.caucho.v5.kelp.TableListener;
import com.caucho.v5.util.HashKey;

/**
 * manager for table listeners.
 */
public class WatchServiceImpl implements WatchService
{
  private final TableManagerKraken _tableManager;
  
  private final HashMap<TableKraken,WatchTable> _tableMap = new HashMap<>();
  //private final HashMap<TableKraken,WatchTable> _tableMapRemote = new HashMap<>();
  
  private final HashMap<TableKraken,Set<HashKey>> _remoteKeys = new HashMap<>();

  private ServiceRef _serviceRef;

  public WatchServiceImpl(TableManagerKraken tableManager)
  {
    Objects.requireNonNull(tableManager);
    
    _tableManager = tableManager;
  }
  
  @OnInit
  public void onStart()
  {
    _serviceRef = ServiceRef.current();
    
    ServicesAmp manager = AmpSystem.currentManager();
    
    EventsSync events = manager.service(EventsSync.class);
    
    events.subscriber(ServerOnUpdate.class, s->onServerUpdate(s));
    events.subscriber(PodOnUpdate.class, p->onPodUpdate(p));
  }
  
  private void onServerUpdate(ServerBartender server)
  {
    if (! server.isUp()) {
      return;
    }
    
    for (Map.Entry<TableKraken,Set<HashKey>> remoteKeys : _remoteKeys.entrySet()) {
      TableKraken table = remoteKeys.getKey();
      
      for (HashKey hashKey : remoteKeys.getValue()) {
        table.addRemoteWatch(hashKey.getHash());
      }
    }
  }
  
  private void onPodUpdate(PodBartender pod)
  {
    for (Map.Entry<TableKraken,Set<HashKey>> remoteKeys : _remoteKeys.entrySet()) {
      TableKraken table = remoteKeys.getKey();

      if (! table.getTablePod().getPodName().equals(pod.name())) {
        continue;
      }
      
      for (HashKey hashKey : remoteKeys.getValue()) {
        table.addRemoteWatch(hashKey.getHash());
      }
    }
  }
  
  public void addWatch(DatabaseWatch watch, 
                       TableKraken table, 
                       byte[] key,
                       Result<Cancel> result)
  {
    WatchTable watchTable = getWatchTable(table);
    
    watchTable.addWatchLocal(watch, key);
    
    // bfs/112b
    // XXX: table.isKeyLocalCopy vs table.isKeyLocalPrimary
    // XXX: using copy because a copy server reads its own data directly
    // XXX: and the timing between a remote watch and the put can result in
    // XXX: the wrong version
    // XXX: (old) notify requires isKeyLocalOwner, otherwise the notify isn't propagated

    if (! table.isKeyLocalCopy(key)) {
      HashKey hashKey = HashKey.create(key);
      Set<HashKey> keys = _remoteKeys.get(table);
      
      if (keys == null) {
        keys = new HashSet<>();
        _remoteKeys.put(table, keys);
      }
        
      if (! keys.contains(hashKey)) {
        table.addRemoteWatch(key);
        
        keys.add(hashKey);
      }
      
      
      // getWatchTable(table).addWatch(watch, key);
    }
    
    WatchHandle handle = new WatchHandle(table, key, watch);
    
    result.ok(_serviceRef.pin(handle).as(Cancel.class));
  }
  
  private WatchTable getWatchTable(TableKraken table)
  {
    return getWatchTable(_tableMap, table);
  }
  
  private WatchTable getWatchTable(HashMap<TableKraken,WatchTable> map,
                                   TableKraken table)
  {
    WatchTable watchTable = map.get(table);
    
    if (watchTable == null) {
      watchTable = new WatchTable(table);
      
      TableListener listener = _serviceRef.pin(watchTable)
                                          .as(TableListener.class);

      table.addListener(listener);
      
      map.put(table, watchTable);
    }
    
    return watchTable;
  }
  
  @Override
  public void removeWatch(DatabaseWatch watch, TableKraken table, byte[] key)
  {
    WatchTable watchTable = getWatchTable(table);

    if (watchTable != null) {
      watchTable.removeWatchLocal(watch, key);
    }
    
    // XXX: remove foreign
  }
  
  /**
   * Adds a watch from a foreign server. Remote notifications will send a copy
   * to the foreign server.
   */
  public void addForeignWatch(TableKraken table, byte []key, String serverId)
  {
    WatchForeign watch = new WatchForeign(key, table, serverId);

    WatchTable watchTable = getWatchTable(table);
    
    watchTable.addWatchForeign(watch, key);
  }
  
  /**
   * Notify local and remote watches for the given table and key
   * 
   * @param table the table with the updated row
   * @param key the key for the updated row
   */
  @Override
  public void notifyWatch(TableKraken table, byte []key)
  {
    WatchTable watchTable = _tableMap.get(table);

    if (watchTable != null) {
      watchTable.onPut(key, TableListener.TypePut.REMOTE);
    }
  }
  
  /**
   * Notify local watches for the given table and key
   * 
   * @param table the table with the updated row
   * @param key the key for the updated row
   */
  public void notifyLocalWatch(TableKraken table, byte []key)
  {
    WatchTable watchTable = _tableMap.get(table);

    if (watchTable != null) {
      watchTable.onPut(key, TableListener.TypePut.LOCAL);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  private class WatchHandle implements Cancel {
    private TableKraken _table;
    private byte []_key;
    private DatabaseWatch _watch;
    
    WatchHandle(TableKraken table, 
                byte []key, 
                DatabaseWatch watch)
    {
      _table = table;
      _key = key;
      _watch = watch;
    }
    
    @Override
    public void cancel()
    {
      removeWatch(_watch, _table, _key);
    }
  }
}
