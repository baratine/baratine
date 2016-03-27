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

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.bartender.pod.PodRef;

/**
 * The proxy for a client registered in the ramp server.
 */
public class StubLinkUnidir extends StubLink
{
  //private final ServiceManagerAmp _rampManager;
  //private final String _selfAddress;
  
  private ServiceRefAmp _queryMapRef;

  public StubLinkUnidir(ServicesAmp ampManager,
                         String remoteAddress,
                         ServiceRefAmp parentRef,
                         ServiceRefAmp queryMapRef,
                         PodRef podCaller,
                         StubAmpOut actorOut)
  {
    super(ampManager, remoteAddress, parentRef, podCaller, actorOut);

    // unidir "from" must be a relative URL
    //if (! selfAddress.startsWith("/")) {
    //  throw new IllegalStateException(selfAddress);
    //}
        
    // _rampManager = rampManager;
    //_selfAddress = selfAddress;
    
    _queryMapRef = queryMapRef;
  }
  
  @Override
  public Object onLookup(String path, ServiceRefAmp parentRef)
  {
    StubLinkUnidir actorLink;
    
    //String childPath = getRemoteAddress() + path;
    String childPath = parentRef.address() + path;
    
    actorLink = new StubLinkUnidir(getServiceManager(),
                               childPath,
                               parentRef, // getSelfServiceRef(),
                               _queryMapRef,
                               getPodCaller(),
                               null); // getActorOut());
    
    ServiceRefAmp actorRef = parentRef.pin(actorLink, childPath);
    
    actorLink.initSelfRef(actorRef);
    
    return actorRef;
  }
  
  @Override
  public ServiceRefAmp getQueryMapRef()
  {
    return _queryMapRef;
  }

  /*
  @Override
  protected QueryRefAmp createQueryChain(Result<?> result)
  {
    // XXX: queryRef chain shouldn't depend on the method ref
    // currently dependent on the system mailbox. Should be related to
    // the actual mailbox if it has a return address. Or perhaps the
    // service itself should have a foreign address.
    
    // RampServiceRef serviceRef = _connActor.getServiceRef();
    // ServiceRefAmp serviceRef = _rampManager.getSystemInbox().getServiceRef();
    
    InboxAmp inboxReply = getInboxReply(); // serviceRef.getInbox();
    long timeout = 0;
    
    return inboxReply.addQuery(result);
  }

  @Override
  protected QueryRefAmp createQueryChain(ResultStream<?> result)
  {
    // XXX: queryRef chain shouldn't depend on the method ref
    // currently dependent on the system mailbox. Should be related to
    // the actual mailbox if it has a return address. Or perhaps the
    // service itself should have a foreign address.
    
    // RampServiceRef serviceRef = _connActor.getServiceRef();
    // ServiceRefAmp serviceRef = _rampManager.getSystemInbox().getServiceRef();
    
    InboxAmp inboxReply = getInboxReply();
    long timeout = 0;
    
    return inboxReply.addQuery(result);
  }
  */

  /*
  @Override
  protected InboxAmp getInboxReply()
  {
    // XXX: queryRef chain shouldn't depend on the method ref
    // currently dependent on the system mailbox. Should be related to
    // the actual mailbox if it has a return address. Or perhaps the
    // service itself should have a foreign address.
    
    //return getServiceManager().getSystemInbox();
    //return super.getInboxReply();
    //return getServiceManager().getSystemInbox();
  }
  */

  /*
  @Override
  String getFromAddress(QueryRefAmp queryChain)
  {
    return "/system";
  }
  */
}
