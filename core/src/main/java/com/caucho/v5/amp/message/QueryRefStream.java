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

import java.util.List;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

import io.baratine.stream.ResultStream;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class QueryRefStream extends QueryRefBase
{
  private ResultStream<?> _result;
  private ServiceRefAmp _targetRef;
  private int _count;
  private long _sequenceComplete = -1;
  
  QueryRefStream(String from, 
                 long id, 
                 ClassLoader loader,
                 ResultStream<?> result,
                 ServiceRefAmp targetRef)
  {
    super(from, id, loader);
    
    _result = result;
    _targetRef = targetRef;
  }
  
  @Override
  public boolean isCancelled()
  {
    return _result.isCancelled();
  }
  
  /*
  public int getCredit()
  {
    return _result.getCredit();
  }
  */
  
  @Override
  public boolean accept(HeadersAmp headers, List<Object> values,
                        long sequence, boolean isComplete)
  {
    if (isCancelled()) {
      sendCancel();
      return true;
    }
    
    synchronized (this) {
      if (values.size() > 0) {
        _count++;
      }
      
      if (isComplete) {
        _sequenceComplete = sequence;
      }
      
      ResultStream result = (ResultStream) _result;

      for (Object value : values) {
        result.accept(value);
      }

      if (_sequenceComplete >= 0 && _sequenceComplete <= _count) {
        result.ok();
        return true;
      }
      
      result.flush();
      return false;
    }
  }
  
  private void sendCancel()
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_targetRef.services())) {
      StreamCancelMessage cancel = new StreamCancelMessage(outbox, _targetRef,
                                                         getFrom(), getId());
    
      long timeout = 100000;
      cancel.offer(timeout);
    }
    
    // System.out.println("CANCELL: " + this);
  }
  
  /*
  @Override
  public void completeStream(HeadersAmp headers, long sequence)
  {
    synchronized (this) {
      ResultStream result = (ResultStream) _result;
    
      _sequenceComplete = sequence;
    

      if (_sequenceComplete <= _count) {
        result.complete();
      }
    }
  }
  */
  
  @Override
  public void fail(HeadersAmp headers, Throwable exn)
  {
    synchronized (this) {
      _result.fail(exn);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getFrom() + "," + getId() + "," + _result + "]";
  }
}
