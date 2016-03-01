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

import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.network.port.PollControllerTcp;
import com.caucho.v5.network.port.PollTcpManagerBase;
import com.caucho.v5.network.port.PollTcpManagerNio;
import com.caucho.v5.websocket.io.InWebSocketConnection;

/**
 * WebSocketClient
 */
class ConnectionWebSocketNio
  //extends ConnectionTcpBase
  implements Runnable
{
  private final Logger log = Logger.getLogger(ConnectionWebSocketNio.class.getName());
  
  private WebSocketClientBaratine _client;
  private InWebSocketConnection _socketConn;
  private Socket _socket;
  private SocketBar _qSocket;
  private PollTcpManagerBase _selectManager;
  private ReadBuffer _is;
  private Executor _executor;
  
  private long _idleExpireTime;

  private PollControllerTcp _keepalive;
  
  ConnectionWebSocketNio(WebSocketClientBaratine client, SocketBar socket)
  {
    //super(socket);
    
    _client = client;
    //_socketConn = (EndpointConnectionSocket) client.getSocketConnection();
    
    _socket = _socketConn.getSocket();
    _qSocket = socket;
    
    //_is = _socketConn.getInputStream();
    
    _selectManager = PollTcpManagerNio.create();
    _executor = ThreadPool.current().getThrottleExecutor();
    
    _keepalive = null;//new PollControllerTcp(this);
  }
  
  void start()
  {
    registerKeepalive();
  }
  
  void registerKeepalive()
  {
    try {
      //_idleExpireTime = _socketConn.getIdleReadTimeout() + CurrentTime.getCurrentTimeActual();

      _socket.getChannel().configureBlocking(false);
      _selectManager.startPoll(_keepalive);
    } catch (Exception e) {
      //_socketConn.disconnect();
      e.printStackTrace();
    }
    
  }
  
  /*
  @Override
  public QSocket socket()
  {
    return _qSocket;
  }
  */
  
  @Override
  public void run()
  {
    boolean isValid = false;
      
    try {
      _socket.getChannel().configureBlocking(true);
      /*
      EndpointReaderWebSocket wsEndpointReader = _client.getEndpointReader();
      
      do {
        if (! wsEndpointReader.onRead()) {
          return;
        }
      } while (wsEndpointReader.isReadAvailable() && ! _client.isClosed());
      
      isValid = ! _client.isClosed();
      
      if (isValid) {
        registerKeepalive();
      }
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
  
  /*
  @Override
  public long idleExpireTime()
  {
    return _idleExpireTime;
  }

  @Override
  public void requestWakeKeepalive()
  {
    _executor.execute(this);
  }

  @Override
  public void requestTimeout()
  {
  }

  @Override
  public void requestDestroy()
  {
  }

  @Override
  public void clientDisconnect()
  {
  }

  @Override
  public int getId()
  {
    return 0;
  }

  @Override
  public InetAddress getLocalAddress()
  {
    return null;
  }

  @Override
  public int getLocalPort()
  {
    return 0;
  }

  @Override
  public InetAddress getRemoteAddress()
  {
    return null;
  }

  @Override
  public int getRemotePort()
  {
    return 0;
  }
  */
}
