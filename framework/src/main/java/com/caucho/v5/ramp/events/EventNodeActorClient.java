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

import io.baratine.service.Result;
import io.baratine.service.Services;
import io.baratine.service.ServiceRef;
import io.baratine.timer.Timers;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.bartender.pod.PodBartender;

/**
 * actor to handle inbound calls.
 */
class EventNodeActorClient extends EventNodeAsset
{
  private static final Logger log = Logger.getLogger(EventNodeActorClient.class.getName());
  
  private String _podName;
  private ServiceRef _podServiceRef;
  
  private boolean _isConnect;

  private EventServer _podServer;

  private PodBartender _pod;

  public EventNodeActorClient(EventServiceRamp root,
                               String podName,
                               String address)
  {
    super(root, address);
    
    _podName = podName;
    
    _pod = BartenderSystem.current().findPod(_podName);
    
    String eventsPath = "pod://" + podName + EventServerImpl.PATH;

    ServicesAmp  manager = ServicesAmp.current();
    _podServiceRef = manager.service(eventsPath).node(0);
    
    _podServer = _podServiceRef.as(EventServer.class);
  }

  @Override
  public void publish(String methodName, Object[] args)
  {
    _podServer.publish(_podName, getPath(), methodName, args); 
  }

  @Override
  public void subscribe()
  {
    boolean isConnect = _isConnect;
    
    _isConnect = true;

    if (! isConnect) {
      NodePodAmp node = getEvents().getEventServer().getPodNode();

      _podServer.subscribe(node.pod().getId(), node.nodeIndex(), getPath(),
                           Result.of(x->{}, e->onSubscribeFail(e, 1)));
    }
  }
  
  private void onSubscribeFail(Throwable e, long timeout)
  {
    log.finer(e.toString());
    log.log(Level.FINEST, e.toString(), e);
    
    ServicesAmp  manager = ServicesAmp.current();
    
    Timers timer = manager.service("timer:///").as(Timers.class);
    
    timer.runAfter(h->resubscribe(timeout), timeout, TimeUnit.SECONDS, 
                   Result.ignore());
  }
  
  private void resubscribe(long timeout)
  {
    NodePodAmp node = getEvents().getEventServer().getPodNode();
    
    long nextTimeout = Math.min(60000, 2 * timeout);
    
    _podServer.subscribe(node.pod().getId(), node.nodeIndex(), getPath(),
                         Result.of(x->{}, e->onSubscribeFail(e, nextTimeout)));
  }

  @Override
  void onServerUpdate(ServerBartender server)
  {
    if (! _pod.getNode(0).isServerCopy(server)) {
      return;
    }
    
    _isConnect = false;
    
    subscribe();
  }
}
