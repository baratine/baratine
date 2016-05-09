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
