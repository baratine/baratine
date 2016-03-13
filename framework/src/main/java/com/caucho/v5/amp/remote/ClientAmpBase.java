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

import java.io.Closeable;
import java.util.Objects;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.ServiceRefWrapper;
import com.caucho.v5.amp.manager.ServiceManagerAmpWrapper;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.baratine.client.ServiceManagerClient;

/**
 * Endpoint for receiving hamp message
 */
abstract public class ClientAmpBase
  extends ServiceManagerAmpWrapper
  implements ServiceManagerClient, Closeable
{
  private final String _uri;
  
  private ServiceRefAmp _channelServiceRef;
  private OutAmpManager _outManager;

  private ChannelClient _channel;

  private ServiceManagerAmp _manager;
  
  protected ClientAmpBase(ServiceManagerAmp manager, String uri)
  {
    Objects.requireNonNull(manager);
    
    _manager = manager;
    
    _uri = uri;
    
    // String address = "remote://";
    
    _outManager = createOutManager();

    // _channel = createChannel(uri);
    
    //_channelServiceRef = _linkBroker.getServiceRefOut();
    //OutboxAmp outbox = _linkBroker.createOutbox();

    //manager.bind(_channel.getServiceRefOut(), "remote://");
    
    //manager.bind(new ServiceRefRemote(), "remote://");
    
    bindRemote();
  }
  
  @Override
  protected ServiceManagerAmp delegate()
  {
    return _manager;
  }

  protected void bindRemote()
  {
    delegate().bind(new ServiceRefRemote(), "remote://");
  }
  
  protected ChannelClient getChannel()
  {
    if (_channel == null) {
      _channel = createChannel(_uri);
    }
    
    return _channel;
  }
  
  protected ChannelClient createChannel(String address)
  {
    ChannelClientImpl client;
    
    client = new ChannelClientImpl(delegate(), getOutAmpManager(), 
                                   address,
                                   delegate().service("/system"));
    
    return client;
  }
  
  protected OutAmpManager getOutAmpManager()
  {
    return _outManager;
  }
  
  public String getUrl()
  {
    return _uri;
  }
  
  protected ServiceRefAmp getServiceRef()
  {
    return _channelServiceRef;
  }
  
  protected OutAmpManager createOutManager()
  {
    return new ClientChannel();
  }
  
  protected OutAmpFactory getOutFactory()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public ClientAmpBase connect()
  {
    /*
    RampClientProxyActor proxyActor = (RampClientProxyActor) _channelServiceRef.getActor();
    
    try {
      proxyActor.connect();
    } catch (MakaiConnectException e) {
      throw e.rethrow(L.l("Unable to connect to remote HAMP server. Check that remote administration has been enabled.\n{0}",
                          e.toString()));
    }
    */
    // _connectionFactory.getConnection();
    /*
    ClientContainerImpl client = new ClientContainerImpl();
    
    client.connectToServer(_endpoint, _uri, _host);
    */
    
    //_linkBroker.waitForLogin();
    
    _outManager.getOut(getChannel());
    
    return this;
  }

    
  //
  // modules
  //

  /*
  @Override
  public boolean isClosed()
  {
    return true;
  }
  */
  
  @Override
  public void close()
  {
    shutdown(ShutdownModeAmp.GRACEFUL);
  }
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    OutAmpManager outManager = _outManager;
    _outManager = null;
    
    if (outManager != null) {
      outManager.close();
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _uri + "]";
  }
  
  private class ClientChannel implements OutAmpManager {
    private OutAmp _out;

    ClientChannel()
    {
    }
    
    @Override
    public boolean isUp()
    {
      return getOutFactory().isUp();
    }

    @Override
    public OutAmp getCurrentOut()
    {
      return _out;
    }

    @Override
    public OutAmp getOut(ChannelClient channel)
    {
      if (_out == null || ! _out.isUp()) {
        _out = getOutFactory().getOut(channel);

        channel.login(_out);
      }

      return _out;
    }

    @Override
    public void close()
    {
      OutAmp conn = _out;
      _out = null;

      if (conn != null) {
        conn.close();
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _uri + "]";
    }
    
  }
  
  private class ServiceRefRemote extends ServiceRefWrapper {
    @Override
    public ServiceRefAmp delegate()
    {
      return getChannel().getServiceRefOut();
    }
  }
}
