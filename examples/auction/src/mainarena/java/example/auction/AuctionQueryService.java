package example.auction;

import java.util.List;

public interface AuctionQueryService
{
  public Auction createAuction(String userId,
                               long durationMs, long startPrice,
                               String title, String description);
  
  public Auction getAuction(long id);
  
  public List<Auction> getAuctions(int limit, boolean isDescending);
  
  public List<Auction> getAuctionsByUser(String userId, int limit, boolean isDescending);
  
  public List<Auction> rawQuery(String query);
}
