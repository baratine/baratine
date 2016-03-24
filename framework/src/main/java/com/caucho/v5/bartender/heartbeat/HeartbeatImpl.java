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

package com.caucho.v5.bartender.heartbeat;

import io.baratine.service.AfterBatch;
import io.baratine.service.Cancel;
import io.baratine.service.Direct;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;
import io.baratine.timer.TimerService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.baratine.InService;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.ServerOnUpdate;
import com.caucho.v5.bartender.link.LinkBartenderEvents;
import com.caucho.v5.bartender.pod.PodHeartbeatService;
import com.caucho.v5.bartender.pod.PodLocalService;
import com.caucho.v5.bartender.pod.PodsManagerServiceLocal;
import com.caucho.v5.bartender.pod.UpdatePodSystem;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * Service for handling the bartender heartbeat messages
 */
@InService(HeartbeatLocalImpl.class)
public class HeartbeatImpl
{
  private static final L10N L = new L10N(HeartbeatImpl.class);
  private static final Logger log
    = Logger.getLogger(HeartbeatImpl.class.getName());

  private final BartenderSystem _bartender;
  
  private final RootHeartbeat _root;
  private final ServerSelf _serverSelf;
  
  private final TimerService _timer;
  private final HeartbeatTask _heartbeatTask;

  private final long _heartbeatTimeout = 60000;
  private final int _hubCount = 3;
  
  private final Lifecycle _lifecycle = new Lifecycle();

  private RackHeartbeat _rack;
  
  private long _lastRackCrc;
  
  private final ArrayList<ServerTarget> _targets = new ArrayList<>();
  
  private final HashMap<String,ClusterTarget> _clusterTargets = new HashMap<>();
  private final HashMap<String,ServerTarget> _targetMap = new HashMap<>();

  private final ServerOnUpdate _serverEvents;
  
  // private final PodServiceImpl _podService;
  
  private ServiceManagerAmp _rampManager;
  private long _lastUpdateTime;
  
  private JoinClient _joinClient;
  private JoinState _joinState = JoinState.INIT;
  
  private JoinTask _joinTask;
  private Lifecycle _joinLifecycle = new Lifecycle();
  
  private long _joinAlarmTime = 1 * 60000;
  private long _joinFullTime = 15 * 60000;
  private long _lastJoinTime;
  
  private boolean _isHubHeartbeat;
  private ArrayList<Result<Boolean>> _hubHeartbeatList = new ArrayList<>();
  private int _hubHeartbeatCount;
  private boolean _isHubHeartbeatSelf;
  
  // private boolean _isStartValid;
  
  private boolean _isSendHeartbeatsRequired;
  private HeartbeatLocalImpl _heartbeatLocal;
    
  public HeartbeatImpl(BartenderSystem bartender,
                              ServerSelf serverSelf,
                              RootHeartbeat root,
                              BartenderBuilderHeartbeat builder)
  {
    Objects.requireNonNull(bartender);
    Objects.requireNonNull(root);
    Objects.requireNonNull(serverSelf);
    
    _bartender = bartender;
    
    _timer = AmpSystem.getCurrent().getTimerService();
    _rampManager = AmpSystem.currentManager();
    
    _heartbeatLocal = new HeartbeatLocalImpl(_bartender, this);
    
    _heartbeatTask = new HeartbeatTask();

    _root = root;
    _serverSelf = serverSelf;
    _rack = _serverSelf.getRack();
    
    _serverEvents = _rampManager.service(ServerOnUpdate.ADDRESS)
                                .as(ServerOnUpdate.class);
    
    // _podService = builder.buildPodService(bartender, this);
    
    _joinClient = new JoinClient(serverSelf, root, _heartbeatLocal);
    
    _joinTask = new JoinTask();
    
    for (ClusterHeartbeat cluster : _root.getClusters()) {
      addClusterTarget(cluster);
    }
  }
  
  private void addClusterTarget(ClusterHeartbeat cluster)
  {
    if (cluster == _serverSelf.getCluster()) {
      return;
    }
    
    _clusterTargets.put(cluster.id(), new ClusterTarget(cluster));
  }
  
  private ClusterTarget createClusterTarget(ClusterHeartbeat cluster)
  {
    ClusterTarget clusterTarget = _clusterTargets.get(cluster.id());
    
    if (clusterTarget == null) {
      addClusterTarget(cluster);
      
      clusterTarget = _clusterTargets.get(cluster.id());
    }
    
    return clusterTarget;
  }
  
  //
  // server access
  //
  
  public ServerSelf getServerSelf()
  {
    return _serverSelf;
  }
  
  @Direct
  public ServerBartender getServer(String id)
  {
    ServerBartender server = _root.getServer(id);
    
    if (server == null) {
      server = _root.findServerByName(id);
    }
    
    return server;
  }

  public ServerHeartbeat createServer(String address, int port, boolean isSSL)
  {
    // XXX: x-cluster issues
    return _root.createServer(address, port, isSSL,
                              0, getServerSelf().getCluster());
  }

  public Iterable<? extends ClusterHeartbeat> getClusters()
  {
    return _root.getClusters();
  }

  public ClusterHeartbeat findCluster(String clusterId)
  {
    return _root.findCluster(clusterId);
  }

  public ClusterHeartbeat getCluster()
  {
    return getRack().getCluster();
  }
  
  public RackHeartbeat getRack()
  {
    return _rack;
  }
  
  private RackHeartbeat getRack(String address)
  {
    return _rack;
  }
  
  private RackHeartbeat getRack(ClusterHeartbeat cluster, String address)
  {
    return cluster.createRack("rack");
  }

  /**
   * Marks if the join phase is complete. Used for initialization to avoid
   * starting services when the self-server is only a backup.
   */
  public boolean isJoinComplete()
  {
    return _joinState.isJoinComplete();
  }
  
  private boolean isJoinRequired()
  {
    long now = CurrentTime.currentTime();
    
    if (now - _lastJoinTime <= _joinFullTime) {
      return true;
    }
    
    if (! isSeedHeartbeatValid()) {
      return true;
    }
    
    return false;
  }
  
  /**
   * Returns true if one of the cluster seeds is an active server.
   */
  private boolean isSeedHeartbeatValid()
  {
    boolean isSeed = false;
    
    for (ServerHeartbeat server : _serverSelf.getCluster().getSeedServers()) {
      if (server.port() > 0) {
        isSeed = true;
        
        if (server.isUp()) {
          return true;
        }
      }
    }

    return ! isSeed;
  }
  
  public boolean isStartValid()
  {
    return _joinState.isJoinComplete();
  }
  
  public PodHeartbeatService getPodHeartbeat()
  {
    return _bartender.getPodHeartbeat();
  }
  
  /*
  public void setJoinComplete(int count)
  {
    //_isJoinComplete = true;
    
    //_joinCount = count;
    
    updatePodsFromHeartbeat();
  }
  */
  
  //
  // lifecycle
  //
  
  public void start(Result<Boolean> result)
  {
    _lifecycle.toActive();
    
    _rampManager.service(LinkBartenderEvents.ADDRESS)
                .subscribe(new NetworkLinkListener());
    
    addTargetServers();
    
    // _service.onHeartbeatStart(getSelfServer());

    _heartbeatTask.accept(null);
    // _timer.runAfter(_heartbeatTask, 0, TimeUnit.SECONDS);
    
    _joinClient.start(x->updateRack(x), 
                      result.of((count,r)->onJoinComplete(count,r)));
  }
  
  public void stop()
  {
    _lifecycle.toDestroy();
    _joinLifecycle.toDestroy();
  }
  
  //
  // join messages
  //

  /**
   * joinServer message to an external configured address from a new server, 
   * including SelfServer.
   * 
   * External address discovery and dynamic server joins are powered by
   * join server.
   * 
   * @param extAddress the configured IP address of the connection
   * @param extPort the configured IP port of the connection
   * @param address the requesting server's IP address
   * @param port the requesting server's IP port
   * @param displayName the requesting server's displayName
   * @param serverHash the requesting server's unique machine hash (MAC + port)
   * 
   * @return RackHeartbeat if joining an existing hub; null if self-join.
   */
  public UpdateRackHeartbeat join(String extAddress, int extPort,
                                  String clusterId,
                                  String address, int port, int portBartender,
                                  String displayName, 
                                  String serverHash,
                                  int seedIndex)
  {
    Objects.requireNonNull(extAddress);
    Objects.requireNonNull(address);
    
    ClusterHeartbeat cluster = _root.findCluster(clusterId);
    
    if (cluster == null) {
      cluster = _root.createCluster(clusterId);
      
      log.fine("Heartbeat create new cluster " + clusterId);
      //System.out.println("Unknown cluster for heartbeat join: " + cluster + " " + clusterId);
      //throw new IllegalStateException("UNKNOWN_CLUSTER: " + cluster + " " + clusterId);
    }
    
    boolean isSSL = false;
    ServerHeartbeat server = cluster.createDynamicServer(address, port, isSSL);
    
    //server.setDisplayName(displayName);
    
    ClusterTarget clusterTarget = null;
    // boolean isJoinCluster = false;
    
    RackHeartbeat rack = server.getRack();
    
    if (rack == null) {
      rack = getRack(cluster, address);
    
      server = rack.createServer(address, port, isSSL);

      //server.setDisplayName(displayName);
      
      log.fine("join-server"
               + " int=" + address + ":" + port + ";" + portBartender
               + " " + displayName
               + " hash=" + serverHash
               + " ext=" + extAddress + ":" + extPort
               + " (" + _serverSelf.getDisplayName() + ")");
    }
      
    if (cluster != _serverSelf.getCluster()) {
      server.toKnown();
      clusterTarget = createClusterTarget(cluster);

      if (clusterTarget.addServer(server)) {
       // isJoinCluster = true;
      }
    }
    
    server.setPortBartender(portBartender);
    
    if (extAddress != null) { // && ! extAddress.startsWith("127")) {
      _serverSelf.setSeedIndex(seedIndex);
      _serverSelf.setLastSeedTime(CurrentTime.currentTime());
      _serverSelf.update();
    }
    
    if (server.isSelf()) {
      joinSelf(server, extAddress, extPort, address, port, serverHash);
    }
    
    // clear the fail time, since the server is now up
    server.clearConnectionFailTime();
    
    server.update();

    updateHeartbeats();
    
    if (server.isSelf()) {
      // join to self
      return null;
    }
    else if (clusterId.equals(_serverSelf.getClusterId())) {
      // join to own cluster
      return server.getRack().getUpdate();
    }
    else {
      // join to foreign cluster
      return _serverSelf.getRack().getUpdate();
    }
  }
  
  /**
   * received a join from this server. Used to update external IPs.
   */
  private void joinSelf(ServerHeartbeat server,
                        String extAddress, int extPort,
                        String address, int port,
                        String serverHash)
  {
    if (server != _serverSelf) {
      throw new IllegalStateException(L.l("Invalid self: {0} vs {1}", 
                                          server, _serverSelf));
    }
    
    if (! serverHash.equals(_serverSelf.getMachineHash())) {
      throw new IllegalStateException(L.l("Invalid server hash {0} against {1}:{2}({3})",
                                          _serverSelf,
                                          address, port, 
                                          serverHash));
    }
    
    if (port != _serverSelf.port()) {
      throw new IllegalStateException(L.l("Invalid server port {0} against {1}:{2}({3})",
                                          _serverSelf,
                                          address, port, 
                                          serverHash));
    }
    
    boolean isSSL = false;
    ServerHeartbeat extServer = getCluster().createServer(extAddress, extPort, isSSL);
    
    server.setExternalServer(extServer);
    
    server.setMachineHash(serverHash);

    server.getRack().update(server.getUpdate());
    
    heartbeatStart(server);
    
    updateHubHeartbeatSelf();
    
    //_podService.updatePodsFromHeartbeat();
    
    log.fine("join self " + server);
  }

  //
  // heartbeat messages
  //
  
  /**
   * Heartbeat from a spoke server includes only its own information.
   */
  public void serverHeartbeat(UpdateServerHeartbeat serverUpdate)
  {
    updateServerStart(serverUpdate);
    //getRack().update();
    updateHeartbeats();
  }
  
  /**
   * Heartbeat from a hub server includes the heartbeat status of its rack.
   */
  public void hubHeartbeat(UpdateServerHeartbeat updateServer, 
                           UpdateRackHeartbeat updateRack,
                           UpdatePodSystem updatePod,
                           long sourceTime)
  {
    RackHeartbeat rack = getCluster().findRack(updateRack.getId());
    
    
    if (rack == null) {
      rack = getRack();
    }
    
    updateRack(updateRack);
    
    updateServerStart(updateServer);
    
    // XXX: _podService.onUpdateFromPeer(updatePod);
    
    // rack.update();
    
    updateTargetServers();

    PodHeartbeatService podHeartbeat = getPodHeartbeat();
    
    if (podHeartbeat != null && updatePod != null) {
      podHeartbeat.updatePodSystem(updatePod);
    }
    
    _joinState = _joinState.onHubHeartbeat(this);
    
    // updateHubHeartbeat();
    updateHeartbeats();
  }
  
  
  public void clusterHeartbeat(String sourceClusterId,
                               UpdateServerHeartbeat updateSelf,
                               UpdateRackHeartbeat updateRack,
                               UpdatePodSystem updatePod,
                               long sequence)
  {
    ClusterHeartbeat cluster = findCluster(sourceClusterId);
    
    if (cluster == null) {
      cluster = _root.createCluster(sourceClusterId);
      
      log.fine("Heartbeat create new cluster " + sourceClusterId);
    }
    
    boolean isSSL = false;
    ServerHeartbeat server = cluster.createServer(updateSelf.getAddress(),
                                                  updateSelf.getPort(),
                                                  isSSL);
    
    server.clearConnectionFailTime();
    server.toKnown();

    // ClusterTarget target = _clusterTargets.get(cluster.getId());
    
    //clusterUpdateRack(cluster, updateRack);
    updateRack(updateRack);
    
    // XXX: _podService.onUpdateFromPeerCluster(sourceClusterId, updatePod);
    
    PodHeartbeatService podHeartbeat = getPodHeartbeat();
    
    if (podHeartbeat != null) {
      podHeartbeat.updatePodSystem(updatePod);
    }
    
    /*
    if (target != null) {
      sendClusterHeartbeat(target);
    }
    */
    
    updateHeartbeats();
  }

  /*
  private void clusterUpdateRack(ClusterHeartbeat cluster,
                                 UpdateRackHeartbeat updateRack)
  {
    RackHeartbeat rackCluster = cluster.createRack("external");
    ClusterTarget target = createClusterTarget(cluster);
    
    rackCluster.updateRack(this, updateRack);
  }
  */
  
  /**
   * Join completion
   */
  private void onJoinComplete(Integer count,
                              Result<Boolean> result)
  {
    if (count == null) {
      count = 0;
    }
    
    _joinState = _joinState.onJoinComplete(count, this);
    
    updatePodsFromHeartbeat();
    
    waitForHubHeartbeat(count, result);

    if (_joinLifecycle.toActive()) {
      _timer.runAfter(_joinTask, getJoinTimeout(), TimeUnit.MILLISECONDS, Result.ignore());
    }
  }
  
  private long getJoinTimeout()
  {
    return _joinAlarmTime;
  }

  private void waitForHubHeartbeat(int count, Result<Boolean> result)
  {
    if (_isHubHeartbeat || count < 2 && _isHubHeartbeatSelf) {
      //_isStartValid = true;
      result.ok(true);
      return;
    }

    _hubHeartbeatCount = count;
    
    result.ok(true);;
    // _hubHeartbeatList.add(result);
  }
  
  /**
   * If the join is to the self server, and there's only one successful
   * join, update immediately. 
   */
  private void updateHubHeartbeatSelf()
  {
    _isHubHeartbeatSelf = true;
    
    if (_hubHeartbeatCount < 2) {
      for (Result<Boolean> result : _hubHeartbeatList) {
        result.ok(true);
      }
      
      _hubHeartbeatList.clear();
    }
  }
 
       
  /**
   * Process an update message for a rack of servers.
   */
  void updateRack(UpdateRackHeartbeat updateRack)
  {
    ClusterHeartbeat cluster = findCluster(updateRack.getClusterId());

    if (cluster == null) {
      return;
    }
    
    RackHeartbeat rack;
    
    if (cluster != _serverSelf.getCluster()) {
      rack = cluster.createRack("external");
      ClusterTarget target = createClusterTarget(cluster);
    }
    else {
      rack = cluster.findRack(updateRack.getId());
    }
    
    if (rack == null) {
      return;
    }
    
    rack.updateRack(this, updateRack);
    
    updateHeartbeats();
  }

  /**
   * Update a server where the update is directly from the peer.
   */
  private void updateServerStart(UpdateServerHeartbeat update)
  {
    // internal communication is non-ssl
    boolean isSSL = false;
    
    ServerHeartbeat server = _root.createServer(update.getAddress(),
                                                update.getPort(),
                                                isSSL,
                                                0,
                                                getCluster());
    
    server.setDisplayName(update.getDisplayName());
    //server.setMachineHash(serverUpdate.getMachineHash());

    updateServer(server, update);
    
    if (server.getRack() != null) {
      server.getRack().update();
    }
  }
  
  /**
   * Update a server with a heartbeat update.
   * 
   * @param server the server to be updated
   * @param update the new update information
   */
  void updateServer(ServerHeartbeat server, UpdateServerHeartbeat update)
  {
    if (server.isSelf()) {
      return;
    }
    
    String externalId = update.getExternalId();

    updateExternal(server, externalId);

    // XXX: validation
    server.setSeedIndex(update.getSeedIndex());

    if (server.onHeartbeatUpdate(update)) {
      if (server.isUp()) {
        onServerStart(server);
      }
      else {
        onServerStop(server);
      }
    }
  }

  /**
   * Update the external server delegation.
   * 
   * Cloud static IPs might be routing IPs that aren't local interfaces. In
   * that case, the routing assignment to the dynamic IP must be passed along
   * with the heartbeat.
   */
  private void updateExternal(ServerHeartbeat server, String externalId)
  {
    if (externalId != null) { 
      ServerHeartbeat serverExternal = _root.getServer(externalId);
      
      server.setExternalServer(serverExternal);
    }
    else {
      server.setExternalServer(null);
      /*
      if (serverExternal != null) {
        serverExternal.setDelegate(data);
        
        _root.updateSequence();
      }
      */
    }
    
    // data.setExternalId(externalId);
  }
  
  private boolean heartbeatStart(ServerHeartbeat server)
  {
    /*
    if (peerServer.getExternalId() != null) {
      server.setExternalId(peerServer.getExternalId());
    }
    */
    //server.setDisplayName(server.getDisplayName());

    if (server.onHeartbeatStart()) {
      onServerStart(server);
    
      server.getRack().update();
      
      // sendHeartbeatPong(server.getAddress(), server.getPort());
      updateHeartbeats();
      
      return true;
    }
    else {
      return false;
    }
  }
  
  private void onServerStart(ServerHeartbeat server)
  {
    if (log.isLoggable(Level.FINER)) {
      log.finer("start-heartbeat " + server + " (" + _serverSelf.getDisplayName() + ")");
    }

    _serverEvents.onServerUpdate(server);
    
    ServerTarget target = findTargetServer(server.getAddress(), server.port());
    
    if (target != null) {
      target.clear();
    }
  }

  /**
   * The peer server is now down.
   */
  private void onServerLinkClose(String hostName)
  {
    ServerHeartbeat server = _root.getServer(hostName);
  
    if (server == null) {
      return;
    }
    
    if (! isHub(server) && ! isHub(_serverSelf)) {
      return;
    }
    
    if (server.onHeartbeatStop()) {
      onServerStop(server);

      if (server.getRack() != null) {
        server.getRack().update();
      }
      
      updateHeartbeats();
    }
  }
  
  private void onServerStop(ServerHeartbeat server)
  {
    if (log.isLoggable(Level.FINER)) {
      log.finer("stop-heartbeat " + server + " (" + _serverSelf.getDisplayName() + ")");
    }

    _serverEvents.onServerUpdate(server);
  }
  
  private void updatePodsFromHeartbeat()
  {
    // update from heartbeat only after receiving a heartbeat message
    // to prioritize the existing pod update over a new
    // server's self-discovery.
    // XXX: _podService.updatePodsFromHeartbeat();
  }

  private void addTargetServers()
  {
    _targets.clear();
    
    for (ServerHeartbeat server : _rack.getServers()) {
      if (server != null && ! server.isSelf()) {
        addTargetServer(server);
      }
    }
  }

  private void addTargetServer(ServerHeartbeat server)
  {
    ServerTarget target = createTargetServer(server);
      
    if (target != null) {
      _targets.add(target);
    }
  }
  
  private ServerTarget createTargetServer(ServerHeartbeat server)
  {
    ServerTarget target = _targetMap.get(server.getId());
    
    if (target != null) {
      return target;
    }
    
    // ServiceManager manager = AmpSystem.getCurrentManager();
    
    String path = HeartbeatService.PATH;
    
    // XXX: need lookup() vs onLookup()
    //ServiceRef serviceRef = server.getServiceRef().lookup(path);
    
    ServiceRef serviceRef;
    
    serviceRef = _rampManager.service("bartender://" + server.getId() + path);

    if (serviceRef != null) {
      HeartbeatService heartbeat = serviceRef.as(HeartbeatService.class);
    
      target = new ServerTarget(server, heartbeat);
      
      _targetMap.put(server.getId(), target);
      
      return target;
    }
    else {
      return null;
    }
  }

  public void onPodSystemUpdate()
  {
    updateHeartbeats();
  }

  public void updatePod(UpdatePodSystem podSystem)
  {
    updateHeartbeats();
  }
  
  /**
   * Sends the heartbeat messages to the servers this node is reponsible for.
   */
  void updateHeartbeats()
  {
    RackHeartbeat rack = _rack;
    // PodHeartbeatService podService = getPodHeartbeatService();

    if (rack == null) { // || podService == null) {
      return;
    }
    
    // XXX: updateTargetServers();
    sendHeartbeats();
  }

  //
  // outgoing heartbeats: sending heartbeat messages to target servers.
  //
  
  private void updateTargetServers()
  {
    updatePodsFromHeartbeat();
    
    _rack.update();
    
    long rackCrc = getRack().getUpdate().getCrc();
    
    if (_lastRackCrc != rackCrc) {
      _lastRackCrc = rackCrc;

      addTargetServers();
    }
  }
  
  /**
   * Sends the heartbeat messages to the servers this node is reponsible for.
   */
  private void sendHeartbeats()
  {
    if (! _lifecycle.isActive()) {
      return;
    }
    
    _isSendHeartbeatsRequired = true;
  }
  
  @AfterBatch
  public void afterBatch()
  {
    if (! _isSendHeartbeatsRequired) {
      return;
    }
    
    _isSendHeartbeatsRequired = false;
    
    if (isHub(_serverSelf)) {
      sendHubHeartbeats();
      sendClusterHeartbeats();
    }
    else {
      sendSpokeHeartbeats();
    }
  }
  
  /**
   * Test if the server is a hub server for its rack.
   * 
   * The hub are the first few active servers, where the servers are sorted by
   * IP address and port. The default hub count is 3.
   */
  private boolean isHub(ServerBartender server)
  {
    int upCount = 0;
    
    for (ServerHeartbeat rackServer : _rack.getServers()) {
      if (rackServer == null || ! rackServer.isUp()) {
        continue;
      }
      
      if (rackServer == server && upCount < _hubCount) {
        return true;
      }
      
      upCount++;
        
      if (_hubCount <= upCount) {
        return false;
      }
    }
    
    return false;
  }
  
  /**
   * Sends full heartbeat messages to all targets. Hub heartbeat messages
   * contains all the servers in the hub and all the pods known to the hub
   * server.
   * @param type 
   */
  private void sendHubHeartbeats()
  {
    RackHeartbeat rack = _rack;
    
    UpdateRackHeartbeat updateRack = rack.getUpdate();
    UpdatePodSystem updatePod = getUpdatePodSystem();
    
    long now = CurrentTime.currentTime();
    
    if (! isJoinComplete()) {
      updatePod = null;
    }

    // send hub update to all servers in the rack
    for (ServerHeartbeat rackServer : _rack.getServers()) {
      if (rackServer == null || rackServer == _serverSelf) {
        continue;
      }

      ServerTarget target = createTargetServer(rackServer);
      
      target.hubHeartbeat(getServerSelf().getUpdate(),
                             updateRack,
                             updatePod,
                             now);
    }
  }
  
  /**
   * Sends a server status message to the hub servers. The server message
   * contains the status for the current server.
   */
  private void sendSpokeHeartbeats()
  {
    for (ServerTarget target : _targets) {
      ServerHeartbeat server = target.getServer();
      
      if (server.getRack() != _rack) {
        continue;
      }
      
      if (isHub(server)) {
        target.sendServerHeartbeat(getServerSelf().getUpdate());
      }
    }
  }
  
  /**
   * Creates a status message for a single server.
   */
  /*
  private UpdateServerHeartbeat createMessage(ServerHeartbeat server)
  {
    return new UpdateServerHeartbeat(server); 
  }
  */
  
  private ServerTarget findTargetServer(String address,
                                        int port)
  {
    for (ServerTarget target : _targets) {
      ServerBartender server = target.getServer();
      
      if (address.equals(server.getAddress()) && port == server.port()) {
        return target;
      }
    }
    
    return null;
  }
  
  private UpdatePodSystem getUpdatePodSystem()
  {
    PodHeartbeatService podService = getPodHeartbeat();
    
    if (podService != null) {
      return podService.getUpdatePodSystem();
    }
    else {
      return null;
    }
  }
  
  private void sendClusterHeartbeats()
  {
    for (ClusterTarget target : _clusterTargets.values()) {
      sendClusterHeartbeat(target);
    }
  }
  
  private void sendClusterHeartbeat(ClusterTarget target)
  {
    /*
    if (! target.isHeartbeatSendTimeout()) {
      return;
    }
    */
    
    UpdateRackHeartbeat updateRack = _rack.getUpdate();
    UpdatePodSystem updatePod = getUpdatePodSystem();
    UpdateServerHeartbeat updateSelf = getServerSelf().getUpdate();
    
    long now = CurrentTime.currentTime();

    /*
    ClusterHeartbeat cluster = target.getCluster();

    if (target.isHeartbeatTimeout() && target.isJoinTimeout()) {
      target.setJoinTime(now);

      _joinClient.joinCluster(cluster);
    }

    target.setHeartbeatSendTime(now);
    */
    
    for (ServerHeartbeat server : target.getServers()) {
      ServerTarget serverTarget = createTargetServer(server);

      if (serverTarget != null) {
        serverTarget.clusterHeartbeat(_serverSelf.getClusterId(),
                                      updateSelf,
                                      updateRack,
                                      updatePod,
                                      now);
      }
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getServerSelf() + "]";
  }
  
  enum JoinState {
    INIT {
      @Override
      public JoinState onJoinComplete(int remoteCount,
                                      HeartbeatImpl heartbeat)
      {
        if (remoteCount <= 0) {
          // PodLocalService podService = heartbeat._bartender.getPodService();
          // podService.initPods();
          
          return onHubHeartbeat(heartbeat);
        }
        else {
          return this;
        }
      }

      @Override
      public JoinState onHubHeartbeat(HeartbeatImpl heartbeat)
      {
        PodLocalService podService = heartbeat._bartender.getPodService();
        PodsManagerServiceLocal podClusterService = heartbeat._bartender.getPodsClusterService();
        
        podService.onJoinStart(Result.onOk(x->{
          heartbeat._bartender.getPodsClusterService().onJoinStart();
        }));
        
        podService.start();
        
        heartbeat.updateHeartbeats();
        
        return HEARTBEAT_RECEIVED;
      }
    },
    
    HEARTBEAT_RECEIVED {
      @Override
      public boolean isJoinComplete()
      {
        return true;
      }
    };
    
    public boolean isJoinComplete()
    {
      return false;
    }
    
    public JoinState onJoinComplete(int remoteCount,
                                    HeartbeatImpl heartbeat)
    {
      return this;
    }

    public JoinState onHubHeartbeat(HeartbeatImpl heartbeat)
    {
      return this;
    }
  }
  
  /**
   * The link listener detects when a peer server's TCP connections have all
   * closed. When the close is detected, it pings the server.
   * 
   * XXX: note that for a non-hub server, the link close might be normal.
   */
  private class NetworkLinkListener implements LinkBartenderEvents
  {
    @Override
    public void onLinkClose(String hostName, int count)
    {
      if (count > 0) {
        return;
      }
      
      onServerLinkClose(hostName);
    }
  }
  
  private class ClusterTarget {
    private final ClusterHeartbeat _cluster;
    
    private long _lastHeartbeat;
    private long _lastHeartbeatSend;
    
    private long _lastJoinSend;
    
    private ArrayList<ServerHeartbeat> _serverList = new ArrayList<>();
    
    ClusterTarget(ClusterHeartbeat cluster)
    {
      _cluster = cluster;
    }
    
    /**
     * @param server
     */
    public boolean addServer(ServerHeartbeat server)
    {
      if (! _serverList.contains(server)) {
        _serverList.add(server);
        
        // server.clearConnectionFailTime();
        
        //_lastHeartbeatSend = 0;
        //_lastJoinSend = 0;
        
        return true;
      }
      else {
        return false;
      }
    }
    
    public Iterable<ServerHeartbeat> getServers()
    {
      ArrayList<ServerHeartbeat> servers = new ArrayList<>();
      
      for (RackHeartbeat rack : _cluster.getRacks()) {
        for (ServerHeartbeat server : rack.getServers()) {
          if (servers.size() < 3) {
            servers.add(server);
          }
        }
      }
      
      for (ServerHeartbeat server : _serverList) {
        if (! servers.contains(server)) {
          servers.add(server);
        }
      }
      
      // return _serverList;
      return servers;
    }

    /*
    ClusterHeartbeat getCluster()
    {
      return _cluster;
    }
    
    boolean isHeartbeatTimeout()
    {
      long now = CurrentTime.getCurrentTime();
      long timeout = 120 * 1000L;
      
      return timeout < (now - _lastHeartbeat);
    }
    
    boolean isHeartbeatSendTimeout()
    {
      long now = CurrentTime.getCurrentTime();
      
      if (_lastHeartbeatSend <= _lastUpdateTime) {
        return true;
      }
      
      long timeout = 120 * 1000L;
      
      return timeout < (now - _lastHeartbeatSend);
    }
    
    void setHeartbeatSendTime(long now)
    {
      _lastHeartbeatSend = now;
    }
    
    boolean isJoinTimeout()
    {
      long now = CurrentTime.getCurrentTime();
      long timeout = 120 * 1000L;
      
      return timeout < (now - _lastJoinSend);
    }
    
    void setJoinTime(long now)
    {
      _lastJoinSend = now;
    }
    */
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _cluster + "]";
    }
  }
  
  /*
  enum HeartbeatType {
    ALARM,
    UPDATE;
  }
  */
  
  private class HeartbeatTask implements Consumer<Cancel> {
    @Override
    public void accept(Cancel handle)
    {
      try {
        sendHeartbeats();
      } finally {
        if (_lifecycle.isActive()) {
          _timer.runAfter(_heartbeatTask, _heartbeatTimeout, TimeUnit.MILLISECONDS, Result.ignore());
        }
      }
    }
  }
  
  private class JoinTask implements Consumer<Cancel> {
    @Override
    public void accept(Cancel handle)
    {
      if (! _joinLifecycle.isActive()) {
        return;
      }
      
      try {
        if (isJoinRequired()) {
          _lastJoinTime = CurrentTime.currentTime();
          
          _joinClient.start(x->updateRack(x), 
                            Result.onOk(count->onJoinComplete(count, Result.ignore())));
        }
      } finally {
        _timer.runAfter(_joinTask, getJoinTimeout(), TimeUnit.MILLISECONDS, Result.ignore());
      }
    }
  }
}
