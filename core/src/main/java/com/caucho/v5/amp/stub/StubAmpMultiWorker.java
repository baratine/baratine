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

import io.baratine.service.Result;

import java.util.List;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.LoadState;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

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
  
  private StubAmp getDelegate()
  {
    return _delegate;
  }
  
  @Override
  public LoadState createLoadState()
  {
    //return new LoadStateMultiWorker();
    return LoadStateActorAmp.NEW;
  }
  
  @Override
  public String name()
  {
    return getDelegate().name();
  }
  
  @Override
  public Class<?> getApiClass()
  {
    return getDelegate().getApiClass();
  }
  
  @Override
  public boolean isExported()
  {
    return getDelegate().isExported();
  }
  
  @Override
  public Object bean()
  {
    return getDelegate().bean();
  }
  
  @Override
  public Object onLookup(String path, ServiceRefAmp parentRef)
  {
    return null;
  }
  
  @Override
  public MethodAmp []getMethods()
  {
    return getDelegate().getMethods();
  }
  
  @Override
  public MethodAmp getMethod(String name)
  {
    return getDelegate().getMethod(name);
  }
  
  @Override
  public StubAmp worker(StubAmp actor)
  {
    // baratine/1072
    return getDelegate();
  }
  
  @Override
  public void onInit(Result<? super Boolean> result)
  {
    getDelegate().onInit(result);
  }

  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
    getDelegate().onShutdown(mode);
  }

  @Override
  public void beforeBatch()
  {
    getDelegate().beforeBatch();
  }
  
  @Override
  public void afterBatch()
  {
    getDelegate().afterBatch();
  }
  
  @Override
  public LoadState load(StubAmp actorMessage, MessageAmp msg)
  {
    return getDelegate().load(msg);
  }
  
  @Override
  public void queryReply(HeadersAmp headers, 
                         StubAmp actor,
                         long qid, 
                         Object value)
  {
    getDelegate().queryReply(headers, actor, qid, value);
  }
  
  @Override
  public void queryError(HeadersAmp headers, 
                         StubAmp actor,
                         long qid, 
                         Throwable exn)
  {
    getDelegate().queryError(headers, actor, qid, exn);
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
    getDelegate().streamReply(headers, actor, qid, sequence,
                              values, exn, isComplete);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getDelegate() + "]";
  }
}
