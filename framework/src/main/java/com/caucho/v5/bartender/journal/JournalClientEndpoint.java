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

import com.caucho.v5.util.L10N;

import io.baratine.web.WebSocket;

/**
 * Websocket endpoint for receiving hamp message
 */
class JournalClientEndpoint
{
  private static final L10N L = new L10N(JournalClientEndpoint.class);
  
  private WebSocket _session;
  
  public JournalClientEndpoint()
  {
  }

  public boolean isClosed()
  {
    return _session == null;
  }

  /*
  @OnOpen
  public void onOpen(Session session, EndpointConfig config)
  {
    _session = session;
  }

  @OnMessage
  public void onMessage(InputStream is)
  {
    System.out.println("MSG: " + is);
  }
 
  @OnClose
  public void onClose()
    throws IOException
  {
    Session session = _session;
    _session = null;
    
    if (session != null) {
      session.close();
    }
  }
  */

  public OutputStream startMessage()
    throws IOException
  {
    if (true) throw new UnsupportedOperationException();
    /*
    Session session = _session;
    
    if (session != null) {
      Basic basic = session.getBasicRemote();
      
      if (basic != null) {
        return basic.getSendStream();
      }
    }
    else {
      throw new IllegalStateException(L.l("journal connection is not active"));
    }

    return null;
    */
    
    return null;
  }

  public void flush()
  {
    if (true) throw new UnsupportedOperationException();
    /*
    Session session = _session;
    
    if (session != null) {
      try {
        Basic remote = session.getBasicRemote();
        
        if (remote != null) {
          remote.flushBatch();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    */
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _session + "]";
  }
}
