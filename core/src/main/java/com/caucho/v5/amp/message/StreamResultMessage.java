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

import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.stream.ResultStream;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class StreamResultMessage<T>
  extends MessageOutboxBase
{
  private final ResultStream<T> _resultNext;

  private ArrayList<T> _values = new ArrayList<>();
  private Throwable _exn;
  private boolean _isComplete;

  private boolean _isSent;

  public StreamResultMessage(OutboxAmp outbox,
                             InboxAmp inbox,
                             ResultStream<T> resultNext)
  {
    super(outbox, inbox);

    Objects.requireNonNull(resultNext);
    
    _resultNext = resultNext;
  }
  
  ResultStream<T> getNext()
  {
    return _resultNext;
  }
  
  boolean isSent()
  {
    return _isSent;
  }
  
  public boolean add(T value)
  {
    if (_isSent) {
      return false;
    }
    
    _values.add(value);
    
    return true;
  }
  
  public boolean complete()
  {
    if (_isSent) {
      return false;
    }
    
    _isComplete = true;
    
    return true;
  }
  
  @Override
  public void fail(Throwable exn)
  {
    failQueue(exn);
  }
  
  boolean failQueue(Throwable exn)
  {
    if (_isSent) {
      return false;
    }
    
    _exn = exn;
    
    _isComplete = true;

    return true;
  }
  
  @Override
  public void offerQueue(long timeout)
  {
    _isSent = true;
    
    ResultStream<T> next = getNext();
    
    if (next.isCancelled()) {
      return;
    }
    
    if (next.isFuture()) {
      if (_exn != null) {
        next.fail(_exn);
      }
      else {
        next.acceptFuture(_resultNext, _values, _isComplete);
      }
    }
    else {
      super.offerQueue(timeout);
    }
  }

  @Override
  public void invoke(InboxAmp inbox, StubAmp actor)
  {
    if (! actor.isPrimary()) {
      return;
    }
    
    _isSent = true;
    
    ResultStream<T> next = getNext();
    
    if (next.isCancelled()) {
      return;
    }
    
    // next.start();

    try {
      for (T value : _values) {
        next.accept(value);
      }
      
      if (_exn != null) {
        next.fail(_exn);
        return;
      }
      else if (_isComplete) {
        next.ok();
      }
    } catch (Throwable e) {
      next.fail(e);
    }
  }
}
