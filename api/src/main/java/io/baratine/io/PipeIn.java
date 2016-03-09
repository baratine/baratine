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
 * Consumer's callback for a {@code Pipe}.
 */
public interface PipeIn<T> extends Pipe<T>
{
  public static final int PREFETCH_DEFAULT = 0;
  public static final int PREFETCH_DISABLE = -1;
  
  public static final int CREDIT_DISABLE = -1;
  
  /**
   * Accept the {@code Flow} object for finer flow control.
   * 
   * The {@code Flow} object can pause the prefetch, or add credits
   * manually when the credit system is used. 
   */
  default void flow(Flow flow)
  {
  }
  
  /**
   * The prefetch size.
   * 
   * Prefetch automatically manages the credits available to the sender.
   * 
   * If {@code PREFETCH_DISABLE} is returned, use the credits instead. 
   */
  default int prefetch()
  {
    return PREFETCH_DEFAULT;
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
  default int credits()
  {
    return CREDIT_DISABLE;
  }
  
  default int capacity()
  {
    return 0;
  }
  
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
     * 
     * @throws IllegalStateException if credits are used
     */
    void pause();
    
    /**
     * Resumes the publisher.
     * 
     * @throws IllegalStateException if credits are used
     */
    void resume();
    
    /**
     * Adds to the credits when prefetch is disabled. Used by applications
     * that need finer control.
     * 
     * Applications using credit need to continually add credits.
     * 
     * @param newCredits additional credits for the publisher
     * 
     * @throws IllegalStateException if prefetch is used
     */
    void credits(int newCredits);
  }
  
  public interface InHandler<T>
  {
    void handle(T value, Throwable exn, boolean ok);
  }
}
