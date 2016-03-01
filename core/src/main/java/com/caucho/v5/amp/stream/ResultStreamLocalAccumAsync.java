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

package com.caucho.v5.amp.stream;

import io.baratine.function.BiConsumerAsync;
import io.baratine.function.FunctionSync;
import io.baratine.service.Result;
import io.baratine.service.ResultStream;

import java.util.Objects;

@SuppressWarnings("serial")
public class ResultStreamLocalAccumAsync<A,R> extends ResultStreamLocalBase<A,R>
{
  private BiConsumerAsync<A,A> _combiner;
  private FunctionSync<A,R> _finisher;
  
  private boolean _isFirst = true;
  private A _accum;
  
  public ResultStreamLocalAccumAsync(ResultStream<? super R> result,
                                     BiConsumerAsync<A,A> combiner,
                                     FunctionSync<A,R> finisher)
  {
    super(result);
    
    Objects.requireNonNull(combiner);
    Objects.requireNonNull(finisher);

    _combiner = combiner;
    _finisher = finisher;
  }

  @Override
  public void accept(A value)
  {
    if (_isFirst) {
      _isFirst = false;
      _accum = value;
    }
    else {
      _combiner.accept(_accum, value, Result.ignore());
    }
  }

  @Override
  public void ok()
  {
    next().accept(_finisher.apply(_accum));
    next().ok();
  }
}
