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

package com.caucho.v5.amp.remote;

import java.util.List;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.bartender.pod.PodRef;

import io.baratine.stream.ResultStream;

/**
 * Output to a remote connection for amp messages.
 */
public interface OutAmp
{
  /**
   * True if the connection is active.
   */
  boolean isUp();
  
  void send(HeadersAmp headers,
            String address, 
            String methodName,
            PodRef podCaller,
            Object[] args);

  void query(HeadersAmp headers,
             String fromAddress, 
             long qid, 
             String address, 
             String methodName,
             PodRef podCaller,
             Object[] args);

  void reply(HeadersAmp headers,
             String address,
             long qid, 
             Object result);

  void queryError(HeadersAmp headers,
                  String address, 
                  long qid,
                  Throwable exn);

  void stream(HeadersAmp headers,
              String fromAddress, 
              long qid, 
              String address, 
              String methodName,
              PodRef podCaller,
              ResultStream<?> result,
              Object[] args);

  void streamReply(HeadersAmp headers, 
                   String remoteAddress, 
                   long id,
                   int sequence,
                   List<Object> values,
                   Throwable exn,
                   boolean isComplete);

  void streamCancel(HeadersAmp headers, 
                    String remoteAddress,
                    String addressFrom, 
                    long qid);

  void flush();

  void close();

  default ServiceRefAmp createServiceRef(ServicesAmp manager,
                                         String address,
                                         ServiceRefAmp callerRef)
  {
    String addressRemote = address;
    String addressSelf = address;
    
    StubAmpOut actorOut
      = new StubAmpOutServer(manager, this, addressRemote, callerRef);
    
    return actorOut.getServiceRef();
  }
}
