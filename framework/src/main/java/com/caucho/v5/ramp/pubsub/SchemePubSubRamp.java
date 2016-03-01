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

package com.caucho.v5.ramp.pubsub;

import io.baratine.service.OnLookup;
import io.baratine.service.Service;
import io.baratine.service.ServiceManager;
import io.baratine.service.ServiceRef;

import java.util.HashMap;
import java.util.Objects;

import com.caucho.v5.amp.Amp;

/**
 * Implementation of the pub/sub bus.
 */
@Service
public class SchemePubSubRamp
{
  private ServiceManager _rampManager;
  
  private HashMap<String,PubSubServiceRamp<?>> _pubSubNodeMap
    = new HashMap<>();
    
  private String _address = "pubsub:";

  private ServiceRef _selfRef;
  
  //private ServiceRef _service;

  // private PubSubServiceRamp _podServer;
  
  public SchemePubSubRamp()
  {
    this("pubsub:");
  }
  
  public SchemePubSubRamp(String address)
  {
    this(address, Amp.getCurrentManager());
  }
  
  public SchemePubSubRamp(String address, ServiceManager rampManager)
  {
    Objects.requireNonNull(address);
    Objects.requireNonNull(rampManager);
    
    _address = address;
    _rampManager = rampManager;
    
    //_service = _rampManager.newService().service(this).build();
    
    // _podServer = new PubSubServiceRamp(this);
  }

  ServiceManager getManager()
  {
    return _rampManager;
  }
  
  public String getName()
  {
    return _address;
  }

  @OnLookup
  public Object onLookup(String path)
  {
    Object value = lookupPath(_address + path);

    return value;
  }

  private Object lookupPath(String address)
  {
    String podName = getPodName(address);
    String path = getSubPath(address);

    return new PubSubServiceRamp(this, podName, path);
  }
  
  /*
  private boolean isLocalPod(String podName)
  {
    PodBartender pod = _podServer.getPod();

    if (podName.isEmpty()) {
      return true;
    }
    else if (pod == null) {
      return true;
    }
    else {
      return podName.equals(pod.getName());
    }
  }
  */

  /*
  EventNodeActor lookupPubSubNode(String address)
  {
    String podName = getPodName(address);
    String path = getSubPath(address);
    
    EventNodeActor actor = _pubSubNodeMap.get(address);

    if (actor == null) {
      if (podName.isEmpty()) {
        actor = new EventNodeActor(this, address);
      }
      else if (isLocalPod(podName)) {
        actor = new EventNodeActorServer(this, podName, address);
      }
      else {
        actor = new EventNodeActorClient(this, podName, address);
      }

      _pubSubNodeMap.put(address, actor);
    }
    
    return actor;
  }
  */

  public String getSubPath(String address)
  {
    int p = address.indexOf("://");
    
    if (p < 0) {
      return address;
    }
    
    int q = address.indexOf('/', p + 3);
    
    return address.substring(q);
  }

  public String getPodName(String address)
  {
    int p = address.indexOf("://");
    
    if (p < 0) {
      return "";
    }
    
    int q = address.indexOf('/', p + 3);
    
    if (q > 0) {
      return address.substring(p + 3, q);
    }
    else {
      return address.substring(p + 3);
    }
  }
}
