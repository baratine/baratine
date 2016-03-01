package example.auction;

public class Bid
{
  private long _price;
  
  private String _userId;
  private long _timeMs;
  
  public Bid(long price, String userId, long timeMs)
  {
    _price = price;
    _userId = userId;
    _timeMs = timeMs;
  }
  
  public long getPrice()
  {
    return _price;
  }
  
  public String getUserId()
  {
    return _userId;
  }
  
  public long getTimeMs()
  {
    return _timeMs;
  }
  
  public boolean equals(long price, String userId)
  {
    return _price == price && _userId == userId;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _price
                                      + "," + _userId
                                      + "," + _timeMs + "]";
  }
}
