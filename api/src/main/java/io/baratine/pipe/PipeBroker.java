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
 * The {@code PipeBroker} is a manager which connect message publishers with
 * message subscribers or consumers.
 * <p>
 * An addressable instance of a manager is available using "pipe:" scheme.
 * <p>
 * Full address of the pipe should include pipe name e.g. "pipe:///quotes"
 * <p>
 * <p>
 * Example: message publisher
 * <p>
 * <blockquote>
 * <pre>
 * &#64;Service
 * &#64;Startup
 * public class Publisher
 * {
 *   private Pipe&lt;String&gt; _pipe;
 *
 *   &#64;Inject &#64;Service("pipe:///quotes")
 *   private PipeBroker&lt;String&gt; _pipes;
 *
 *   &#64;OnInit
 *   public void init()
 *   {
 *     //request PipeBroker to create a Pipe instance at "pipe:///quotes"
 *     //callback {@code ready} receives an initialized Pipe instance
 *     //available to send messages
 *     _pipes.publish((pipe,exception)-&gt; ready(pipe));
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
 *   //callback {@code ready} is called by the PipeBroker when Pipe is established
 *   //argument pipe will be used for sending messages through the pipe
 *   public void ready(Pipe&lt;String&gt; pipe)
 *   {
 *     _pipe = pipe;
 *   }
 * }
 * </pre>
 * </blockquote>
 * <p>
 * Example: message subscriber
 * <p>
 * <blockquote>
 * <pre>
 * &#64;Service
 * &#64;Startup
 * public class Consumer
 * {
 *   &#64;Inject &#64;Service("pipe:///quotes")
 *   PipeBroker&lt;String&gt; _pipes;
 *
 *   &#64;OnInit
 *   public void init()
 *   {
 *     _pipes.consume((message, exception, isClosed) -&gt; onMessage(message, exception, isClosed));
 *   }
 *
 *   public void onMessage(String message, Exception e, boolean isClosed)
 *   {
 *     if (_isClosed) {
 *       //handle closed
 *     } else if (e != null) {
 *       //handle exception
 *     } else {
 *       //handle message e.g.
 *       System.out.println(message);
 *     }
 *   }
 * }
 * </pre>
 * </blockquote>
 *
 * @see PipePub
 * @see PipeSub
 */
@Service("pipe:///{name}")
public interface PipeBroker<T>
{
  /**
   * Registers a message consumer.
   * e.g.
   * <blockquote><pre>
   * &#64;&#64;Service
   * public class MyConsumer {
   *   &#64;Inject &#64;Service("pipe:///foo")
   *   PipeBroker&lt;String&gt; _pipes;
   *   //
   *   &#64;OnInit
   *   public void init() {
   *     _pipes.consume((message, exception, isClosed)-&gt;{//handle the message});
   *   }
   * }
   * </pre></blockquote>
   *
   * @param consumer subscriber callback for incoming messages, exceptions or close events.
   */
  void consume(PipeSub<T> consumer);

  /**
   * Registers a message subscriber. Multiple message subscribers can be
   * registered for the same pipe.
   * <p>
   * <blockquote><pre>
   * PipeBroker&lt;String&gt; pipes;
   * //register first subscriber
   * pipes.subscribe((message, exception, isClosed)-&gt;{ //handle the message});
   * //register additional subscriber
   * pipes.subscribe((message, exception, isClosed)-&gt;{ //handle the message});
   * </pre></blockquote>
   *
   * @param subscriber subscriber callback for incoming messages, exceptions or close events
   */
  void subscribe(PipeSub<T> subscriber);

  /**
   * Registers a message publisher.
   * <p>
   * <blockquote><pre>
   *   PipeBroker&lt;String&gt; pipes
   *   pipes.publish((pipe, exception)-&gt;pipe.next("GOLD 1,329.00"));
   * </pre></blockquote>
   *
   * @param publisher publisher call back for obtaining publishing end of the pipe or
   *               exception which prevented establishing a pipe.
   *
   */
  void publish(PipePub<T> publisher);

  /**
   * Convenience method for sending messages without a dedicated publisher.
   * <p>
   * <blockquote>
   * <pre>
   * public class QuoteManager {
   *   &#64;Inject &#64;Service("pipe:///quote")
   *   private PipeBroker _pipe;
   *
   *   public void publishQuote() {
   *     _pipe.send("GOLD 1,329.00", Result.ignore());
   *   }
   * }
   * </pre>
   * </blockquote>
   *
   * @param value message to send
   * @param result asynchronous result of type Void, must be ignored.
   */
  void send(T value, Result<Void> result);
}
