package plain;

import javax.inject.Inject;

import com.caucho.junit.RunnerBaratine;
import com.caucho.junit.ServiceTest;
import com.caucho.junit.State;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RunnerBaratine.class)
@ServiceTest(QjunitServicesLifeCycle.Q_ServiceImpl.class)
public class QjunitServicesLifeCycle
{
  @Inject
  @Service("/hello")
  private Q_service _service;

  @Test
  public void test1()
  {
    Assert.assertEquals("Hello ServiceRefLocal[/hello]", _service.test());
    Assert.assertEquals("\n  Q_ServiceImpl()", State.state());

    Assert.assertEquals("Hello ServiceRefLocal[/hello]", _service.test());
    Assert.assertEquals("", State.state());
  }

  @Test
  public void test2()
  {
    Assert.assertEquals("Hello ServiceRefLocal[/hello]", _service.test());
    Assert.assertEquals("\n  Q_ServiceImpl()", State.state());

    Assert.assertEquals("Hello ServiceRefLocal[/hello]", _service.test());
    Assert.assertEquals("", State.state());
  }

  public interface Q_service
  {
    String test();
  }

  @Service("/hello")
  public static class Q_ServiceImpl
  {
    public Q_ServiceImpl()
    {
      State.addState("\n  Q_ServiceImpl()");
    }

    public void test(Result<String> result)
    {
      result.ok("Hello " + ServiceRef.current());
    }
  }
}
