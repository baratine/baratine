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

import java.lang.reflect.AnnotatedType;
import java.util.List;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.LoadStateAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

import io.baratine.service.Result;

/**
 * amp disruptor method
 */
public class StubAmpMultiWorker extends StubAmpStateBase
{
  private final StubAmp _delegate;
  
  public StubAmpMultiWorker(StubAmp delegate)
  {
    _delegate = delegate;
  }
  
  private StubAmp delegate()
  {
    return _delegate;
  }
  
  @Override
  public LoadStateAmp createLoadState()
  {
    //return new LoadStateMultiWorker();
    return LoadStateActorAmp.NEW;
  }
  
  @Override
  public String name()
  {
    return delegate().name();
  }
  
  @Override
  public AnnotatedType api()
  {
    return delegate().api();
  }
  
  @Override
  public boolean isPublic()
  {
    return delegate().isPublic();
  }
  
  @Override
  public Object bean()
  {
    return delegate().bean();
  }
  
  @Override
  public Object onLookup(String path, ServiceRefAmp parentRef)
  {
    return null;
  }
  
  @Override
  public MethodAmp []getMethods()
  {
    return delegate().getMethods();
  }
  
  @Override
  public MethodAmp methodByName(String name)
  {
    return delegate().methodByName(name);
  }
  
  @Override
  public StubAmp worker(StubAmp actor)
  {
    // baratine/1072
    return delegate();
  }
  
  @Override
  public void onInit(Result<? super Boolean> result)
  {
    delegate().onInit(result);
  }

  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
    delegate().onShutdown(mode);
  }

  @Override
  public void beforeBatch()
  {
    delegate().beforeBatch();
  }
  
  @Override
  public void afterBatch()
  {
    delegate().afterBatch();
  }
  
  @Override
  public LoadStateAmp load(StubAmp actorMessage, MessageAmp msg)
  {
    return delegate().load(msg);
  }
  
  @Override
  public void queryReply(HeadersAmp headers, 
                         StubAmp actor,
                         long qid, 
                         Object value)
  {
    delegate().queryReply(headers, actor, qid, value);
  }
  
  @Override
  public void queryError(HeadersAmp headers, 
                         StubAmp actor,
                         long qid, 
                         Throwable exn)
  {
    delegate().queryError(headers, actor, qid, exn);
  }
  
  @Override
  public void streamReply(HeadersAmp headers, 
                          StubAmp actor,
                          long qid, 
                          int sequence,
                          List<Object> values,
                          Throwable exn,
                          boolean isComplete)
  {
    delegate().streamReply(headers, actor, qid, sequence,
                              values, exn, isComplete);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + delegate() + "]";
  }
}
