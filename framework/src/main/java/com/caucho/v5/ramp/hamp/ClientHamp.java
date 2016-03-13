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

package com.caucho.v5.ramp.hamp;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.remote.ClientAmpBase;
import com.caucho.v5.amp.remote.OutAmpFactory;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;

/**
 * Endpoint for receiving hamp message
 */
public class ClientHamp extends ClientAmpBase
{
  private ConnectionHampFactoryClient _connectionFactory;
  
  public ClientHamp(String uri)
  {
    this(uri, null, null);
  }
  
  public ClientHamp(String uri, String user, String password)
  {
    super(createRampManager(), uri);
    
    _connectionFactory
      = new ConnectionHampFactoryClient(delegate(), uri, user, password);
  }
  
  private static ServiceManagerAmp createRampManager()
  {
    ServiceManagerBuilderAmp builder = ServiceManagerAmp.newManager();
    
    builder.name("hamp:");
    builder.contextManager(false);
    
    return builder.start();
  }
  
  public void setVirtualHost(String host)
  {
    _connectionFactory.setVirtualHost(host);
  }
  
  @Override
  protected OutAmpFactory getOutFactory()
  {
    return _connectionFactory;
  }
  

  /*
  protected RegistryAmpInClientImpl createBroker(String address)
  {
    return new RegistryHampInClient(getDelegate(), getChannel(), address);
  }
  */
}
