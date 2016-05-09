package plain;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.caucho.junit.RunnerBaratine;
import com.caucho.junit.State;
import com.caucho.junit.TestTime;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.timer.Timers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RunnerBaratine.class)
public class QjunitServiceTimer
{
  @Inject
  @Service("timer:")
  private Timers _timer;

  @Test
  public void test() throws InterruptedException
  {
    _timer.runAfter(t -> State.add("\n  timer!"),
                    2,
                    TimeUnit.SECONDS,
                    Result.ignore());

    TestTime.addTime(1, TimeUnit.SECONDS);
    Thread.sleep(10);
    Assert.assertEquals("", State.state());

    TestTime.addTime(2, TimeUnit.SECONDS);
    Thread.sleep(10);

    Assert.assertEquals("\n  timer!", State.state());
  }

  @Test
  public void test(@Service("timer:") Timers timer) throws InterruptedException
  {
    timer.runAfter(t -> State.add("\n  timer!"),
                   2,
                   TimeUnit.SECONDS,
                   Result.ignore());

    TestTime.addTime(1, TimeUnit.SECONDS);
    Thread.sleep(10);
    Assert.assertEquals("", State.state());

    TestTime.addTime(2, TimeUnit.SECONDS);
    Thread.sleep(10);

    Assert.assertEquals("\n  timer!", State.state());
  }
}
