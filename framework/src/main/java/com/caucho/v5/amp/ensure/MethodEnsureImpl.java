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

package com.caucho.v5.amp.ensure;

import java.util.Objects;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.ParameterAmp;
import com.caucho.v5.amp.stub.StubAmpBean;
import com.caucho.v5.util.Crc64;
import com.caucho.v5.util.IdentityGenerator;

import io.baratine.service.Result;
import io.baratine.service.ResultChain;

/**
 * Method for an ensure stub.
 */
public class MethodEnsureImpl implements MethodEnsureAmp
{
  private final EnsureDriverImpl _driver;
  private final MethodAmp _method;
  private long _methodId;
  private IdentityGenerator _idGen;
  private boolean _isInit;
  
  MethodEnsureImpl(EnsureDriverImpl driver, MethodAmp method)
  {
    Objects.requireNonNull(driver);
    Objects.requireNonNull(method);
    
    _driver = driver;
    _method = method;
    
    _methodId = methodId(method);
    
    int nodeId = 0;
    _idGen = IdentityGenerator.newGenerator().node(nodeId).get(); 
  }
  
  private long methodId(MethodAmp method)
  {
    long id = 43;
    
    id = Crc64.generate(id, method.declaringClass().getName());
    id = Crc64.generate(id, method.name());
    
    for (ParameterAmp param : method.parameters()) {
      id = Crc64.generate(id, param.rawClass().getName());
    }
    
    return id;
  }

  @Override
  public void onActive(StubAmpBean stub)
  {
    if (! _isInit) {
      _isInit = true;

      _driver.onActive(_methodId, this, stub);
    }
  }
  
  void onActive(long id, StubAmpBean stub, String address, Object []args)
  {
    ServiceRefAmp serviceRef = stub.services().service(address);
    
    _method.query(HeadersNull.NULL,
                  Result.of(x->onComplete(id),
                            exn->onComplete(id )),
                  serviceRef.stub(),
                  args);
  }
  
  @Override
  public <T> ResultChain<T> ensure(StubAmpBean stub, 
                                   ResultChain<T> result,
                                   Object[] args)
  {
    long id = _idGen.get();
    
    _driver.put(id, _methodId, stub.name(), args);
    
    return ResultChain.of(result, 
                          x->{ onComplete(id); return x; },
                          (exn,r)->{ onComplete(id); r.fail(exn); }); 
  }
  
  private void onComplete(long id)
  {
    _driver.remove(id, _methodId);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _method + "]";
  }
}
