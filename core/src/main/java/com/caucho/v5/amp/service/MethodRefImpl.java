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

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.stub.MethodAmp;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class MethodRefImpl extends MethodRefBase
{
  private final ServiceRefAmp _serviceRef;
  private final MethodAmp _method;

  public MethodRefImpl(ServiceRefAmp serviceRef,
                       MethodAmp method)
  {
    _serviceRef = serviceRef;
    _method = method;
  }
  
  @Override
  public final String getName()
  {
    return _method.name();
  }
  
  public final ServiceRefAmp getService()
  {
    return _serviceRef;
  }
  
  @Override
  public final boolean isClosed()
  {
    return _method.isClosed();
  }
  
  @Override
  public final InboxAmp getInbox()
  {
    return getService().inbox();
  }
  
  @Override
  public MethodAmp method()
  {
    return _method;
  }
  
  @Override
  public void offer(MessageAmp message)
  {
    getInbox().offer(message, InboxAmp.TIMEOUT_INFINITY);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _method + "]";
  }
}
