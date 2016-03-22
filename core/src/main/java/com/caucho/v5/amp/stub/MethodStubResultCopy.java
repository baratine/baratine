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

import java.util.Objects;

import com.caucho.v5.amp.spi.HeadersAmp;

import io.baratine.service.Result;

/**
 * {@code @Copy Result} wrapper to properly handle the result.
 */
class MethodStubResultCopy<T,V> extends MethodAmpWrapper
{
  private MethodAmp _delegate;
  private TransferAsset<T,V> _shim;
  
  MethodStubResultCopy(MethodAmp delegate,
                       TransferAsset<T,V> shim)
  {
    Objects.requireNonNull(delegate);
    Objects.requireNonNull(shim);
    
    _delegate = delegate;
    _shim = shim;
  }
  
  @Override
  protected MethodAmp delegate()
  {
    return _delegate;
  }
  
  private TransferAsset<T,V> shim()
  {
    return _shim;
  }
  
  
  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    StubAmp stub)
  {
    delegate().query(headers, new ResultShim(result, stub, shim()), 
                     stub);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                     StubAmp stub,
                     Object arg1)
  {
    delegate().query(headers, new ResultShim(result, stub, shim()),
                     stub, arg1);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    StubAmp stub,
                    Object arg1,
                    Object arg2)
  {
    delegate().query(headers, new ResultShim(result, stub, shim()),
                     stub, arg1, arg2);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    StubAmp stub,
                    Object arg1,
                    Object arg2,
                    Object arg3)
  {
    delegate().query(headers, new ResultShim(result, stub, shim()),
                     stub, arg1, arg2, arg3);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    StubAmp stub,
                    Object []args)
  {
    delegate().query(headers, new ResultShim(result, stub, shim()),
                     stub, args);
  }
  
}
