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

package com.caucho.v5.amp.message;

import java.util.Objects;

import com.caucho.v5.amp.outbox.WorkerOutbox;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

/**
 * Message to shut down an instance.
 */
public class MessageWrapperClassLoader implements MessageAmp
{
  private final MessageAmp _delegate;
  private final ClassLoader _loader;
  
  public MessageWrapperClassLoader(MessageAmp delegate,
                                   ClassLoader loader)
  {
    Objects.requireNonNull(delegate);
    Objects.requireNonNull(loader);
    
    _delegate = delegate;
    _loader = loader;
  }
  
  private final MessageAmp getDelegate()
  {
    return _delegate;
  }

  @Override
  public void offerQueue(long timeout)
  {
    inboxTarget().offer(this, timeout);
  }

  @Override
  public void offer(long timeout)
  {
    inboxTarget().offer(this, timeout);
  }

  @Override
  public WorkerOutbox worker()
  {
    return getDelegate().worker();
  }

  /*
  @Override
  public Type getType()
  {
    return getDelegate().getType();
  }
  */

  @Override
  public void invoke(InboxAmp inbox, ActorAmp actor)
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(_loader);
      
      getDelegate().invoke(inbox, actor);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  @Override
  public void fail(Throwable exn)
  {
    getDelegate().fail(exn);
  }

  @Override
  public InboxAmp inboxTarget()
  {
    return getDelegate().inboxTarget();
  }

  /*
  @Override
  public InboxAmp getInboxContext()
  {
    return getDelegate().getInboxContext();
  }
  */

  @Override
  public OutboxAmp getOutboxCaller()
  {
    return getDelegate().getOutboxCaller();
  }

  @Override
  public HeadersAmp getHeaders()
  {
    return getDelegate().getHeaders();
  }
}
