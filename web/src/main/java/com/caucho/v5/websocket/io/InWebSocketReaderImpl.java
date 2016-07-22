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

package com.caucho.v5.websocket.io;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.ReadStream;
import com.caucho.v5.util.ModulePrivate;
import com.caucho.v5.websocket.io.CloseReason.CloseCodes;

/**
 * User facade for http requests.
 */
@ModulePrivate
public class InWebSocketReaderImpl
  implements InWebSocket
{
  private static final Logger log
    = Logger.getLogger(InWebSocketReaderImpl.class.getName());
  
  private final FrameIn _inFrame;

  private FrameListener _session;
  private int _op;

  private boolean _isClosed;
  
  public InWebSocketReaderImpl(FrameIn is)
  {
    Objects.requireNonNull(is);
    
    _inFrame = is;
  }
  
  //
  // duplex callbacks
  //

  public void init(ReadStream is)
    throws IOException
  {
    Objects.requireNonNull(is);

    _inFrame.init(_session, is);
  }
  
  @Override
  public boolean isReadAvailable()
  {
    try {
      return _inFrame.available() > 0;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean onRead()
    throws IOException
  {
    boolean isValid = false;
    
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      //thread.setContextClassLoader(_session.getClassLoader());
      
      //_session.beforeBatch();
      
      do {
        if (_isClosed || ! readFrame()) {
          _isClosed = true;
          return false;
        }
        
        isValid = true;
      } while (_inFrame.available() > 0);
    } catch (IOException e) {
      if (_isClosed) {
        log.log(Level.FINEST, e.toString(), e);
        return false;
      }
      else {
        throw e;
      }
    } finally {
      //_session.afterBatch();
      
      thread.setContextClassLoader(oldLoader);
    }
    
    return isValid;
  }
  
  private boolean readFrame()
    throws IOException
  {  
    if (! _inFrame.readFrameHeader()) {
      // disconnect();
      
      return false;
    }

    int opcode = _inFrame.getOpcode();

    switch (opcode) {
    case WebSocketConstants.OP_BINARY:
      //_session.getBinaryHandler().onRead(_inFrame);
      _op = opcode;
      break;

    case WebSocketConstants.OP_TEXT:
      //_session.getTextHandler().onRead(_inFrame);
      _op = opcode;
      break;
      
    case WebSocketConstants.OP_CONT:
      if (_op == WebSocketConstants.OP_BINARY) {
        //_session.getBinaryHandler().onRead(_inFrame);
      }
      else if (_op == WebSocketConstants.OP_TEXT) {
        //_session.getTextHandler().onRead(_inFrame);
      }
      else {
        log.fine(this + " unexpected opcode " + opcode);

        CloseReason reason
          = new CloseReason(CloseCodes.PROTOCOL_ERROR, "unexpected opcode");
          
        _session.onClose(reason);
        //_session.close(reason);
      }
      break;
      
    case -1:
    {
      log.fine(this + " unexpected disconnect");

      CloseReason reason
        = new CloseReason(CloseCodes.GOING_AWAY, "disconnect");
        
      _session.onClose(reason);
      //_session.close(reason);
      return false;
    }

    default:
    {
      log.fine(this + " unexpected opcode " + opcode);

      CloseReason reason
        = new CloseReason(CloseCodes.PROTOCOL_ERROR, "unexpected opcode");
        
      _session.onClose(reason);
      //_session.close(reason);
      return false;
    }
    }
    
    if (_inFrame.isFinal()) {
      _op = -1;
    }
    
    return true;
  }

  public void onReadTimeout()
  {
    // _listener.onTimeout(this);
  }

  public void onDisconnectRead()
  {
    CloseReason reason = new CloseReason(CloseCodes.CLOSED_ABNORMALLY, "disconnect");
    
    //_session.writeDisconnect();
    _session.onClose(reason);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
