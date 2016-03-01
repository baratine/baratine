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

package com.caucho.v5.ramp.compute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Spliterator;
import java.util.function.Supplier;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.ResultStreamJoin;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.L10N;

import io.baratine.function.RunnableSync;
import io.baratine.service.OnLookup;
import io.baratine.service.Result;
import io.baratine.service.ResultStream;
import io.baratine.service.Service;

/**
 * Timer service.
 */
@Service
public class ComputeServiceImpl
{
  private static final L10N L = new L10N(ComputeServiceImpl.class);
  
  private final Lifecycle _lifecycle = new Lifecycle();
  private TaskManager _taskManager;
  
  public ComputeServiceImpl()
  {
  }
  
  private TaskManager getTaskManager()
  {
    if (_taskManager == null) {
      AmpSystem ampSystem = AmpSystem.getCurrent();
      
      if (ampSystem != null) {
        //_taskManager = ampSystem.getTaskManager();
        throw new UnsupportedOperationException();
      }
      
      if (_taskManager == null) {
        throw new UnsupportedOperationException(L.l("Task manager is not available in this context"));
      }
    }
    
    return _taskManager;
  }
  
  @OnLookup
  public ComputeServiceImpl onLookup(String path)
  {
    return this;
  }
  
  public void run(RunnableSync task, Result<Void> result)
  {
    getTaskManager().run(task, result);
  }
  
  public <T> void execute(Supplier<T> task, Result<T> result)
  {
    getTaskManager().execute(task, result);
  }
  
  /*
  public <T> void spliterator(Spliterator<T> iter, ResultStream<T> result)
  {
    spliterator(s.spliterator(), result);
  }
  */
  
  public <T> void spliterator(Spliterator<T> s, ResultStream<T> result)
  {
    ArrayList<Spliterator<T>> splits = new ArrayList<>();
    
    splits.add(s);
    
    int count = 2 * getTaskManager().getWorkers();
    split(splits, count);
    
    int size = splits.size();
    
    if (size == 1) {
      getTaskManager().run(new ComputeTask<T>(splits.get(0), result));
      // _workers.compute(splits.get(0), new ResultSinkWrapper<T>(result));
      return;
    }

    /*
    if (true) {
      throw new UnsupportedOperationException(L.l("fork unsupported for {0}", result));
    }
    */
    
    ServiceRefAmp serviceRef = ServiceRefAmp.current();
    
    //ResultStreamAmp<T> resultAmp = (ResultStreamAmp<T>) result;
    
    ResultStreamJoin<T> join = new ResultStreamJoin<T>(result, serviceRef.inbox());
    for (int i = 0; i < size; i++) {
      //resultSinkFork[i].begin(0);
      //_workers.compute(splits.get(i), new ResultSinkWrapper<T>(resultSinkFork[i]));
      
      getTaskManager().run(new ComputeTask<T>(splits.get(i), join.fork()));
    }
    
    join.ok();
  }
  
  private <T> void split(ArrayList<Spliterator<T>> splits, int max)
  {
    while (splits.size() < max) {
      Collections.sort(splits, (x,y)->Long.signum(y.estimateSize() - x.estimateSize()));

      Spliterator<T> first = splits.get(0);
      Spliterator<T> next = first.trySplit();
      
      if (next == null) {
        return;
      }
      
      splits.add(next);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
