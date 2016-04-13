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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.bartender.websocket.ProtocolBartender;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.network.port.ConnectionProtocol;
import com.caucho.v5.network.port.Protocol;
import com.caucho.v5.network.port.StateConnection;
import com.caucho.v5.util.L10N;
import com.caucho.v5.web.webapp.InvocationBaratine;
import com.caucho.v5.web.webapp.RequestBaratineImpl;

import io.baratine.service.ServiceRef;


/**
 * State for a single http request.
 */
public class RequestHttpState implements ConnectionProtocol
{
  private static final L10N L = new L10N(RequestHttpState.class);
  private static final Logger log
    = Logger.getLogger(RequestHttpState.class.getName());
  
  private ConnectionHttp _connHttp;

  private final RequestHttpBase _requestHttp;
  
  private RequestBaratineImpl _request;
  
  private InvocationBaratine _invocation;

  private RequestUpgrade _upgrade;
  
  private StateHttpEnum _state = StateHttpEnum.IDLE;
  
  //private RequestHttpState _next;
  //private RequestHttpState _prev;
  private long _sequenceRead;
  private long _sequence;

  public RequestHttpState(ProtocolHttp protocolHttp)
  {
    Objects.requireNonNull(protocolHttp);
    
      // _connHttp = connHttp;
    
    _requestHttp = new RequestHttp(protocolHttp, this);
    //if (true) throw new UnsupportedOperationException();
    
    //RequestHttpBase requestHttp = null;
    //_requestHttp = requestHttp;
  }
  
  public static String dorp()
  {
    return "dorp";
  }

  public void init(ConnectionHttp connHttp)
  {
    // _connTcp = connTcp;
    _connHttp = connHttp;
    
    _requestHttp.init(connHttp);
    
    _request = null;
    _state = StateHttpEnum.IDLE;
    _sequenceRead = 0;
  }
  
  public ConnectionHttp connHttp()
  {
    return _connHttp;
  }
  
  public RequestHttpBase requestHttp()
  {
    return _requestHttp;
  }
  
  public InvocationBaratine invocation()
  {
    return _invocation;
  }
  
  /*
  public RequestHttpState next()
  {
    return _next;
  }
  
  public RequestHttpState prev()
  {
    return _prev;
  }
  */
  
  /**
   * onAccept called on connection accept.
   */
  @Override
  public void onAccept()
  {
  //  _state = StateHttpEnum.IDLE;
  }

  /**
   * Service is the main call when data is available from the socket.
   */
  @Override
  public StateConnection service()
  {
    try {
      StateConnection nextState = _state.service(this);

      //return StateConnection.CLOSE;
      return nextState;
      
      /*
      if (_invocation == null && getRequestHttp().parseInvocation()) {
        if (_invocation == null) {
          return NextState.CLOSE;
        }
        return _invocation.service(this, getResponse());
      }
      else 
      if (_upgrade != null) {
        return _upgrade.service();
      }
      else if (_invocation != null) {
        return _invocation.service(this);
      }
      else {
        return StateConnection.CLOSE;
      }
      */
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      e.printStackTrace();
      
      toClose();
      
      return StateConnection.CLOSE_READ_A;
    }
  }
  
  /*
  //@Override
  public void next(ConnectionProtocol next)
  {
    RequestHttpState nextBar = (RequestHttpState) next;
    _next = nextBar;
    
    if (nextBar != null) {
      nextBar._prev = this;
    }
  }
  */
  
  private StateConnection accept()
  {
    try {
      _state = StateHttpEnum.READ;
      
      //_requestHttp = _connHttp.newRequestHttp();

      _invocation = (InvocationBaratine) _requestHttp.parseInvocation();
      
      if (_invocation == null) {
        _state = StateHttpEnum.CLOSE;
        
        
        _state = _state.toIdle();
        
        connHttp().requestComplete(this, false);
        connHttp().protocol().requestFree(this);
        
        return StateConnection.CLOSE;
      }
      
      // sequence used for write ordering 
       _sequenceRead = connHttp().allocateSequence();
      
      _request = (RequestBaratineImpl) connHttp().protocol().newRequest(connHttp());
      
      _request.init(this);
      
      if (_state.isBodyComplete()) {
        _request.bodyComplete();
      }
      else if (! requestHttp().isUpgrade()) {
        _requestHttp.readBodyChunk();
      }
      
      StateConnection nextState = _invocation.service(_request);
      
      ServiceRef.flushOutboxAndExecuteLast();
      
      if (! _state.isBodyComplete()) {
        return StateConnection.READ;
      }
      else if (_state == StateHttpEnum.UPGRADE) {
        return StateConnection.READ;
      }
      else if (_requestHttp.isKeepalive()) {
        return StateConnection.READ;
      }
      else {
        return StateConnection.CLOSE_READ_A;
      }
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      e.printStackTrace();
      
      //toClose();
      
      return StateConnection.CLOSE;
    }
  }
  
  long sequence()
  {
    return _sequenceRead;
  }

  public void upgrade(ConnectionProtocol upgrade)
  {
    connHttp().request(upgrade);
    //_upgrade = upgrade;
    
    //upgrade.start();
    
    requestHttp().upgrade();
    
    _state = _state.toUpgrade();
    
    requestHttp().getConnection().proxy().requestWake();
  }
  
  private StateConnection readBody()
  {
    try {
      _requestHttp.readBodyChunk();
      
      /*
      if (! _isBodyComplete) {
        // XXX: only on non-upgrade and non-101
        _requestHttp.readBodyChunk(this);
      }
      */
      
      if (! _state.isBodyComplete()) {
        return StateConnection.READ;
      }
      
      //requestProxy().bodyComplete(this);
      
      ServiceRef.flushOutboxAndExecuteLast();
      
      if (_requestHttp.isKeepalive()) {
        return StateConnection.READ;
      }
      else {
        return StateConnection.CLOSE_READ_A;
      }
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      e.printStackTrace();
      
      toClose();
      
      return StateConnection.CLOSE;
    }
  }

  public void onBodyChunk(TempBuffer tBuf)
  {
    _request.bodyChunk(tBuf);
  }

  public void onBodyComplete()
  {
    _state = _state.toBodyComplete();
    
    connHttp().requestComplete(this, requestHttp().isKeepalive());
    
    _request.bodyComplete();
  }

  @Override
  public StateConnection onCloseRead()
  {
    _state = _state.toCloseRead();
    
    switch (_state) {
    case CLOSE_READ:
      return StateConnection.CLOSE_READ_S;
      
    case CLOSE: 
      return StateConnection.CLOSE;
      
    default:
      return StateConnection.CLOSE;
    }
  }
  
  //@Override
  public void onCloseWrite()
  {
    StateHttpEnum state = _state;
    
    _state = state.toCloseWrite();

    onClose();
    
    ConnectionHttp connHttp = connHttp();
    
    //RequestHttpState reqNext = next();
    
    connHttp.onCloseWrite();
    
    _state = _state.toIdle();
    
    connHttp.protocol().requestFree(this);
    
    /*
    if (reqNext != null) {
      reqNext.writePending();
    }
    */

    /*
    switch (state) {
    case CLOSE_READ:
      connHttp().connTcp().proxy().requestWake();
      break;
    }
    */
    
    //_next = null;
    
    /*
    RequestHttpBase requestHttp = _requestHttp;
    _requestHttp = null;
    
    if (requestHttp != null) {
      requestHttp.freeSelf();
    }
    */
  }
  
  //@Override
  public boolean isPrevCloseWrite()
  {
    /*
    RequestHttpState prev = prev();
    
    if (prev == null) {
      return true;
    }
    else {
      return prev._state.isCloseWrite();
    }
    */
    //
    return true;
  }
  
  private void writePending()
  {
    RequestHttpBase reqHttp = requestHttp();
    
    if (reqHttp != null) {
      reqHttp.writePending();
    }
  }
  
  protected boolean startBartender()
    throws IOException
  {
    ConnectionProtocol request = null;
    
    ProtocolHttp protocolHttp = connHttp().protocol();

    if (protocolHttp == null) {
      log.fine("http-protocol is not available");
      toReadFail();
      return false;
    }

    Protocol extProtocol = protocolHttp.extensionProtocol();

    if (extProtocol == null) {
      log.fine("extension-protocol is not configured");
      toReadFail();
      return false;
    }
    
    // XXX:
    /*
    if (! hmuxProtocol.isLocalAddress(getRemoteAddr())) {
      return false;
    }
    */
    
    if (log.isLoggable(Level.FINEST)) {
      log.finest("extension: " + extProtocol + " " + connHttp());
    }
    
    // XXX:
    
    ProtocolBartender wsProtocol = (ProtocolBartender) extProtocol;
    
    RequestBaratineImpl req = (RequestBaratineImpl) request;
    
    return wsProtocol.upgrade(req).isClose();
  }
  
  private void toClose()
  {
    
  }
  
  private void toReadFail()
  {
    
  }

  //
  // body callback
  //
  
  @Override
  public String toString()
  {
    InvocationBaratine invocation = _invocation;
    
    if (invocation != null) {
      return getClass().getSimpleName() + "[" + invocation.getURI() + "]";
    }
    else {
      return getClass().getSimpleName() + "[null]";
    }
  }
  
  private enum StateHttpEnum
  {
    IDLE
    {
      @Override
      public StateConnection service(RequestHttpState request)
      {
        return request.accept();
      }
      
      @Override
      public StateHttpEnum toUpgrade() { return StateHttpEnum.UPGRADE; }
    },
    
    READ {
      @Override
      public StateHttpEnum toUpgrade() { return StateHttpEnum.UPGRADE; }
      
      @Override
      public StateConnection service(RequestHttpState request)
      {
        return request.readBody();
      }
      
    },
    
    UPGRADE {
      @Override
      public StateHttpEnum toCloseRead() { return UPGRADE_CLOSE_READ; }
    },
    
    UPGRADE_CLOSE_READ {
      @Override
      public StateHttpEnum toCloseWrite() { return CLOSE; }
      
      @Override
      public boolean isBodyComplete() { return true; }
    },
    
    CLOSE_READ {
      @Override
      public StateHttpEnum toCloseWrite() { return CLOSE; }
      
      @Override
      public StateHttpEnum toUpgrade() { return UPGRADE_CLOSE_READ; }
      
      
      @Override
      public boolean isBodyComplete() { return true; }
    },
    
    CLOSE_WRITE {
      @Override
      public StateHttpEnum toCloseRead() { return CLOSE; }

      @Override
      public boolean isCloseWrite() { return true; }
    },
    
    CLOSE {
      @Override
      public StateHttpEnum toCloseRead() { return this; }

      @Override
      public StateHttpEnum toCloseWrite() { return this; }
      
      @Override
      public boolean isBodyComplete() { return true; }

      @Override
      public boolean isCloseWrite() { return true; }

      @Override
      public StateHttpEnum toIdle() { return IDLE; }
    };

    public StateConnection service(RequestHttpState requestBaratineImpl)
    {
      throw new IllegalStateException(toString());
    }

    public StateHttpEnum toUpgrade()
    {
      throw new IllegalStateException(toString());
    }

    public StateHttpEnum toCloseRead()
    {
      return CLOSE_READ;
    }

    public StateHttpEnum toCloseWrite()
    {
      return CLOSE_WRITE;
    }
    
    public boolean isCloseWrite()
    {
      return false;
    }
    
    public boolean isBodyComplete()
    {
      return false;
    }
    
    public StateHttpEnum toBodyComplete()
    {
      return CLOSE_READ;
    }
    
    public StateHttpEnum toIdle()
    {
      System.out.println("BAD_IDLE: " + this);
      throw new IllegalStateException(toString());
    }
  }
}
