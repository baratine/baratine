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

package com.caucho.v5.bartender.link;

import java.io.Closeable;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.manager.ServiceManagerAmpWrapper;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.util.L10N;

/**
 * Client for connecting to the champ service.
 */
public class ClientBartenderLocal extends ServiceManagerAmpWrapper
  implements ServiceManagerClient, Closeable
{
  private static final L10N L = new L10N(ClientBartenderLocal.class);
  
  private final String _uri = "local:";

  private ServicesAmp _ampManager;
  
  public ClientBartenderLocal()
  {
    _ampManager = ServicesAmp.newManager().get();
  }
  
  @Override
  public ServicesAmp delegate()
  {
    return _ampManager;
  }
  
  @Override
  public ServiceRefAmp service(String address)
  {
    if (address.startsWith("remote://")) {
      String tail = address.substring("remote://".length());
      
      return _ampManager.service(tail);
    }
    else {
      return super.service(address);
    }
  }

  @Override
  public String getUrl()
  {
    return null;
  }

  @Override
  public ClientBartenderLocal connect()
  {
    return this;
  }

  /*
  @Override
  public boolean isActive()
  {
    return true;
  }
  */

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
  }

  @Override
  public void close()
  {
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
