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

package com.caucho.v5.bartender.link;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.service.ServiceRefAlias;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.hamp.AuthHampManager;
import com.caucho.v5.bartender.hamp.HampManager;
import com.caucho.v5.bartender.hamp.HampManagerBuilder;
import com.caucho.v5.bartender.heartbeat.HeartbeatSeedService;
import com.caucho.v5.bartender.heartbeat.HeartbeatSeedServiceImpl;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.bartender.pod.SchemeBartenderPodProxy;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.http.pod.PodContainer;
import com.caucho.v5.network.NetworkSystemBartender;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.L10N;


/**
 * The link system manages connections between bartender servers.
 */
public class LinkBartenderSystem extends SubSystemBase
{
  private static final L10N L = new L10N(LinkBartenderSystem.class);
  
  // priority must be before network so it's available to handle incoming
  // messages
  public static final int START_PRIORITY
    = NetworkSystemBartender.START_PRIORITY + 1;
  
  private final AmpSystem _ampSystem;
  
  private final HampManager _linkManager;
  private final ServerBartender _serverSelf;
  
  private final AtomicLong _externalMessageReadCount = new AtomicLong();
  private final AtomicLong _externalMessageWriteCount = new AtomicLong();
  
  private AuthHampManager _authManager;
  
  private SchemeBartenderBase _schemeBartenderSystem;
  private SchemeBartenderBase _schemeBartenderPod;

  // private RampServiceRef _systemActorRef;

  private LinkBartenderService _linkService;

  //private RootBartender _rootBartender;

  private BartenderSystem _bartender;

  private HampManager _linkManagerPublic;
  
  private LinkBartenderSystem(BartenderSystem bartender)
  {
    Objects.requireNonNull(bartender);
    
    _bartender = bartender;
    _serverSelf = bartender.serverSelf();
    // _rootBartender = rootBartender;
    
    _ampSystem = AmpSystem.getCurrent();
    
    if (_ampSystem == null) {
      throw new ConfigException(L.l("{0} requires an active {1}",
                                    getClass().getSimpleName(),
                                    AmpSystem.class.getSimpleName()));
    }
    
    ServiceManagerAmp rampManager = _ampSystem.getManager();
    
    _linkManager = createChampManager();
    _linkManagerPublic = createLinkManagerPublic();
    
    ServiceRefAmp systemRef = rampManager.service("system://");
    
    // String alias = HampServer.calculateHostName(selfServer) + "/system";
    String alias = "public:///system";

    ServiceRefAmp aliasSystemRef = new ServiceRefAlias(alias, systemRef);
    
    rampManager.registry().bind(alias, aliasSystemRef);
    
    init();
  }
  
  public static LinkBartenderSystem createAndAddSystem(BartenderSystem bartender)
  {
    SystemManager system = SystemManager.getCurrent();
    
    LinkBartenderSystem champ = new LinkBartenderSystem(bartender);
    
    system.addSystem(champ);
    
    return champ;
  }
  
  protected ServerBartender getServerSelf()
  {
    return _serverSelf;
  }

  private HampManager createChampManager()
  {
    ServiceManagerAmp ampManager = AmpSystem.currentManager();
    
    LinkBartenderServiceImpl linkServiceImpl
      = new LinkBartenderServiceImpl(getServerSelf());
    
    _linkService = ampManager.newService(linkServiceImpl)
                             .as(LinkBartenderService.class);
    
    HeartbeatSeedServiceImpl seedService
      = new HeartbeatSeedServiceImpl((ServerHeartbeat) _serverSelf);
    
    ampManager.newService(seedService).address("public://" + HeartbeatSeedService.ADDRESS).ref();
    
    // .bind("/system/cluster")
    
    //_champService = ampManager.createProxy(_systemActorRef, 
    //                          ChampSystemService.class);
    
    // _champService = _systemActorRef.as(ChampSystemService.class);
   
    HampManagerBuilder builder = new HampManagerBuilder();
    builder.ampManager(()->ampManager);
    builder.serverSelf(_serverSelf);
    
    return new LinkBartenderManager(builder, _linkService);
  }

  private HampManager createLinkManagerPublic()
  {
    ServiceManagerAmp ampManager = ServiceManagerAmp.newManager().start();
    
    HeartbeatSeedServiceImpl seedService
      = new HeartbeatSeedServiceImpl((ServerHeartbeat) _serverSelf);
    
    ampManager.newService(seedService).address("public://" + HeartbeatSeedService.ADDRESS).ref();
   
    HampManagerBuilder builder = new HampManagerBuilder();
    builder.ampManager(()->ampManager);
    builder.serverSelf(_serverSelf);
    
    return new HampManager(builder);
  }
  
  protected void init()
  {
    ServiceManagerAmp rampManager = getManager();
      
    ServerLinkBartenderBuilder builder = new ServerLinkBartenderBuilder();
    builder.rampManager(rampManager);
      
    _schemeBartenderSystem = new SchemeBartenderSystem(_bartender,
                                                       builder,
                                                       getServerSelf());
    
    _schemeBartenderSystem.bind("bartender:");
  }
  
  public void initNetworkService(NetworkSystemBartender networkSystem)
  {
    networkSystem.serviceBartender(_linkManager.serverPath(),
                                   _linkManager.serverFactory());
    networkSystem.serviceBartender(_linkManager.unidirPath(),
                                   _linkManager.unidirFactory());
    
    networkSystem.servicePublic(_linkManagerPublic.serverPath(),
                                _linkManagerPublic.serverFactory());
  }
  
  public String getAddress()
  {
    return _ampSystem.getAddress();
  }
  
  public ServiceManagerAmp getManager()
  {
    return _ampSystem.getManager();
  }
  
  public SchemeBartenderBase getSchemeBartenderPod()
  {
    return _schemeBartenderPod;
  }

  @Override
  protected Level getLifecycleLogLevel()
  {
    return Level.FINER;
  }

  AuthHampManager getAuthManager()
  {
    return _authManager;
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
    
    PodContainer podContainer = PodContainer.getCurrent();
    
    if (podContainer != null) {
      ServiceManagerBuilderAmp ampBuilder = ServiceManagerAmp.newManager();
      //ServiceManagerBartender ampManagerChamp = new ServiceManagerBartender(rampBuilder);
      // ServiceManagerAmp ampManagerPod = Amp.newManager();
      
      ampBuilder.name("system-pod," + _serverSelf.getDisplayName());
      ampBuilder.autoServices(true);
      ServiceManagerAmp ampManagerPod = ampBuilder.start();
      
      ampManagerPod.inboxSystem().serviceRef().bind("public:///system");
      
      ServiceRefPodAppRoot podAppRootRef
        = new ServiceRefPodAppRoot(ampManagerPod, podContainer);
      
      podAppRootRef.bind("public:///s");

      // cluster: scheme for reply
      ServerLinkBartenderBuilder linkBuilder = new ServerLinkBartenderBuilder();
      linkBuilder.rampManager(ampManagerPod);
      linkBuilder.webSocketPath("/pod");

      SchemeBartenderBase schemeBartenderPod;
      schemeBartenderPod = new SchemeBartenderPod(_bartender, linkBuilder, 
                                                  getServerSelf());
      schemeBartenderPod.bind(schemeBartenderPod.address());

      _schemeBartenderPod = schemeBartenderPod;

      // bartender-pod: is bound in AmpSystem
      ServiceManagerAmp ampManager = _ampSystem.getManager();
      SchemeBartenderPodProxy schemeBartenderPodProxy
        = new SchemeBartenderPodProxy(_bartender, ampManager, null);
      schemeBartenderPodProxy.bind(schemeBartenderPodProxy.address());

      // hamp protocol /pod manages the pod requests
      HampManagerBuilder builder = new HampManagerBuilder();
      builder.unidirPath("/pod");
      builder.replyScheme("bartender-pod:");
      builder.ampManager(()->ampManagerPod);
      builder.serverSelf(_serverSelf);

      HampManager hampManager = new HampManager(builder);

      if (true) { throw new UnsupportedOperationException(); }
      //NetworkSystem.getCurrent().publishBartender(hampManager.getUnidirEndpointConfig());
    }
    
    _linkManager.start();
  }
  
  @Override
  public void stop(ShutdownModeAmp mode)
    throws Exception
  {
    super.stop(mode);
    
    _schemeBartenderSystem.close();
  }

  public void addExternalMessageRead()
  {
    _externalMessageReadCount.incrementAndGet();
  }
  
  public long getExternalMessageReadCount()
  {
    return _externalMessageReadCount.get();
  }

  public void addExternalMessageWrite()
  {
    _externalMessageWriteCount.incrementAndGet();
  }
  
  public long getExternalMessageWriteCount()
  {
    return _externalMessageWriteCount.get();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getAddress() + "]";
  }
}
