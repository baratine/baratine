package example.arena;

public class Arena
{
  protected long _id;
  protected long _count;
  
  public Arena()
  {
    
  }
  
  public Arena(Arena arena)
  {
    _id = arena.getId();
    _count = arena.getCount();
  }
  
  public long getId()
  {
    return _id;
  }
  
  public long getCount()
  {
    return _count;
  }
}
