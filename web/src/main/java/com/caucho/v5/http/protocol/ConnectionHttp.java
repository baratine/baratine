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

package com.caucho.v5.http.protocol;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.health.shutdown.ShutdownSystem;
import com.caucho.v5.network.port.ConnectionProtocol;
import com.caucho.v5.network.port.ConnectionTcp;
import com.caucho.v5.network.port.StateConnection;

/**
 * Handles a HTTP connection.
 */
public class ConnectionHttp implements ConnectionProtocol
{
  private static final Logger log
    = Logger.getLogger(ConnectionHttp.class.getName());

  private final ProtocolHttp _protocol;
  
  private final ConnectionTcp _conn;
  private final long _sequence;
  
  private final OutHttpProxy _outProxy;
  
  private ConnectionProtocol _request;
  
  private AtomicLong _sequenceRead = new AtomicLong();
  private AtomicLong _sequenceWrite = new AtomicLong();
  private AtomicLong _sequenceFlush = new AtomicLong();

  private AtomicBoolean _isClosePending = new AtomicBoolean();

  /**
   * Creates a new HttpRequest.  New connections reuse the request.
   *
   * @param server the owning server.
   */
  public ConnectionHttp(ProtocolHttp protocol,
                        ConnectionTcp conn,
                        long sequence)
  {
    Objects.requireNonNull(protocol);
    Objects.requireNonNull(conn);
    
    _protocol = protocol;
    _conn = conn;
    _sequence = sequence;
    
    ServicesAmp ampManager = conn.port().ampManager();
    
    _outProxy = ampManager.newService(new OutHttpProxyImpl(this))
                          .name(conn.toString())
                          .as(OutHttpProxy.class);
    
    // _requestHttp = new RequestHttp(protocol, conn, this);
  }
  
  public ProtocolHttp protocol()
  {
    return _protocol;
  }
  
  public ConnectionTcp connTcp()
  {
    return _conn;
  }
  
  public ConnectionProtocol request()
  {
    return _request;
  }
  
  private ConnectionProtocol requestOrCreate()
  {
    ConnectionProtocol request = _request;
    
    if (request == null) {
      //request = protocol().newRequest(this);
      request = newRequestHttp();
      
      request.onAccept();
      
      _request = request;
    }
    
    return request;
  }

  public void request(ConnectionProtocol request)
  {
    Objects.requireNonNull(request);
    
    ConnectionProtocol oldRequest = _request;
    //Objects.requireNonNull(oldRequest);
    
    _request = request;
  }

  public void requestOut(ConnectionProtocol request)
  {
    //Objects.requireNonNull(request);
    
    //ConnectionProtocol oldRequest = _request;
    //Objects.requireNonNull(oldRequest);
    
    //_request = request;
    
    //if (oldRequest != null) {
    //  oldRequest.onCloseRead();
    //}
  }

  public OutHttpProxy outProxy()
  {
    return _outProxy;
  }


  @Override
  public String url()
  {
    ConnectionProtocol request = request();

    if (request != null) {
      return request.url();
    }
    else {
      return null;
    }
  }
  
  //
  // ConnectionTcp callbacks
  //
 
  /**
   * Http requests wait for read data before beginning.
   */
  @Override
  public final boolean isWaitForRead()
  {
    return true;
  }

  /**
   * Called first when the connection is first accepted.
   */
  @Override
  public void onAccept()
  {
    if (_request != null) {
      System.out.println("OLD_REQUEST: " + _request);
    }
    
    /*
    _request = protocol().newRequest(this);
    _request.onAccept();
    */
  }

  public RequestHttpState newRequestHttp()
  {
    RequestHttpState request = protocol().requestHttpAllocate();
    
    request.init(this);
    
    return request;
  }

  /**
   * Service a HTTP request.
   *
   * @return the next state for the read thread
   */
  @Override
  public StateConnection service()
    throws IOException
  {
    try {
      ConnectionProtocol request = requestOrCreate();

      if (request == null) {
        log.warning("Unexpected empty request: " + this);
        
        return StateConnection.CLOSE;
      }
      
      //_requestHttp.parseInvocation();
      
        
        /*
        if (requestFacade == null) {
          _requestHttp.startRequest();
          requestFacade = _requestHttp.getRequestFacade();
          
          //return NextState.CLOSE;
        }
        */

      StateConnection next = request.service();
      
      if (next != StateConnection.CLOSE) {
        return next;
      }
      else {
        return onCloseRead();
      }
    } catch (OutOfMemoryError e) {
      String msg = "Out of memory in RequestProtocolHttp";
        
      ShutdownSystem.shutdownOutOfMemory(msg);
        
      log.log(Level.WARNING, e.toString(), e);
    } catch (Throwable e) {
      e.printStackTrace();
      log.log(Level.WARNING, e.toString(), e);
    }

    return StateConnection.CLOSE;
  }

  @Override
  public StateConnection onCloseRead()
  {
    ConnectionProtocol request = request();
    
    if (request != null) {
      request.onCloseRead();
    }
    
    if (_sequenceFlush.get() < _sequenceRead.get()) {
      _isClosePending.set(true);
      
      if (_sequenceFlush.get() < _sequenceRead.get()) {
        return StateConnection.CLOSE_READ_S;
      }
      else {
        _isClosePending.set(false);
        
        return StateConnection.CLOSE;
      }
    }
    else {
      return StateConnection.CLOSE;
    }
  }
  
  @Override
  public void onTimeout()
  {
    ConnectionProtocol request = request();
    
    if (request != null) {
      request.onTimeout();
    }
  }

  @Override
  public void onClose()
  {
    ConnectionProtocol request = _request;
    _request = null;
    
    if (request != null) {
      request.onClose();
    }
  }
  
  public long allocateSequence()
  {
    return _sequenceRead.incrementAndGet();
  }

  public long sequenceWrite()
  {
    return _sequenceWrite.get();
  }

  public void requestComplete(RequestHttpState requestHttpState, 
                              boolean isKeepalive)
  {
    ConnectionProtocol oldRequest = _request;
    Objects.requireNonNull(oldRequest);
    
    _request = null;
  }

  //@Override
  public void onCloseWrite()
  {
    _sequenceWrite.incrementAndGet();

    /*
    if (_isClosePending.compareAndSet(true, false)) {
      // XXX: wake?
      connTcp().proxy().requestWake();
    }
    */
  }
  
  public void onFlush()
  {
    _sequenceFlush.set(_sequenceWrite.get());

    if (_isClosePending.compareAndSet(true, false)) {
      // XXX: wake?
      connTcp().proxy().requestWake();
    }
  }
  
  //
  // output tasks
  //
  
  @Override
  public String toString()
  {
    /*
    HttpContainer httpSystem = http();
    
    String serverId;
    
    if (httpSystem != null)
      serverId = httpSystem.getServerDisplayName();
    else {
      serverId = "server";
    }
    */
    
    String serverId = "server";
    
    // int connId = _conn.getConnectionId();
    long connId = _sequence;

    if ("".equals(serverId))
      return getClass().getSimpleName() + "[" + connId + "]";
    else {
      return getClass().getSimpleName() + ("[" + serverId + ", " + connId + "]");
    }
  }
}
