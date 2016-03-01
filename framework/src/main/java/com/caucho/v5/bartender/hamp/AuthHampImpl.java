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

import com.caucho.v5.amp.remote.ChannelServerImpl;
import com.caucho.v5.ramp.hamp.NonceQuery;

/**
 * ServerLinkActor handles link messages, i.e. to=null, which is primarily
 * authentication.
 */

public class AuthHampImpl
{
  private final ChannelServerImpl _registryIn;
  private final AuthHampManager _authManager;
  private final LinkType _linkType;
  
  public AuthHampImpl(ChannelServerImpl registryIn,
                      AuthHampManager authManager,
                      LinkType linkType)
  {
    _registryIn = registryIn;
    _authManager = authManager;
    _linkType = linkType;
  }

  //
  // message handling
  //
  
  public void hostName(String hostName)
  {
    _registryIn.setHostName(hostName);
  }

  public NonceQuery getNonce(String uid, String clientNonce)
  {
    return _authManager.generateNonce(uid, clientNonce);
  }

  public boolean login(String uid, Object credentials)
  {
    String ipAddress = "127.0.0.1";

    if (_linkType != LinkType.UNIDIR) {
      _authManager.authenticate(uid, credentials, ipAddress);
    }
     
    _registryIn.onLogin(uid);
    
    return true;
  }
  
  protected void notifyValidLogin()
  {
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  enum LinkType {
    UNIDIR,
    DUPLEX;
  }
}
