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
@ServiceTest(QjunitExtends.Q_basicService.class)
public class QjunitExtends extends QjunitSuper
{
  @Test
  public void test()
  {
    ResultFuture<String> result = new ResultFuture<>();

    _service.test(result);

    Assert.assertEquals("Hello World!", result.get(1, TimeUnit.SECONDS));
  }

  @Service
  public static class Q_basicService
  {
    public void test(Result<String> result)
    {
      result.ok("Hello World!");
    }
  }
}

class QjunitSuper
{
  @Inject
  @Service
  QjunitExtends.Q_basicService _service;
}
