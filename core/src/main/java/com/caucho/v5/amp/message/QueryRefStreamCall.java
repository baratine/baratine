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

import io.baratine.service.Cancel;
import io.baratine.service.ResultStream;

import com.caucho.v5.amp.spi.HeadersAmp;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class QueryRefStreamCall extends QueryRefBase
{
  private long _id;
  private ResultStream<?> _result;
  private Cancel _cancel;
  
  QueryRefStreamCall(long id,
                     ResultStream<?> result,
                     Cancel cancel)
  {
    super("from", id, Thread.currentThread().getContextClassLoader());
    
    _id = id;
    _result = result;
    _cancel = cancel;
  }
  
  @Override
  public boolean isCancelled()
  {
    return _result.isCancelled();
  }
  
  @Override
  public void cancel()
  {
    _cancel.cancel();
  }

  @Override
  public void fail(HeadersAmp headers, Throwable exn)
  {
    // TODO Auto-generated method stub
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getId() + "," + _result + "]";
  }
}
