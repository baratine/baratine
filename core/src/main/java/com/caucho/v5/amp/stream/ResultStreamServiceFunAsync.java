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

import io.baratine.function.BiFunctionAsync;
import io.baratine.stream.ResultStream;

@SuppressWarnings("serial")
public class ResultStreamServiceFunAsync<T> extends ResultStreamServiceBase<T,T>
{
  private boolean _isFirst = true;
  private T _value;
  
  private BiFunctionAsync<T,T,T> _fun;
  
  public ResultStreamServiceFunAsync(ResultStream<? super T> result,
                                     BiFunctionAsync<T,T,T> fun)
  {
    super(result);

    _fun = fun;
  }

  @Override
  public void accept(T value)
  {
    if (_isFirst) {
      _isFirst = false;
      _value = value;
    }
    else {
      _fun.apply(_value, value, (x,e)->{ 
        if (e != null) {
          next().fail(e);
        }
        else {
          _value = x;
        }
      });
    }
  }

  @Override
  public void ok()
  {
    next().accept(_value);

    super.ok();
  }

  @Override
  public ResultStream<T> createMapSelf(ResultStream<? super T> result)
  {
    return new ResultStreamServiceFunAsync<>(result, _fun);
  }
}
