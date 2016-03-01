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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.remote.ChannelServerFactory;
import com.caucho.v5.amp.remote.OutAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.pod.PodRef;
import com.caucho.v5.ramp.hamp.InHamp;
import com.caucho.v5.ramp.hamp.OutHamp;
import com.caucho.v5.websocket.io.CloseReason.CloseCode;

import io.baratine.io.Buffer;
import io.baratine.service.ResultStream;
import io.baratine.web.ServiceWebSocket;
import io.baratine.web.WebSocket;

/**
 * Hamp websocket endpoint for sending and receiving hamp message
 */
public class HampService
  implements OutAmp, ServiceWebSocket<InputStream,Buffer>
{
  private static final Logger log
    = Logger.getLogger(HampService.class.getName());
  
  private final Level _logLevel = Level.FINEST;
  
  private ServiceManagerAmp _manager;
  private ChannelAmp _channel;
  private WebSocket _webSocket;
  //private RemoteEndpoint.Basic _remote;
  private InHamp _inHamp;
  private OutHamp _outHamp;

  private ServiceRefAmp _serviceRefOut;

  private ServiceRefAmp _callerRef;

  private ChannelServerFactory _channelFactory;
  
  public HampService()
  {
  }

  protected HampService(ServiceManagerAmp manager,
                                  ChannelAmp channel)
  {
    _manager = manager;

    _channel = channel;
    
    _inHamp = new InHamp(manager, _channel);
    _outHamp = new OutHamp();
    
    _serviceRefOut = _channel.getServiceRefOut();
    _callerRef = _channel.getCallerRef();
  }
  
  public HampService(ServiceManagerAmp ampManager,
                               ChannelServerFactory channelFactory)
  {
    _manager = ampManager;
    _channelFactory = channelFactory;
  }

  protected ServiceRefAmp getServiceRefOut()
  {
    return _serviceRefOut;
  }
  
  protected ServiceRefAmp getCallerRef()
  {
    return _callerRef;
  }
  
  protected ServiceManagerAmp getRampManager()
  {
    return _manager;
  }
  
  @Override
  public void open(WebSocket session)
    //throws IOException
  {
    /*
    if (config instanceof EndpointHampConfig) {
      EndpointHampConfig hampConfig = (EndpointHampConfig) config;
      
      _manager = hampConfig.getAmpManager();
      
      _channel = hampConfig.getChannelFactory().create(this);

      _inHamp = new InHamp(_manager, _channel);
      _outHamp = new OutHamp();
    }
    */
    
    _channel = _channelFactory.create(this);
    
    _webSocket = session;
    
    _inHamp = new InHamp(_manager, _channel);
    _outHamp = new OutHamp();
    //_remote = _session.getBasicRemote();
    
    //_remote.setBatchingAllowed(true);
    
    if (log.isLoggable(_logLevel)) {
      log.log(_logLevel, "HAMP @OnOpen " + session + " " + _channel);
    }
    
    //return true;
  }

  @Override
  public void next(InputStream is, WebSocket ws)
    throws IOException
  {
    _manager.addRemoteMessageRead();

    try {
      OutboxAmp outbox = OutboxAmp.current();
      
      Objects.requireNonNull(outbox);
      
      _inHamp.readMessage(is, outbox);
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  @Override
  public void ping(String s, WebSocket ws)
    throws IOException
  {
    ws.pong(s);
  }
  
  //@OnClose
  public void onClose(CloseCode reason)
    throws IOException
  {
    WebSocket session = _webSocket;

    ChannelAmp channel = _channel;

    if (channel != null) {
      channel.shutdown(ShutdownModeAmp.GRACEFUL);
    }
    
    if (session != null) {
      session.close();
    }
    
    if (log.isLoggable(_logLevel)) {
      log.log(_logLevel, "HAMP @OnClose " + session + " " + reason + " " + _channel);
    }
  }
   
  @Override
  public boolean isUp()
  {
    WebSocket session = _webSocket;
    
    return session != null && session.isClosed();
  }

  @Override
  public void send(HeadersAmp headers,
                   String address, 
                   String methodName,
                   PodRef podCaller,
                   Object[] args)
  {
    _manager.addRemoteMessageWrite();
    
    if (log.isLoggable(_logLevel)) {
      log.log(_logLevel, "hamp-send-w " + methodName + " {to:" + address + "," + headers + "}");
    }
    
    try (OutputStream os = getSendStream()) {
      _outHamp.send(os, headers, address, methodName, podCaller, args);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void query(HeadersAmp headers,
                    String fromAddress, 
                    long id, 
                    String address, 
                    String methodName,
                    PodRef podCaller,
                    Object[] args)
  {
    _manager.addRemoteMessageWrite();
    
    if (log.isLoggable(_logLevel)) {
      log.log(_logLevel, "hamp-query-w " + methodName
                + " {to:" + address + "," + headers 
                + ",from=" + fromAddress + ",qid=" + id + "}");
    }

    try (OutputStream os = getSendStream()) {
      _outHamp.query(os, headers,
                        fromAddress, id, 
                        address, methodName, podCaller,
                        args);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stream(HeadersAmp headers,
                     String fromAddress, 
                     long id, 
                     String address, 
                     String methodName,
                     PodRef podCaller,
                     ResultStream<?> result,
                     Object[] args)
  {
    _manager.addRemoteMessageWrite();
    
    if (log.isLoggable(_logLevel)) {
      log.log(_logLevel, "hamp-stream" + methodName
                + " {to:" + address + "," + headers 
                + ",from=" + fromAddress + ",qid=" + id + "}");
    }
    
    try (OutputStream os = getSendStream()) {
      _outHamp.stream(os, headers,
                        fromAddress, id, 
                        address, methodName, podCaller, 
                        result, args);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void reply(HeadersAmp headers,
                    String address,
                    long qId, 
                    Object result)
  {
    _manager.addRemoteMessageWrite();
    
    try (OutputStream os = getSendStream()) {
      _outHamp.queryResult(os, headers, address, qId, result);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void queryError(HeadersAmp headers,
                         String address, 
                         long qId,
                         Throwable exn)
  {
    _manager.addRemoteMessageWrite();
    
    try (OutputStream os = getSendStream()) {
      _outHamp.queryError(os, headers, address, qId, exn);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void streamReply(HeadersAmp headers,
                          String address,
                          long qId, 
                          int sequence,
                          List<Object> results,
                          Throwable exn,
                          boolean isComplete)
  {
    _manager.addRemoteMessageWrite();
    
    try (OutputStream os = getSendStream()) {
      _outHamp.streamResult(os, headers, address, qId, sequence, results, exn, isComplete);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void streamCancel(HeadersAmp headers,
                           String address,
                           String addressFrom,
                           long qId)
  {
    _manager.addRemoteMessageWrite();
    
    try (OutputStream os = getSendStream()) {
      _outHamp.streamCancel(os, headers, address, addressFrom, qId);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public OutputStream getSendStream()
    throws IOException
  {
    return _webSocket.outputStream();
  }
  
  @Override
  public void flush()
  {
    try {
      WebSocket webSocket = _webSocket;

      if (webSocket!= null) {
        webSocket.flush();
      }
    } catch (Exception e) {
      log.log(Level.FINEST, e.toString(), e);
    }
  }

  @Override
  public void close()
  {
    try {
      WebSocket webSocket = _webSocket;

      if (webSocket != null) {
        webSocket.close();
      }
    } catch (Exception e) {
      log.log(Level.FINEST, e.toString(), e);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _webSocket + "]";
  }
}
