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

package com.caucho.v5.http.pod;

import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.manager.ServiceManagerAmpImpl;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.RegistryAmp;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.deploy2.DeployInstance2;
import com.caucho.v5.inject.InjectManagerAmp;
import com.caucho.v5.javac.WorkDir;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.loader.DependencyContainer;
import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;

/**
 * Builder for the webapp to encapsulate the configuration process.
 */
public class PodApp implements DeployInstance2, PodManagerApp
//implements DeployInstanceEnvironment, EnvironmentBean, PodManagerApp
{
  private static final Logger log = Logger.getLogger(PodApp.class.getName());
  
  private static final EnvironmentLocal<PodApp> _podAppLocal
    = new EnvironmentLocal<>();
  
  private String _id;
  
  private EnvironmentClassLoader _classLoader;
  
  private Throwable _configException;

  private ServiceManagerAmp _ampManager;

  private PodAppBuilder _builder;
  
  private final Lifecycle _lifecycle;

  //private PodAppController _controller;

  private InjectManagerAmp _injectManager;

  private String _tag;

  private NodePodAmp _podNode;
  
  private long _activeWaitTime = 60000L;

  private ServerBartender _serverSelf;
  private DependencyContainer _depend = new DependencyContainer();
  
  PodApp(PodAppBuilder builder)
  {
    _id = builder.getId();
    
    _classLoader = builder.getClassLoader();
    Objects.requireNonNull(_classLoader);
    
    //_classLoader.addScanListener();
    
    _builder = builder;
    //_controller = builder.getController();
    
    _serverSelf = BartenderSystem.getCurrentSelfServer();
    _podNode = _builder.getPodShard();
    
    _depend.add(_classLoader);
    _depend.setCheckInterval(2000);
    
    _lifecycle = new Lifecycle(log, toString(), Level.FINER);
    
    _podAppLocal.set(this, _classLoader);
  }
  
  public static PodApp getCurrent()
  {
    return _podAppLocal.get();
  }
  
  public String getId()
  {
    return _id;
  }
  
  public String getPodName()
  {
    //return _controller.getPodName();
    throw new UnsupportedOperationException();
  }
  
  public String getTag()
  {
    return _tag;
  }
  
  //@Override
  public EnvironmentClassLoader getClassLoader()
  {
    return _classLoader;
  }
  
  PodAppBuilder getBuilder()
  {
    return _builder;
  }

  public boolean isActive()
  {
    return _lifecycle.isActive();
  }
  
  public PathImpl getRootDirectory()
  {
    //return _controller.getRootDirectory();
    throw new UnsupportedOperationException();
  }
  
  public NodePodAmp getPodNode()
  {
    //return _controller.getPodNode();
    throw new UnsupportedOperationException();
  }

  //@Override
  public void setConfigException(Throwable e)
  {
    _configException = e;
    
    setAmpConfigException(e);
  }

  private void setAmpConfigException(Throwable e)
  {
    if (_ampManager instanceof ServiceManagerAmpImpl) {
      ((ServiceManagerAmpImpl) _ampManager).setConfigException(e);
    }
  }

  @Override
  public Throwable getConfigException()
  {
    return _configException;
  }

  /*
  @Override
  public boolean isDeployIdle()
  {
    return false;
  }
  */

  @Override
  public ServiceManagerAmp getAmpManager()
  {
    return _ampManager;
  }

  public Iterable<ServiceRefAmp> getServiceList()
  {
    ArrayList<ServiceRefAmp> services = new ArrayList<>();
    
    ServiceManagerAmp ampManager = getAmpManager();
    
    RegistryAmp registry = ampManager.registry();
    
    for (ServiceRefAmp service : registry.getServices()) {
      if (service.address().startsWith("public:///")) {
        services.add(service);
      }
      else if (service.address().startsWith("pod:///")) {
        services.add(service);
      }
      else if (service.address().startsWith("session:///")) {
        services.add(service);
      }
      
    }
    // ampManager.getL..getS
    
    return services;
  }

  @Override
  public boolean isModified()
  {
    return _depend.isModified() || _lifecycle.isAfterStopping();
  }

  @Override
  public boolean isModifiedNow()
  {
    return _depend.isModifiedNow() || _lifecycle.isAfterStopping();
  }

  @Override
  public boolean logModified(Logger log)
  {
    return _depend.logModified(log);
  }
  
  // lifecycle
  //

  protected void initConstructor()
  {
    try {
      // _classLoader.setId("web-app:" + getId());

      PathImpl rootDirectory = getRootDirectory();

      VfsOld.setPwd(rootDirectory, _classLoader);
      WorkDir.setLocalWorkDir(rootDirectory.lookup("META-INF/work"),
                              _classLoader);
      
      // _listenerManager = getBuilder().getListenerManager();

      //getBuilder().getScanner().addScanListeners();
      
      // _invocationDependency.add(_controller);

      _injectManager = InjectManagerAmp.create(_classLoader);
      //_cdiManager.addExtension(new CdiExtensionBaratine(_cdiManager));
      _injectManager.scanRoot(_classLoader);
    } catch (Throwable e) {
      setConfigException(e);
    }
  }

  /*
  @Override
  public void preConfigInit() throws Exception
  {
  }
  */

  public void postClassLoaderInit()
  {
    // bartender.setLocalShard(pod.getNode(podNode));
    ServiceManagerBuilderAmp builder = ServiceManagerAmp.newManager();
    builder.name(EnvLoader.getEnvironmentName());
    
    BartenderSystem bartender = BartenderSystem.current();
    if (bartender != null) {
      PodBartender pod = bartender.findActivePod(getPodName());
    
      Objects.requireNonNull(pod);
    
      int podNode = _builder.getPodNode();
      bartender.setLocalPod(pod);
      //builder.setPodNode(new ServiceNodeImpl(pod.getNode(podNode)));
    }
    
    /*
    //builder.setJournalMaxCount(_builder.getJournalMaxCount());
    builder.setJournalDelay(_builder.getJournalDelay());
    // XXX: config timing issue
    builder.debug(_builder.isDebug());
    builder.debugQueryTimeout(_builder.getDebugQueryTimeout());
    builder.autoStart(false);
    */
    _ampManager = builder.start();

    /*
    if (_controller.getConfigException() != null) {
      setAmpConfigException(_controller.getConfigException());
    }
    else if (_configException != null) {
      setAmpConfigException(_configException);
    }
    */
   
    Amp.contextManager(_ampManager);
    
    /*
    _ampManager.run(()->{
      EmbedBuilder embedBuilder = new EmbedBuilder();
      embedBuilder.scanClassLoader();
      embedBuilder.build();
    });
    */
  }

  /*
  @Override
  public void init() throws Exception
  {
  }
  */

  public boolean waitForActive()
  {
    return _lifecycle.waitForActive(_activeWaitTime);
  }

  // @Override
  public void start()
  {
    if (! _lifecycle.toStarting()) {
      return;
    }

    if (! isPodOwner()) {
      // XXX: managed by controller?
      System.out.println("Started without pod ownership: " + this);
      _lifecycle.toActive();
      return;
    }
    
    Objects.requireNonNull(_ampManager);
    
    Object oldCxt = null;
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_ampManager)) {
      oldCxt = outbox.getAndSetContext(_ampManager.inboxSystem());
      
      _tag = _builder.getTag();

      getClassLoader().start();
    
      //if (isPodOwner()) {
      _ampManager.start();
      
      onPodAppStart();
    
      _lifecycle.toActive();
      
      outbox.getAndSetContext(oldCxt);
    } catch (Throwable e) {
      _lifecycle.toError();
      
      throw e;
    }
  }
  
  private boolean isPodOwner()
  {
    if (_podNode.index() < 0) {
      return true;
    }
    
    return _podNode.isServerOwner(_serverSelf);
  }

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    if (! _lifecycle.toDestroy()) {
      return;
    }
    
    try {
      onPodAppStop();
      
      //ShutdownModeAmp mode = _controller.getShutdownMode();

      ServiceManagerAmp ampManager = _ampManager;
      
      if (ampManager != null) {
        ampManager.shutdown(mode);
      }
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    } finally {
      getClassLoader().destroy();
    }
  }
  
  private void onPodAppStart()
  {
    getPodContainer().onPodStart(getId(), getTag());
  }
  
  private void onPodAppStop()
  {
    getPodContainer().onPodStop(getId(), getTag());
  }
  
  private PodContainer getPodContainer()
  {
    //return _controller.getContainer();
    throw new UnsupportedOperationException();
  }
  
  @Override
  public String toString()
  {
    if (_lifecycle != null) {
      return getClass().getSimpleName() + "[" + getId() + "," + _lifecycle.getState() + "]";
    }
    else {
      return getClass().getSimpleName() + "[" + getId() + "]";
    }
  }

  @Override
  public DynamicClassLoader classLoader()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Throwable configException()
  {
    // TODO Auto-generated method stub
    return null;
  }
}
