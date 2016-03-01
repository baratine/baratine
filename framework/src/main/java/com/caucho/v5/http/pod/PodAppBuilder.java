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

import java.util.Objects;

import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.config.Configurable;
import com.caucho.v5.http.pod.PodContainer.PodConfigLocal;
import com.caucho.v5.loader.CompilingLoader;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.loader.SimpleLoader;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.PathImpl;

/**
 * Builder for the webapp to encapsulate the configuration process.
 */
public class PodAppBuilder // implements DeployInstanceBuilder<PodApp>
{
  private static final L10N L = new L10N(PodAppBuilder.class);
  
  // private final PodAppController _controller;
  private EnvironmentClassLoader _classLoader;
  
  private Throwable _configException;

  private PodApp _podApp;

  private String _podName;

  private PodContainer _podContainer;
  
  private String _tag;

  private int _journalMaxCount = -1;
  private long _journalTimeout = -1;

  private boolean _isDebug;

  private long _debugQueryTimeout = 600 * 1000;
  
  //private DynamicClassLoader _libraryLoader;
  
  /**
   * Builder Creates the webApp with its environment loader.
   */
  public PodAppBuilder() // PodAppController controller)
  {
    //Objects.requireNonNull(controller);
    
    //_podContainer = controller.getContainer();
    
    //_controller = controller;
    
    _podName = "pod"; // controller.getPodName();
    
    // _tag = controller.getTag();

    //_libraryLoader = new DynamicClassLoader(controller.getParentClassLoader());
    
    /*
    for (Path path : controller.getLibraryPaths()) {
      
    }
    */
    
    
    /*
    _classLoader
      = EnvironmentClassLoader.create(controller.getParentClassLoader(),
                                      "podapp:" + getId());
    */
    PodConfigLocal podLocal = _podContainer.getPodLocal(_podName);
    
    for (PathImpl path : podLocal.getClassPath()) {
      if (! path.exists()) {
        continue;
      }
      
      if (path.getTail().endsWith(".jar")) {
        new SimpleLoader(_classLoader, path).init();
      }
      else if (path.isDirectory()) {
        new CompilingLoader(_classLoader, path).init();
      }
    }
    
    /*
    Path lib = _controller.getRootDirectory().lookup("lib");
    new TreeLoader(_classLoader, lib);
    
    Path classes = _controller.getRootDirectory().lookup("classes");
    CompilingLoader classesLoader = new CompilingLoader(_classLoader, classes);
    _classLoader.addLoader(classesLoader);
    classesLoader.init();
    */
    
    /*
    NodePodAmp podNode = controller.getPodNode();
    
    BartenderSystem.getCurrent().setLocalShard(podNode, _classLoader);
    */
    
    /*
    LibController libController = _podContainer.getLibraryController("common");

    if (libController != null) {
      LibApp libApp = libController.request();
    
      if (libApp != null) {
        _classLoader.addImportLoader(libApp.getClassLoader());
      }
    }
    */
    
    getPodApp().initConstructor();
  }

  /*
  public PodAppController getController()
  {
    return _controller;
  }
  */

  // @Override
  public PodApp getInstance()
  {
    return getPodApp();
  }
  
  String getId()
  {
    //return _controller.getId();
    //return _id;
    throw new UnsupportedOperationException();
  }
  
  String getPodName()
  {
    return _podName;
  }

  public int getPodNode()
  {
    //return _controller.getPodNodeIndex();
    return 0;
  }

  public NodePodAmp getPodShard()
  {
    //return _controller.getPodNode();
    return null;
  }
  
  @Configurable
  public void setTag(String tag)
  {
    _tag = tag;
  }
  
  public String getTag()
  {
    return _tag;
  }
  
  /*
  public void setJournalMaxCount(int count)
  {
    _journalMaxCount = count;
  }
  
  public int getJournalMaxCount()
  {
    return _journalMaxCount;
  }
  */
  
  /*
  public void setJournalDelay(Period period)
  {
    _journalTimeout = period.getPeriod();
  }
  
  public long getJournalDelay()
  {
    return _journalTimeout;
  }
  */
  
  public void setDebug(boolean isDebug)
  {
    _isDebug = isDebug;
  }
  
  public boolean isDebug()
  {
    return _isDebug;
  }
  
  /*
  public void setDebugQueryTimeout(Period timeout)
  {
    _debugQueryTimeout = timeout.getPeriod();
    
    setDebug(true);
  }
  
  public long getDebugQueryTimeout()
  {
    return _debugQueryTimeout;
  }
  */
  
  //@Override
  public EnvironmentClassLoader getClassLoader()
  {
    return _classLoader;
  }
  
  /*
  @Override
  public void setConfigException(Throwable exn)
  {
    if (exn != null) {
      getPodApp().setConfigException(exn);
    }
  }
  
  @Override
  public Throwable getConfigException()
  {
    return _configException;
  }
  */

  /*
  @Override
  public void preConfigInit()
  {
    //Path libs = _controller.getRootDirectory().lookup("libs");
    //new TreeLoader(_libraryLoader, libs);
    
    for (Depend depend : _controller.getDependList()) {
      _classLoader.addDependency(depend);
    }

    try {
      getPodApp().preConfigInit();
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
    
    //for (Path path : _controller.getApplicationPaths()) {
    //  _classLoader.addDependency(new Depend(path));
    //}
    
    //for (Path path : _controller.getLibraryPaths()) {
    //  _libraryLoader.addDependency(new Depend(path));
    //}
  }
  */
  
  /*
  @Override
  public void postClassLoaderInit()
  {
    try {
      PodApp podApp = getPodApp();
      
      podApp.postClassLoaderInit();
    } catch (Exception e) {
      e.printStackTrace();
      throw ConfigException.wrap(e);
    }
  }
  
  public void addProgram(ConfigProgram program)
  {
    program.configure(getPodApp());
  }
  */
  
  // @Override
  public PodApp build()
  {
    return getPodApp();
  }
  
  protected final PodApp getPodApp()
  {
    PodApp podApp = _podApp;
    
    if (podApp == null) {
      _podApp = podApp = createPodApp();
      Objects.requireNonNull(podApp);
    }
    
    return podApp;
  }
  
  protected final PodApp createPodApp()
  {
    return new PodApp(this);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getId() + "]";
  }
}
