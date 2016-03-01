package example;

import io.baratine.core.ResultFuture;
import io.baratine.core.ServiceClient;

public class HelloMain
{
  public static void main(String[] args)
    throws Exception
  {
    String url = "http://localhost:8085/s/pod/";

    if (args.length > 0) {
      url = args[0];
    }

    try (ServiceClient client = ServiceClient.newClient(url).build()) {
      Hello hello = client.lookup("remote:///hello")
                          .as(Hello.class);

      hello.hello("Result", (s,e) -> {
        System.out.println("hello (Result): " + s);
      });

      Thread.sleep(1000);

      HelloSync helloSync = client.lookup("remote:///hello")
                                  .as(HelloSync.class);

      System.out.println("hello (RPC): " + helloSync.hello("rpc"));

      ResultFuture<String> future = new ResultFuture<>();
      hello.hello("future", future);
      System.out.println("hello (future): " + future.get());
    }
  }
}
