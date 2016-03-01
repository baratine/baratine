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

package com.caucho.v5.baratine.client;

import io.baratine.service.ServiceClient;
import io.baratine.service.ServiceManager;

import com.caucho.v5.amp.manager.ServiceManagerWrapper;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.ramp.hamp.ClientHamp;


/**
 * Baratine client for Java.
 */
public class BaratineClient extends ServiceManagerWrapper 
  implements ServiceManagerClient, ServiceClient
{
  private ClientHamp _client;
  
  public BaratineClient(String uri)
  {
    _client = new ClientHamp(uri);
  }
  
  public BaratineClient(String uri, String user, String password)
  {
    _client = new ClientHamp(uri, user, password);
  }
  
  @Override
  protected ServiceManager delegate()
  {
    return _client;
  }

  @Override
  public String getUrl()
  {
    return _client.getUrl();
  }

  @Override
  public BaratineClient connect()
  {
    _client.connect();
    
    return this;
  }

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    _client.shutdown(mode);
  }

  @Override
  public void close()
  {
    _client.close();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getUrl() + "]";
  }
}
