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

import javax.inject.Inject;

import com.caucho.junit.RunnerBaratine;
import com.caucho.junit.ServiceTest;
import com.caucho.junit.State;
import com.caucho.junit.TestTime;
import io.baratine.service.Cancel;
import io.baratine.service.OnInit;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.Startup;
import io.baratine.timer.Timers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

//Specify JUnit Runner to use with this test
@RunWith(RunnerBaratine.class)
//Specify service to deploy
@ServiceTest(QjunitScheduledServiceTest.ScheduledService.class)
public class QjunitScheduledServiceTest
{
  @Test
  public void test(@Service("timer:") Timers timer) throws InterruptedException
  {
    //add a second and check that timer did not fire
    TestTime.addTime(1, TimeUnit.SECONDS);
    Thread.sleep(100);
    Assert.assertEquals("", State.state());

    //add two seconds and check that timer did fire
    TestTime.addTime(2, TimeUnit.SECONDS);
    Thread.sleep(100);

    Assert.assertEquals("\n  fire!", State.state());
  }

  @Service
  @Startup //specifies that Service should be started after deployment
  public static class ScheduledService
  {
    @Inject
    @Service("timer:")
    Timers timers;

    //OnInit method will run on startup of the service
    @OnInit
    public void init()
    {
      timers.runAfter(this::fire, 2, TimeUnit.SECONDS, Result.ignore());
    }

    private void fire(Cancel cancel)
    {
      State.add("\n  fire!");
    }
  }
}
