package example.auction;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import com.caucho.amp.hamp.ClientHamp;
import com.caucho.bartender.champ.ClientChamp;

import io.baratine.core.ResourceManager;
import io.baratine.core.Result;
import io.baratine.core.ResultFailure;
import io.baratine.core.ResultWithFailure;
import io.baratine.core.ServiceRef;

public class AuctionMain
{
  public static void main(String []argv)
    throws Exception
  {
    String url = "http://localhost:8085/s/pod/";

    if (argv.length > 0) {
      url = argv[0];
    }

    ClientHamp client = new ClientHamp(url);
    
    ServiceRef ref = client.lookup("remote:///auction/5");
    AuctionService auction = ref.as(AuctionService.class);

    System.out.println("hello (RPC): " + auction.end());
    //System.out.println("hello (RPC): " + auction.setActive(true));

    final Thread currentThread = Thread.currentThread();
    
    AtomicBoolean isDone = new AtomicBoolean(false);
    
    ref = client.lookup("remote:///auction");
    ResourceManager manager = ref.as(ResourceManager.class);
    
    System.err.println("AuctionMain.main0: " + manager.findOne("_id = 5"));
    //System.err.println("AuctionMain.main0: " + manager.findOne("_id = *"));

    //auction.setActive(false, new MyResult<Boolean>(currentThread));

    
    /*
    auction.setActive(
        false,
        value -> {
            System.out.println("completed: " + value);
        
            isDone.set(true);
        
            currentThread.interrupt();
        },
        error -> {
          System.out.println("exception: " + error.getClass());
          
          error.printStackTrace();
        }
    );
    */
    
    try {
      System.err.println("AuctionMain.main1");
      auction.getBidList(new MyResult<List<Bid>>(currentThread, isDone));
      
      System.out.println("calling thread going to sleep...");
      
      Thread.sleep(1000 * 5);
    }
    catch (Exception e) {
      System.out.println("calling thread woken up");
    }
    
    System.err.println("AuctionMain.main2: " + isDone);

    if (! isDone.get()) {
      System.out.println("error: didn't receive a response");
    }
    
    client.close();
  }
  
  static class MyResult<T> implements ResultWithFailure<T> {
    private final Thread _callingThread;
    private final AtomicBoolean _isDone;
    
    public MyResult(Thread callingThread, AtomicBoolean isDone)
    {
      _callingThread = callingThread;
      
      _isDone = isDone;
    }
    
    public void completed(T result)
    {
      System.out.println("MyResult.completed0: " + result);
      
      _isDone.set(true);

      if (_callingThread != null) {
        _callingThread.interrupt();
      }
    }
    
    public void failed(Throwable ex)
    {
      System.out.println("MyResult.failed0: " + ex);
      
      _isDone.set(true);

      if (_callingThread != null) {
        _callingThread.interrupt();
      }
    }
  }
}
