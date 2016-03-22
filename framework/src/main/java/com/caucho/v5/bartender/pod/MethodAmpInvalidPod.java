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

package com.caucho.v5.bartender.pod;

import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionNotFound;

import java.util.logging.Logger;

import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.MethodAmpBase;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.http.pod.PodApp;
import com.caucho.v5.util.L10N;

/**
 * Sender for an actor ref.
 */
public final class MethodAmpInvalidPod extends MethodAmpBase
{
  private static final Logger log
    = Logger.getLogger(MethodAmpInvalidPod.class.getName());
  
  private static final L10N L = new L10N(MethodAmpInvalidPod.class);
  
  private final String _className;
  private final String _methodName;
  
  public MethodAmpInvalidPod(String className, String methodName)
  {
    _className = className;
    _methodName = methodName;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _methodName + "," + _className + "]";
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object[] args)
  {
    log.warning(errorMsg());
  }

  @Override
  public void query(HeadersAmp headers, 
                    Result<?> result,
                    StubAmp actor,
                    Object[] args)
  {
    
    ServiceExceptionNotFound exn = new ServiceExceptionNotFound(errorMsg());
    exn.fillInStackTrace();
    
    result.fail(exn);
    //System.out.println("QR-A: " + exn + " " + queryRef);
    //queryRef.complete("OK");
  }
  
  private String errorMsg()
  {
    String msg = L.l("{0} is not a service on the local pod.\n  {1}\n  {2}", 
                     this,
                     PodApp.getCurrent(),
                     BartenderSystem.getCurrentShard());
    
    return msg;
  }
}
