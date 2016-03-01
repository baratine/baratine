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

import io.baratine.service.MethodRef;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;
import io.baratine.spi.Headers;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;

public class TransactionImpl
{
  private static final Logger log
    = Logger.getLogger(TransactionImpl.class.getName());
  
  private ArrayList<Call> _methodCalls = new ArrayList<>();

  public void send(ServiceRef serviceRef,
                   MethodRefXA methodRef, 
                   Headers headers, 
                   Object[] args)
  {
    _methodCalls.add(new Call(serviceRef, methodRef, headers, args));
  }

  public void query(ServiceRefAmp serviceRef, 
                    MethodRefXA methodRef,
                    Headers headers, 
                    Object[] args)
  {
    _methodCalls.add(new Call(serviceRef, methodRef, headers, args));
  }
  

  public void commit(Result<Boolean> result)
  {
    int size = _methodCalls.size();

    if (size == 0) {
      result.ok(true);
      return;
    }
    
    int prepareCount = 0;
    
    for (Call call : _methodCalls) {
      if (call.isPrepare()) {
        prepareCount++;
      }
    }
    
    if (prepareCount > 0) {
      PrepareResult prepareResult = new PrepareResult(result, prepareCount);
      
      for (Call call : _methodCalls) {
        call.prepare(prepareResult);
      }
    }
    else {
      commitImpl(result);
    }
  }
  
  public void commitImpl(Result<Boolean> result)
  {
    CommitResult commitResult = new CommitResult(result, _methodCalls.size());
      
    for (Call call : _methodCalls) {
      call.commit(commitResult);
    }
  }
  
  private void rollbackImpl()
  {
    for (Call call : _methodCalls) {
      call.rollback();
    }
  }
  
  private static class Call {
    private ServiceRef _service;
    private MethodRefXA _method;
    private Headers _header;
    private Object []_args;
    
    Call(ServiceRef service,
         MethodRefXA method,
         Headers header,
         Object []args)
    {
      _service = service;
      _method = method;
      _header = header;
      _args = args;
    }
    
    public boolean isPrepare()
    {
      return _method.getPrepare() != null;
    }

    public void prepare(Result<Object> result)
    {
      MethodRefAmp prepare = _method.getPrepare();
      
      if (prepare != null) {
        prepare.query(_header, result, _args);
      }
    }

    void commit(Result<Object> result)
    {      
      _method.getDelegate().query(_header, result, _args);
    }
    
    public void rollback()
    {
      MethodRefAmp rollback = _method.getRollback();
      
      if (rollback != null) {
        rollback.send(_header, _args);
      }
    }
  }
  
  private class PrepareResult implements Result<Object>
  {
    private int _count;
    private Result<Boolean> _result;
    
    PrepareResult(Result<Boolean> result, int count)
    {
      _result = result;
      
      if (count <= 0) {
        throw new IllegalArgumentException();
      }
      
      _count = count;
    }
    
    @Override
    public void ok(Object value)
    {
      if (--_count == 0) {
        commitImpl(_result);
      }
    }

    @Override
    public void fail(Throwable exn)
    {
      int count = _count;
      _count = -1;
      
      log.log(Level.FINER, exn.toString(), exn);
      
      if (count > 0) {
        rollbackImpl();
        _result.fail(exn);
      }
    }
    
    @Override
    public void handle(Object value, Throwable exn)
    {
      if (exn != null) {
        fail(exn);
      }
      else {
        ok(value);
      }
    }
  }
  
  private class CommitResult implements Result<Object>
  {
    private Result<Boolean> _result;
    private int _count;
    
    CommitResult(Result<Boolean> result, int count)
    {
      _result = result;
      _count = count;
    }
    
    @Override
    public void ok(Object value)
    {
      if (--_count == 0) {
        if (_result != null) {
          _result.ok(true);
        }
      }
    }

    @Override
    public void fail(Throwable exn)
    {
      int count = _count;
      _count = -1;
      
      log.log(Level.FINER, exn.toString(), exn);
      
      if (count > 0) {
        _result.fail(exn);
      }
    }
    
    public void handle(Object value, Throwable exn)
    {
      if (exn != null) {
        fail(exn);
      }
      else {
        ok(value);
      }
    }
  }
}
