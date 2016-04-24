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

import io.baratine.pipe.Credits.OnAvailable;
import io.baratine.pipe.Pipe;
import io.baratine.pipe.Pipes;
import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;
import io.baratine.service.Result;

/**
 * Implementation of the pipes
 */
abstract public class PipeAsset<T> implements Pipes<T>
{
  //private SchemePipeImpl _scheme;
  //private String _address;
  
  private ArrayList<SubscriberNode> _consumers = new ArrayList<>();
  
  private ArrayList<SubscriberNode> _subscribers = new ArrayList<>();
  
  //private ArrayList<BiConsumer<String,Result<Void>>> _onChildList = new ArrayList<>();
  //private ArrayList<Runnable> _pendingInit = new ArrayList<>();
  
  private long _sequence;
  
  private StateInit _initSend = StateInit.NEW;
  private StateInit _initReceive = StateInit.NEW;
  
  abstract public String id();

  @Override
  public void subscribe(ResultPipeIn<T> result)
  {
    initReceive();
    
    SubscriberNode sub = new SubscriberNode(result.pipe());
    
    _subscribers.add(sub);
    
    result.pipe().credits().onAvailable(sub);
    result.ok(null);
  }

  @Override
  public void consume(ResultPipeIn<T> result)
  {
    initReceive();
    
    SubscriberNode sub = new SubscriberNode(result.pipe());
    
    _consumers.add(sub);
    
    result.pipe().credits().onAvailable(sub);
    result.ok(null);
  }
  
  private void unsubscribe(SubscriberNode node)
  {
    _consumers.remove(node);
    _subscribers.remove(node);
  }

  @Override
  public void publish(ResultPipeOut<T> result)
  {
    initSend();
    
    result.ok(new PublisherNode());
  }
  
  @Override
  public void send(T value, Result<Void> result)
  {
    initSend();
    
    onSend(value);
    
    result.ok(null);
  }
  
  protected void onSend(T value)
  {
  }
  
  protected void sendDriver(T value)
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
  
  private void initSend()
  {
    if (_initSend == StateInit.INIT) {
      return;
    }
    
    _initSend = StateInit.INIT;
    
    onInitSend();
  }
  
  protected void onInitSend()
  {
  }
  
  private void initReceive()
  {
    if (_initReceive == StateInit.INIT) {
      return;
    }
    
    _initReceive = StateInit.INIT;
    
    onInitReceive();
  }
  
  protected void onInitReceive()
  {
  }
  
  /*
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
  */
  
  /*
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
  */
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + id() + "]";
  }
  
  private class PublisherNode implements Pipe<T>
  {
    @Override
    public void next(T value)
    {
      onSend(value);
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
  
  /*
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
  */
  
  private enum StateInit {
    NEW,
    INITIALIZING,
    INIT;
  }
}
