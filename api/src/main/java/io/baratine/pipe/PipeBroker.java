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

import io.baratine.service.Result;
import io.baratine.service.Service;

/**
 * The Pipes service is a broker between publishers and subscribers, available
 * at the "pipe:" scheme.
 * <p>
 * <p>
 * Example: publisher / producer
 * <p>
 * <code>
 * <pre>
 * @Service
 * @Startup
 * public class Publisher
 * {
 *   private Pipe<String> _pipe;
 *
 *   @Inject @Service("pipe:///test")
 *   Pipes<String> _pipes;
 *
 *   @OnInit
 *   public void init()
 *   {
 *     //request Pipes to create a Pipe instance at "pipe:///test"
 *     //callback {@code ready} receives an initialized Pipe instance
 *     //available to send messages
 *     _pipes.publish((out,e)->ready(out));
 *   }
 *
 *   //method for sending the messages
 *   //note, that method should be called only after the _pipe is
 *   //initialized via {@code ready} callback
 *   public Void publish(String msg)
 *   {
 *     _pipe.next(msg);
 *     return null;
 *   }
 *
 *   //callback {@code ready} is called by the Pipes when a Pipe is established
 *   //argument pipe can be used to publish messages
 *   public void ready(Pipe<String> pipe)
 *   {
 *     _pipe = pipe;
 *   }
 * }
 * </pre>
 * </code>
 * <p>
 * Example: client / subscriber ( sink )
 * <p>
 * <code>
 * <pre>
 * @Service
 * @Startup
 * public class Consumer
 * {
 *   @Inject @Service("pipe:///test")
 *   Pipes<String> _pipes;
 *
 *   @OnInit
 *   public void init()
 *   {
 *     _pipes.consume((message, exception, fail) -> next(message));
 *   }
 *
 *   public void next(String message)
 *   {
 *     System.out.println(message);
 *   }
 * }
 * </pre>
 * </code>
 *
 * @see PipePub
 * @see PipeSub
 */
@Service("pipe:///{name}")
public interface PipeBroker<T>
{
  /**
   * Registers a message consumer.
   *
   * @param result
   */
  void consume(PipeSub<T> result);

  /**
   * Registers a message subscriber. Multiple message subscribers can be
   * registered for the same pipe
   *
   * @param result
   */
  void subscribe(PipeSub<T> result);

  /**
   * Registers a message publisher.
   *
   * @param result
   */
  void publish(PipePub<T> result);

  /**
   * Convenience method for sending messages without a dedicated publisher.
   *
   * @param value
   * @param result
   */
  void send(T value, Result<Void> result);
  
  /*
  default void onChild(@Pin BiConsumer<String,Result<Void>> onChild, 
                       @Pin Result<Cancel> result)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }
  */
}
