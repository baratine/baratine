package example.user.impl;

import javax.inject.Inject;

import example.user.api.User;
import example.user.api.UserManager;
import example.user.api.UserService;
import io.baratine.core.Lookup;
import io.baratine.core.Modify;
import io.baratine.core.OnLoad;
import io.baratine.core.OnLookup;
import io.baratine.core.OnSave;
import io.baratine.core.Result;
import io.baratine.core.Service;
import io.baratine.core.ServiceRef;
import io.baratine.store.Store;

@Service("/_user")
public class UserManagerImpl implements UserManager
{
  @Inject @Lookup("/_user")
  private transient ServiceRef _selfRef;

  @Inject @Lookup("store://_user")
  private transient ServiceRef _storeRef;

  @Inject @Lookup("store://_user")
  private transient Store<Long> _store;

  private long _idSequence;

  @OnLookup
  public UserService onLookup(String url)
  {
    Store<User> userStore = _storeRef.lookup(url).as(Store.class);

    return new UserServiceImpl(url, userStore);
  }

  @Modify
  public void create(String firstName, String lastName,
                     Result<User> result)
  {
    long id = _idSequence++;

    UserService user = _selfRef.lookup("/" + id).as(UserService.class);

    user.create(id, firstName, lastName, result);
  }

  public void delete(long id, Result<Boolean> result)
  {
    UserService user = _selfRef.lookup("/" + id).as(UserService.class);

    user.delete(result);
  }

  @OnLoad
  public void onLoad(Result<Boolean> result)
  {
    _store.get("idSequence", result.from(idSequence -> onLoadComplete(idSequence)));
  }

  private boolean onLoadComplete(long idSequence)
  {
    _idSequence = idSequence;

    return true;
  }

  @OnSave
  public void onSave(Result<Boolean> result)
  {
    _store.put("count", _idSequence, result.from(Void -> true));
  }
}
