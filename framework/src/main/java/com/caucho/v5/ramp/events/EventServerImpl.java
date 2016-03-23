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

package com.caucho.v5.ramp.events;

import io.baratine.service.Cancel;

import java.util.HashMap;
import java.util.Objects;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.ServerOnUpdate;
import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.bartender.pod.PodBartender;

/**
 * Pod server for remote event calls.
 */
public class EventServerImpl
{
  public static final String PATH = "/baratine/events";
  
  private final EventSchemeImpl _events;

  private PodBartender _pod;
  
  private HashMap<String,EventNodeServer> _clientMap = new HashMap<>();

  private NodePodAmp _podNode;

  private Cancel _updateHandle;
  
  public EventServerImpl(EventSchemeImpl eventsScheme)
  {
    Objects.requireNonNull(eventsScheme);
    
    _events = eventsScheme;
    
    _pod = BartenderSystem.getCurrentPod();
    _podNode = BartenderSystem.getCurrentShard();
  }
  
  PodBartender getPod()
  {
    return _pod;
  }
  
  NodePodAmp getPodNode()
  {
    return _podNode;
  }

  public void subscribe(String podName,
                        int nodeIndex,
                        String address)
  {
    EventNodeServer eventNode = _clientMap.get(address);
    
    if (eventNode == null) {
      eventNode = new EventNodeServer(_pod);
      
      _clientMap.put(address, eventNode);
      
      EventNodeAsset localNode = _events.lookupPubSubNode(address);
      
      localNode.subscribe(eventNode);
    }
    
    // EventNodeClient client = new EventNodeClient(podName, nodeIndex, address);
    
    eventNode.addClient(podName, nodeIndex, address);
  }
  
  public void publish(String podName,
                      String address, 
                      String methodName, 
                      Object []args)
  {
    EventNodeAsset localNode = _events.lookupPubSubNode(address);

    if (localNode != null) {
      localNode.publishFromRemote(methodName, args);
    }
  }

  void publishImpl(String podName, 
                          String address, 
                          String methodName,
                          Object[] args)
  {
    EventNodeServer node = _clientMap.get(address);
    
    if (node != null) {
      node.publish(methodName, args);
    }
  }
  
  private boolean isLocalPod(String podName)
  {
    if (podName == null || podName.isEmpty()) {
      return true;
    }
    else if (_pod == null) {
      return true;
    }
    else {
      return _pod.name().equals(podName);
    }
  }

  void init()
  {
    if (getPod() == null) {
      return;
    }
      
    ServiceRefAmp serviceRef = ServiceRefAmp.current();
      
    String podName = getPod().name();

    if (getPodNode() != null && getPodNode().index() == 0) {
      serviceRef.pin(this)
                .bind("pod://" + podName + EventServerImpl.PATH);
    }
    
    ServiceManagerAmp ampManager = AmpSystem.currentManager();
    
    _updateHandle = ampManager.service(ServerOnUpdate.ADDRESS)
                              .subscribe((ServerOnUpdate) x->onServerUpdate(x));
  }

  void destroy()
  {
    if (_updateHandle != null) {
      _updateHandle.cancel();
    }
  }
  
  private void onServerUpdate(ServerBartender server)
  {
    _events.onServerUpdate(server);
  }
}
