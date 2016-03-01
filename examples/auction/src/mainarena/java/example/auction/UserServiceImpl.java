package example.auction;

import io.baratine.core.Modify;
import io.baratine.core.ResourceService;
import io.baratine.core.ServiceManager;
import io.baratine.core.ServiceRef;
import io.baratine.core.Services;

@ResourceService("public:///user/{_id}")
public class UserServiceImpl extends User implements UserService
{
  @Override
  public boolean login(String name, String email)
  {
    if (email == null || email.length() == 0) {
      return false;
    }
    else if (getTimeJoinedMs() <= 0) {
      return false;
    }
    else if (! getName().equals(name)) {
      return false;
    }
    else if (! getEmail().equals(email)) {
      return false;
    }
    else {
      //loggedIn();
      
      return true;
    }
  }
  
  @Modify
  @Override
  public boolean create(String name, String email)
  {
    if (getTimeJoinedMs() > 0) {
      return false;
    }
    
    long timeMs = getTimeService().getTime();
    
    create(name, email, timeMs);
    
    return true;
  }
  
  private TimeService getTimeService()
  {
    String serviceName = "public:///time";
    
    ServiceManager serviceManager = Services.getCurrentManager();
    ServiceRef ref = serviceManager.lookup(serviceName);
    
    return ref.as(TimeService.class);
  }
}
