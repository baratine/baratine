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

import java.util.function.BiConsumer;

import io.baratine.service.Cancel;
import io.baratine.service.Pin;
import io.baratine.service.Result;
import io.baratine.service.Service;


/**
 * The Pipes service is a broker between publishers and subscribers, available
 * at the "pipe:" scheme.
 */
@Service("pipe:///{name}")
public interface Pipes<T>
{
  void consume(ResultPipeIn<T> result);
  
  void subscribe(ResultPipeIn<T> result);
  
  void publish(ResultPipeOut<T> result);
  
  void send(T value, Result<Void> result);
  
  /*
  default void onChild(@Pin BiConsumer<String,Result<Void>> onChild, 
                       @Pin Result<Cancel> result)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }
  */
}
