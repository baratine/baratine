package web;

import java.io.IOException;
import java.util.Map;

import com.caucho.junit.Http;
import com.caucho.junit.HttpClient;
import com.caucho.junit.ServiceTest;
import com.caucho.junit.WebRunnerBaratine;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.web.Body;
import io.baratine.web.Get;
import io.baratine.web.Post;
import io.baratine.web.Query;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(WebRunnerBaratine.class)
@ServiceTest(QwebRunJsonTest.Q_basicService.class)
@Http(port = 8086)
public class QwebRunJsonTest
{
  @Test
  public void testMap() throws IOException
  {
    HttpClient client = new HttpClient(8086);

    HttpClient.Response response = client.get("/get?v=Hello+World!").go();

    final Map map = response.readMap();

    Assert.assertEquals("Hello World!", map.get("_value"));
  }

  @Test
  public void getBean(HttpClient client) throws IOException
  {
    HttpClient.Response response = client.get("/get?v=Hello+World!").go();

    Q_fooBean bean = response.readObject(Q_fooBean.class);

    Assert.assertEquals("Q_fooBean[Hello World!]", bean.toString());
  }

  @Test
  public void postBean(HttpClient client) throws Exception
  {
    final HttpClient.Request post = client.post("/post");

    post.body(new Q_fooBean("Who is there?"));

    final HttpClient.Response response = post.go();

    final Q_fooBean bean = response.readObject(Q_fooBean.class);

    Assert.assertEquals("Q_fooBean[Who is there?]", bean.toString());
  }

  @Service
  public static class Q_basicService
  {
    @Get
    public void get(@Query("v") String value, Result<Q_fooBean> result)
    {
      result.ok(new Q_fooBean(value));
    }

    @Post
    public void post(@Body Q_fooBean bean, Result<Q_fooBean> result)
    {
      result.ok(bean);
    }
  }

  public static class Q_fooBean
  {
    private String _value;

    public Q_fooBean()
    {
    }

    public Q_fooBean(String value)
    {
      _value = value;
    }

    @Override
    public String toString()
    {
      return this.getClass().getSimpleName() + "[" + _value + "]";
    }
  }
}
