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
import java.util.function.BiConsumer;

import io.baratine.pipe.Credits.OnAvailable;
import io.baratine.pipe.Pipe;
import io.baratine.pipe.Pipes;
import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;
import io.baratine.service.Cancel;
import io.baratine.service.Pin;
import io.baratine.service.Result;

/**
 * Implementation of the pipes
 */
class PipeNode<T> implements Pipes<T>
{
  private SchemePipeImpl _scheme;
  private String _address;
  
  private ArrayList<SubscriberNode> _consumers = new ArrayList<>();
  
  private ArrayList<SubscriberNode> _subscribers = new ArrayList<>();
  
  private ArrayList<BiConsumer<String,Result<Void>>> _onChildList = new ArrayList<>();
  private ArrayList<Runnable> _pendingInit = new ArrayList<>();
  
  private long _sequence;
  
  private StateInit _init = StateInit.NEW;
  
  public PipeNode(SchemePipeImpl scheme, String address)
  {
    Objects.requireNonNull(scheme);
    Objects.requireNonNull(address);
    
    _scheme = scheme;
    _address = address;
  }

  @Override
  public void subscribe(ResultPipeIn<T> result)
  {
    SubscriberNode sub = new SubscriberNode(result.pipe());
    
    _subscribers.add(sub);
    
    result.pipe().credits().onAvailable(sub);
    result.ok(null);
    
    init();
  }

  @Override
  public void consume(ResultPipeIn<T> result)
  {
    SubscriberNode sub = new SubscriberNode(result.pipe());
    
    _consumers.add(sub);
    
    result.pipe().credits().onAvailable(sub);
    result.ok(null);
    
    init();
  }
  
  private void unsubscribe(SubscriberNode node)
  {
    _consumers.remove(node);
    _subscribers.remove(node);
  }

  @Override
  public void publish(ResultPipeOut<T> result)
  {
    if (! init() && pendingPublish(result)) {
      return;
    }
    
    init();
    
    result.ok(new PublisherNode());
  }
  
  private boolean pendingPublish(ResultPipeOut<T> result)
  {
    if (_init == StateInit.INIT) {
      return false;
    }
    
    _pendingInit.add(()->publish(result));
    
    return true;
  }
  
  @Override
  public void send(T value, Result<Void> result)
  {
    if (! init() && pendingSend(value, result)) {
      return;
    }
    
    send(value);

    result.ok(null);
  }
  
  private boolean pendingSend(T value, Result<Void> result)
  {
    if (_init == StateInit.INIT) {
      return false;
    }
    
    _pendingInit.add(()->send(value, result));
    
    return true;
  }
  
  private void send(T value)
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
  
  private boolean init()
  {
    if (_init == StateInit.INIT) {
      return true;
    }
    else if (_init == StateInit.INITIALIZING) {
      return false;
    }
    
    _init = StateInit.INITIALIZING;
    
    int p = _address.lastIndexOf('/');
    
    if (p > 0) {
      String parent = _address.substring(0, p);
      String child = _address.substring(p + 1);
      
      Result<Void> result = this::onChildComplete;
      
      _scheme.onChild(parent, child, result);
      
      return false;
    }
    else {
      _init = StateInit.INIT;
      
      return false;
    }
  }
  
  private void onChildComplete(Void value, Throwable exn)
  {
    _init = StateInit.INIT;
    
    for (Runnable task : _pendingInit) {
      task.run();
    }
    
    _pendingInit.clear();
  }
  
  @Override
  public void onChild(@Pin BiConsumer<String,Result<Void>> onChild, 
                      @Pin Result<Cancel> result)
  {
    _onChildList.add(onChild);
    
    result.ok(new OnChildCancel(onChild));
  }

  public void onChild(String child, Result<Void> result)
  {
    if (_onChildList.size() == 0) {
      result.ok(null);
      return;
    }
    
    Result.Fork<Void,Void> fork = result.fork();
    
    for (BiConsumer<String,Result<Void>> onChild : _onChildList) {
      onChild.accept(child, fork.branch());
    }
    
    fork.join(x->null);
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
      send(value);
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
  
  private class SubscriberNode implements OnAvailable
  {
    private Pipe<T> _pipe;
    
    SubscriberNode(Pipe<T> pipe)
    {
      Objects.requireNonNull(pipe);
      _pipe = pipe;
    }

    @Override
    public void available()
    {
    }

    @Override
    public void cancel()
    {
      unsubscribe(this);
    }
    
    public void next(T value)
    {
      Pipe<T> pipe = _pipe;
      
      if (pipe != null && pipe.credits().available() > 0) {
        pipe.next(value);
      }
    }
  }
  
  private class OnChildCancel implements Cancel
  {
    private BiConsumer<String,Result<Void>> _onChild;
    
    OnChildCancel(BiConsumer<String,Result<Void>> onChild)
    {
      _onChild = onChild;
    }
    
    public void cancel()
    {
      _onChildList.remove(_onChild);
    }
    
  }
  
  private enum StateInit {
    NEW,
    INITIALIZING,
    INIT;
  }
}
