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

package com.caucho.v5.amp.inbox;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.deliver.WorkerDeliver;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.util.L10N;

import io.baratine.service.ServiceExceptionNotFound;

/**
 * Mailbox for an actor
 */
public class InboxNull extends InboxBase
{
  private static final L10N L = new L10N(InboxNull.class);
  
  public static final InboxNull NULL = new InboxNull();
  
  private final String _address;
  private final ServiceRefAmp _serviceRef;

  private InboxNull()
  {
    super(null);
    
    _address = "null";
    _serviceRef = null;
  }

  public InboxNull(String address)
  {
    super(null);
    
    _address = address;
    _serviceRef = null;
  }

  public InboxNull(String address,
                     ServiceRefAmp serviceRef,
                     ServiceManagerAmp manager)
  {
    super(manager);
    
    _address = address;
    _serviceRef = serviceRef;
  }

  @Override
  public ServiceRefAmp serviceRef()
  {
    if (_serviceRef != null) {
      return _serviceRef;
    }
    else {
      throw new NullPointerException(toString());
    }
  }

  @Override
  public StubAmp stubDirect()
  {
    throw serviceNotFound();
  }

  @Override
  public boolean offer(MessageAmp message, long timeout)
  {
    throw serviceNotFound();
  }
  
  @Override
  public HeadersAmp createHeaders(HeadersAmp callerHeaders,
                                  ServiceRefAmp serviceRef,
                                  MethodAmp method)
  {
    throw serviceNotFound();
  }
  
  private ServiceExceptionNotFound serviceNotFound()
  {
    throw new ServiceExceptionNotFound(L.l("'{0}' is an unknown service in {1}", 
                                           _address,
                                           manager()));
  }
  
  @Override
  public boolean isClosed()
  {
    return true;
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _address + "]";
  }

  @Override
  public MessageAmp getMessage()
  {
    // TODO Auto-generated method stub
    return null;
  }
  
  @Override
  public WorkerDeliver<MessageAmp> worker()
  {
    return WorkerDeliver.createNull();
  }

  /*
  @Override
  public InboxAmp getInbox()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setInbox(InboxAmp inbox)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void setMessage(MessageAmp message)
  {
    // TODO Auto-generated method stub
    
  }
  */
}
