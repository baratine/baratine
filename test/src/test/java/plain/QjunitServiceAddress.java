package plain;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import com.caucho.junit.RunnerBaratine;
import com.caucho.junit.ServiceTest;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;
import io.baratine.service.Service;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RunnerBaratine.class)
@ServiceTest(QjunitServiceAddress.Q_basicServiceImpl.class)
public class QjunitServiceAddress
{
  @Inject
  @Service("/hello")
  private Q_basicService _service;

  @Test
  public void test()
  {
    ResultFuture<String> result = new ResultFuture<>();

    _service.test(result);

    Assert.assertEquals("Hello World!", result.get(1, TimeUnit.SECONDS));
  }

  @Test
  public void test(@Service("/hello") Q_basicService service)
  {
    ResultFuture<String> result = new ResultFuture<>();

    service.test(result);

    Assert.assertEquals("Hello World!", result.get(1, TimeUnit.SECONDS));
  }

  public interface Q_basicService
  {
    public void test(Result<String> result);
  }

  @Service("/hello")
  public static class Q_basicServiceImpl
  {
    public void test(Result<String> result)
    {
      result.ok("Hello World!");
    }
  }
}
