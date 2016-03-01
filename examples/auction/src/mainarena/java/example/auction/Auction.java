package example.auction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Auction
{
  protected long _id;
  
  protected String _userId;
  
  protected long _startTimeMs;
  protected long _endTimeMs;

  protected long _durationMs;
  
  protected long _startPrice;
  
  protected String _title;
  protected String _description;
  
  protected List<Bid> _bidList = new ArrayList<Bid>();
  
  public Auction()
  {
  }
  
  public Auction(Auction auction)
  {
    _id = auction.getId();
    _userId = auction.getUserId();
    
    _startTimeMs = auction.getStartTimeMs();
    _endTimeMs = auction.getEndTimeMs();
    
    _durationMs = auction.getDurationMs();
    
    _startPrice = auction.getStartPrice();
    _title = auction.getTitle();
    _description = auction.getDescription();
    
    List<Bid> bidList = auction.getBidList();
    
    if (bidList != null) {
      _bidList.addAll(bidList);
    }  
  }
  
  public Auction(long id,
                 String userId,
                 long startTimeMs,
                 long durationMs,
                 long startPrice,
                 String title,
                 String description)
  {    
    _id = id;
    
    create(userId, startTimeMs, durationMs, startPrice, title, description);
  }
  
  public boolean create(String userId,
                        long startTimeMs,
                        long durationMs,
                        long startPrice,
                        String title,
                        String description)
  {
    if (_startTimeMs > 0) {
      return false;
    }
    
    _userId = userId;
    
    _startTimeMs = startTimeMs;
    _durationMs = durationMs;
    
    _startPrice = startPrice;
    
    _title = title;
    _description = description;
    
    return true;
  }
  
  public boolean addBid(long timeMs, long price, String userId)
  {
    if (! isActive(timeMs)) {
      return false;
    }
    
    if (_bidList.size() > 0) {
      Bid currentHighestBid = _bidList.get(_bidList.size() - 1);
      
      if (price <= currentHighestBid.getPrice()) {
        return false;
      }
    }
        
    Bid bid = new Bid(price, userId, timeMs);
    _bidList.add(bid);
    
    return true;
  }
  
  public boolean cancelBid(long price, String userId)
  {
    if (! isActive()) {
      return false;
    }
    
    Iterator<Bid> iter = _bidList.iterator();
    
    while (iter.hasNext()) {
      Bid bid = iter.next();
      
      if (bid.equals(price, userId)) {
        iter.remove();
        
        return true;
      }
    }
    
    return false;
  }
  
  public boolean end(long endTimeMs)
  {
    if (! isActive()) {
      return false;
    }
    
    _endTimeMs = endTimeMs;
    
    return true;
  }
  
  private boolean isActive()
  {
    return _startTimeMs > 0 && _endTimeMs <= 0;
  }
  
  private boolean isActive(long timeMs)
  {
    return isActive() && _startTimeMs + _durationMs > timeMs;
  }
  
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    
    long dollars = _startPrice / 100;
    long cents = _startPrice % 100;
    
    sb.append(dollars);
    sb.append('.');
    
    if (cents < 10) {
      sb.append('0');
    }
    
    sb.append(cents);
    
    return getClass().getSimpleName() + "[" + _id
                                      + "," + _userId
                                      + "," + _startTimeMs
                                      + "," + _endTimeMs
                                      + ",$" + sb
                                      + "," + _bidList + "]";
  }
  
  public String toString2()
  {
    return toString();
  }
  
  public long getId()
  {
    return _id;
  }
  
  public String getUserId()
  {
    return _userId;
  }
  
  public long getStartTimeMs()
  {
    return _startTimeMs;
  }
  
  public long getEndTimeMs()
  {
    return _endTimeMs;
  }
  
  public long getDurationMs()
  {
    return _durationMs;
  }
  
  public long getStartPrice()
  {
    return _startPrice;
  }
  
  public String getTitle()
  {
    return _title;
  }
  
  public String getDescription()
  {
    return _description;
  }
  
  public List<Bid> getBidList()
  {
    return _bidList;
  }
}
