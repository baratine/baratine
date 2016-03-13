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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.remote.ChannelServerLinkImpl;
import com.caucho.v5.amp.remote.ClientAmpBase;
import com.caucho.v5.amp.remote.OutAmp;
import com.caucho.v5.amp.remote.OutAmpFactory;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.ramp.hamp.InHamp;

import io.baratine.service.ServiceRef;

/**
 * HMTP client protocol
 */
public class LinkHamp extends ClientAmpBase implements Runnable
{
  private static final Logger log
    = Logger.getLogger(LinkHamp.class.getName());
  
  private InputStream _is;
  private OutputStream _os;

  private String _address;

  private boolean _isClosed;
  
  private InHamp _in;

  private ChannelServerLinkImpl _channel;

  private OutHampImpl _out;

  public LinkHamp(InputStream is,
                  OutputStream os)
  {
    this(ServiceManagerAmp.newManager().start(),
         "remote://",
         is, os);
  }

  public LinkHamp(ServiceManagerAmp manager,
                  String peerAddress,
                  InputStream is, 
                  OutputStream os)
  {
    super(manager, "remote:");
    
    _is = is;
    _os = os;
    
    // RampServiceRef readService = RampClientReadActor.create(manager);
    
    /*
    ChannelManagerService sessionManager = new ChannelManagerServiceImpl(manager);
    
    String podName = null;
    
    ChannelServerFactory channelFactory
      = new RegistryAmpInServerFactoryImpl(manager, sessionManager, podName);
      */
    
    //_conn = new HampWriteConnection(_os, brokerFactory, peerAddress);
    _out = new OutHampImpl(_os);
    
    _channel = new ChannelServerLinkImpl(()->manager, _out);
    
    // _channel.onLogin("link");

    // XXX: need remote
    ServiceRefAmp channelRef = _channel.getServiceRefOut();

    // make remote:// available for callers 
    channelRef.bind(peerAddress);
    
    // make remote:// available for remote reply
    _channel.bind(peerAddress, channelRef);

    _in = new InHamp(manager, _channel);
    _in.init(_is);
  }

  @Override
  protected void bindRemote()
  {
  }
  

  public String getAddress()
  {
    return _address;
  }

  public void setAddress(String address)
  {
    _address = address;
  }
  
  public ServiceManagerAmp getManager()
  {
    return (ServiceManagerAmp) delegate();
  }

  @Override
  public String getUrl()
  {
    return "remote:///link";
  }

  @Override
  public LinkHamp connect()
  {
    return this;
  }

  /*
  @Override
  public <T> T lookup(String address, Class<T> api)
  {
    return getManager().lookup(address).as(api);
  }

  @Override
  public void publish(String address, Object actor)
  {
    getManager().service(actor).bind(address);
  }
  */
  
  protected OutAmpFactory getOutFactory()
  {
    return new OutAmpFactoryImpl();
  }
  
  private class OutAmpFactoryImpl implements OutAmpFactory {
    @Override
    public boolean isUp()
    {
      return ! isClosed();
    }

    @Override
    public OutAmp getOut(ChannelAmp registry)
    {
      return _out;
    }
  }
  
  public boolean isClosed()
  {
    return _isClosed;
  }

  /**
   * Receive messages from the client
   */
  @Override
  public void run()
  {
    try {
      while (! isClosed() && _is.read() > 0 && _in.readMessage(_is)) {
        ServiceRef.flushOutbox();
      }
    } catch (EOFException e) {
      log.finer(this + " end of file");
      
      if (log.isLoggable(Level.ALL)) {
        log.log(Level.ALL, e.toString(), e);
      }
    } catch (SocketException e) {
      e.printStackTrace();
      log.finer(this + " socket closed:" + e);
      
      if (log.isLoggable(Level.ALL)) {
        log.log(Level.ALL, e.toString(), e);
      }
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    } catch (Throwable e) {
      e.printStackTrace();
      throw e;
    } finally {
      close();
    }
  }

  @Override
  public void close()
  {
    _isClosed = true;
    
    _channel.shutdown(ShutdownModeAmp.GRACEFUL);
    
    _out.close();

    try {
      _is.close();
    } catch (Exception e) {
      log.log(Level.FINEST, e.toString(), e);
    }
    
    try {
      _os.close();
    } catch (Exception e) {
      log.log(Level.FINEST, e.toString(), e);
    }
  }
}
