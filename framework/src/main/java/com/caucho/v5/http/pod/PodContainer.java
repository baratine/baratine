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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.baratine.ServicePod;
import com.caucho.v5.baratine.ServicePod.NodeBaratine;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.deploy2.DeployHandle2;
import com.caucho.v5.http.container.HttpContainerBuilder;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.loader.EnvLoaderListener;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.PathImpl;

import io.baratine.service.Result;
import io.baratine.service.ServiceManager;

/**
 * Contains Baratine service pod deployments.
 */
public class PodContainer
  implements EnvLoaderListener
{
  private static final L10N L = new L10N(PodContainer.class);
  private static final Logger log
    = Logger.getLogger(PodContainer.class.getName());

  private static final EnvironmentLocal<PodContainer> _local
    = new EnvironmentLocal<>();
  
  //private HttpContainerServlet _httpContainer;

  // The context class loader
  private EnvironmentClassLoader _classLoader;
  
  private final Lifecycle _lifecycle;
  
  private PathImpl _libExpandPath;
  private PathImpl _podExpandPath;

  // List of default service webApp configurations
  /*
  private ArrayList<PodConfig> _podDefaultList
    = new ArrayList<>();
    */

  // private DeployContainerImpl<PodApp,PodAppController> _serviceDeploy;
  
  /*
  private ConcurrentHashMap<String,PodAppController> _podAppMap
    = new ConcurrentHashMap<>();
  
  private ConcurrentHashMap<String,PodLoaderController> _podLoaderMap
    = new ConcurrentHashMap<>();
    */
  
  private ConcurrentHashMap<String,PodConfigLocal> _podConfigMap
    = new ConcurrentHashMap<>();
  
  //private PodBuilderServiceSync _podBuilder;
  /*
  private PodsDeployService _podsDeployService;
  private PodsConfigService _podsConfigService;
  */
  
  //private ServiceServer _serverBaratine;
  
  private long _startWaitTime = 10000L;

  private Throwable _configException;
  //private boolean _isDeployDirectoryEnable;
  private BartenderSystem _bartender;
  
  private ConcurrentHashMap<String,DeployHandle2<PodLoader>> _podLoaderHandleMap
    = new ConcurrentHashMap<>();
  
  private ConcurrentHashMap<String,PodAppHandle> _podAppHandleMap
    = new ConcurrentHashMap<>();

  //private ShutdownModeAmp _shutdownMode = ShutdownModeAmp.IMMEDIATE;
  private ShutdownModeAmp _shutdownMode = ShutdownModeAmp.GRACEFUL;
  private AmpSystem _ampSystem;
  private ServiceManagerAmp _ampManager;
  
  /**
   * Creates the webApp with its environment loader.
   */
  public PodContainer(BartenderSystem bartender,
                      HttpContainerBuilder builder)
  {
    Objects.requireNonNull(bartender);
    Objects.requireNonNull(builder);
    
    _bartender = bartender;
    _ampSystem = AmpSystem.getCurrent();
    
    //_httpContainer = httpContainer;

    _classLoader = EnvLoader.getEnvironmentClassLoader();
    
    _local.set(this, _classLoader);

    _lifecycle = new Lifecycle(log, "pod-container");
    
    _ampManager = _ampSystem.getManager();
  }
  
  public static PodContainer getCurrent()
  {
    return _local.get();
  }

  public ServerBartender getSelfServer()
  {
    return _bartender.serverSelf();
  }
  
  public PathImpl getPodExpandPath()
  {
    return _podExpandPath;
  }
  
  public void setPodExpandPath(PathImpl path)
  {
    Objects.requireNonNull(path);
    
    _podExpandPath = path;
  }
  
  public PathImpl getLibExpandPath()
  {
    return _libExpandPath;
  }
  
  public void setLibExpandPath(PathImpl path)
  {
    Objects.requireNonNull(path);
    
    _libExpandPath = path;
  }
  
  /*
  public boolean isDeployDirectoryEnable()
  {
    return _isDeployDirectoryEnable;
  }
  
  public void setDeployDirectoryEnable(boolean isDeployDirectoryEnable)
  {
    _isDeployDirectoryEnable = isDeployDirectoryEnable;
  }
  */
  
  protected void initConstructor()
  {
  }

  /*
  protected HttpContainerServlet getHttpContainer()
  {
    return _httpContainer;
  }
  */
  
  /*
  public InvocationDecoder getInvocationDecoder()
  {
    return getHttpContainer().getInvocationDecoder();
  }
  */

  /**
   * Gets the class loader.
   */
  public ClassLoader getClassLoader()
  {
    return _classLoader;
  }

  /**
   * sets the class loader.
   */
  public void setEnvironmentClassLoader(EnvironmentClassLoader loader)
  {
    _classLoader = loader;
  }
  
  /*
  public String getClusterId()
  {
    return getHttpContainer().getClusterName();
  }
  */

  /**
   * Gets the root directory.
   */
  /*
  public Path getRootDirectory()
  {
    return getHttpContainer().getRootDirectory();
  }
  */

  /**
   * Sets a configuration exception.
   */
  public void setConfigException(Throwable e)
  {
    _configException = e;
  }

  /**
   * Returns true if modified.
   */
  public boolean isModified()
  {
    return _lifecycle.isDestroyed() || _classLoader.isModified();
  }
  
  public ShutdownModeAmp getShutdownMode()
  {
    return _shutdownMode;
  }
  
  public void setShutdownMode(ShutdownModeAmp shutdownMode)
  {
    Objects.requireNonNull(shutdownMode);
    
    _shutdownMode = shutdownMode;
  }
  
  //
  // service/baratine

  /*
  public DeployContainer<PodApp,PodAppController> getDeployContainer()
  {
    return _serviceDeploy;
  }
  */

  /**
   * Adds an service default
   */
  /*
  public void addPodDefault(PodConfig config)
  {
    _podDefaultList.add(config);
  }
  */

  /**
   * Returns the list of ear defaults
   */
  /*
  public ArrayList<PodConfig> getPodDefaultList()
  {
    return _podDefaultList;
  }
  */

  /**
   * pod-deploy: create a new service deploy.
   */
  /*
  public PodDeployConfig createPodDeploy()
  {
    return new PodDeployConfig(this);
  }
  */

  /**
   * Adds the pod-deploy config.
   */
  /*
  public void addPodDeploy(PodDeployConfig generator)
  {
  }
  */

  public PodBartender getPod(String podName)
  {
    return _bartender.findPod(podName);
  }

  /**
   * Returns a handle to the PodApp service, specified by the pod-app id.
   */
  public PodAppHandle getPodAppHandle(String id)
  {
    PodAppHandle handle = _podAppHandleMap.get(id);
    
    if (handle == null) {
      // handle = _podsDeployService.getPodAppHandle(id);
      
      _podAppHandleMap.putIfAbsent(id, handle);
    }
    
    return handle;
  }

  public DeployHandle2<PodLoader> getPodLoaderHandle(String id)
  {
    /*
    DeployHandle<PodLoader> handle = _podLoaderHandleMap.get(id);
    
    if (handle == null) {
      handle = _podsDeployService.getPodLoaderHandle(id);
      
      _podLoaderHandleMap.putIfAbsent(id, handle);
    }
    
    return handle;
    */
    throw new UnsupportedOperationException();
  }

  public Iterable<PodAppHandle> getPodAppHandles()
  {
    //return _podsDeployService.getPodAppHandles();
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void onPodStart(String id, String tag)
  {
    //_podBuilder.onPodAppStart(id, tag);
  }

  public void onPodStop(String id, String tag)
  {
    //_podBuilder.onPodAppStop(id, tag);
  }

  public void waitForDeploy(String podName, String tag, Result<Boolean> result)
  {
    //_podsDeployService.waitForLoaderDeploy(podName, result);
    result.ok(true);
  }
  
  //
  // dynamic local config stuff
  //

  /*
  public ServiceServer getServerBaratine()
  {
    return _serverBaratine;
  }
  */
  
  public void addPodClassPath(String pod, PathImpl classPath)
  {
    PodConfigLocal config = getPodLocal(pod);
    
    synchronized (config) {
      config.addClassPath(classPath);
    }
  }
  
  PodConfigLocal getPodLocal(String podName)
  {
    synchronized (_podConfigMap) {
      PodConfigLocal config = _podConfigMap.get(podName);
      
      if (config == null) {
        config = new PodConfigLocal(podName);
        
        _podConfigMap.put(podName, config);
      }
      
      return config;
    }
  }
  

  /**
   * Starts the container.
   */
  public void start()
  {
    try {
      if (! _lifecycle.toActive()) {
        return;
      }
      // _serviceDeploy.start();
      
      /*
      Path dataDir = RootDirectorySystem.getCurrentDataDirectory();
      
      if (dataDir != null) {
        _podExpandPath = dataDir.lookup("pods");
      }
      */
    
      /*
      if (_podExpandPath != null) {
        PodsDeployServiceImpl podsDeploy = new PodsDeployServiceImpl(this);
        _podsDeployService = _ampManager.newService(podsDeploy)
                                       .as(PodsDeployService.class);
        
        PodsConfigServiceImpl podsConfig
          = new PodsConfigServiceImpl(this, _podsDeployService);
        _podsConfigService = _ampManager.newService(podsConfig).as(PodsConfigService.class);

        PodBuilderServiceImpl podDeploy = new PodBuilderServiceImpl(this);
        _podBuilder = _ampManager.newService(podDeploy)
                                .as(PodBuilderServiceSync.class);
        
        _serverBaratine = new ServerBaratineImpl();
        
        ServiceRef.toRef(_podBuilder).start();
        ServiceRef.toRef(_podsConfigService).start();
        ServiceRef.toRef(_podsDeployService).start();
      }
      */
      
      //_serverBaratine = new ServerBaratineImpl();
      
      // wait for pod deploy to complete
      //_podDeploy.update();
      
      // wait for all controllers to start
      /*
      for (DeployHandle<?> handle : _podDeploy.getActiveHandles()) {
        handle.request();
      }
      */
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
  }

  /*
  public PodBuilderService getBuilderService()
  {
    return _podBuilder;
  }
  */

  /*
  public PodsDeployService getDeployService()
  {
    return _podsDeployService;
  }

  public PodsConfigService getConfigService()
  {
    return _podsConfigService;
  }
  */

  /**
   * Returns true if the webApp container has been closed.
   */
  public final boolean isDestroyed()
  {
    return _lifecycle.isDestroyed();
  }

  /**
   * Returns true if the webApp container is active
   */
  public final boolean isActive()
  {
    return _lifecycle.isActive();
  }

  /**
   * Closes the container.
   * @param mode 
   */
  public boolean stop(ShutdownModeAmp mode)
  {
    Objects.requireNonNull(mode);
    
    if (! _lifecycle.toStop()) {
      return false;
    }
    
    _shutdownMode = mode;

    /*
    if (_podBuilder != null) {
      ServiceRefAmp.toServiceRef(_podBuilder).shutdown(mode);
      ServiceRefAmp.toServiceRef(_podsDeployService).shutdown(mode);
      ServiceRefAmp.toServiceRef(_podsConfigService).shutdown(mode);
    }
    */

    return true;
  }

  /**
   * Closes the container.
   */
  public void destroy()
  {
    stop(ShutdownModeAmp.GRACEFUL);
    
    if (! _lifecycle.toDestroy()) {
      return;
    }
    
    // _podsDeployService.destroy();
  }

  /**
   * Handles the case where a class loader is dropped.
   */
  @Override
  public void classLoaderDestroy(DynamicClassLoader loader)
  {
    destroy();
  }

  /**
   * Handles the case where the environment is stopping
   */
  @Override
  public void environmentStop(EnvironmentClassLoader loader)
  {
    stop(ShutdownModeAmp.GRACEFUL);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _classLoader.getId() + "]";
  }

  /*
  private class ServerBaratineImpl implements ServiceServer
  {
    @Override
    public ServicePod pod(String name)
    {
      int p = name.indexOf('.');
      
      if (p > 0) {
        throw new IllegalArgumentException(name);
      }

      String podName = "pods/" + name;
      
      PodBartender pod = _bartender.findPod(name);

      return new PodBaratineImpl(pod, podName);
    }
      
    @Override
    public PodBuilder newPod(String name)
    {
      ServiceManagerAmp manager = _ampSystem.getManager();

      return new PodBuilderImpl(this, manager, _podBuilder, name);
    }
    
    @Override
    public ServiceManager client()
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void close()
    {
    }

    @Override
    public void closeImmediate()
    {
      close();
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _classLoader.getId() + "]";
    }
  }
  */
  
  private class PodBaratineImpl implements ServicePod
  {
    private final PodBartender _pod;
    private final String _podName;
    
    PodBaratineImpl(PodBartender pod, String podName)
    {
      Objects.requireNonNull(pod);
      Objects.requireNonNull(podName);
      
      _pod = pod;
      _podName = podName;
    }
    
    @Override
    public NodeBaratine node(int index)
    {
      String nodeName = _podName + "." + index;
      
      PodAppHandle podAppHandle = getPodAppHandle(nodeName);
      
      Objects.requireNonNull(podAppHandle);

      return new NodeBaratineImpl(podAppHandle);
    }
    
    @Override
    public int getNodeCount()
    {
      return _pod.nodeCount();
    }
    
    @Override
    public ServiceManager manager()
    {
      return node(0).manager();
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _podName + "]";
    }
  }
  
  private class NodeBaratineImpl implements NodeBaratine
  {
    private PodAppHandle _handle;
    
    NodeBaratineImpl(PodAppHandle handle)
    {
      Objects.requireNonNull(handle);
      
      _handle = handle;
    }
    
    @Override
    public ServiceManager manager()
    {
      ServiceManagerAmp manager = _handle.requestManager();

      if (manager != null) {
        return manager;
      }
      else {
        throw new IllegalStateException(L.l("pod-app {0} is not active", _handle));
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _handle.getId() + "]";
    }
  }
  
  static class PodConfigLocal {
    private String _podName;
    
    private ArrayList<PathImpl> _classPath = new ArrayList<>();
    
    PodConfigLocal(String podName)
    {
      Objects.requireNonNull(podName);
      
      _podName = podName;
    }
    
    void addClassPath(PathImpl path)
    {
      _classPath.add(path);
    }

    public Iterable<PathImpl>  getClassPath()
    {
      return _classPath;
    }
  }
}
