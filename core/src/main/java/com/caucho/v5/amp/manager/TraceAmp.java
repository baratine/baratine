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

package com.caucho.v5.amp.manager;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp.Trace;
import com.caucho.v5.amp.message.TraceMessage;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.util.Base64Util;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.RandomUtil;

/**
 * trace implementation
 */
public class TraceAmp implements Trace
{
  private static final Logger log = Logger.getLogger(TraceAmp.class.getName());
  
  //private AmpManager _manager;
  
  private OutboxAmp _outbox;

  private MessageAmp _message;
  
  TraceAmp(ServicesAmpImpl manager)
  {
    //_manager = manager;
    
    OutboxAmp outboxAmp = OutboxAmp.current();
    
    if (outboxAmp != null) {
      _outbox = outboxAmp;
      
      _message = _outbox.message();
      
      HeadersAmp headers = _message.getHeaders();
      
      StringBuilder sb = new StringBuilder();
      
      long now = CurrentTime.currentTime();
      
      Base64Util.encode(sb, now);
      
      long id = RandomUtil.getRandomLong();
      
      Base64Util.encode(sb, id);
      
      String traceId = sb.toString();
      
      headers = headers.add("trace.id", traceId);
      
      TraceMessage traceMessage = new TraceMessage(traceId, headers, _message);
     
      _outbox.message(traceMessage);
    }
  }
  
  public static void begin()
  {
    OutboxAmp outbox = OutboxAmp.current();
    
    if (outbox == null) {
      return;
    }
    
    // OutboxAmp outbox = (OutboxAmp) outboxDeliver;

    MessageAmp message = outbox.message();

    HeadersAmp headers = message.getHeaders();

    StringBuilder sb = new StringBuilder();

    long now = CurrentTime.currentTime();

    Base64Util.encode(sb, now);

    long idRand = RandomUtil.getRandomLong();

    Base64Util.encode(sb, idRand);

    String id = sb.toString();
    
    headers = headers.add("trace.id",  id);

    TraceMessage traceMessage = new TraceMessage(id, headers, message);

    outbox.message(traceMessage);
  }
  
  public static boolean isTrace()
  {
    OutboxAmp outbox = OutboxAmp.current();
    
    if (outbox == null) {
      return false;
    }
    
    // OutboxAmp outbox = (OutboxAmp) outboxDeliver;

    MessageAmp message = outbox.message();

    HeadersAmp headers = message.getHeaders();

    return headers.get("trace.id") != null;
  }
  
  public static String getTraceId()
  {
    OutboxAmp outbox = OutboxAmp.current();
    
    if (outbox == null) {
      return null;
    }
    
    // OutboxAmp outbox = (OutboxAmp) outboxDeliver;

    MessageAmp message = outbox.message();
    
    HeadersAmp headers = message.getHeaders();
    
    return String.valueOf(headers.get("trace.id"));
  }
  
  public static void end()
  {
    OutboxAmp outbox = OutboxAmp.current();
    
    if (outbox == null) {
      return;
    }
    
    // OutboxAmp outbox = (OutboxAmp) outboxDeliver;

    MessageAmp message = outbox.message();
    
    if (message instanceof TraceMessage) {
      TraceMessage trace = (TraceMessage) message;
      
      MessageAmp prev = trace.getPrev();
      
      outbox.message(prev);
    }
  }
  
  public static void deliverBreakpoint(String traceId,
                                       String address,
                                       String methodName)
  {
    if (log.isLoggable(Level.FINER)) {
      log.finer("message deliver {{" + traceId + "}} " + address + " " + methodName);
    }
  }
  
  @Override
  public void close()
  {
    if (_outbox != null) {
      _outbox.message(_message);
    }
  }
}
