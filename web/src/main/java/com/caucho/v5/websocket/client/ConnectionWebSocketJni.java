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

package com.caucho.v5.websocket.client;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.network.port.PollControllerTcp;
import com.caucho.v5.network.port.PollTcpManager;
import com.caucho.v5.websocket.io.InWebSocket;

/**
 * WebSocketClient
 */
class ConnectionWebSocketJni 
  // extends ConnectionTcp 
  implements Runnable
{
  private final Logger log = Logger.getLogger(ConnectionWebSocketJni.class.getName());
  
  private WebSocketClientBaratine _client;
  private InWebSocket _socketConn;
  private SocketBar _qSocket;
  private PollTcpManager _selectManager;
  private ReadBuffer _is;
  private Executor _executor;
  private PollControllerTcp _keepalive;
  
  private long _idleExpireTime;
  
  ConnectionWebSocketJni(WebSocketClientBaratine client,
                         SocketBar qSocket,
                         PollTcpManager selectManager)
    throws IOException
  {
    //super(qSocket);
    
    _client = client;
    
    _qSocket = qSocket;
    
    //_is = _socketConn.getInputStream();
    
    _selectManager = selectManager;
//    _keepalive = new PollControllerTcp(this);
    
    _executor = ThreadPool.current().getThrottleExecutor();
  }
  
  void start()
  {
    if (keepalive()) {
      _executor.execute(this);
    }
  }
  
  //@Override
  public SocketBar socket()
  {
    return _qSocket;
  }
  
  @Override
  public void run()
  {
    boolean isValid = false;

    try {
      /*
      InWebSocket wsEndpointReader = _client.getEndpointReader();

      do {
        if (! wsEndpointReader.onRead()) {
          return;
        }
      } while (! _client.isClosed() && keepalive());
      
      isValid = _socketConn.getInputStream().canRead();
      */
    } catch (Exception e) {
      e.printStackTrace();
      log.log(Level.WARNING, e.toString(), e);
    } finally {
      if (! isValid) {
        _client.close();
      }
    }
  }
  
  private boolean keepalive()
  {
    try {
      /*
      InWebSocket inWs = _client.getEndpointReader();
      
      if (inWs.isReadAvailable()) {
        return true;
      }

      ReadStream is = _socketConn.getInputStream();

      if (is.available() > 0) {
        return true;
      }
      
      long timeout = _socketConn.getIdleReadTimeout();
      
      timeout = 600 * 1000;
      
      _idleExpireTime = timeout + CurrentTime.getCurrentTimeActual();

      return _selectManager.startKeepalive(_keepalive) != KeepaliveResult.START;
      */
    } catch (Exception e) {
      //_socketConn.disconnect();
      e.printStackTrace();
      
      throw new RuntimeException(e);
    }
    
    return true;
  }
  
  //@Override
  public long idleExpireTime()
  {
    return _idleExpireTime;
  }

  //@Override
  public void requestWakeKeepalive()
  {
    _executor.execute(this);
  }

  //@Override
  public void requestTimeout()
  {
    _client.close();
  }

  //@Override
  public void requestDestroy()
  {
    _client.close();
  }

  //@Override
  public void clientDisconnect()
  {
    _client.close();
  }
}
