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

import io.baratine.service.ResultStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.remote.OutAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.bartender.pod.PodRef;
import com.caucho.v5.ramp.hamp.OutHamp;

/**
 * Websocket endpoint for receiving hamp message
 */
class OutHampImpl implements OutAmp
{
  private static final Logger log
    = Logger.getLogger(OutHampImpl.class.getName());
  
  //private final RampChannelBroker _connBroker;
  // private final RampServiceRef _readService;
  
  private OutputStream _os;
  private OutHamp _hampWriter;
  
  protected OutHampImpl(OutputStream os)
  /*
                                RampReadBrokerFactory brokerFactory, 
                                String peerAddress)
                                */
  {
    _os = os;
    _hampWriter = new OutHamp();
    
    // _readService = readService;
    //_connBroker = brokerFactory.create(this);
  }
  
  /*
  @Override
  public RampChannelBroker getConnectionBroker()
  {
    return _connBroker;
  }
  */
  
  /*
  @Override
  public RampServiceRef getReadService()
  {
    return getReadBroker().getReadService();
  }
  */
  
  /*
  @Override
  public String getReadAddress()
  {
    return getConnectionBroker().getReadAddress();
  }
  */

  /*
  @Override
  public RampConnection getConnection(RampBrokerChannel broker)
  {
    return this;
  }
  */
  
  @Override
  public boolean isUp()
  {
    return _os != null;
  }

  @Override
  public void send(HeadersAmp headers,
                   String address, 
                   String methodName,
                   PodRef podCaller,
                   Object[] args)
  {
    if (log.isLoggable(Level.FINER)) {
      log.finer("hamp-send " + methodName + " {to:" + address + "," + headers + "}");
    }
    
    try {
      _os.write('h'); // marker for eof
      _hampWriter.send(_os, headers, address, methodName, podCaller, args);
      _hampWriter.flushBuffer();
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
    if (log.isLoggable(Level.FINER)) {
      log.finer("hamp-query " + methodName
                + " {to:" + address
                + ",from:" + fromAddress
                + "," + headers + "}");
    }
    
    try {
      _os.write('h'); // marker for eof
      _hampWriter.query(_os, headers,
                        fromAddress, id, 
                        address, methodName, podCaller,
                        args);
      _hampWriter.flushBuffer();
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
                     ResultStream<?> stream,
                     Object[] args)
  {
    if (log.isLoggable(Level.FINER)) {
      log.finer("hamp-stream " + methodName
                + " {to:" + address
                + ",from:" + fromAddress
                + "," + headers + "}");
    }
    
    try {
      _os.write('h'); // marker for eof
      _hampWriter.stream(_os, headers,
                        fromAddress, id, 
                        address, methodName, podCaller, 
                        stream, args);
      _hampWriter.flushBuffer();
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
    try {
      _os.write('h'); // marker for eof
      _hampWriter.queryResult(_os, headers, address, qId, result);
      _hampWriter.flushBuffer();
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
    try {
      _os.write('h'); // marker for eof
      _hampWriter.queryError(_os, headers, address, qId, exn);
      _hampWriter.flushBuffer();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void streamReply(HeadersAmp headers,
                          String address,
                          long qId, 
                          int sequence,
                          List<Object> values,
                          Throwable exn,
                          boolean isComplete)
  {
    /*
    try {
      _os.write('h'); // marker for eof
      _hampWriter.queryResult(_os, headers, address, qId, result);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    */
  }

  @Override
  public void streamCancel(HeadersAmp headers,
                           String address,
                           String addressFrom,
                           long qId)
  {
    System.out.println("CANCEL: " + this);
    /*
    try {
      _os.write('h'); // marker for eof
      _hampWriter.queryResult(_os, headers, address, qId, result);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    */
  }

  @Override
  public void flush()
  {
    try {
      if (_os != null) {
        _os.flush();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void close()
  {
    try {
      OutputStream os = _os;
      _os = null;

      if (os!= null) {
        os.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
  @Override
  public RampConnection getCurrentConnection()
  {
    return this;
  }
  */
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _os + "]";
  }
}
