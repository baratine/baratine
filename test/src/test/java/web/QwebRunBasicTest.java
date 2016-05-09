package web;

import com.caucho.junit.HttpClient;
import com.caucho.junit.WebRunnerBaratine;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(WebRunnerBaratine.class)
public class QwebRunBasicTest
{
  @Test
  public void test404() throws Exception
  {
    HttpClient client = new HttpClient(8080);

    final HttpClient.Request request = client.get("/test");

    int status = request.go().status();

    Assert.assertEquals(404, status);
  }
}
