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

import io.baratine.service.ResultStream;

/**
 * service proxy for a stream call. The proxy will queue result messages to 
 * the client stub.
 */
class StreamCallJoinStub extends ResultStream.Wrapper<Object,Object>
{
  private int _forkCount;
  private int _completeCount;
  
  StreamCallJoinStub(ResultStream<Object> next)
  {
    super(next);
  }

  @Override
  public void accept(Object value)
  {
    next().accept(value);
  }
  
  void fork()
  {
    _forkCount++;
  }
  
  @Override
  public void ok()
  {
    if (_forkCount <= ++_completeCount) {
      next().ok();
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + next() + "]";
  }
}
