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
import java.io.InputStream;
import java.util.logging.Logger;

import com.caucho.v5.db.journal.JournalStream;
import com.caucho.v5.http.websocket.WebSocketSession;
import com.caucho.v5.io.TempBuffer;

import io.baratine.io.Bytes;
import io.baratine.web.ServiceWebSocket;
import io.baratine.web.WebSocket;

/**
 * Websocket endpoint for receiving hamp message
 */
class JournalServerEndpoint
  implements ServiceWebSocket<InputStream,Bytes>
{
  private static final Logger log
    = Logger.getLogger(JournalServerEndpoint.class.getName());
  
  private WebSocketSession _session;
  private JournalStream _jOut;

  private JournalFactory _journalConfig;
  
  public JournalServerEndpoint(JournalFactory config)
  {
    _journalConfig = config;
  }
  
  @Override
  public void next(InputStream is, WebSocket ws)
    throws IOException
  {
    int code = is.read();
    
    switch (code) {
    case 'C':
      break;
      
    case 'S': {
      StringBuilder sb = new StringBuilder();
      int ch;
      
      while ((ch = is.read()) >= 0) {
        sb.append((char) ch);
      }
      
      _jOut = _journalConfig.openJournal(sb.toString());
      break;
    }
      
    case 'M':
      readJournalEntry(is);
      break;
      
    case 'B':
      checkpointStart();
      break;
      
    case 'E':
      checkpointEnd();
      break;
      
    default:
      System.out.println("UNKNOWN CODE: " + (char) code + " " + code);
      break;
    }
  }
  
  private void readJournalEntry(InputStream is)
    throws IOException
  {
    _jOut.start();

    TempBuffer tBuf = TempBuffer.create();
    byte []buffer = tBuf.buffer();
    int len;
    
    while ((len = is.read(buffer, 0, buffer.length)) >= 0) {
      _jOut.write(buffer, 0, len);
    }
    
    _jOut.complete();
    
    TempBuffer.free(tBuf);

    _jOut.flush();
  }
  
  private void checkpointStart()
    throws IOException
  {
    _jOut.saveStart();

    _jOut.flush();
  }
  
  private void checkpointEnd()
    throws IOException
  {
    _jOut.saveEnd();

    _jOut.flush();
  }

  public void onClose()
    throws IOException
  {
    WebSocketSession session = _session;
    
    if (session != null) {
      session.close();
    }
    
    JournalStream jOut = _jOut;
    _jOut = null;
    
    if (jOut != null) {
      jOut.close();
    }
  }

  @Override
  public void open(WebSocket webSocket)
  {
    System.out.println("OPEN-JOURNAL: " + webSocket);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _session + "]";
  }
}
