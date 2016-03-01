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

package com.caucho.v5.bartender.pod;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.util.ModulePrivate;
import com.caucho.v5.vfs.Depend;

/**
 * Configuration for a pod.cf file
 * 
 * The pod.cf sets the replication type ("solo", "pair", "triad", "cluster"), 
 * and any explicit server assignments.
 * 
 * Any other configuration items are passed to the pod-app when it's started.
 */
@ModulePrivate
public class PodsConfig
{
  private static final Logger log = Logger.getLogger(PodsConfig.class.getName());
  
  //private HashMap<String,PodConfig> _podMap = new HashMap<>();
  
  private Depend _depend;
  private boolean _isBaseModified = true;
  
  public void setCurrentDepend(Depend depend)
  {
    _depend = depend;
  }
  
  void setBaseModified(boolean isBaseModified)
  {
    _isBaseModified = isBaseModified;
  }
  
  boolean isBaseModified()
  {
    return _isBaseModified;
  }

  /**
   * Adds a new pod configuration.
   */
  /*
  @Configurable
  public void addPod(PodConfigProxy podProxy)
  {
    String podName = podProxy.getName();
    
    PodConfig pod = _podMap.get(podName);
    
    if (pod == null) {
      pod = new PodConfig();
      pod.setName(podName);
      _podMap.put(podName, pod);
    }
    else {
      pod.preInit();
    }
    
    if (_depend != null) {
      pod.addDepend(_depend);
    }
    
    podProxy.getProgram().configure(pod);
    
    pod.configureServers(podProxy.getServers());
  }
  */
  
  /*
  public void addPodImpl(PodConfig pod)
  {
    Objects.requireNonNull(pod);
    
    _podMap.put(pod.getName(), pod);
  }
  
  public Collection<PodConfig> getPods()
  {
    return _podMap.values();
  }
  */

  public void setConfigException(Exception e)
  {
    log.log(Level.FINER, e.toString(), e);
  }
}
