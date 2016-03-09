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

package io.baratine.io;

/**
 * Subscriber's callback for a {@code Pipe}.
 */
public interface PipeIn<T> extends Pipe<T>
{
  public static final int PREFETCH_DEFAULT = 0;
  public static final int PREFETCH_DISABLE = -1;
  
  default void inFlow(Flow flow)
  {
  }
  
  default int prefetch()
  {
    return PREFETCH_DEFAULT;
  }
  
  default int capacity()
  {
    return 0;
  }

  @Override
  default void next(T value)
  {
    handle(value, null, false);
  }
  
  @Override
  default void fail(Throwable exn)
  {
    handle(null, exn, false);
  }
  
  @Override
  default void ok()
  {
    handle(null, null, true);
  }
  
  void handle(T value, Throwable exn, boolean isOk);
  
  /**
   * {@code Flow} controls the pipe prefetch
   */
  public interface Flow
  {
    /**
     * Pause publisher from adding to the prefetch queue by stopping the
     * automatic adding of credits.
     * 
     * Items currently in the prefetch queue will be delivered, and the
     * publisher can still add up to the current prefetch credit, but the 
     * publisher cannot add more items after that.
     */
    void pause();
    
    /**
     * Resumes the publisher.
     */
    void resume();
    
    /**
     * Adds to the prefetch queue when prefetch is disabled. Used by applications
     * that need finer control over the prefetch queue.
     * 
     * Applications using credit need to continually add credits.
     * 
     * @param newCredits additional credits for the publisher
     */
    void credit(int newCredits);
  }
}
