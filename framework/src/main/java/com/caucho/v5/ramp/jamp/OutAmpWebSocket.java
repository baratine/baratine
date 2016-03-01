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

package com.caucho.v5.ramp.jamp;

import java.io.IOException;
import java.util.List;

import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.bartender.pod.PodRef;

import io.baratine.web.WebSocket;

/**
 * Stream writer for jamp/hamp.
 */
public interface OutAmpWebSocket
{
  void query(WebSocket session, 
             HeadersAmp headers, 
             String fromAddress,
             long qid, 
             String address, 
             String methodName,
             PodRef podCaller,
             Object[] args)
    throws IOException;

  void reply(WebSocket session, 
             HeadersAmp headers, 
             String address, 
             long qid,
             Object result)
    throws IOException;

  void queryError(WebSocket session, 
                  HeadersAmp headers, 
                  String address,
                  long qid, 
                  Throwable exn)
                      throws IOException;

  void send(WebSocket session, 
            HeadersAmp headers, 
            String address,
            String methodName,
            PodRef podCaller,
            Object[] args)
    throws IOException;

  void streamReply(WebSocket session, 
                   HeadersAmp headers,
                   String address,
                   long qid,
                   int sequence,
                   List<Object> results,
                   Throwable exn,
                   boolean isComplete)
    throws IOException;
}
