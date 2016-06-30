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

package com.caucho.v5.bartender.files;

import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.proc.ProcKraken;
import com.caucho.v5.bartender.proc.ProcPods;
import com.caucho.v5.bartender.proc.ProcRoot;
import com.caucho.v5.bartender.proc.ProcServers;
import com.caucho.v5.bartender.proc.ProcServices;
import com.caucho.v5.bartender.proc.ProcTempStore;
import com.caucho.v5.kraken.KrakenSystem;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.VfsOld;

import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

public class BartenderFileSystem extends SubSystemBase
{
  private static final Logger log
    = Logger.getLogger(BartenderFileSystem.class.getName());
  
  private static final L10N L = new L10N(BartenderFileSystem.class);
  
  public static final int START_PRIORITY = KrakenSystem.START_PRIORITY + 1;

  private FileServiceRootImpl _rootServiceImpl;
  private FileServiceRoot _rootService;
  
  private ServiceRef _schemeRef;
  
  private FilesDeployServiceImpl _deployServiceImpl;

  private ServiceRefAmp _deployService;

  private FileServiceRootImpl _localServiceImpl;

  private FileServiceRoot _localService;

  private ServiceRefAmp _localServiceRef;

  private ServiceRefAmp _rootServiceRef;

  /*
  protected BartenderSystem()
  {
  }
  */
  
  protected BartenderFileSystem()
  {
  }
  
  public static BartenderFileSystem getCurrent()
  {
    return SystemManager.getCurrentSystem(BartenderFileSystem.class);
  }
  
  /*
  public static BartenderSystem createAndAddSystem()
  {
    ResinSystem system = preCreate(BartenderSystem.class);

    BartenderSystem barSystem = new BartenderSystem();
    
    system.addSystem(BartenderSystem.class, barSystem);
    
    return barSystem;
  }
  */
  
  public static BartenderFileSystem 
  createAndAddSystem()
  {
    SystemManager system = preCreate(BartenderFileSystem.class);

    BartenderFileSystem barSystem = new BartenderFileSystem();
    
    system.addSystem(BartenderFileSystem.class, barSystem);
    
    return barSystem;
  }
  
  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }
  
  @Override
  public void start()
  {
    BartenderSystem bartender = BartenderSystem.current();
    
    ServicesAmp rampManager = AmpSystem.currentManager();
    
    String clusterId = BartenderSystem.getCurrentSelfServer().getClusterId();
    String clusterPod = "cluster_hub." + clusterId;
    String localPod = "local";
    
    _rootServiceImpl = new FileServiceRootImpl("bfs://cluster_hub", clusterPod, rampManager);
    _rootServiceRef = _rootServiceImpl.getServiceRef();
    _rootService = _rootServiceRef.bind("bfs://")
                                   .start()
                                   .as(FileServiceRoot.class);
    
    _localServiceImpl = new FileServiceRootImpl("bfs://" + localPod, localPod, rampManager);
    _localServiceRef = _localServiceImpl.getServiceRef();
    _localService = _localServiceImpl.getServiceRef()
                                     .start()
                                     .as(FileServiceRoot.class);
    
    FileServiceBind proc = rampManager.newService(new ProcRoot())
                                      .as(FileServiceBind.class);
    
    bind("/proc", proc);
    
    // _rootService.bind("///local", _localService);
    
    _schemeRef = rampManager.newService(createSchemeServiceImpl())
                            .address("bfs:")
                            .ref();
    
    BfsPathRoot rootPath = new BfsPathRoot(_schemeRef);
    
    //Vfs.getLocalScheme().put("bfs", rootPath);
    VfsOld.getDefaultScheme().put("bfs", rootPath);
    
    _deployServiceImpl = new FilesDeployServiceImpl(_schemeRef);
    _deployService = rampManager.newService(_deployServiceImpl)
                                .address("public:///bartender-files")
                                .ref();
    
    FileServiceBind procServers = rampManager.newService(new ProcServers(bartender))
                                             .as(FileServiceBind.class);
    
    bind("/proc/servers", procServers);
    
    FileServiceBind procPods = rampManager.newService(new ProcPods(bartender))
                                          .as(FileServiceBind.class);
    
    bind("/proc/pods", procPods);
    
    FileServiceBind procServices = rampManager.newService(new ProcServices(bartender))
                                          .as(FileServiceBind.class);
    
    bind("/proc/services", procServices);
    
    /*
    FileServiceBind procWebApps = rampManager.service(new ProcWebApps(bartender))
                                             .as(FileServiceBind.class);
    
    bind("/proc/webapps", procWebApps);
    */
    
    FileServiceBind procKraken = rampManager.newService(new ProcKraken(bartender))
                                             .as(FileServiceBind.class);
    
    bind("/proc/kraken", procKraken);
    
    FileServiceBind procTemp = rampManager.newService(new ProcTempStore())
                                             .as(FileServiceBind.class);
    
    bind("/proc/temp-store", procTemp);
  }
  
  private void bind(String path, FileServiceBind bind)
  {
    _rootService.bind(path, bind);
    _localService.bind(path, bind);
  }

  public FilesSchemeServiceImpl createSchemeServiceImpl()
  {
    if (_rootService == null) {
      return null;
    }
    
    FilesSchemeServiceImpl files = new FilesSchemeServiceImpl(_rootService);
    
    files.addPod("local", _localService);
    
    return files;
  }
  
  @Override
  public void stop(ShutdownModeAmp mode)
  {
    if (_schemeRef != null) {
      _schemeRef.close(Result.ignore());
    }

    if (_rootServiceRef != null) {
      _rootServiceRef.close(Result.ignore());
    }
    
    if (_localServiceRef != null) {
      _localServiceRef.close(Result.ignore());
    }
  }

  public ServiceRef getRootServiceRef()
  {
    return _schemeRef;
  }

  public FileServiceRoot getRootService()
  {
    return _rootService;
  }
}
