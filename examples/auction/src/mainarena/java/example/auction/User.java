package example.auction;

public class User
{
  private long _id;
  private String _name;
  private String _email;
  
  private long _timeJoinedMs;
  private long _timeLastLoginMs;
  
  public User()
  {
  }
  
  public User(long id, String name, String email, long timeJoinedMs)
  {
    this(id, name, email, timeJoinedMs, 0);
  }
  
  public User(long id, String name, String email, long timeJoinedMs, long timeLastLoginMs)
  {
    _id = id;
    
    _name = name;
    _email = email;
    
    _timeJoinedMs = timeJoinedMs;
    _timeLastLoginMs = timeLastLoginMs;
  }
  
  public User(User user)
  {
    _id = user.getId();
    
    _name = user.getName();
    _email = user.getEmail();
    
    _timeJoinedMs = user.getTimeJoinedMs();
    _timeLastLoginMs = user.getTimeLastLoginMs();
  }
  
  public long getId()
  {
    return _id;
  }
  
  public String getName()
  {
    return _name;
  }
  
  public String getEmail()
  {
    return _email;
  }
  
  public long getTimeJoinedMs()
  {
    return _timeJoinedMs;
  }
  
  public long getTimeLastLoginMs()
  {
    return _timeLastLoginMs;
  }
  
  protected void loggedIn(long timeMs)
  {
  }
  
  public void create(String name, String email, long timeJoinedMs)
  {
    _name = name;
    _email = email;
    _timeJoinedMs = timeJoinedMs;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _id
                                      + "," + _name
                                      + "," + _email
                                      + "," + _timeJoinedMs
                                      + "," + _timeLastLoginMs + "]";
  }
}
