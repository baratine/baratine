package example.auction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.baratine.core.BeforeCreate;
import io.baratine.core.BeforeDelete;
import io.baratine.core.Lookup;
import io.baratine.core.Modify;
import io.baratine.core.OnActive;
import io.baratine.core.OnLookup;
import io.baratine.core.ResourceManager;
import io.baratine.core.ResourceService;
import io.baratine.core.Result;
import io.baratine.core.ServiceManager;
import io.baratine.core.ServiceRef;
import io.baratine.core.Services;
import io.baratine.timer.TimerService;

@ResourceService("public:///auction/{_id}")
public class AuctionServiceImpl extends Auction implements AuctionService
{
  @OnActive
  public void onActive()
  {
    System.err.println("AuctionServiceImpl.onActive0");
  }
  
  @Modify
  @BeforeCreate
  @Override
  public boolean create(String userId,
                        long durationMs, long startPrice,
                        String title, String description)
  {
    long startTimeMs = getTimeService().getTime();
    
    boolean result = super.create(userId, startTimeMs, durationMs, startPrice, title, description);
    
    /*
    getTimerService().runAfter(new Runnable() {
      public void run() {
        System.err.println("AuctionServiceImpl.create1");
        Thread.dumpStack();
      }
    }, durationMs, TimeUnit.MILLISECONDS);
    */
    
    return result;
  }
  
  @BeforeDelete
  @Override
  public void delete()
  {
  }
  
  @Modify
  @Override 
  public boolean end()
  {
    long endTimeMs = getTimeService().getTime();
    
    return super.end(endTimeMs);
  }
 
  @Override
  public List<Bid> getBidList()
  {
    return super.getBidList();
  }
  
  @Override
  public void getBidList(Result<List<Bid>> result)
  {
    result.completed(getBidList());
  }
  
  @Modify
  @Override
  public boolean addBid(long price, String userId)
  {
    long timeMs = getTimeService().getTime();
    
    return addBid(timeMs, price, userId);
  }
  
  @Modify
  @Override
  public boolean cancelBid(long price, String userId)
  {
    return super.cancelBid(price, userId);
  }
  
  private TimeService getTimeService()
  {
    String serviceName = "public:///time";
    
    ServiceManager serviceManager = Services.getCurrentManager();
    ServiceRef ref = serviceManager.lookup(serviceName);
    
    return ref.as(TimeService.class);
  }
  
  private TimerService getTimerService()
  {
    String serviceName = "public:///timer:";
    
    ServiceManager serviceManager = Services.getCurrentManager();
    ServiceRef ref = serviceManager.lookup(serviceName);
    
    return ref.as(TimerService.class);
  }
}
