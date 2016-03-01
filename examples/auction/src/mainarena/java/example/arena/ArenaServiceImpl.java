package example.arena;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.baratine.core.Modify;
import io.baratine.core.ResourceService;
import io.baratine.core.Services;

@ResourceService("public:///arena/{_id}")
public class ArenaServiceImpl extends Arena implements ArenaService
{
  private static final Logger log 
    = Logger.getLogger(ArenaServiceImpl.class.getName());
  
  public ArenaServiceImpl()
  {
  }
  
  @Modify
  public String addCountLong(long increment)
  { 
    if (increment <= 0) {
      increment = 1;
    }
    
    _count += increment;
    
    long count = _count;
        
    return getClass().getSimpleName() + "[" + _id + "," + count + "," + increment + "]";
  }
  
  @Modify
  public String addCount(String incrementString)
  {
    if (log.isLoggable(Level.FINE)) {
      log.fine("addCount0: " + incrementString);
    }
    
    long increment = 0;
    
    try {
      increment = Long.parseLong(incrementString);
    }
    catch (NumberFormatException e) {
      log.log(Level.FINE, e.getMessage(), e);
    }
    
    return addCountLong(increment);
  }
  
  public long getCount()
  {
    if (log.isLoggable(Level.FINE)) {
      log.fine("getCount0: " + _count);
    }
    
    System.err.println("ArenaServiceImpl.getCount0: " + Services.getCurrentService() + " . " + Services.getCurrentService().hashCode());
    
    return _count;
  }
}
