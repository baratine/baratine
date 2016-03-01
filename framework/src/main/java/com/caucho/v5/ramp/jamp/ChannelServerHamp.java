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

package com.caucho.v5.ramp.jamp;

import java.util.function.Supplier;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.remote.ChannelServerImpl;
import com.caucho.v5.amp.remote.OutAmp;
import com.caucho.v5.amp.spi.RegistryAmp;

/**
 * Broker for returning JAMP services.
 */
public class ChannelServerHamp extends ChannelServerImpl
{
  private String _addressPod;
  private String _sessionId;

  ChannelServerHamp(Supplier<ServiceManagerAmp> ampManagerRef,
                          RegistryAmp registry,
                          OutAmp out,
                          String address,
                          String addressPod,
                          String sessionId)
  {
    super(ampManagerRef, registry, out, address, sessionId);
    
    _addressPod = addressPod;
    _sessionId = sessionId;
  }
  
  @Override
  public ServiceRefAmp lookupService(String address)
  {
    if (address.startsWith("public:///")) {
      String addressService = address.substring("public://".length());
      
      return super.lookupService(_addressPod + addressService);
    }
    else if (address.startsWith("session://")) {
      int p = address.indexOf('/', "session:///".length());
      
      if (p > 0) {
        address = address.substring(0, p);
      }
      
      address = address + "/" + _sessionId;
      
      return super.lookupService(address);
    }
    else {
      return super.lookupService(address);
    }
  }
}
