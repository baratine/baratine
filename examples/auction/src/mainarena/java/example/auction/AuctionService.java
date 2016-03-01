package example.auction;

import io.baratine.core.Result;

import java.util.List;

public interface AuctionService
{
  public boolean create(String userId,
                        long durationMs, long startPrice,
                        String title, String description);
 
  public void delete();
  
  public boolean end();
  
  public List<Bid> getBidList();
  
  public void getBidList(Result<List<Bid>> result);

  public boolean addBid(long price, String userId);
  public boolean cancelBid(long price, String userId);
}
