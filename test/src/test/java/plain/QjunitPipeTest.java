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

import static org.junit.Assert.assertEquals;

import com.caucho.junit.RunnerBaratine;
import com.caucho.junit.ServiceTest;
import com.caucho.junit.State;
import io.baratine.pipe.Message;
import io.baratine.pipe.PipeIn;
import io.baratine.pipe.PipeBrokerSync;
import io.baratine.service.Service;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RunnerBaratine.class)
@ServiceTest(QjunitPipeTest.Q_subString.class)
public class QjunitPipeTest
{
  @Test
  public void test(@Service("pipe:///test") PipeBrokerSync<Message<String>> pipes)
  {
    pipes.send(Message.newMessage("hello"));

    State.sleep(50);

    assertEquals("\nonMessage(hello)", State.state());
  }

  @Service
  public static class Q_subString
  {
    @PipeIn("pipe:///test")
    private void onMessage(String msg)
    {
      State.add("\nonMessage(" + msg + ")");
    }
  }
}
