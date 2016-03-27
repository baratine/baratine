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
import com.caucho.v5.amp.service.ServiceRefClient;
import com.caucho.v5.bartender.pod.PodRef;

/**
 * Factory for remote pod serviceRefs, specific to a foreign 
 * address and calling pod.
 * 
 * The calling pod is for class loading.
 */
public class ServiceRefLinkFactory
{
  private ServicesAmp _manager;
  private ServiceRefAmp _serviceRefOut;
  private ServiceRefAmp _queryMapRef;
  private StubAmpOut _actorOut;
  
  private String _scheme;

  public ServiceRefLinkFactory(ServicesAmp manager, 
                               ServiceRefAmp serviceRefOut,
                               StubAmpOut actorOut, 
                               ServiceRefAmp queryMapRef,
                               String scheme)
  {
    _manager = manager;
    _serviceRefOut = serviceRefOut;
    _actorOut = actorOut;
    _queryMapRef = queryMapRef;
    _scheme = scheme;
    
    if (_queryMapRef.isClosed()) {
      System.err.println("Invalid queryMap: " + _queryMapRef + " " + manager);
      Thread.dumpStack();
      //throw new IllegalStateException(String.valueOf(_queryMapRef));
    }
  }

  /**
   * @param string
   * @return
   */
  public ServiceRefAmp createLinkService(String address)
  {
    return createLinkService(address, null);
  }

  /**
   * Return the serviceRef for a foreign path and calling pod.
   * 
   * @param path the service path on the foreign server
   * @param podCaller the name of the calling pod.
   */
  public ServiceRefAmp createLinkService(String path, PodRef podCaller)
  {
    
    StubLink actorLink;
    
    String address = _scheme + "//" + _serviceRefOut.address() + path;
    
    ServiceRefAmp parentRef = _actorOut.getServiceRef();
    
    if (_queryMapRef != null) {
      //String addressSelf = "/system";
      //ServiceRefAmp systemRef = _manager.getSystemInbox().getServiceRef();
      actorLink = new StubLinkUnidir(_manager, path, parentRef, _queryMapRef, podCaller, _actorOut);
    }
    else {
      actorLink = new StubLink(_manager, path, parentRef, podCaller, _actorOut);
    }

    ServiceRefAmp linkRef = _serviceRefOut.pin(actorLink, address);
    
    // ServiceRefClient needed to maintain workers, cloud/0420
    ServiceRefAmp clientRef = new ServiceRefClient(address,
                                                       linkRef.stub(),
                                                       linkRef.inbox());
    
    actorLink.initSelfRef(clientRef);

    
    return clientRef;
  }
}
