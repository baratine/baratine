package ${package};

import com.caucho.junit.ConfigurationBaratine;
import com.caucho.junit.RunnerBaratine;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import io.baratine.core.*;

@RunWith(RunnerBaratine.class)
@ConfigurationBaratine(services = {EchoServiceImpl.class})
public class EchoTest
{
  @Inject @Lookup("/echo")
  private EchoServiceSync _echo;

  @Test
  public void test()
  {
    String message = "Hello World!";

    Assert.assertEquals(message, _echo.echo(message));
  }

  @Test
  public void testAsync()
  {
    String message = "Hello World!";

    ResultFuture<String> future = new ResultFuture<>();

    _echo.echo(message, future);

    String echo = future.get();

    Assert.assertEquals(message, echo);
  }
}
