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

package com.caucho.v5.bartender.journal;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.bartender.websocket.ClientBartenderWebSocket;
import com.caucho.v5.db.journal.JournalStream;
import com.caucho.v5.io.IoUtil;

/**
 * The general low-level journal system.
 */
public class PeerJournalStream implements JournalStream
{
  private static final Logger log
    = Logger.getLogger(PeerJournalStream.class.getName());
  
  private ServerHeartbeat _peer;
  private String _name;
  private JournalClientEndpoint _endpoint;
  private OutputStream _os;
  
  PeerJournalStream(String name,
                    ServerHeartbeat peer)
  {
    _name = name;
    _peer = peer;
  }

  /**
   * Start a journalled message.
   */
  @Override
  public void start()
  {
    try {
      JournalClientEndpoint endpoint = connect();
      
      if (endpoint != null) {
        OutputStream os;
        
        _os = os = endpoint.startMessage();
        if (os != null) {
          os.write('M');
        }
      }
    } catch (Exception e) {
      log.finer(e.toString());
      //e.printStackTrace();
      // // throw new RuntimeException(e);
    }
  }
  
  /**
   * write the contents of a journal message.
   */
  @Override
  public void write(byte[] buffer, int offset, int length)
  {
    try {
      OutputStream os = _os;

      if (os != null) {
        os.write(buffer, offset, length);
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }

  /**
   * Complete a journaled message.
   */
  @Override
  public boolean complete()
  {
    OutputStream os = _os;
    _os = null;
    
    IoUtil.close(os);

    return true;
  }

  @Override
  public void flush()
  {
    JournalClientEndpoint endpoint = _endpoint;
    
    if (endpoint != null) {
      endpoint.flush();
    }
  }

  @Override
  public boolean isSaveRequired()
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean saveStart()
  {
    try {
      JournalClientEndpoint endpoint = connect();
      if (endpoint != null) {
        try (OutputStream os = endpoint.startMessage()) {
          os.write('B');
        }
      }
      
    } catch (Exception e) {
      log.fine(e.toString());
      
      if (log.isLoggable(Level.FINEST)) {
        log.log(Level.FINEST, e.toString(), e);
      }

      // throw new RuntimeException(e);
    }
    
    return false;
  }

  @Override
  public boolean saveEnd()
  {
    try {
      JournalClientEndpoint endpoint = connect();
      
      if (endpoint != null) {
        try (OutputStream os = endpoint.startMessage()) {
          os.write('E');
        }
      }
      
    } catch (Exception e) {
      log.fine(e.toString());
      
      if (log.isLoggable(Level.FINEST)) {
        log.log(Level.FINEST, e.toString(), e);
      }
      
      // throw new RuntimeException(e);
    }
    
    return false;
  }
  
  @Override
  public long getReplaySequence()
  {
    return 0;
  }
  
  @Override
  public void replay(ReplayCallback callback)
  {
  }

  @Override
  public void close()
  {
  }
  
  public JournalClientEndpoint connect()
    throws Exception
  {
    JournalClientEndpoint endpoint = _endpoint;
    
    if (endpoint != null && ! endpoint.isClosed()) {
      return endpoint;
    }
    
    _endpoint = null;
    
    if (! _peer.isUp()) {
      return null;
    }
    
    endpoint = new JournalClientEndpoint();
    
    // endpoint.setAuth(_user, _password);
    
    String uri = getURI();
    
    ClientBartenderWebSocket wsClient = new ClientBartenderWebSocket(uri, endpoint);
    // endpoint.setClient(wsClient);
    
    wsClient.connect();
    
    if (endpoint.isClosed()) {
      log.fine("journal connection failed: " + this + " " + uri);
      return null;
    }
    
    _endpoint = endpoint;
    
    try (OutputStream os = endpoint.startMessage()) {
      os.write('S');
      os.write(_name.getBytes());
    }
    
    return endpoint;
  }
  
  private String getURI()
  {
    return "bartender://" + _peer.getAddress() + ":" + _peer.getPortBartender() + "/journal";
  }

  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _name + "]";
  }
}
