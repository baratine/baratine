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

package com.caucho.v5.amp.service;

import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionNotFound;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.inbox.InboxNull;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.StubAmpNull;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.MethodAmpBase;
import com.caucho.v5.util.L10N;

/**
 * Sender for an actor ref.
 */
public class MethodRefNull extends MethodRefBase
{
  private static final L10N L = new L10N(MethodRefNull.class);
  
  private final ServiceRefAmp _serviceRef;
  private final String _methodName;
  
  public MethodRefNull(ServiceRefAmp serviceRef, String methodName)
  {
    _serviceRef = serviceRef;
    _methodName = methodName;
  }
  
  @Override
  public boolean isClosed()
  {
    return true;
  }
  
  @Override
  public String getName()
  {
    return _methodName;
  }
  
  @Override
  public ServiceRefAmp getService()
  {
    return _serviceRef;
  }
  
  @Override
  public void offer(MessageAmp message)
  {
    message.invoke(null, new StubAmpNull(_serviceRef.address()));
  }
  
  @Override
  public MethodAmp method()
  {
    return new NullMethod();
  }
  
  @Override
  public InboxAmp getInbox()
  {
    return new InboxNull(_serviceRef.address(), 
                           _serviceRef,
                           _serviceRef.manager());
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _methodName + "," + _serviceRef + "]";
  }
  
  class NullMethod extends MethodAmpBase {
    @Override
    public void query(HeadersAmp headers,
                      Result<?> result,
                      StubAmp actor,
                      Object []args)
    {
      result.fail(new ServiceExceptionNotFound(
                                   L.l("{0} is an unknown service", 
                                       _serviceRef)));
    }

  }
}
