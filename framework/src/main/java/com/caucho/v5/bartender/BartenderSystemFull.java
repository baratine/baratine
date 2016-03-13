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

package com.caucho.v5.bartender;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.heartbeat.HeartbeatImpl;
import com.caucho.v5.bartender.heartbeat.HeartbeatLocalImpl;
import com.caucho.v5.bartender.heartbeat.HeartbeatService;
import com.caucho.v5.bartender.heartbeat.HeartbeatServiceLocal;
import com.caucho.v5.bartender.link.LinkBartenderSystem;
import com.caucho.v5.bartender.link.SchemeBartenderBase;
import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.bartender.pod.PodHeartbeatService;
import com.caucho.v5.bartender.pod.PodLocalService;
import com.caucho.v5.bartender.pod.PodManagerService;
import com.caucho.v5.bartender.pod.PodsManagerService;
import com.caucho.v5.bartender.pod.PodsManagerServiceImpl;
import com.caucho.v5.bartender.pod.PodsManagerServiceLocal;
import com.caucho.v5.bartender.pod.SchemePodSystem;
import com.caucho.v5.bartender.proc.AdminService;
import com.caucho.v5.bartender.proc.AdminServiceImpl;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.Crc64;
import com.caucho.v5.util.L10N;

import io.baratine.service.ResultFuture;
import io.baratine.service.ServiceExceptionConnect;
import io.baratine.service.ServiceRef;

public class BartenderSystemFull extends BartenderSystem
{
  private static final L10N L = new L10N(BartenderSystemFull.class);
  private static final Logger log
    = Logger.getLogger(BartenderSystemFull.class.getName());
  
  public static final int START_PRIORITY = START_PRIORITY_HEARTBEAT;
  
  private static final EnvironmentLocal<PodBartender> _localPod
    = new EnvironmentLocal<>();
    
  private static final EnvironmentLocal<NodePodAmp> _localShard
    = new EnvironmentLocal<>();
  
  private final RootBartender _root;
  private final ServerBartender _serverSelf;
  
  private boolean _isClosed;
  
  private HeartbeatLocalImpl _serviceImpl;
  private ServiceRef _serviceRef;
  
  // private BartenderActorCache _actorCache;
  private HeartbeatServiceLocal _heartbeatLocal;
  
  //private FailoverService _failoverService;

 // private PodServiceImpl _clusterServiceImpl;

  private LinkBartenderSystem _linkSystem;
  
  private final HeartbeatService _heartbeat;
  private final PodHeartbeatService _podHeartbeat;
  
  private PodLocalService _podService;
  // private PodManagerService _podManager;

  //private JoinService _joinService;

  private boolean _isFileSystemEnabled;
  private AdminService _adminService;
  private PodManagerService _podManagerHub;
  private PodsManagerServiceLocal _podsService;
  
  protected BartenderSystemFull(BartenderBuilder builder)
  {
    _serverSelf = builder.getServerSelf();
    
    builder.build(this);
    
    _root = builder.getRoot();
    
    _heartbeat = builder.getHeartbeat();
    _heartbeatLocal = builder.getHeartbeatLocal();
    
    _podHeartbeat = builder.getPodHeartbeat();
    _podService = builder.getPodService();
    // _podManager = builder.getPodManager();
    
    builder.buildPods();
    
    // _joinClientImpl = builder.buildJoinClient();
    
    _isFileSystemEnabled = builder.isFileSystemEnabled();
    
    _linkSystem = LinkBartenderSystem.createAndAddSystem(this);
    
    /*
    _serviceImpl = new BartenderServiceImpl(this,
                                            _heartbeat,
                                            _podService);
                                            */
    
    ServiceManagerAmp ampManager = AmpSystem.currentManager();

    new SchemePodSystem(this, ampManager).bind("pod:");
  }

  /**
   * Creates a new network cluster service.
   */
  public static BartenderSystem
  createAndAddSystem(BartenderBuilder builder)
  {
    BartenderSystemFull systemBartender
      = new BartenderSystemFull(builder);
    
    SystemManager system = preCreate(BartenderSystem.class);

    system.addSystem(BartenderSystem.class, systemBartender);
    
    return systemBartender;
  }

  @Override
  public boolean isFileSystemEnabled()
  {
    return _isFileSystemEnabled;
  }
  
  @Override
  public PodBartender getLocalPod()
  {
    PodBartender pod = _localPod.get();
    
    if (pod != null) {
      return pod;
    }
    else {
      return findPod("cluster");
    }
  }
  
  @Override
  public PodBartender getLocalPod(ClassLoader loader)
  {
    PodBartender pod = _localPod.get(loader);
    
    if (pod != null) {
      return pod;
    }
    else {
      return findPod("cluster");
    }
  }
  
  @Override
  public void setLocalPod(PodBartender pod)
  {
    _localPod.set(pod);
  }
  
  @Override
  public NodePodAmp getLocalShard()
  {
    NodePodAmp shard = _localShard.get();
    
    if (shard != null) {
      return shard;
    }
    else {
      return null;
    }
  }
  
  @Override
  public void setLocalShard(NodePodAmp shard, ClassLoader loader)
  {
    _localShard.set(shard, loader);
  }
  
  @Override
  public boolean isClosed()
  {
    return _isClosed;
  }
  
  @Override
  public HeartbeatServiceLocal getService()
  {
    return _heartbeatLocal;
  }
  
  /*
  public FailoverService getFailoverService()
  {
    return _failoverService;
  }
  */
  
  @Override
  public RootBartender getRoot()
  {
    return _root;
  }

  @Override
  public PodHeartbeatService getPodHeartbeat()
  {
    return _podHeartbeat;
  }
  
  @Override
  public PodLocalService getPodService()
  {
    return _podService;
  }
  
  @Override
  public PodsManagerServiceLocal getPodsClusterService()
  {
    return _podsService;
  }
  
  @Override
  public PodBartender findPod(String podName)
  {
    return _podHeartbeat.getPod(canonName(podName));
  }

  @Override
  public PodBartender findActivePod(String podName)
  {
    return _podHeartbeat.getActivePod(canonName(podName));
  }
  
  private String canonName(String podName)
  {
    int p = podName.indexOf('.');
    
    if (p < 0) {
      if ("local".equals(podName)) {
        podName = "local.local";
      }
      else {
        podName = podName + '.' + serverSelf().getClusterId();
      }
    }
    
    return podName;
  }

  //@Override
  protected ServiceRef getServiceRef()
  {
    return _serviceRef;
  }
  
  /**
   * Return the champ scheme for pod messages.
   */
  public SchemeBartenderBase schemeBartenderPod()
  {
    return _linkSystem.getSchemeBartenderPod();
  }
  
  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }
  
  @Override
  public void start()
    throws Exception
  {
    super.start();
    
    ServiceManagerAmp rampManager = AmpSystem.currentManager();

    _adminService = rampManager.newService(new AdminServiceImpl(this))
                               .address("public:///management")
                               .start()
                               .as(AdminService.class);
    
    _podsService = rampManager.newService(new PodsManagerServiceImpl(this))
                              .address("public://" + PodsManagerService.ADDRESS)
                              .start()
                              .as(PodsManagerServiceLocal.class);
    
    _podManagerHub = rampManager.service("pod://cluster_root" + PodManagerService.PATH)
                                .as(PodManagerService.class);
    
    // _linkSystem.start();
    
    ResultFuture<Boolean> future = new ResultFuture<>();
    
    _heartbeatLocal.start(future);
    
    try {
      Boolean value = rampManager.run(10, TimeUnit.SECONDS, 
                                      r->_heartbeatLocal.start(r));
      
      if (! Boolean.TRUE.equals(value)) { // future.get(10, TimeUnit.SECONDS))) {
        System.out.println("DELAYED_START:");
      }
    } catch (ServiceExceptionConnect e) {
      if (log.isLoggable(Level.FINER)) {
        log.finer(e.toString());
      }
      else {
        log.log(Level.FINEST, e.toString(), e);
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    // _podsService.onJoinStart();
    
    // XXX: timing issues with join complete
    // _podService.start();
    
    // startJoin();
  }

  @Override
  HeartbeatService getHeartbeatService()
  {
    return _heartbeat;
  }

  @Override
  public HeartbeatServiceLocal getHeartbeatLocal()
  {
    return _heartbeatLocal;
  }
  
  @Override
  HeartbeatImpl getHeartbeatServiceImpl()
  {
    // return _heartbeatServiceImpl;
    return null;
  }

  /*
  public void startJoin()
  {
    ResultFuture<Integer> joinFuture = new ResultFuture<>();

    _joinClient.start(joinFuture);
    
    int count = 0;
    
    try {
      count = joinFuture.get(2, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    if (count <= 0) {
      return;
    }
    
    // If the server contacts another server in the cluster, wait for
    // an update so it can start with correct cluster information.
    
    ResultFuture<Boolean> heartbeatFuture = new ResultFuture<>();
    
    _joinService.waitForHubHeartbeat(count, heartbeatFuture);
    
    try {
      heartbeatFuture.get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
      // System.out.println("EXN: " + count + " " + e + " " + BartenderSystem.getCurrentSelfServer());
    } finally {
      // XXX: _heartbeatServiceImpl.setJoinComplete(_joinClient.getJoinServerCount());
      
      // _podServiceImpl.updatePodFailover();
    }
  }
  */

  @Override
  public ServerBartender findServerByName(String name)
  {
    return _heartbeatLocal.getServer(name);
  }

  @Override
  public ServerBartender getServerHandle(String address, 
                                         int port)
  {
    boolean isSSL = false;
    
    return _heartbeatLocal.getServerHandle(address, port, isSSL);
  }

  @Override
  public ClusterBartender findCluster(String clusterId)
  {
    return _heartbeatLocal.findCluster(clusterId);
  }

  
  @Override
  protected HeartbeatLocalImpl getBartenderServiceImpl()
  {
    return _serviceImpl;
  }

  @Override
  public PodManagerService getPodManagerHub()
  {
    return _podManagerHub;
  }
  
  @Override
  public ServerBartender serverSelf()
  {
    return _serverSelf;
  }

  /*
  public JoinService getJoinService()
  {
    return _joinService;
  }
  */
  
  //@Override
  protected String getHostName()
  {
    try {
      InetAddress localHost = InetAddress.getLocalHost();
      
      return localHost.getHostName();
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    try {
      InetAddress localHost = InetAddress.getLocalHost();
      
      return localHost.getHostAddress();
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    return getAddress();
  }

  protected String getAddress()
  {
    try {
      return SocketSystem.current().getHostAddress();
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    return "127.0.0.1";
  }
  
  protected long createUniqueServerHash(int port)
  {
    long hash = 0;
    
    hash = Crc64.generate(hash, port);
    hash = Crc64.generate(hash, getHardwareAddress()); 
    
    return hash;
  }
  
  protected byte []getHardwareAddress()
  {
    return SocketSystem.current().getHardwareAddress();
  }
  
  @Override
  public void stop(ShutdownModeAmp mode) throws Exception
  {
    _podService.stop();
    _heartbeatLocal.stop();
    
    _linkSystem.stop(mode);
    // _service.shutdown();
  }
}
