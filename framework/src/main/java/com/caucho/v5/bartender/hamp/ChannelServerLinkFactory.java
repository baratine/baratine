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

package com.caucho.v5.bartender.hamp;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.remote.ChannelServer;
import com.caucho.v5.amp.remote.ChannelServerFactory;
import com.caucho.v5.amp.remote.ChannelServerImpl;
import com.caucho.v5.amp.remote.OutAmp;
import com.caucho.v5.amp.remote.RegistryAmpServerShared;
import com.caucho.v5.bartender.hamp.AuthHampImpl.LinkType;

/**
 * Creates the link broker for a hamp connection.
 */
public class ChannelServerLinkFactory implements ChannelServerFactory
{
  private final HampManager _hampManager;
  private final RegistryAmpServerShared _registry;
  
  public ChannelServerLinkFactory(HampManager manager)
  {
    _hampManager = manager;
    
    String podName = null;
    
    _registry = new RegistryAmpServerShared(()->manager.ampManager(), podName);
  }
  
  @Override
  public ChannelServer create(OutAmp out)
  {
    ServicesAmp rampManager = _hampManager.ampManager();
   
    ChannelServerImpl channel
      = new ChannelServerImpl(()->rampManager, _registry, out, "remote://", "");
    
    ServiceRefAmp serviceRefOut = channel.getServiceRefOut();
    
    AuthHampManager authManager = _hampManager.getAuthManager();
      
    AuthHampImpl authImpl
        = new AuthHampImpl(channel, authManager, LinkType.DUPLEX);
      
    String linkAddress = "link:///auth";
    
    ServiceRefAmp authRef = serviceRefOut.pin(authImpl);

    channel.bind(linkAddress, authRef);
    
    return channel;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _hampManager + "]";
  }
}
