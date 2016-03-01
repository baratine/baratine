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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.RandomUtil;
/**
 * Manages the distributed cache
 */
public final class ClientKrakenImpl implements ClientKraken
{
  private static final L10N L = new L10N(ClientKrakenImpl.class);
  private static final Logger log
    = Logger.getLogger(ClientKrakenImpl.class.getName());
  
  private final TableManagerKraken _tableManager;
  
  private final ServerBartender _selfServer;
  //private int _selfIndex; // server index for this server
  
  //private HashMap<HashKey,TablePodImpl> _tableMap = new HashMap<>();
  
  private ClusterServiceKrakenImpl _rowServiceImpl;
  private ClusterServiceKraken _rowService;
  
  // private KrakenStartupServiceImpl _startupServiceImpl;
  // private KrakenStartupService _startupService;
  // private CacheReplicationActor _cacheReplicationActor;
  
  // private CacheBackupEngine _backupEngine = new AbstractCacheBackupEngine();
  
  private boolean _isTriadUpdateComplete;

  // private TriadShard<RowClusterService> _triadFirst;

  // private TriadShard<RowClusterService> _triadRemoteFirst;
  
  private int _putChunkMin = 256 * 1024;
  
  private PodKraken _podManager;
  private String _address;
  
  private AtomicLong _putSequence = new AtomicLong();
  private BartenderSystem _system;

  // private RowServiceHub _triadRemoteAll;

  public ClientKrakenImpl(TableManagerKraken tableManager)
  {
    Objects.requireNonNull(tableManager);
    
    _tableManager = tableManager;
    
    /// should be server id + timestamp
    _putSequence.set(RandomUtil.getRandomLong());
    
    _system = BartenderSystem.current();
    _selfServer = tableManager.serverSelf();
    
    String podId = "cluster_hub";
    
    PodBartender pod = _system.findPod(podId);
    
    if (! pod.isValid()) {
      throw new IllegalStateException(L.l("{0} is an unknown pod {1}", podId, pod));
    }
    
    ServiceManagerAmp rampManager = AmpSystem.currentManager();
    _podManager = new PodKraken(tableManager, rampManager, pod);
    
    startBind();
  }
  
  PodKraken getShardManager()
  {
    return _podManager;
  }

  public TableManagerKraken getTableManager()
  {
    return _tableManager;
  }
  
  public void startBind()
  {
    _rowServiceImpl = new ClusterServiceKrakenImpl(_tableManager,
                                                   this);
    
    ServiceRefAmp storeServiceRef = _tableManager.getStoreServiceRef();
    
    ServiceRefAmp serviceRef = storeServiceRef.pin(_rowServiceImpl);
    
    _rowService = serviceRef.bind("public://" + ClusterServiceKraken.UID)
                            .as(ClusterServiceKraken.class);
  }
  
  @Override
  public void start()
  {
    //_selfServer = BartenderSystem.getCurrent().getSelfServerImpl();
    
    _address = "bartender://" + _selfServer.getId() + ClusterServiceKraken.UID;

    initProxy();

    _rowService.start();

    startRequestUpdates();
  }
  
  private KelpManager getKelpBacking()
  {
    return _tableManager.getKelpBacking();
  }

  private void initProxy()
  {
  }

  @Override
  public PodKraken getPod(String podName)
  {
    if (podName == null) {
      return _podManager;
    }
    
    PodBartender pod = _system.findPod(podName);
    
    if (pod == null) {
      throw new IllegalStateException(L.l("{0} is an unknown pod", podName));
    }
    
    ServiceManagerAmp rampManager = AmpSystem.currentManager();
    
    return new PodKraken(_tableManager, rampManager, pod);
  }

  /*
  public void init(ObjectStore<?,?> store)
  {
    String masterAddress = store.getConfiguration().getStoreOwner();
    
    if (masterAddress != null) {
      initDependentStore(store, masterAddress);
    }
  }
  
  private void initDependentStore(ObjectStore<?,?> store,
                                  String master)
  {
    ClusterBartender cluster = _selfServer.getRoot().findCluster(master);
  
    if (cluster != null) {
      ServerBartender owner = cluster.getRacks()[0].getServer(0);

      initDependentStore(store, owner);
    }
  }
  */
  
  /*
  private void initDependentStore(EntityStoreKraken store,
                                  ServerBartender owner)
  {
    _rowService.addDependentStore(store, owner);
    
    //registerListener(owner, store);
    //System.out.println("LISTEN: " + owner);
  }
  */
  
  /*
  @Override
  public StoreBackend create(StoreBackend backend)
  {
    return new StoreBackendCluster(backend, this);
  }
  */

  //
  // get methods
  //

  @Override
  public TablePod getTable(byte []tableKey)
  {
    TableKraken tableKraken = getTableManager().getTable(tableKey);
    
    if (tableKraken != null) {
      return tableKraken.getTablePod();
    }
    else {
      return null;
    }
  }
  //
  // put methods
  //
  
  //
  // starting
  //
  
  void startRequestUpdates()
  {
    _tableManager.startRequestUpdates();
  }
}
