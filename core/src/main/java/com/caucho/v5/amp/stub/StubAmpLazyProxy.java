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

package com.caucho.v5.amp.stub;

import java.util.logging.Logger;

import com.caucho.v5.amp.service.ServiceRefLazy;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;

/**
 * Null actor
 */
public class StubAmpLazyProxy extends StubAmpBase implements StubAmp
{
  private static final L10N L = new L10N(StubAmpLazyProxy.class);
  private static final Logger log = Logger.getLogger(StubAmpLazyProxy.class.getName());
  
  private ServiceRefLazy _serviceRefLazy;
  private StubAmp _delegate;
  
  public StubAmpLazyProxy(ServiceRefLazy serviceRefLazy)
  {
    _serviceRefLazy = serviceRefLazy;
  }
  
  protected StubAmp getDelegate()
  {
    StubAmp delegate = _delegate;
  
    if (delegate == null) {
      StubAmp actor = _serviceRefLazy.stub();
      
      if (! actor.isClosed()) {
        _delegate = delegate = actor;
      }
    }
    
    return delegate;
  }
  
  @Override
  public boolean isUp()
  {
    StubAmp delegate = getDelegate();
    
    return delegate != null && delegate.isUp();
  }

  @Override
  public MethodAmp methodByName(String methodName)
  {
    StubAmp delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.methodByName(methodName);
    }
    else {
      System.out.println("EEP: " + methodName);
      return null;
    }
  }

  @Override
  public void queryReply(HeadersAmp headers,
                         StubAmp actor,
                         long qid, 
                         Object value)
  {
    log.warning(L.l("Unexpected queryReply to {0} with value {1}", actor, value));
  }

  @Override
  public void queryError(HeadersAmp headers,
                         StubAmp actor,
                         long qid, 
                         Throwable exn)
  {
    log.warning(L.l("Unexpected queryReply to {0} with exception {1}", actor, exn));
    exn.printStackTrace();
  }

  @Override
  public void beforeBatch()
  {
  }

  @Override
  public void afterBatch()
  {
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _serviceRefLazy.address() + "]";
  }

  @Override
  public boolean isJournalReplay()
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void queuePendingReplayMessage(MessageAmp msg)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void queuePendingMessage(MessageAmp msg)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void deliverPendingReplay(InboxAmp inbox)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void deliverPendingMessages(InboxAmp inbox)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void onLoad(Result<? super Boolean> result)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void afterBatchImpl()
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void onSaveChildren(SaveResult saveResult)
  {
    // TODO Auto-generated method stub
    
  }
}
