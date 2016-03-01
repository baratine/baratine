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

package com.caucho.v5.amp.actor;

import io.baratine.service.Result;
import io.baratine.service.ResultStream;

import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;

/**
 * Abstract stream for an actor.
 */
public class MethodAmpChild extends MethodAmpWrapper
{
  private final MethodAmp _delegate;
  
  private ActorAmp _actorChild;
  
  MethodAmpChild(MethodAmp method,
                 ActorAmp actorChild)
  {
    _delegate = method;
    
    _actorChild = actorChild;
  }
  
  @Override
  protected final MethodAmp delegate()
  {
    return _delegate;
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actorDeliver)
  {
    delegate().send(headers, _actorChild);
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actorDeliver,
                   Object arg1)
  {
    delegate().send(headers, _actorChild, arg1);
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actorDeliver,
                   Object arg1,
                   Object arg2)
  {
    delegate().send(headers, _actorChild, arg1, arg2);
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actorDeliver,
                   Object arg1,
                   Object arg2,
                   Object arg3)
  {
    delegate().send(headers, _actorChild, arg1, arg2, arg3);
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actorDeliver,
                   Object []args)
  {
    delegate().send(headers, _actorChild, args);
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    ActorAmp actorDeliver)
  {
    delegate().query(headers, result, _actorChild);
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    ActorAmp actorDeliver,
                    Object arg1)
  {
    delegate().query(headers, result, _actorChild, arg1);
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    ActorAmp actorDeliver,
                    Object arg1,
                    Object arg2)
  {
    delegate().query(headers, result, _actorChild, arg1, arg2);
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    ActorAmp actorDeliver,
                    Object arg1,
                    Object arg2,
                    Object arg3)
  {
    delegate().query(headers, result, _actorChild, arg1, arg2, arg3);
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    ActorAmp actorDeliver,
                    Object []args)
  {
    // getDelegate().query(headers, queryRef, _actorChild, args);
    
    delegate().query(headers, result, actorDeliver, args);
  }
  
  //
  // map-reduce methods
  //

  @Override
  public <T> void stream(HeadersAmp headers,
                         ResultStream<T> result,
                         ActorAmp actor,
                         Object []args)
  {
    delegate().stream(headers, result, _actorChild, args);
  }
  
  //
  // impl methods
  //
  
  /*
  @Override
  public ActorAmp getActorInvoke(ActorAmp actorDeliver)
  {
    return _actorChild;
  }
  */

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + name()
            + "]");
  }
}
