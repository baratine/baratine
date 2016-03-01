package example.counter;

import io.baratine.core.ResultFuture;
import io.baratine.core.ServiceClient;

public class CounterClientMain
{
  public static void main(String[] args)
    throws Exception
  {
    String url = "http://localhost:8085/s/pod";

    if (args.length > 0) {
      url = args[0];
    }

    try (ServiceClient client = ServiceClient.newClient(url).build()) {
      CounterService counterService = client.lookup("remote:///counter")
                                            .as(CounterService.class);

      // nonblocking: using a JDK8 lambda of an async Result
      counterService.addAndGet(
        3,
        longValue -> {
          System.out.println("Count (async): " + longValue);
        }
      );

      CounterServiceSync counterServiceSync
        = client.lookup("remote:///counter")
                .as(CounterServiceSync.class);

      // blocking
      System.out.println("Count: " + counterServiceSync.addAndGet(100));

     Thread.sleep(1000);
    }
  }
}