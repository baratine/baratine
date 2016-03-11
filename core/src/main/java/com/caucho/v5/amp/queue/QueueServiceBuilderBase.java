/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.amp.queue;

import com.caucho.v5.amp.outbox.DeliverOutbox;
import com.caucho.v5.amp.outbox.MessageOutbox;
import com.caucho.v5.amp.outbox.QueueService;
import com.caucho.v5.util.L10N;

/**
 * Interface for an actor queue
 */
public abstract class QueueServiceBuilderBase<M extends MessageOutbox<M>>
  implements QueueServiceBuilder<M>, QueueDeliverBuilder<M>
{
  private static final L10N L = new L10N(QueueServiceBuilderBase.class);
  
  private static final int DEFAULT_INITIAL = 16;
  private static final int DEFAULT_CAPACITY = 1024;
  
  //private DeliverOutbox<M> []_processors;
  //private int _initial = 64;
  private int _initial = -1;
  private int _capacity = -1;
  private boolean _isMultiworker;
  private int _multiworkerOffset = 1;
  
  /*
  public QueueServiceBuilderBase<M>
  processors(DeliverOutbox<M> ...processors)
  {
    if (processors == null)
      throw new NullPointerException();
    else if (processors.length == 0)
      throw new IllegalArgumentException();
    
    _processors = processors;
    
    return this;
  }
  */
  
  /*
  public DeliverOutbox<M> []getProcessors()
  {
    return _processors;
  }
  */
  
  public QueueServiceBuilderBase<M> capacity(int capacity)
  {
    _capacity = capacity;

    return this;
  }
  
  public int capacity()
  {
    if (_capacity > 0) {
      return _capacity;
    }
    else {
      return DEFAULT_CAPACITY;
    }
  }
  
  public QueueServiceBuilderBase<M> 
  initial(int initial)
  {
    _initial = initial;
    
    return this;
  }
  
  public int initial()
  {
    if (_initial > 0) {
      return _initial;
    }
    else if (_capacity > 0) {
      return capacity();
    }
    else {
      return DEFAULT_INITIAL;
    }
  }
  
  public QueueServiceBuilderBase<M>
  multiworker(boolean isMultiworker)
  {
    _isMultiworker = isMultiworker;
    
    return this;
  }
  
  public boolean isMultiworker()
  {
    return _isMultiworker;
  }
  
  public QueueServiceBuilderBase<M>
  multiworkerOffset(int offset)
  {
    _multiworkerOffset = offset;
    
    return this;
  }
  
  public int multiworkerOffset()
  {
    return _multiworkerOffset;
  }

  protected void validateFullBuilder()
  {
    /*
    if (_processors == null) {
      throw new IllegalStateException(L.l("processors is required"));
    }
    */
    
    validateBuilder();
  }

  protected void validateBuilder()
  {
    /*
    if (_capacity <= 0) {
      throw new IllegalStateException(L.l("capacity is required"));
    }
    */
  }

  public QueueService<M> build(DeliverOutbox<M> processor)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /*
  public QueueService<M> build(DeliverOutbox<M> []processors)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  /*
  public DisruptorBuilderQueue<M> disruptorBuilder(DeliverFactory<M> actorFactory)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */
}
