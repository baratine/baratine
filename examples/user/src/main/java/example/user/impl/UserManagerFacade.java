package example.user.impl;

import javax.inject.Inject;

import example.user.api.User;
import example.user.api.UserManager;
import example.user.api.UserService;
import io.baratine.core.Lookup;
import io.baratine.core.OnLookup;
import io.baratine.core.Result;
import io.baratine.core.Service;
import io.baratine.core.ServiceRef;

@Service("public:///user")
public class UserManagerFacade implements UserManager
{
  @Inject @Lookup("pod://pod/_user")
  private ServiceRef _userManagerRef;

  @Inject @Lookup("pod://pod/_user")
  private UserManager _userManager;

  @OnLookup
  public UserService onLookup(String url)
  {
    UserService user = _userManagerRef.lookup(url).as(UserService.class);

    return new UserServiceFacadeChild(user);
  }

  @Override
  public void create(String firstName, String lastName, Result<User> result)
  {
    _userManager.create(firstName, lastName, result);
  }

  @Override
  public void delete(long id, Result<Boolean> result)
  {
    _userManager.delete(id, result);
  }

  static class UserServiceFacadeChild implements UserService {
    private UserService _user;

    public UserServiceFacadeChild(UserService user)
    {
      _user = user;
    }

    public void create(long id, String firstName, String lastName, Result<User> result)
    {
      _user.create(id, firstName, lastName, result);
    }

    public void get(Result<User> result)
    {
      _user.get(result);
    }

    public void setName(String firstName, String lastName, Result<Boolean> result)
    {
      _user.setName(firstName, lastName, result);
    }

    public void delete(Result<Boolean> result)
    {
      _user.delete(result);
    }
  }
}
