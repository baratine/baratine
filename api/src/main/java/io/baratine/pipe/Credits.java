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
 * to send a next message.
 * <p>
 *
 * Example below provides simplified code which demonstrates concept of granting
 * credits. Production code should subscribe to Credits with OnAvailable callback
 * for proper implementation.
 * <p>
 * <blockquote>
 * <pre>
 * public class TestPipesQuotes
 * {
 *   &#64;Inject
 *   Services _services;
 *
 *   public void quotes() throws InterruptedException
 *   {
 *     QuoteServer server = _services.newService(QuoteServer.class)
 *                          .auto()
 *                          .as(QuoteServer.class);
 *
 *     QuoteClient client = _services.newService(QuoteClient.class)
 *                          .auto()
 *                          .start()
 *                          .as(QuoteClient.class);
 *
 *     State.sleep(100);  //give time for pipe to connect subscriber anc consumer
 *     server.newQuote("MARS 9.99"); //send the first quote. It will come through
 *                                  // and use up the only available credit
 *
 *     server.newQuote("MARS 9.98"); //send second quote. It will be ignored
 *                                   //by the if statement in newQuote() because
 *                                   //available credits is 0 at this point
 *     Thread.sleep(100);
 *
 *     client.addCredit(1);          //adds a credit
 *     Thread.sleep(10);             //time for credits to propagate
 *
 *     server.newQuote("MARS 9.97"); //send another quote which should now come through
 *
 *     State.sleep(1000);
 *   }
 *
 *   &#64;Service
 *   public static class QuoteClient
 *   {
 *     private Credits _credits;
 *
 *     &#64;OnInit
 *     public void init()
 *     {
 *       PipeSub&lt;String&gt; subscriber = PipeSub.of(this::onMessage)
 *                                          .prefetch(0)
 *                                          .credits(1)
 *                                          .credits(c -&gt; {
 *                                                            _credits = c;
 *                                                          });
 *
 *       Services services = Services.current();
 *
 *       QuoteServer server = services.service(QuoteServer.class);
 *
 *       server.connect(subscriber);
 *     }
 *
 *     public void addCredit(int credits)
 *     {
 *       _credits.add(credits);
 *     }
 *
 *     private void onMessage(String message)
 *     {
 *       System.out.println("new quote: " + message);
 *     }
 *   }
 *
 *   &#64;Service
 *   public static class QuoteServer
 *   {
 *     private Pipe&lt;String&gt; _quotes;
 *     private Credits _credits;
 *
 *     public void connect(PipeSub&lt;String&gt; subscription)
 *     {
 *       _quotes = subscription.pipe();
 *       _credits = _quotes.credits();
 *
 *       subscription.ok(null);
 *     }
 *
 *     public void newQuote(String quote)
 *     {
 *       if (_credits != null &amp;&amp; _credits.available() &gt; 0) {
 *         _quotes.next(quote);
 *       }
 *     }
 *   }
 * }
 *   </pre>
 * </blockquote>
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
  @FunctionalInterface
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
