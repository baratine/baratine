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

import io.baratine.function.FunctionSync;
import io.baratine.stream.ResultStream;

@SuppressWarnings("serial")
public class ResultStreamServiceMapSync<U,T> extends ResultStreamServiceBase<U,T>
{
  private FunctionSync<? super U,? extends T> _fun;
  
  public ResultStreamServiceMapSync(ResultStream<T> result,
                                    FunctionSync<? super U,? extends T> fun)
  {
    super(result);

    _fun = fun;
  }

  @Override
  public void accept(U value)
  {
    next().accept(_fun.apply(value));
  }

  @Override
  public ResultStream<U> createMapSelf(ResultStream<? super T> result)
  {
    return new ResultStreamServiceMapSync<>(result, _fun);
  }
}
