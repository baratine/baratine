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

package io.baratine.service;

/**
 * Container for ResultStream impl classes.
 */
class ResultStreamImpl
{
  static class ResultAsync<S> implements Result<Void>
  {
    private ResultStream<S> _resultStream;
    private Iterable<S> _values;
    private boolean _isComplete;
    
    ResultAsync(ResultStream<S> resultStream, 
                Iterable<S> values,
                boolean isComplete)
    {
      _resultStream = resultStream;
      _values = values;
      _isComplete = isComplete;
    }
    
    @Override
    public void completeFuture(Void resultValue)
    {
      for (S value : _values) {
        _resultStream.accept(value);
      }
      
      if (_isComplete) {
        _resultStream.ok();
      }
    }

    @Override
    public void ok(Void result)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    public void handle(Void result, Throwable exn)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    public void fail(Throwable exn)
    {
      _resultStream.fail(exn);
    }
  }
  
  @SuppressWarnings("serial")
  static final class ResultStreamIgnore<U> implements ResultStream<U> {
    private ResultStream<?> _result;
    
    ResultStreamIgnore(ResultStream<?> result)
    {
      _result = result;
    }

    @Override
    public void accept(U v)
    {
    }
    
    @Override
    public void ok()
    {
      _result.ok();
    }
    
    @Override
    public void fail(Throwable exn)
    {
      _result.fail(exn);
    }
    
    @Override
    public void handle(U value, Throwable exn, boolean isEnd)
    {
    }
  }
  
  @SuppressWarnings("serial")
  static class ResultStreamMap<T> implements ResultStream<T> {
    private ResultStream<T> _result;
    private ResultStream<?> _resultReduce;
    
    ResultStreamMap(ResultStream<T> result, ResultStream<?> resultReduce)
    {
      _result = result;
      _resultReduce = resultReduce;
    }
    
    @Override
    public void start()
    {
      _result.start();
      _resultReduce.start();
    }

    @Override
    public void accept(T v)
    {
      _result.accept(v);
    }
    
    @Override
    public void ok()
    {
      _result.ok();
    }
    
    @Override
    public void fail(Throwable exn)
    {
      _result.fail(exn);
    }
    
    public void handle(T value, Throwable exn, boolean isEnd)
    {
      if (isEnd) {
        ok();
      }
      else if (exn != null) {
        fail(exn);
      }
      else {
        accept(value);
      }
    }
    
    @Override
    public ResultStream<?> createJoin()
    {
      return new ResultStreamIgnore<Object>(this);
    }
    
    @Override
    public ResultStream<T> createFork(ResultStream<Object> resultJoin)
    {
      return new ResultStreamMap<T>(_result, resultJoin);
    }
  }
}
