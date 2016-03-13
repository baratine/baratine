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

import io.baratine.service.ServiceException;
import io.baratine.service.ServiceExceptionUnavailable;

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.ServiceRefWrapper;
import com.caucho.v5.amp.manager.ServiceManagerAmpImpl;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.util.L10N;

/**
 * Entry to the scheme system.
 */
public class ServiceRefPodRoot extends ServiceRefWrapper
{
  private static final L10N L = new L10N(ServiceRefPodRoot.class);
  private static final Logger log = Logger.getLogger(ServiceRefPodRoot.class.getName());
  
  private final ServiceManagerAmp _managerSystem;
  private final ServiceManagerAmp _manager;
  private PodBartender _pod;
  private String _address;
  private ServiceRefAmp [][]_services;
  private SchemePod _podScheme;
  private long _podSequence;
  // private ServiceRefAmp _selfService;
  
  private AtomicReference<ServiceRefAmp> _localServiceRef
    = new AtomicReference<>();
  
  ServiceRefPodRoot(SchemePod podScheme,
                    ServiceManagerAmp manager,
                    PodBartender pod,
                    String address)
  {
    _podScheme = podScheme;
    _manager = manager;
    _pod = pod;
    _address = address;
    
    _managerSystem = AmpSystem.currentManager();
    Objects.requireNonNull(_managerSystem);
    
    // _selfService = _manager.service(new Object());
  }
  
  @Override
  public String address()
  {
    // return _address;
    if (_pod != null) {
      return "pod://" + _pod.name();
    }
    else {
      return "pod://";
    }
  }
  
  @Override
  public ServiceManagerAmp manager()
  {
    return _manager;
  }
  
  /*
  public ServiceRefAmp getSelfService()
  {
    return _selfService;
  }
  */

  @Override
  public ServiceRefAmp bind(String address)
  {
    address = ServiceManagerAmpImpl.toCanonical(address);

    manager().bind(this, address);

    return this;
  }
  
  @Override
  public int nodeCount()
  {
    return _pod.nodeCount();
  }
  
  @Override
  public ServiceRefAmp node(int hash)
  {
    return new ServiceRefPodRootNode(_podScheme, _manager, _pod, _address,
                                     hash);
  }
  
  @Override
  public ServiceRefAmp onLookup(String path)
  {
    // String address = "//" + _pod.getName() + path;
    
    int hash = HashPod.hash(path);

    return new ServiceRefPod(this, path, hash);
  }

  @Override
  public MethodRefAmp getMethod(String methodName)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public MethodRefAmp getMethod(String methodName, Type type)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public ServiceRefAmp delegate()
  {
    throw new ServiceException(L.l("Pod root does not have a delegate"));
  }
  
  ServiceRefAmp getActiveService(int hash)
  {
    int depth = _pod.getDepth();
    /*
    ServiceRefAmp[][] podServices = getPodServices();
    
    int nodeCount = podServices.length;
    
    ServiceRefAmp []services = podServices[hash % nodeCount];
    
    for (ServiceRefAmp service : services) {
      if (service != null && service.isUp()) {
        return service;
      }
    }
    */
    
    for (int i = 0; i < depth; i++) {
      ServiceRefAmp service = getPodService(hash, i);

      if (service != null && service.isUp()) {
        return service;
      }
    }

    throw new ServiceExceptionUnavailable(L.l("No active servers for {0} node {1}", 
                                              address(), 
                                              Math.abs(hash % _pod.nodeCount())));
  }
  
  ServiceRefAmp getLocalService()
  {
    ServiceRefAmp serviceRef = _localServiceRef.get();
    
    if (serviceRef != null && serviceRef.isUp()) {
      return serviceRef;
    }
    
    serviceRef = getLocalServiceImpl();
    
    _localServiceRef.set(serviceRef);
    
    return serviceRef;
  }
    
  private ServiceRefAmp getLocalServiceImpl()
  {
    // ServiceRefAmp[][] podServices = getPodServices();
    
    PodBartender pod = _pod;
    
    int nodes = pod.nodeCount();
    int depth = pod.getDepth();
    
    for (int j = 0; j < depth; j++) {
      for (int i = 0; i < nodes; i++) {
        NodePodAmp node = pod.getNode(i);
      
        if (node == null) {
          continue;
        }
        
        // XXX: issues with loading backup node instances
        // e.g. on server [b] for a solo, load api class causes class load
      
        ServerBartender server = node.server(j);
        
        if (server != null && server.isSelf()) {
          return getPodService(i, j);
        }
      }
    }
    
    return null;
  }

  private ServiceRefAmp getPodService(int nodeHash, int depthIndex)
  {
    PodBartender pod = _pod;
    
    ServiceRefAmp [][]services = _services;
    
    int count = pod.nodeCount();
    int depth = pod.getDepth();
    
    int nodeIndex;
    
    if (count > 0) {
      nodeIndex = Math.abs(nodeHash) % count;
    }
    else {
      nodeIndex = 0;
    }

    if (services == null || _podSequence != pod.getSequence()) {
      services = new ServiceRefAmp[count][];
      
      for (int i = 0; i < count; i++) {
        ServiceRefAmp []shardServices = new ServiceRefAmp[depth];
        services[i] = shardServices;
      }
      
      if (count == 0) {
        services = new ServiceRefAmp[1][];
        services[0] = new ServiceRefAmp[0];
      }
      
      _services = services;
      _podSequence = pod.getSequence();
    }
    
    ServiceRefAmp service = services[nodeIndex][depthIndex];
    
    if (service == null) {
      NodePodAmp node = pod.getNode(nodeIndex);
      
      service = initService(node, depthIndex);
      
      services[nodeIndex][depthIndex] = service;
    }
    
    return service;
  }

  public PodBartender getPod()
  {
    return _pod;
  }
  
  private ServiceRefAmp initService(NodePodAmp podNode, int i)
  {
    // XXX: dynamic servers not handled properly
    ServerBartender server = podNode.server(i);

    if (server == null) {
      return null;
    }
    
    String serverId = server.getId();
    if (serverId.isEmpty()) {
      return null;
    }
    
    PodBartender pod = podNode.pod();
    String podName = pod.name();
    //String name = podName + "." + pod.getClusterId();
    String name = podName;
    int podIndex = podNode.nodeIndex();
    
    String url = getBartenderUrl(serverId, name, podIndex);
    
    ServiceRefAmp serviceRef = _manager.service(url);
    
    return serviceRef;
  }
  
  protected String getBartenderUrl(String serverId, String podName, int index)
  {
    return _podScheme.getBartenderUrl(serverId, podName, index);
    //return "bartender-pod://" + serverId + "/s/" + podName + '.' + index;
  }
}
