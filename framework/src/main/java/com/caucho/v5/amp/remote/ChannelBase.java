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

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.RegistryAmp;

/**
 * Broker specific to the server link. The broker will serve link-specific
 * actors like the login actor.
 * 
 * The broker requires a login to allow access to the general system. It's
 * expected that a login actor will be registered and will call the
 * <code>setLogin</code> method.
 */
public class ChannelBase implements ChannelAmp
{
  private ServiceManagerAmp _manager;
  
  public ChannelBase(ServiceManagerAmp manager)
  {
    _manager = manager;
  }
  
  @Override
  public ServiceManagerAmp getManager()
  {
    return _manager;
  }
  
  /*
  @Override
  public OutboxAmp createOutbox()
  {
    //OutboxAmpDirect outbox = new OutboxAmpDirect();
    //outbox.setInbox(_manager.getSystemInbox());
    
    //return outbox;
    return _manager.getSystemOutbox();
  }
  */
  
  protected RegistryAmp getRegistry()
  {
    return getManager().registry();
  }
  
  @Override
  public ServiceRefAmp service(String address)
  {
    return getRegistry().service(address);
  }

  @Override
  public GatewayReply createGatewayReply(String address)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public ServiceRefAmp createGatewayRef(String address)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
