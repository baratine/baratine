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

import java.util.concurrent.TimeUnit;

import io.baratine.service.Cancel;

/**
 * {@code Credits} controls message flow. The methods on this interface are used
 * to let the publisher know when consumer is able to accept more messages.
 * <p>
 * This is achieved by giving credits to the publisher. The publisher should
 * read the credits value and make sure it's greater than 0 before attempting
 * to send a next message
 */
public interface Credits extends Cancel
{
  /**
   * Returns a long representing current sequence value
   *
   * @return current credit sequence value
   */
  long get();

  /**
   * Returns a number of messages client can accept.
   *
   * @return number of messages client can accept
   */
  int available();

  /**
   * Sets the new credit sequence when prefetch is disabled. Used by
   * applications that need finer control.
   * <p>
   * Applications using credit need to continually add credits.
   *
   * @param creditSequence next credit in the sequence
   * @throws IllegalStateException if prefetch is used
   */
  default void set(long creditSequence)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Adds credits.
   * <p>
   * Convenience method based on the {@code credits} methods.
   *
   * @param newCredits number of credits to add
   */
  default void add(int newCredits)
  {
    set(get() + newCredits);
  }

  /**
   * This method cancels available credits. Following the
   * cancel call the pipe will no longer be delivering messages to the
   * subscriber.
   */
  @Override
  default void cancel()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Publisher callback when more credits may be available.
   *
   * @see OnAvailable
   */
  default void onAvailable(OnAvailable ready)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Method offerTimeout configures how long the Pipe.next() will wait for
   * queue to accept next message before throwing an IllegalStateException
   * with no credits available message.
   *
   * @param timeout timeout value
   * @param unit    timeout unit modifier
   */
  default void offerTimeout(long timeout, TimeUnit unit)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Publisher callback when more credits may be available.
   */
  interface OnAvailable
  {
    /**
     * Method called when more credits are available
     */
    void available();

    /**
     * Method called when method fail is called on a publisher pipe
     */
    default void fail(Throwable exn)
    {
    }

    /**
     * Method called
     */
    default void cancel()
    {
    }
  }
}
