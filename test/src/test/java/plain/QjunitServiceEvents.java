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
 * @author Alex Rojkov
 */

package plain;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.inject.Inject;

import com.caucho.junit.RunnerBaratine;
import com.caucho.junit.State;
import io.baratine.event.Events;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RunnerBaratine.class)
public class QjunitServiceEvents
{
  @Inject
  @Service("event:")
  private Events _events;

  @Test
  public void test() throws InterruptedException
  {
    _events.consumer(Consumer.class,
                     c -> {State.addText(c.toString());},
                     Result.ignore());

    ResultFuture<Consumer> result = new ResultFuture<>();

    _events.publisher(Consumer.class, result);

    Consumer<String> publisher = result.get(1, TimeUnit.SECONDS);

    publisher.accept("Hello World!");

    ServiceRef.flushOutbox();

    Thread.sleep(10);

    Assert.assertEquals("Hello World!", State.state());
  }

  @Test
  public void test(@Service("event:") Events events) throws InterruptedException
  {
    events.consumer(Consumer.class,
                    c -> {State.addText(c.toString());},
                    Result.ignore());

    ResultFuture<Consumer> result = new ResultFuture<>();

    events.publisher(Consumer.class, result);

    Consumer<String> publisher = result.get(1, TimeUnit.SECONDS);

    publisher.accept("Hello World!");

    ServiceRef.flushOutbox();

    Thread.sleep(10);

    Assert.assertEquals("Hello World!", State.state());
  }
}
