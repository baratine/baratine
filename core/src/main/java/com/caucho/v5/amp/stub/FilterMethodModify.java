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

import com.caucho.v5.amp.spi.HeadersAmp;

import io.baratine.service.ResultChain;

/**
 * @OnModify implementation for a stub.
 */
public class FilterMethodModify extends MethodAmpWrapper
{
  private final MethodAmp _delegate;
  
  public FilterMethodModify(MethodAmp delegate)
  {
    _delegate = delegate;
  }
  
  @Override
  protected final MethodAmp delegate()
  {
    return _delegate;
  }
  
  @Override
  public boolean isModify()
  {
    return true;
  }
  
  @Override
  public void send(HeadersAmp headers,
                   StubAmp stub,
                   Object []args)
  {
    delegate().send(headers, stub, args);
    
    setModified(stub);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp stub)
  {
    delegate().query(headers, 
                     nextModified(result, stub), 
                     stub);
    
    setModified(stub);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp stub,
                    Object a1)
  {
    delegate().query(headers, 
                     nextModified(result, stub),
                     stub,
                     a1);
    
    setModified(stub);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp stub,
                    Object a1,
                    Object a2)
  {
    delegate().query(headers, 
                     nextModified(result, stub),
                     stub,
                     a1, a2);
    
    setModified(stub);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp stub,
                    Object a1,
                    Object a2,
                    Object a3)
  {
    delegate().query(headers, 
                     nextModified(result, stub), 
                     stub, 
                     a1, a2, a3);
    
    //setModified(stub);
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp stub,
                    Object []args)
  {
    delegate().query(headers, 
                     nextModified(result, stub), 
                     stub, 
                     args);

    // setModified(stub);
  }
  
  private ResultChain<?> nextModified(ResultChain<?> result,
                                      StubAmp stub)
  {
    return ResultChain.then(result, 
                            (v,r)->{ 
                              setModified(stub); 
                              ((ResultChain) r).ok(v); 
                            });
  }
  
  private void setModified(StubAmp stub)
  {
    stub.onModify();
    
    /*
    if (actor instanceof ActorSkeletonResource) {
      ActorSkeletonResource actorModify = (ActorSkeletonResource) actor;
      
      actorModify.onModify();
    }
    */
  }
}
