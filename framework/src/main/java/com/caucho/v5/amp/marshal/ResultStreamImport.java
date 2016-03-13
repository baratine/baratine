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

package com.caucho.v5.amp.marshal;

import io.baratine.stream.ResultStream;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class ResultStreamImport extends ResultStream.Wrapper<Object,Object>
{
  private final ModuleMarshal _marshal;
  private final ClassLoader _classLoader;

  public ResultStreamImport(ResultStream delegate,
                      ModuleMarshal marshal,
                      ClassLoader importLoader)
  {
    super(delegate);
    
    _marshal = marshal;
    _classLoader = importLoader;
  }

  @Override
  public void accept(Object result)
  {
    Object resultCvt = _marshal.convert(result);
    
    next().accept(resultCvt);
  }
  
  @Override
  public ResultStream<?> createJoin()
  {
    //return getNext().createJoin();
    return next().createJoin();
  }
  
  @Override
  public ResultStream<Object> createFork(ResultStream<Object> resultJoin)
  {
    ResultStream<Object> fork = next().createFork(resultJoin);
    
    // System.err.println("FORK: " + resultJoin);
    // Thread.dumpStack();
    
    return new ResultStreamImport(fork, _marshal, _classLoader);
  }
  
  public Object writeReplace()
  {
    throw new IllegalStateException(getClass().getName());
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + next() + "]";
  }
}
