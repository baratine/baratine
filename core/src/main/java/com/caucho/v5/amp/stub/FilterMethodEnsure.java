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
import com.caucho.v5.amp.spi.StubContainerAmp;

import io.baratine.service.ResultChain;

/**
 * @Ensure implementation for a stub.
 */
public class FilterMethodEnsure extends MethodAmpWrapper
{
  private final MethodAmp _delegate;
  
  public FilterMethodEnsure(MethodAmp delegate)
  {
    _delegate = delegate;
  }
  
  @Override
  protected final MethodAmp delegate()
  {
    return _delegate;
  }
  
  //
  // lifecycle methods
  //
  
  @Override
  public void onActive(StubContainerAmp container)
  {
    super.onActive(container);
    
    container.onActiveEnsure(this);
  }
  
  // 
  // method calls
  //

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp stub)
  {
    result = stub.ensure(delegate(), result);
    
    delegate().query(headers, result, stub);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp stub,
                    Object a1)
  {
    result = stub.ensure(delegate(), result, a1);
    
    delegate().query(headers, result, stub, a1);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp stub,
                    Object a1,
                    Object a2)
  {
    result = stub.ensure(delegate(), result, a1, a2);
    
    delegate().query(headers, result, stub, a1, a2);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp stub,
                    Object a1,
                    Object a2,
                    Object a3)
  {
    result = stub.ensure(delegate(), result, a1, a2, a3);
    
    delegate().query(headers, result, stub, a1, a2, a3);
    
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp stub,
                    Object []args)
  {
    result = stub.ensure(delegate(), result, args);
    
    delegate().query(headers, result, stub, args);
  }
}
