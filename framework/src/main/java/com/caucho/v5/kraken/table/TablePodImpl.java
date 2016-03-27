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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.bartender.pod.PodOnUpdate;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.kelp.BackupKelp;
import com.caucho.v5.kelp.GetStreamResult;
import com.caucho.v5.kelp.PageServiceSync.PutType;
import com.caucho.v5.kraken.table.TablePodNode.NodeTableContext;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

import io.baratine.event.EventsSync;
import io.baratine.service.Cancel;
import io.baratine.service.Result;
import io.baratine.service.ServiceException;
import io.baratine.service.ServiceManager;
import io.baratine.service.ServiceRef;
/**
 * Manages the distributed cache
 */
public final class TablePodImpl implements TablePod
{
  private static final L10N L = new L10N(TablePodImpl.class);
  private static final Logger log
    = Logger.getLogger(TablePodImpl.class.getName());
  
  private final ServerBartender _serverSelf;
  //private final ClientKrakenImpl _clientKraken;
  private final PodKraken _podKraken;
  private final TableKraken _table;
  
  private int _putChunkMin = 256 * 1024;
  
  // private PodKraken _shardingManager;
  // private long _startupLastUpdateTime;
  // private String _address;
  
  private AtomicLong _putSequence = new AtomicLong();
  // private TableKelp _table;
  // private PodKraken _podManager;
  private TablePodNode[] _tableNodes;
  private TableManagerKraken _tableManager;
  private BackupKelp _replicationCallback;
  
  private ServiceRef _updateRef;
  private long _lastPodCrc;
  private PodBartender _pod;
  private Cancel _updateCancel;

  // private RowServiceHub _triadRemoteAll;

  public TablePodImpl(TableManagerKraken tableManager,
                      TableKraken table,
                      PodKraken podKraken)
  {
    Objects.requireNonNull(table);
    Objects.requireNonNull(tableManager);
    Objects.requireNonNull(podKraken);
    
    _serverSelf = BartenderSystem.current().serverSelf();
    
    Objects.requireNonNull(_serverSelf);
    
    _tableManager = tableManager;
    _table = table;
    _podKraken = podKraken;
    
    _pod = podKraken.getPodBartender();
    
    _tableNodes = new TablePodNode[0];
    
    getTableNodes();
    
    //RampManager rampManager = AmpSystem.getCurrentManager();
    
    _replicationCallback
      = _tableManager.getStoreServiceRef()
                      .pin(new BackupTableKrakenCallback(this))
                      .as(BackupKelp.class);
    
    // _krakenManager = clientKraken.getKrakenManager();
    
    /// should be server id + timestamp
    // RandomUtil.getRandomLong());
    _putSequence.set(CurrentTime.currentTime() << 16);
    
    _lastPodCrc = _pod.getCrc();
    
    start();
  }
  
  PodKraken getPodManager()
  {
    return _podKraken;
  }
  
  @Override
  public String getName()
  {
    return getTable().getName();
  }

  @Override
  public TableKraken getTable()
  {
    return _table;
  }
  
  @Override
  public String getPodName()
  {
    return _podKraken.getPodBartender().name();
  }
  
  @Override
  public BackupKelp getReplicationCallback()
  {
    return _replicationCallback;
  }

  int getPutChunkMin()
  {
    return _putChunkMin;
  }
  
  long nextPutSequence()
  {
    return _putSequence.incrementAndGet();
  }
  
  // @Override
  public void start()
  {
    // _cacheReplicationActor = new CacheReplicationActor(_selfServer, _cacheService);
    // _cacheReplicationActor.start();
    startRequestUpdates();
    
    ServiceManagerAmp services = AmpSystem.currentManager();
    
    //ServiceRef podUpdateRef = services.service(PodOnUpdate.ADDRESS);
    
    _updateRef = _tableManager.getStoreServiceRef()
                              .pin((PodOnUpdate) p->onPodUpdate(p));
    
    EventsSync events = services.service(EventsSync.class); 
    
    _updateCancel = events.subscriber(PodOnUpdate.class, 
                                      _updateRef.as(PodOnUpdate.class));
  }
  
  public void stop()
  {
    if (_updateCancel != null) {
      _updateCancel.cancel();
    }
  }
  
  private KelpManager getKelpBacking()
  {
    return _table.getKelpBacking();
  }

  //
  // get methods
  //

  @Override
  public void get(byte[] key, Result<Boolean> result)
  {
    long version = 0;
    
    getIfUpdate(key, version, result);
  }

  @Override
  public void getIfUpdate(byte[] key,
                          long version,
                          Result<Boolean> result)
  {
    TablePodNode rowNode = getNode(key);
    
    rowNode.invoke(new NodeTableContextGet(_table, key, version, rowNode, result));
  }

  @Override
  public void findByName(String name, Result<byte[]> result)
  {
    TablePodNode rowNode = getNode(0);
    
    rowNode.invoke(new NodeTableContextFind(_table.getKey(), name, result));
  }

  //
  // put methods
  //

  @Override
  public void put(byte []rowKey, 
                  StreamSource data,
                  Result<Boolean> result)
  {
    TablePodNode rowNode = getNode(rowKey);

    rowNode.invoke(new NodeTableContextPut(this, _table.getKey(), rowKey, data, result));
  }

  @Override
  public void remove(byte []rowKey, long version,
                     Result<? super Boolean> result)
  {
    TablePodNode rowNode = getNode(rowKey);
    
    rowNode.invoke(new NodeTableContextRemove(_table.getKey(), rowKey, version, result));
  }
  
  //
  // starting
  //
  
  @Override
  public void startRequestUpdates()
  {
    for (TablePodNode tableNode : getTableNodes()) {
      addStartupNode(tableNode);
    }
  }

  @Override
  public void addStartupNode(TablePodNode tableNode)
  {
    tableNode.clearStartFailure();

    if (tableNode.isStartComplete()) {
      return;
    }
    
    if (tableNode.isStartFailed()) {
      return;
    }
    
    long startupLastUpdateTime = _table.getStartupLastUpdateTime();

    tableNode.startRequestUpdates(this, startupLastUpdateTime);

    /*
    if (! tableNode.isStartComplete()) {
      return;
    }
    */
  }
  
  @Override
  public void startUpdates(TablePodNodeStartup podNodeState,
                           int index)
  {
    TablePodNode podNode = podNodeState.getNode();
    
    ServerBartender server = podNode.getServer(index);
    
    if (server == null) {
      podNodeState.onStartupFailed(index, null);
      return;
    }
    
    if (server.isSelf()) {
      podNodeState.onStartupCompleted(index);
      return;
    }
    
    /*
    if (! server.isAssigned()) {
      podNodeState.onStartupCompleted(index);
      return;
    }
    */
    
    if (! server.isUp()) {
      podNodeState.onStartupFailed(index, null);
      return;
    }

    /*
    System.out.println("START: " + index + " " + server.isUp()
                       + " " + getTable().getName() + " peer:" + server
                       + " self:" + BartenderSystem.getCurrentSelfServer());
*/                       

    long now = CurrentTime.currentTime();
    
    long startupLastUpdateTime = _table.getStartupLastUpdateTime();
    long delta = startupLastUpdateTime - now - 30 * 60000L;

    ClusterServiceKraken proxy = podNode.getProxy(index);
    // System.out.println("PROX: " + proxy + " " + podNode + " " + _serverSelf.getDisplayName());
    if (proxy == null) {
      /*
      System.out.println("NO-PROXY: " + proxy + " " + podNode
                         + " " + BartenderSystem.getCurrentSelfServer());
                         */

      podNodeState.onStartupFailed(index, null);
      return;
    }
    
    //StartupCompleteCallback cb = new StartupCompleteCallback(podNodeState, index);
    
    Result<Boolean> cb = Result.of(x->podNodeState.onStartupCompleted(index),
                                     e->podNodeState.onStartupFailed(index, e));
    //_shardState.onStartupCompleted(_server);

    proxy.requestStartupUpdates(_serverSelf.getId(), _table.getKey(),
                                podNode.getIndex(), delta, cb);
  }
  
  private TablePodNode getNode(byte []key)
  {
    int hash = _table.getPodHash(key);
    
    return getNode(hash);
  }

  @Override
  public TablePodNode getNode(int hash)
  {
    NodePodAmp podNode = getPodNode(hash);
    
    return getTableNodes()[podNode.nodeIndex()];
  }
  
  protected TablePodNode []getTableNodes()
  {
    TablePodNode[] tableNodes = _tableNodes;
    
    if (_pod.nodeCount() == tableNodes.length) {
      return tableNodes;
    }
    
    tableNodes = new TablePodNode[_pod.nodeCount()];
    
    for (int i = 0; i < tableNodes.length; i++) {
      tableNodes[i] = new TablePodNode(this, _podKraken, _pod, i);
    }
    
    _tableNodes = tableNodes;
    
    return tableNodes;
  }

  @Override
  public NodePodAmp getPodNode(int hash)
  {
    return _pod.getNode(hash);
  }
  
  @Override
  public int getServerCount()
  {
    return _pod.serverCount();
  }
  
  @Override
  public int getNodeCount()
  {
    return _pod.nodeCount();
  }
  
  @Override
  public int getVirtualNodeCount()
  {
    return _pod.getVnodeCount();
  }
  
  @Override
  public boolean isLocal()
  {
    for (TablePodNode node : getTableNodes()) {
      if (! node.isSelfOwner()) {
        System.out.println("NSI: " + node + " " + node.getServer(0));
        return false;
      }
    }
    
    return true;
  }

  @Override
  public ServerBartender getServerSelf()
  {
    ServerBartender []podServers = _pod.getServers();

    ServerBartender self = _serverSelf;

    int index = findServer(podServers, self);

    if (index >= 0) {
      return podServers[index];
    }
    else {
      return null;
    }
  }

  @Override
  public void findOne(Result<byte[]> result, 
                      ServerBartender server, 
                      String sql, 
                      Object []args)
  {
    ClusterServiceKraken proxy = getNode(server);
    
    if (proxy == null) {
      result.ok(null);
      return;
    }

    proxy.findOne(result.of((x,r)->onFindOneKey(x,r)),
                  getTable().getTableKey(), sql, args);
    //rowNode.invoke(new GetStreamContext(_table.getKey(), key, result));
  }

  @Override
  public void findAll(Result<Iterable<byte[]>> result, 
                      ServerBartender server, 
                      String sql, 
                      Object []args)
  {
    ClusterServiceKraken proxy = getNode(server);

    if (proxy == null) {
      result.ok(null);
      return;
    }

    //proxy.findAll(new FindAllKeyResult(result), getTable().getTableKey(), sql, args);
    
    proxy.findAll(result.of((x,r)->onFindAllKey(x,r)),
                  getTable().getTableKey(), sql, args);
    //rowNode.invoke(new GetStreamContext(_table.getKey(), key, result));
  }

  /**
   * Distributed update table. All owning nodes will get a request. 
   */
  @Override
  public void update(Result<Integer> result, 
                     int nodeIndex,
                     String sql,
                     Object[] args)
  {
    NodePodAmp node = _podKraken.getNode(nodeIndex);
    
    for (int i = 0; i < node.serverCount(); i++) {
      ServerBartender server = node.server(i);
      
      if (server != null && server.isUp()) {
        ClusterServiceKraken proxy = _podKraken.getProxy(server);
        
        // XXX: failover
        
        proxy.update(result, nodeIndex, sql, args);
        return;
      }
    }

    RuntimeException exn = new ServiceException(L.l("update failed with no live servers"));
    exn.fillInStackTrace();
    
    // XXX: fail
    result.fail(exn);
  }

  @Override
  public void addRemoteWatch(byte[] key, int hash)
  {
    NodePodAmp node = _podKraken.getNode(hash);
    
    for (int i = 0; i < node.serverCount(); i++) {
      ServerBartender server = node.server(i);
      
      if (server == null) {
      }
      else if (server.equals(_serverSelf)) {
        return;
      }
      else if (server.isUp()) {
        ClusterServiceKraken proxy = _podKraken.getProxy(server);
        
        proxy.addWatch(_table.getKey(), key, _serverSelf.getId());
      }
    }

    RuntimeException exn = new ServiceException(L.l("add watch failed with no live servers"));
    exn.fillInStackTrace();
    
    // XXX: fail
    // Result.Adapter.failed(result, exn);
  }

  /**
   * Notify the watches on the target server.
   */
  @Override
  public void notifyForeignWatch(byte[] key, String serverId)
  {
    ClusterServiceKraken proxy = _podKraken.getProxy(serverId);
    
    if (proxy != null) {
      proxy.notifyLocalWatch(_table.getKey(), key);
    }
  }

  /**
   * Notify the watches on the target server.
   */
  @Override
  public void notifyWatch(byte[] key)
  {
    int hash = _table.getPodHash(key);
    
    TablePodNode node = getNode(hash);
    
    if (node.isSelfOwner()) {
      _table.notifyWatch(key);
    }
    else {
      ServerBartender owner = node.getOwner();
      
      ClusterServiceKraken proxy = _podKraken.getProxy(owner.getId());
    
      if (proxy != null) {
        proxy.notifyWatch(_table.getKey(), key);
      }
    }
  }
  
  private ClusterServiceKraken getNode(ServerBartender server)
  {
    ClusterServiceKraken proxy = _podKraken.getProxy(server);
    
    return proxy;
  }

  @Override
  public ArrayList<ServerBartender> findServersQueryCover()
  {
    PodBartender pod = _podKraken.getPodBartender();
    
    boolean []nodesFound = new boolean[getNodeCount()];
    
    ServerBartender []podServers = pod.getServers();

    ServerBartender self = _serverSelf;

    int index = findServer(podServers, self);
    
    ArrayList<ServerBartender> servers = new ArrayList<>();
    
    for (int i = 0; i < podServers.length; i++) {
      ServerBartender server = podServers[(i + index) % podServers.length];
      
      if (fillServersQueryCover(nodesFound, server)) {
        servers.add(server);
      }
    }
    
    return servers;
  }

  @Override
  public ArrayList<ServerBartender> getUpdateServers()
  {
    PodBartender pod = _podKraken.getPodBartender();
    
    ArrayList<ServerBartender> servers = new ArrayList<>();
    
    int nodeCount = pod.nodeCount();
    int depth = pod.getDepth();
    
    for (int i = 0; i < nodeCount; i++) {
      for (int j = 0; j < depth; j++) {
        ServerBartender server = pod.getNode(i).server(j);
        
        if (server != null && server.isUp()) {
          if (! servers.contains(server)) {
            servers.add(server);
          }
          
          break;
        }
      }
    }
      
    return servers;
  }
  
  private int findServer(ServerBartender []podServers, ServerBartender server)
  {
    for (int i = 0; i < podServers.length; i++) {
      if (server.equals(podServers[i])) {
        return i;
      }
    }
    
    return 0;
  }
  
  private boolean fillServersQueryCover(boolean []nodesFound,
                                        ServerBartender server)
  {
    if (server == null || ! server.isUp()) {
      return false;
    }
    
    boolean isServerAdded = false;
    
    for (int i = 0; i < nodesFound.length; i++) {
      if (nodesFound[i]) {
        continue;
      }
      
      if (getPodNode(i).isServerOwner(server)) {
        nodesFound[i] = true;
        isServerAdded = true;
      }
    }
    
    return isServerAdded;
  }

  //@Override
  public void getUpdatesFromLocal(int podIndex, 
                                  long accessTime, 
                                  Result<Boolean> result)
  {
    getKelpBacking().getUpdates(getTable(),
                                podIndex,
                                accessTime,
                                result);
  }
  
  private void onPodUpdate(PodBartender pod)
  {
    if (! pod.getId().equals(_podKraken.getPodBartender().getId())) {
      return;
    }
    
    if (_lastPodCrc != pod.getCrc()) {
      _lastPodCrc = pod.getCrc();
      
      onPodUpdate();
    }
  }
  
  private void onPodUpdate()
  {
    // int nodeCount = _podKraken.getNodeCount();

    /*
    if (nodeCount != _tableNodes.length) {
      _tableNodes = new TablePodNode[_podKraken.getNodeCount()];
    
      for (int i = 0; i < _tableNodes.length; i++) {
        _tableNodes[i] = new TablePodNode(this,
                                          _podKraken,
                                          _podKraken.getPodBartender(),
                                          i);
      }
    }
    */

    for (TablePodNode podNode : getTableNodes()) {
      podNode.onUpdate();
    }
  }
  
  private void onFindOneKey(byte []key, Result<byte[]> result)
  {
    if (key == null) {
      result.ok(null);
      return;
    }
    
    get(key, result.of(x->onFindOneGet(x, key)));
  }
  
  private byte []onFindOneGet(Boolean value, byte []key)
  {
    if (Boolean.TRUE.equals(value)) {
      return key;
    }
    else {
      return null;
    }    
  }
  
  private void onFindAllKey(Iterable<byte[]> iter, Result<Iterable<byte[]>> result)
  {
    if (iter == null) {
      result.ok(null);
      return;
    }
    
    FindAnyGetResult findAnyGet = new FindAnyGetResult(result, iter);
    findAnyGet.next();
  }
  
  //
  // sub-classes
  //
  
  private class NodeTableContextRemove extends NodeTableContext
  {
    private byte []_rowKey;
    private byte []_tableKey;
    private long _version;
    private Result<? super Boolean> _result;
    
    private int _count;
    
    NodeTableContextRemove(byte []tableKey,
                  byte []rowKey,
                  long version,
                  Result<? super Boolean> result)
    {
      _tableKey = tableKey;
      _rowKey = rowKey;
      _version = version;
      _result = result;
    }
    
    @Override
    public boolean isSingleRequest()
    {
      return false;
    }

    @Override
    public void invoke(ClusterServiceKraken service)
    {
      _count++;
      service.remove(_tableKey, _rowKey, _version, _result.of((x,r)->onResult(r)));
    }

    @Override
    public void fallthru()
    {
      _result.ok(false);
      //close();
      // Result.Adapter.failed(_result, new RuntimeException("failed put"));
    }
    
    public void close()
    {
      super.close();
      
      onResult(_result);
    }
    
    private void onResult(Result<? super Boolean> result)
    {
      if (--_count <= 0) {
        _result.ok(true);
      }
    }
  }
  
  private class NodeTableContextGet extends NodeTableContext
    implements Result<GetStreamResult>
  {
    private TableKraken _table;
    private byte []_key;
    private long _version;
    private Result<Boolean> _result;
    private TablePodNode _node;
    
    NodeTableContextGet(TableKraken table,
                     byte []key,
                     long version,
                     TablePodNode node,
                     Result<Boolean> result)
    {
      _table = table;
      _key = key;
      _version = version;
      _node = node;
      _result = result;
    }
    
    @Override
    public void invoke(ClusterServiceKraken service)
    {
      service.get(_table.getKey(), _key, _version, this);
    }

    @Override
    public void fallthru()
    {
      _result.ok(false);
    }

    @Override
    public void ok(GetStreamResult getResult)
    {
      if (getResult == null) {
        // _result.complete(false);
        
        if (false && _version > 0 && ! _node.isSelfCopy()) {
          // bfs/1181 remove if our value is obsolete
          // XXX: vs kraken/2122
          getKelpBacking().remove(_table.getKey(), _key, _version, _result);
        }
          
        _result.ok(false);
      }
      else if (getResult.isFound()) {
        if (getResult.isUpdate()) {
          StreamSource ss = getResult.getStreamSource();
          
          getKelpBacking().put(_table.getTableKelp(), ss, PutType.LOAD, _result);
        }
        else {
          // should update timeout
          _result.ok(true);
        }
      }
      else {
        _result.ok(false);
      }
    }

    @Override
    public void fail(Throwable exn)
    {
      _result.fail(exn);
    }
    
    public void handle(GetStreamResult getResult, Throwable exn)
    {
      if (exn != null) {
        _result.fail(exn);
      }
      else {
        ok(getResult);
      }
    }
  }
  
  private class NodeTableContextFind extends NodeTableContext
    implements Result<byte[]>
  {
    private byte []_tableKey;
    private Object _arg;
    private Result<byte[]> _result;
    
    NodeTableContextFind(byte []tableKey,
                      Object arg,
                      Result<byte[]> result)
    {
      _tableKey = tableKey;
      _arg = arg;
      _result = result;
    }
    
    @Override
    public void invoke(ClusterServiceKraken service)
    {
      service.find(_tableKey, _arg, this);
    }

    @Override
    public void fallthru()
    {
      _result.ok(null);
    }

    @Override
    public void handle(byte []value, Throwable exn)
    {
      if (exn != null) {
        _result.fail(exn);
      }
      else {
        _result.ok(value);
      }
    }
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
           + "[" + _table
           + "," + _podKraken.getPodBartender().name()
           + "]");
  }
  
  private class FindAnyGetResult extends Result.Wrapper<Boolean,Iterable<byte[]>>
  {
    private Iterable<byte[]> _list;
    private Iterator<byte[]> _iter;
    
    private boolean _isComplete;
    
    FindAnyGetResult(Result<Iterable<byte[]>> result, Iterable<byte[]> list)
    {
      super(result);
      
      _list = list;
      _iter = list.iterator();
    }
    
    @Override
    public void ok(Boolean value)
    {
      next();
    }
    
    void next()
    {
      if (! _iter.hasNext()) {
        if (_isComplete) {
          return;
        }
        _isComplete = true;
        
        delegate().ok(_list);
        return;
      }
      
      get(_iter.next(), this);
    }
  }

  /*
  private class PodListener implements PodOnUpdate {
    @Override
    public void onUpdate(PodBartender pod)
    {
    }
  }
  */
}
