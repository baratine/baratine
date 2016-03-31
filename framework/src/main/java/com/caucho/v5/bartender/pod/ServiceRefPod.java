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

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.manager.ServicesAmpImpl;
import com.caucho.v5.amp.marshal.ImportAware;
import com.caucho.v5.amp.service.ServiceRefWrapper;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.util.L10N;

/**
 * Entry to the scheme system.
 */
public class ServiceRefPod extends ServiceRefWrapper implements ImportAware
{
  private static final L10N L = new L10N(ServiceRefPod.class);
  private static final Logger log = Logger.getLogger(ServiceRefPod.class.getName());
  
  private final ServicesAmp _manager;
  private final ServiceRefPodRoot _podRoot;
  private final String _path;
  private final int _hash;
  
  private ServiceRefActive _serviceRefActive;
  private ServiceRefActive _serviceRefLocal;
  
  ServiceRefPod(ServiceRefPodRoot podRoot,
                String path,
                int hash)
  {
    _podRoot = podRoot;
    _manager = podRoot.manager();
    _path = path;
    _hash = hash;
  }
  
  @Override
  public String address()
  {
    return _podRoot.address() + _path;
  }
  
  @Override
  public ServicesAmp manager()
  {
    return _manager;
  }
  
  @Override
  public int nodeCount()
  {
    return _podRoot.nodeCount();
  }
  
  @Override
  public ServiceRefAmp pinNode(int hash)
  {
    return new ServiceRefPod(_podRoot, _path, Math.abs(hash));
  }

  @Override
  public ServiceRefAmp bind(String address)
  {
    address = ServicesAmpImpl.toCanonical(address);

    manager().bind(this, address);

    return this;
  }
  
  @Override
  public ServiceRefAmp onLookup(String path)
  {
    return _podRoot.onLookup(_path + path);
  }

  @Override
  public MethodRefAmp methodByName(String methodName)
  {
    return new MethodRefPod(this, methodName, null);
  }

  @Override
  public MethodRefAmp methodByName(String methodName, Type type)
  {
    return new MethodRefPod(this, methodName, type);
  }
  
  /*
  public InboxAmp getSelfInbox()
  {
    return _podRoot.getSelfService().getInbox();
  }
  */
  
  @Override
  public ServiceRefAmp delegate()
  {
    ServiceRefAmp serviceRef = getActiveService();
    
    return serviceRef;
  }
  
  @Override
  public ClassLoader classLoader()
  {
    ServiceRefAmp serviceRef = getLocalService();
    
    if (serviceRef != null) {
      return serviceRef.classLoader();
    }
    else {
      return manager().classLoader();
    }
  }
  
  @Override
  public ServiceRefAmp export(ClassLoader importLoader)
  {
    ServicesAmp manager = Amp.getContextManager(importLoader);
    
    String path = _podRoot.address() + _path;
    
    ServiceRefAmp serviceRefAmp = manager.service(path);
    
    return serviceRefAmp;
  }
  
  @Override
  public boolean isClosed()
  {
    try {
      return getActiveService().isClosed();
    } catch (ServiceException e) {
      log.log(Level.FINEST, e.toString(), e);
      
      return false;
    }
  }

  public PodBartender getPod()
  {
    return _podRoot.getPod();
  }

  /* XXX:
  ServiceRefAmp[] getServices()
  {
    return _podRoot.getServices();
  }
  */

  /**
   * Returns the active service for this pod and path's hash.
   * 
   * The hash of the path selects the pod's node. The active server is the
   * first server in the node's server list that is up.
   */
  public ServiceRefAmp getActiveService()
  {
    return getActiveService(_hash);
  }
  
  public ServiceRefAmp getActiveService(int hash)
  {
    ServiceRefAmp serviceRefRoot = _podRoot.getActiveService(hash);

    if (serviceRefRoot == null) {
      throw new ServiceException(L.l("Pod {0} does not have an active server.",
                                     address()));
    }
    
    ServiceRefActive serviceRefActive = _serviceRefActive;
    
    if (serviceRefActive != null) {
      ServiceRefAmp serviceRef = serviceRefActive.getService(serviceRefRoot);
      
      if (serviceRef != null) {
        return serviceRef;
      }
    }
    
    // baratine/8331
    ServiceRefAmp serviceRef = (ServiceRefAmp) serviceRefRoot.onLookup(_path);

    Objects.requireNonNull(serviceRef);
    
    _serviceRefActive = new ServiceRefActive(serviceRefRoot, serviceRef);
    
    // serviceRef.start();
    
    return serviceRef;
  }

  /**
   * Returns the active service for this pod and path's hash.
   * 
   * The hash of the path selects the pod's node. The active server is the
   * first server in the node's server list that is up.
   */
  public ServiceRefAmp getLocalService()
  {
    ServiceRefAmp serviceRefRoot = _podRoot.getLocalService();
    //ServiceRefAmp serviceRefRoot = _podRoot.getClientService();
    
    if (serviceRefRoot == null) {
      return null;
    }
    
    ServiceRefActive serviceRefLocal = _serviceRefLocal;
    
    if (serviceRefLocal != null) {
      ServiceRefAmp serviceRef = serviceRefLocal.getService(serviceRefRoot);
      
      if (serviceRef != null) {
        return serviceRef;
      }
    }
    
    ServiceRefAmp serviceRef = serviceRefRoot.onLookup(_path);

    _serviceRefLocal = new ServiceRefActive(serviceRefRoot, serviceRef);
    
    // serviceRef.start();
    
    return serviceRef;
  }

  /**
   * Caches the active service ref, based on the active root service.
   * 
   * When the root service changes because of a server going down, the
   * cache value is invalid and needs to be recalculated. 
   */
  private static class ServiceRefActive {
    private final ServiceRefAmp _serviceRefRoot;
    private final ServiceRefAmp _serviceRef;
    
    ServiceRefActive(ServiceRefAmp serviceRefRoot,
                     ServiceRefAmp serviceRef)
    {
      _serviceRefRoot = serviceRefRoot;
      _serviceRef = serviceRef;
    }
    
    ServiceRefAmp getService(ServiceRefAmp serviceRefRoot)
    {
      if (_serviceRefRoot == serviceRefRoot) {
        return _serviceRef;
      }
      else {
        return null;
      }
    }
  }
}
