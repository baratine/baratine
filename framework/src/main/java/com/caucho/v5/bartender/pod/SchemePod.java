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

import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.manager.ServicesAmpImpl;
import com.caucho.v5.amp.service.MethodRefNull;
import com.caucho.v5.amp.service.ServiceRefBase;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.BartenderSystem;

/**
 * Entry to the scheme system.
 */
public class SchemePod extends ServiceRefBase
{
  private final BartenderSystem _bartender;
  private final ServicesAmp _manager;
  
  private final ConcurrentHashMap<String,PodBartender> _podMap
    = new ConcurrentHashMap<>();
  
  private final ConcurrentHashMap<String,ServiceRefAmp> _serviceRefPodMap
    = new ConcurrentHashMap<>();
  
  public SchemePod(BartenderSystem bartender,
                   ServicesAmp manager)
  {
    _bartender = bartender;
    _manager = manager;
  }
  
  @Override
  public String address()
  {
    return "pod:";
  }
  
  @Override
  public ServicesAmp services()
  {
    return _manager;
  }
  
  @Override
  public ServiceRefAmp onLookup(String address)
  {
    if (address.startsWith("///")) {
      return super.onLookup(address);
    }
    
    int s = address.indexOf("//");
    
    s = Math.max(s, 0);
    
    String host;
    String path;
    
    int p = address.indexOf('/', s + 2);
    
    if (p > 0) {
      host = address.substring(2, p);
      path = address.substring(p);
    }
    else {
      host = address.substring(2);
      path = "";
    }
    
    ServiceRefAmp podRootRef = lookupPodRoot(host);

    if (podRootRef == null) {
      return null;
    }
    else if (path.isEmpty()) {
      return podRootRef;
    }
    else {
      return podRootRef.onLookup(path);
    }
      
    /*
    // match with TableKraken
    int hash = HashPod.hash(path);
      
    ShardPod podNode = pod.getNode(hash);
      
    return new ServiceRefPod(this,
                             _manager, podNode, getAddress() + address,
                             path);
                             */
  }
  
  private ServiceRefAmp lookupPodRoot(String podName)
  {
    if (podName == null || podName.equals("") || podName.equals("null")) {
      throw new IllegalArgumentException();
    }
    
    
    int hash = -1;
    
    int p = podName.lastIndexOf(':');

    if (p > 0 && Character.isDigit(podName.charAt(p + 1))) {
      hash = Integer.parseInt(podName.substring(p + 1));
      podName = podName.substring(0, p);
    }
    
    ServiceRefAmp serviceRef = _serviceRefPodMap.get(podName);
    
    if (serviceRef != null) {
      if (hash >= 0) {
        serviceRef = serviceRef.pinNode(hash);
      }
      
      return serviceRef;
    }
    
    PodBartender pod = getPod(podName);
  
    if (pod == null) {
      return null;
    }
    
    ServiceRefAmp podRoot = _serviceRefPodMap.get(podName);;
    
    if (podRoot == null) {
      podRoot = createPodRoot(pod);
    
      _serviceRefPodMap.put(podName, podRoot);
    
      podRoot = _serviceRefPodMap.get(podName);
      podRoot.start();
    }
    
    if (hash >= 0) {
      podRoot = podRoot.pinNode(hash);
    }
    
    return podRoot;
  }
  
  protected ServiceRefAmp createPodRoot(PodBartender pod)
  {
    //ActorPodRoot actorPod = new ActorPodRoot(this, _manager, pod);
    
    //return _manager.service(actorPod);
    
    return new ServiceRefPodRoot(this, _manager, pod, address());
  }

  public String getBartenderUrl(String serverId, String podName, int index)
  {
    return "bartender-pod://" + serverId + "/s/" + podName + '.' + index;
  }
  
  private PodBartender getPod(String podName)
  {
    PodBartender pod = _podMap.get(podName);
    
    if (pod == null) {
      pod = _bartender.findPod(podName);
      _podMap.putIfAbsent(podName, pod);
    }
    
    return pod;
  }

  @Override
  public ServiceRefAmp bind(String address)
  {
    address = ServicesAmpImpl.toCanonical(address);

    services().bind(this, address);

    return this;
  }

  @Override
  public MethodRefAmp methodByName(String methodName)
  {
    return new MethodRefNull(this, methodName);
  }
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
  }
}
