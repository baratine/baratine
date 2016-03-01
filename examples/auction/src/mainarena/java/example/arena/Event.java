package example.arena;

public class Event
{
  protected long _id;
  protected long _arenaId;
  
  private long _time;
  
  public long getId()
  {
    return _id;
  }
  
  public long getArenaId()
  {
    return _arenaId;
  }
  
  public long getTime()
  {
    return _time;
  }
}
