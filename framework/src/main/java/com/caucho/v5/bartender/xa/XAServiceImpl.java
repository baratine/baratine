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

package com.caucho.v5.bartender.xa;

import io.baratine.service.Direct;
import io.baratine.service.MethodRef;
import io.baratine.service.OnInit;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;
import io.baratine.spi.Headers;

import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.util.L10N;

public class XAServiceImpl
{
  private static final L10N L = new L10N(XAServiceImpl.class);
  
  private static final ThreadLocal<TransactionImpl> _xaLocal
    = new ThreadLocal<>();

  private ServiceRef _serviceRef;
  private MethodRef _commitRef;

  public XAServiceImpl()
  {
  }
  
  @OnInit
  public void onInit()
  {
    _serviceRef = ServiceRef.current();
    _commitRef = _serviceRef.getMethod("commitImpl");
  }
    
  public static TransactionImpl getTransaction()
  {
    return _xaLocal.get();
  }
    
  @Direct
  public void begin()
  {
    TransactionImpl xa = _xaLocal.get();
    
    if (xa != null) {
      throw new IllegalStateException(L.l("Nested transactions are forbidden"));
    }
    
    xa = new TransactionImpl();
    
    _xaLocal.set(xa);
  }
  
  @Direct
  public void commit(Result<Boolean> result)
  {
    TransactionImpl xa = _xaLocal.get();
    
    if (xa == null) {
      throw new IllegalStateException(L.l("Commit is missing a transaction"));
    }
    
    _xaLocal.set(null);
    
    // Headers headers = HeadersNull.NULL;
    
    _commitRef.query(result, xa);
  }
  
  public void commitImpl(TransactionImpl xa, Result<Boolean> result)
  {
    xa.commit(result);
  }
  
  @Direct
  public boolean rollback()
  {
    TransactionImpl xa = _xaLocal.get();
    
    _xaLocal.set(null);
    
    if (xa == null) {
      throw new IllegalStateException(L.l("rollback called without a transaction"));
    }
    
    
    return true;
  }
}
