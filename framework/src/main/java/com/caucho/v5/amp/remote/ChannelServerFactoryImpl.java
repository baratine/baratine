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

import java.util.function.Supplier;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.RegistryAmp;

/**
 * Creates connection brokers for server connections.
 */
public class ChannelServerFactoryImpl
  implements ChannelServerFactory
{
  private final Supplier<ServicesAmp> _managerRef;
  
  private final RegistryAmpServerShared _registry;
  
  private ChannelManagerService _sessionManager;
  
  public ChannelServerFactoryImpl(Supplier<ServicesAmp> managerRef,
                                  ChannelManagerService sessionManager,
                                  String podName)
  {
    _managerRef = managerRef;
    
    _registry = new RegistryAmpServerShared(managerRef, podName);
    
    _sessionManager = sessionManager;
  }
  
  protected RegistryAmp getRegistry()
  {
    return _registry;
  }

  @Override
  public ChannelServer create(OutAmp out)
  {
    String address = generateAddress(out);
    
    int p = address.lastIndexOf('/');
    String chanId = address.substring(p + 1);

    ChannelServer registryIn = createChannel(out, address, chanId);

    return initRegistry(registryIn, address);
  }
  
  protected Supplier<ServicesAmp> getRampManagerRef()
  {
    return _managerRef;
  }
  
  protected ServicesAmp getRampManager()
  {
    return _managerRef.get();
  }
  
  protected ChannelManagerService getSessionManager()
  {
    return _sessionManager;
  }
  
  protected String generateAddress(OutAmp conn)
  {
    return getSessionManager().generateAddress(getProtocolName());
  }
  
  protected String getProtocolName()
  {
    return "/jamp";
  }
  
  protected ChannelServer createChannel(OutAmp out, 
                                               String address,
                                               String chanId)
  {
    return new ChannelServerImpl(_managerRef, _registry, out, address, chanId);
  }
  
  protected ChannelServer initRegistry(ChannelServer registryIn,
                                             String address)
  {
    // baratine/2100
    registryIn.onLogin("anon");
    
    return registryIn;
  }
}
