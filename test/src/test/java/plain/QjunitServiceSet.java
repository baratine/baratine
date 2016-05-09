package plain;

import javax.inject.Inject;

import com.caucho.junit.RunnerBaratine;
import com.caucho.junit.ServiceTest;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RunnerBaratine.class)
@ServiceTest(QjunitServiceSet.Q_fooServiceImpl.class)
@ServiceTest(QjunitServiceSet.Q_barServiceImpl.class)
public class QjunitServiceSet
{
  @Inject
  @Service("/foo")
  private Q_service _foo;

  @Inject
  @Service("/bar")
  private Q_service _bar;

  @Test
  public void test()
  {
    Assert.assertEquals("Hello ServiceRefLocal[/foo]", _foo.test());

    Assert.assertEquals("Hello ServiceRefLocal[/bar]", _bar.test());
  }

  @Test
  public void test(@Service("/foo") Q_service foo,
                   @Service("/bar") Q_service bar)
  {
    Assert.assertEquals("Hello ServiceRefLocal[/foo]", foo.test());

    Assert.assertEquals("Hello ServiceRefLocal[/bar]", bar.test());
  }

  @Test
  public void testFoo()
  {
    Assert.assertEquals("Hello ServiceRefLocal[/foo]", _foo.test());
  }

  @Test
  public void testBar()
  {
    Assert.assertEquals("Hello ServiceRefLocal[/bar]", _bar.test());
  }

  public interface Q_service
  {
    String test();
  }

  @Service("/foo")
  public static class Q_fooServiceImpl
  {
    public void test(Result<String> result)
    {
      result.ok("Hello " + ServiceRef.current());
    }
  }

  @Service("/bar")
  public static class Q_barServiceImpl
  {
    public void test(Result<String> result)
    {
      result.ok("Hello " + ServiceRef.current());
    }
  }
}
