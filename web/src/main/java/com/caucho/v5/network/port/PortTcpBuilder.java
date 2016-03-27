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

package com.caucho.v5.network.port;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.io.SSLFactory;
import com.caucho.v5.io.ServerSocketBar;
import com.caucho.v5.network.ssl.JsseSSLFactory;

import io.baratine.config.Config;

/**
 * Represents a protocol connection.
 */
//@Configurable
public class PortTcpBuilder
{
  private Config _env;
  
  private String _portName = "server";
  
  private int _portDefault = -1;
  
  private AtomicLong _connSequence;

  private ServerSocketBar _serverSocket;

  private Protocol _protocol;

  private String _address;

  private ServicesAmp _ampManager;
  
  public PortTcpBuilder(Config env)
  {
    Objects.requireNonNull(env);
    
    _env = env;
  }

  public void ampManager(ServicesAmp ampManager)
  {
    _ampManager = ampManager;
  }
  
  public AtomicLong getConnectionSequence()
  {
    if (_connSequence != null) {
      return _connSequence;
    }
    else {
      return new AtomicLong();
    }
  }
  
  String portName()
  {
    return _portName;
  }
  
  public PortTcpBuilder portName(String name)
  {
    Objects.requireNonNull(name);
    
    _portName = name;
    
    return this;
  }
  
  public PortTcpBuilder portDefault(int port)
  {
    _portDefault = port;
    
    return this;
  }

  public void protocol(Protocol protocol)
  {
    Objects.requireNonNull(protocol);
    
    _protocol = protocol;
  }

  Protocol protocol()
  {
    return _protocol;
  }
  
  String address()
  {
    if (_address == null || "*".equals(_address) || "".equals(_address)) {
      return null;
    }
    else {
      return _address;
    }
  }
  
  int port()
  {
    return _env.get(portName() + ".port", int.class, _portDefault);
  }
  
  /*
  public void serverSocket(ServerSocketBar serverSocket)
  {
    _serverSocket = serverSocket;
  }
  */

  public SSLFactory sslFactory()
  {
    boolean isSsl = _env.get(portName() + ".ssl", boolean.class, false);
    
    if (! isSsl) {
      return null;
    }
    
    JsseSSLFactory sslFactory = new JsseSSLFactory();
    
    sslFactory.setSelfSignedCertificateName("baratine");
    
    sslFactory.init();

    return sslFactory;
  }
  
  public ServerSocketBar serverSocket()
  {
    return _serverSocket;
  }
  
  public PortTcp get()
  {
    return new PortTcp(this);
  }

  public void enableJni(boolean isJni)
  {
    // TODO Auto-generated method stub
    
  }

  public void acceptThreadMin(int idleMin)
  {
    // TODO Auto-generated method stub
    
  }

  public void acceptThreadMax(int idleMax)
  {
    // TODO Auto-generated method stub
    
  }

  public void socketTimeout(int timeout)
  {
    // TODO Auto-generated method stub
    
  }

  public void address(String address)
  {
    _address = address;
  }

  public void keepalivePortThreadTimeout(int threadTimeout)
  {
    // TODO Auto-generated method stub
    
  }

  public ServicesAmp ampManager()
  {
    Objects.requireNonNull(_ampManager);
    
    return _ampManager;
  }

  public PollTcpManagerBase pollManager()
  {
    return null;
  }
}
