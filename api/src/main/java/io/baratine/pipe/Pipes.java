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

package io.baratine.pipe;

import java.util.function.Consumer;

import io.baratine.pipe.Pipe.FlowOut;
import io.baratine.pipe.Pipe.InHandler;
import io.baratine.pipe.PipesImpl.PipeOutResultImpl;
import io.baratine.pipe.PipesImpl.ResultPipeInHandlerImpl;
import io.baratine.pipe.PipesImpl.ResultPipeInImpl;
import io.baratine.service.Result;
import io.baratine.service.Service;


/**
 * The Pipes service is a broker between publishers and subscribers, available
 * at the "pipe:" scheme.
 */
@Service("pipe:///{param[0]}")
public interface Pipes<T>
{
  void consume(ResultPipeIn<T> result);
  
  void subscribe(ResultPipeIn<T> result);
  
  void publish(ResultPipeOut<T> result);
  
  public static <T> PipeOutBuilder<T> out(FlowOut<T> flow)
  {
    return new PipeOutResultImpl<>(flow);
  }
  
  public static <T> PipeOutBuilder<T> out(Result<Pipe<T>> result)
  {
    return new PipeOutResultImpl<>(result);
  }
  
  public static <T> ResultPipeIn<T> in(Pipe<T> pipe)
  {
    return new ResultPipeInImpl<>(pipe);
  }
  
  public static <T> Pipe<T> in(InHandler<T> handler)
  {
    return new ResultPipeInHandlerImpl<>(handler);
  }
  
  public interface PipeOutBuilder<T> extends ResultPipeOut<T>
  {
    PipeOutBuilder<T> fail(Consumer<Throwable> onFail);
  }
  
  public interface PipeInBuilder<T> extends ResultPipeIn<T>
  {
    PipeInBuilder<T> fail(Consumer<Throwable> onFail);
    PipeInBuilder<T> close(Runnable onClose);
    
    PipeInBuilder<T> credits(long initialCredit);
    
    PipeInBuilder<T> prefetch(int prefetch);
    
    PipeInBuilder<T> capacity(int size);
  }
}
