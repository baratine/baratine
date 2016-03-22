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

package com.caucho.v5.amp.remote;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.StubAmpBase;
import com.caucho.v5.bartender.pod.PodRef;

/**
 * The proxy for a client registered in the ramp server.
 */
abstract public class ActorAmpOut extends StubAmpBase
{
  private static final Logger log
    = Logger.getLogger(ActorAmpOut.class.getName());
  
  private final ServiceManagerAmp _ampManager;
  
  // the address advertised to the remote
  private final String _remoteAddress;
  
  private final String _selfAddress;
  
  private ServiceRefAmp _selfServiceRef;
  private ServiceRefAmp _serviceRef;

  protected ActorAmpOut(ServiceManagerAmp ampManager,
                        String remoteAddress,
                        ServiceRefAmp selfServiceRef)
  {
    Objects.requireNonNull(ampManager);
    Objects.requireNonNull(selfServiceRef);
    
    _ampManager = ampManager;
    _remoteAddress = remoteAddress;
    _selfServiceRef = selfServiceRef;
    _selfAddress = selfServiceRef.address();
  }
  
  protected void init(ServiceManagerAmp ampManager)
  {
    _serviceRef = createService(getServiceManager());
  }
  
  protected ServiceManagerAmp getServiceManager()
  {
    return _ampManager;
  }
  
  protected ServiceRefAmp createService(ServiceManagerAmp ampManager)
  {
    return ampManager.newService(this).ref();
  }

  @Override
  public String name()
  {
    return _remoteAddress;
  }

  public ServiceRefAmp getServiceRef()
  {
    return _serviceRef;
  }
  
  String getRemoteAddress()
  {
    return _remoteAddress;
  }

  String getSelfAddress()
  {
    return _selfAddress;
  }

  ServiceRefAmp getSelfServiceRef()
  {
    return _selfServiceRef;
  }

  @Override
  public Class<?> getApiClass()
  {
    return getClass();
  }
  
  @Override
  public Object onLookup(String path, ServiceRefAmp parentRef)
  {
    PodRef podCaller = null;
    
    ActorLink actorLink = new ActorLink(getServiceManager(), path, parentRef, podCaller, this);
    
    ServiceRefAmp actorRef = parentRef.pin(actorLink, parentRef.address() + path);
    
    actorLink.initSelfRef(actorRef);
    // ServiceManagerAmp manager = getServiceManager();
    
    //return manager.service(parentRef, actor, path);
    return actorRef;
  }

  @Override
  public StubAmp worker(StubAmp actorMessage)
  {
    //System.out.println("GA: " +  this + " " + actorMessage);
    if (actorMessage instanceof ActorLink) {
      return this;
    }
    else {
      return super.worker(actorMessage);
    }
    //return this;
  }

  /**
   * Returns the connection writer, opening a new connection if the old
   * one has closed.
   */
  abstract OutAmp getOut();
  
  /**
   * Returns the connection writer, or null if no connection is available.
   */
  abstract OutAmp getCurrentOut();

  @Override
  public void queryReply(HeadersAmp headers, 
                         StubAmp rampActor,
                         long id,
                         Object result)
  {
    ActorLink connProxy = (ActorLink) rampActor;

    getOut().reply(headers, connProxy.getRemoteAddress(), id, result);
  }

  @Override
  public void queryError(HeadersAmp headers,
                         StubAmp actorDeliver,
                         long id,
                         Throwable exn)
  {
    try {
      ActorLink connProxy = (ActorLink) actorDeliver;

      getOut().queryError(headers, connProxy.getRemoteAddress(), id, exn);
    } catch (Throwable e) {
      log.warning("Failed to deliver query error: " + exn + " " + this);
      e.printStackTrace();
      exn.printStackTrace();
    }
  }

  @Override
  public void streamReply(HeadersAmp headers, 
                          StubAmp rampActor,
                          long id,
                          int sequence,
                          List<Object> values,
                          Throwable exn,
                          boolean isComplete)
  {
    ActorLink actorLink = (ActorLink) rampActor;

    getOut().streamReply(headers, actorLink.getRemoteAddress(), id, sequence,
                         values, exn, isComplete);
  }

  @Override
  public void streamCancel(HeadersAmp headers,
                           StubAmp queryActor,
                           String addressFrom, 
                           long qid)
  {
    ActorLink actorLink = (ActorLink) queryActor;

    getOut().streamCancel(headers, actorLink.getRemoteAddress(), addressFrom, qid);
  }

  @Override
  public final void afterBatch()
  {
    OutAmp out = getCurrentOut();

    if (out != null) {
      out.flush();
    }
  }
  
  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
    // XXX: add API to close?
    OutAmp out = getCurrentOut();
    
    if (out != null) {
      out.close();
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getCurrentOut() + "]";
  }
}
