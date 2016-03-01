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

import java.lang.ref.SoftReference;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.deploy2.DeployInstance2;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.loader.EnvironmentLocal;

/**
 * Builder for the webapp to encapsulate the configuration process.
 */
public class PodLoader implements DeployInstance2 
//implements DeployInstanceEnvironment, EnvironmentBean
{
  private static final Logger log = Logger.getLogger(PodLoader.class.getName());
  
  private static final EnvironmentLocal<PodLoader> _podAppLocal
    = new EnvironmentLocal<>();
  
  private String _id;
  
  private Throwable _configException;

  //private PodLoaderBuilder _builder;
  
  private final Lifecycle _lifecycle;

  private EnvironmentClassLoader _classLoader;
  //private PodLoaderController _controller;
  
  private WeakHashMap<ClassLoader,SoftReference<ClassLoader>> _loaderMap
    = new WeakHashMap<>();

  private String _podName;
  
  PodLoader() // PodLoaderBuilder builder)
  {
    _podName = "pod";
    _id = "pods/" + _podName;
    //_id = builder.getId();
    
    //_builder = builder;
    
    //_classLoader = builder.getClassLoader();
    //_controller = builder.getController();
    
    _lifecycle = new Lifecycle(log, toString(), Level.FINER);
  }
  
  public String getId()
  {
    return _id;
  }
  
  public String getPodName()
  {
    return _podName; // _controller.getPodName();
  }
  
  /*
  @Override
  public EnvironmentClassLoader getClassLoader()
  {
    return _classLoader;
  }
  */
  
  /*
  public PathImpl getRootDirectory()
  {
    return _controller.getRootDirectory();
  }

  @Override
  public void setConfigException(Throwable e)
  {
    _configException = e;
  }

  @Override
  public Throwable getConfigException()
  {
    return _configException;
  }

  @Override
  public boolean isDeployIdle()
  {
    return false;
  }

  @Override
  public boolean isModified()
  {
    return false;
  }

  @Override
  public boolean isModifiedNow()
  {
    return false;
  }

  @Override
  public boolean logModified(Logger log)
  {
    return getClassLoader().logModified(log);
  }
  */
  
  // lifecycle
  //

  protected void initConstructor()
  {
  }

  /*
  @Override
  public void preConfigInit() throws Exception
  {
    BartenderSystem bartender = BartenderSystem.getCurrent();
    
    PodBartender pod = bartender.findActivePod(getPodName());
    
    Objects.requireNonNull(pod);
    
    bartender.setLocalPod(pod);
  }

  @Override
  public void init() throws Exception
  {
  }
  */

  /*
  @Override
  public void start()
  {
    if (! _lifecycle.toStarting()) {
      return;
    }
    
    // getClassLoader().scan();
    
    _lifecycle.toActive();
  }
  */

  /**
   * Builds a combined class-loader with the target service loader as a
   * parent, and this calling pod loader as a child.
   */
  public ClassLoader buildClassLoader(ClassLoader serviceLoader)
  {
    synchronized (_loaderMap) {
      SoftReference<ClassLoader> extLoaderRef = _loaderMap.get(serviceLoader);
      
      if (extLoaderRef != null) {
        ClassLoader extLoader = extLoaderRef.get();
        
        if (extLoader != null) {
          return extLoader;
        }
      }
    }
    
    String parentId = EnvLoader.getEnvironmentName(serviceLoader);
    
    String id = _id + "!" + parentId;
    
    //DynamicClassLoader extLoader = new PodExtClassLoader(serviceLoader, id);
    DynamicClassLoader extLoader = null;

    /*
    LibraryLoader libLoader
      = new LibraryLoader(extLoader, getRootDirectory().lookup("lib"));
    libLoader.init();
    
    CompilingLoader compLoader
      = new CompilingLoader(extLoader, getRootDirectory().lookup("classes"));
    compLoader.init();
    
    synchronized (_loaderMap) {
      SoftReference<ClassLoader> extLoaderRef = _loaderMap.get(serviceLoader);
      
      if (extLoaderRef != null) {
        ClassLoader extLoaderOld = extLoaderRef.get();
        
        if (extLoaderOld != null) {
          return extLoaderOld;
        }
      }
      
      _loaderMap.put(serviceLoader, new SoftReference<>(extLoader));
    }
    */
    
    return extLoader;
  }

  //@Override
  public void shutdown(ShutdownModeAmp mode)
  {
    _lifecycle.toDestroy();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getId() + "]"; 
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
