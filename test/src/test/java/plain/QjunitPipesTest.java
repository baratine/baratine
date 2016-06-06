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

import javax.inject.Inject;

import com.caucho.junit.RunnerBaratine;
import com.caucho.junit.ServiceTest;
import com.caucho.junit.State;
import io.baratine.pipe.Pipe;
import io.baratine.pipe.Pipes;
import io.baratine.service.OnInit;
import io.baratine.service.Service;
import io.baratine.service.Startup;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RunnerBaratine.class)
@ServiceTest(QjunitPipesTest.Publisher.class)
@ServiceTest(QjunitPipesTest.Consumer.class)
public class QjunitPipesTest
{
  @Inject
  @Service
  Publisher _publisher;

  @Inject
  @Service
  Consumer _consumer;

  @Test
  public void test()
  {
    _publisher.publish("Hello World!");
    State.sleep(100);
    Assert.assertEquals("Hello World!", _consumer.state());
  }

  @Service
  @Startup
  public static class Publisher
  {
    private Pipe<String> _pipe;

    @Inject
    @Service("pipe:///test")
    Pipes<String> _pipes;

    @OnInit
    public void init()
    {
      _pipes.publish((out, e) -> ready(out));
    }

    public Void publish(String msg)
    {
      _pipe.next(msg);
      return null;
    }

    public void ready(Pipe<String> pipe)
    {
      _pipe = pipe;
    }
  }

  @Service
  @Startup
  public static class Consumer
  {
    @Inject
    @Service("pipe:///test")
    Pipes<String> _pipes;

    StringBuilder _state = new StringBuilder();

    @OnInit
    public void init()
    {
      _pipes.consume((message, exception, fail) -> next(message));
    }

    public void next(String message)
    {
      _state.append(message);
    }

    public String state()
    {
      return _state.toString();
    }
  }
}
