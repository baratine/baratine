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

import java.util.Objects;

@SuppressWarnings("serial")
public class BiConsumerMapSync<S,U,T> implements BiConsumerAsync<S,U>
{
  private BiConsumerAsync<S,T> _next;
  private FunctionSync<? super U,? extends T> _map;
  
  BiConsumerMapSync(BiConsumerAsync<S,T> next,
                    FunctionSync<? super U,? extends T> map)
  {
    Objects.requireNonNull(next);
    Objects.requireNonNull(map);
    
    _next = next;
    _map = map;
  }

  @Override
  public void accept(S s, U u, Result<Void> result)
  {
    _next.accept(s, _map.apply(u), result);
  }
}
