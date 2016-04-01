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

package com.caucho.v5.ramp.pipe;

import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.util.L10N;

import io.baratine.pipe.Pipe;
import io.baratine.pipe.Pipe.FlowOut;
import io.baratine.pipe.Pipes;
import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;

/**
 * Implementation of the pipes
 */
class PipeNode<T> implements Pipes<T>
{
  private static final L10N L = new L10N(PipeNode.class);
    
  private String _address;
  
  private ArrayList<SubscriberNode> _consumers = new ArrayList<>();
  
  private ArrayList<SubscriberNode> _subscribers = new ArrayList<>();
  
  private long _sequence;
  
  public PipeNode(String address)
  {
    Objects.requireNonNull(address);
    
    _address = address;
  }

  @Override
  public void subscribe(ResultPipeIn<T> result)
  {
    SubscriberNode sub = new SubscriberNode();
    
    _subscribers.add(sub);
    
    result.ok(sub);
  }

  @Override
  public void consume(ResultPipeIn<T> result)
  {
    SubscriberNode sub = new SubscriberNode();
    
    _consumers.add(sub);
    
    result.ok(sub);
  }
  
  private void unsubscribe(SubscriberNode node)
  {
    _consumers.remove(node);
    _subscribers.remove(node);
  }

  @Override
  public void publish(ResultPipeOut<T> result)
  {
    result.ok(new PublisherNode());
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _address + "]";
  }
  
  private class PublisherNode implements Pipe<T>
  {
    @Override
    public void next(T value)
    {
      for (SubscriberNode sub : _subscribers) {
        sub.next(value);
      }
      
      long seq = _sequence++;
      int size = _consumers.size();
      
      if (size > 0) {
        int index = (int) (seq % size);
        
        _consumers.get(index).next(value);
      }
    }

    @Override
    public void close()
    {
    }

    @Override
    public void fail(Throwable exn)
    {
      System.out.println("FAIL:" + exn);
    }
  }
  
  private class SubscriberNode implements FlowOut<T>
  {
    private Pipe<T> _pipe;

    @Override
    public void ready(Pipe<T> pipe)
    {
      _pipe = pipe;
    }

    @Override
    public void cancel()
    {
      unsubscribe(this);
    }
    
    public void next(T value)
    {
      Pipe<T> pipe = _pipe;
      
      if (pipe != null && pipe.available() > 0) {
        pipe.next(value);
      }
    }
  }
}
