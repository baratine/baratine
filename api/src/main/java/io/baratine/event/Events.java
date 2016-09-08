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

package io.baratine.event;

import io.baratine.service.Cancel;
import io.baratine.service.Pin;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import io.baratine.vault.Vault;

/**
 * Baratine Event Service allows sending and receiving events using convenient
 * application domain API.
 * <p>
 * Contract for sender and receiver can be desribed in a single reusable
 * interface. Obtaining event sender will be done with one of the publisher*
 * methods. Publisher method creates a proxy, which adheres to the interface
 * and is connected to an event queue.
 * <p>
 * <blockquote><pre>
 *   public interface EventActor {
 *     void fire(String data);
 *   }
 *   ...
 *   &#64;Service
 *   &#64;Startup
 *   public class EventSender
 *   {
 *     &#64;Inject
 *     &#64;Service("event://")
 *     private Events _events;
 *     private EventActor _sender;
 *     //
 *     &#64;OnInit
 *     public void init()
 *     {
 *       _events.publisher(EventActor.class,
 *       Result.of(sender -&gt; _sender = sender));
 *     }
 *     //
 *     &#64;Get
 *     public void send(&#64;Query("v") String data, Result&lt;Void&gt; result)
 *     {
 *       _sender.fire(data);
 *       result.ok(null);
 *     }
 *   }
 * </pre></blockquote>
 * In the example above the publisher binds with its API and requires
 * consumer or subscriber binding call that also uses just the API:
 * <p>
 * <blockquote><pre>
 *   &#64;Service
 *   &#64;Startup
 *   public class EventReceiver
 *   {
 *     &#64;Inject
 *     &#64;Service("event://")
 *     private Events _events;
 *     //
 *     &#64;OnInit
 *     public void init()
 *     {
 *       _events.subscriber(EventActor.class,
 *                          new EventReceiverActor(),
 *                          Result.ignore());
 *     }
 *     //
 *     class EventReceiverActor implements EventActor
 *     {
 *       &#64;Override
 *       public void fire(String data)
 *       {
 *         System.out.println("EventReceiverActor: " + data);
 *       }
 *     }
 *   }
 * </pre></blockquote>
 * <p>
 * Registration with path allows for multiple queues. E.g.
 * <blockquote><pre>
 *   //obtain publisher at path "data"
 *   _events.publisherPath("data",
 *                         EventActor.class,
 *                         Result.of(sender -&gt; _sender = sender));
 *   //subscribe to events at path "data"
 *   _events.subscriber("data",
 *                      new EventReceiverActor(),
 *                      Result.ignore());
 * </pre></blockquote>
 * <p>
 * Queues can be hierarchical e.g. "department/shipping", "department/orders".
 *
 * Methods consume and subscribe receive a cancel handler for respective consumers
 * and subscribers. Upon calling the cancel method on the handler event delivery
 * stops.
 */
@Service("event://")
public interface Events extends Vault<String,ServiceRef>
{
  /**
   * Registers an event publisher at a given path.
   *
   * @param path   path / queue name
   * @param api    api class (interface) of the sender proxy
   * @param result holder for the sender proxy instance
   * @param <T>    type i.e. sender interface
   */
  <T> void publisherPath(String path, Class<T> api, @Pin Result<T> result);

  /**
   * Registers an event publisher of a given class.
   * <p>
   * Internally, the api.getName() is used as a path.
   *
   * @param api    api class (interface) of the sender proxy
   * @param result holder for the sender proxy instance
   * @param <T>    type i.e. sender interface
   */
  <T> void publisher(Class<T> api, @Pin Result<T> result);

  /**
   * Registers consumer at a given path.
   * <p>
   * Messages are consumed after reaching a consumer.
   *
   * @param path     path / queue name
   * @param consumer consumer object, must conform to the sender api
   * @param result   holder for event consumer cancel handler
   * @param <T>      type i.e. receiver interface
   */
  <T> void consumer(String path,
                    @Pin T consumer,
                    Result<? super Cancel> result);

  /**
   * Registers consumer using a given api class.
   * <p>
   * Internally, the api.getName() is used as a path.
   *
   * @param api      api class (interface) of a consumer object
   * @param consumer consumer object, must conform to the sender api
   * @param result   holder for event consumer cancel handler
   * @param <T>      type i.e. receiver interface
   */
  <T> void consumer(Class<T> api,
                    @Pin T consumer,
                    Result<? super Cancel> result);

  /**
   * Registers a subscriber at a given path.
   *
   * @param path     path / queue name
   * @param consumer consumer object, must conform to the sender api
   * @param result   holder for event consumer cancel handler
   * @param <T>      type i.e. receiver interface
   */
  <T> void subscriber(String path,
                      @Pin T consumer,
                      Result<? super Cancel> result);

  /**
   * Registers a subscriber using a given api class.
   * <p>
   * Internally, the api.getName() is used as a path
   *
   * @param api      api class (interface) of a consumer object
   * @param consumer consumer object, must conform ot the sender api
   * @param result   holder for event consumer cancel handler
   * @param <T>      type i.e. receiver interface
   */
  <T> void subscriber(Class<T> api,
                      @Pin T consumer,
                      Result<? super Cancel> result);
}
