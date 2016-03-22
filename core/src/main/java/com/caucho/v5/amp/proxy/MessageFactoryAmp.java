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

package com.caucho.v5.amp.proxy;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.stub.MethodAmp;

import io.baratine.io.ResultPipeIn;
import io.baratine.io.ResultPipeOut;
import io.baratine.service.Result;
import io.baratine.stream.ResultStream;

/**
 * Factory for proxy message
 */
public interface MessageFactoryAmp
{
  void send(ServiceRefAmp serviceRef,
            MethodAmp method);
  
  void send(ServiceRefAmp serviceRef,
            MethodAmp method,
            Object arg);
  
  void send(ServiceRefAmp serviceRef,
            MethodAmp method,
            Object []args);
  
  <V> void queryResult(Result<V> result,
                       long timeout,
                       ServiceRefAmp serviceRef,
                       MethodAmp method);
  
  <V> void queryResult(Result<V> result,
                       long timeout,
                       ServiceRefAmp serviceRef,
                       MethodAmp method,
                       Object arg1);
  
  <V> void queryResult(Result<V> result,
                       long timeout,
                       ServiceRefAmp serviceRef,
                       MethodAmp method,
                       Object []args);
  
  <V> V queryFuture(long timeout,
                    ServiceRefAmp serviceRef,
                    MethodAmp method,
                    Object []args);
  
  <V> V queryFuture(ServiceRefAmp serviceRef,
                    MethodAmp method,
                    Object []args);

  <V> void streamResult(ResultStream<V> result,
                    long timeout,
                    ServiceRefAmp serviceRef,
                    MethodAmp method,
                    Object []args);

  <V> void resultOutPipe(ResultPipeOut<V> result,
                       long timeout,
                       ServiceRefAmp serviceRef,
                       MethodAmp method,
                       Object []args);

  <V> void resultInPipe(ResultPipeIn<V> result,
                       long timeout,
                       ServiceRefAmp serviceRef,
                       MethodAmp method,
                       Object []args);
}
