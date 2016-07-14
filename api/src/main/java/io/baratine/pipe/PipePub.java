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

import io.baratine.service.ResultChain;
import io.baratine.service.ServiceException;

/**
 * {@code OutStream} is a source of a pipe to write.
 * <pre><code>
 *   service.publish(Pipes.out(new MyFlow()));
 * </code></pre>
 */
@FunctionalInterface
public interface PipePub<T> extends ResultChain<Pipe<T>>
{  
  void handle(Pipe<T> pipe, Throwable exn) throws Exception;
  
  /**
   * The prefetch size.
   * 
   * Prefetch automatically manages the credits available to the sender.
   * 
   * If {@code PREFETCH_DISABLE} is returned, use the credits instead. 
   */
  default PipePub<T> prefetch(int prefetch)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * The initial number of credits. Can be zero if no initial credits.
   * 
   * To enable credits and disable the prefetch queue, return a non-negative
   * value.
   * 
   * If {@code CREDIT_DISABLE} is returned, use the prefetch instead. This
   * is the default behavior. 
   */
  default PipePub<T> credits(long credits)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default void capacity(int capacity)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  default void ok(Pipe<T> pipe)
  {
    try {
      handle(pipe, null);
    } catch (Throwable e) {
      fail(e);
    }
  }
  
  @Override
  default void fail(Throwable exn)
  {
    try {
      handle(null, exn);
    } catch (RuntimeException e) {
      throw e;
    } catch (Error e) {
      throw e;
    } catch (Throwable e) {
      throw ServiceException.createAndRethrow(e);
    }
  }
}
