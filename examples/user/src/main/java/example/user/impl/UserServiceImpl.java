package example.user.impl;

import example.user.api.User;
import example.user.api.UserService;
import io.baratine.core.Modify;
import io.baratine.core.OnLoad;
import io.baratine.core.OnSave;
import io.baratine.core.Result;
import io.baratine.store.Store;

public class UserServiceImpl implements UserService
{
  private String _url;
  private User _user;

  private transient Store<User> _store;
  private transient ServiceState _state = ServiceState.UNINITIALIZED;

  public UserServiceImpl(String url, Store<User> store)
  {
    _url = url;

    _store = store;
  }

  public void get(Result<User> result)
  {
    validateState();

    result.complete(_user);
  }

  @Modify
  public void setName(String firstName, String lastName, Result<Boolean> result)
  {
    validateState();

    User user = new User(_user.getId(), firstName, lastName);

    _user = user;

    result.complete(true);
  }

  @Modify
  public void delete(Result<Boolean> result)
  {
    validateState();

    _user = null;

    _state = _state.toDeleted();

    result.complete(true);
  }

  @Modify
  public void create(long id, String firstName, String lastName, Result<User> result)
  {
    _user = new User(id, firstName, lastName);

    _state = _state.toInitialized();

    result.complete(_user);
  }

  @OnLoad
  public void onLoad(Result<Boolean> result)
  {
    _store.get(_url, result.from(user -> onLoadComplete(user)));
  }

  private boolean onLoadComplete(User user)
  {
    if (user != null) {
      _user = user;

      _state = _state.toInitialized();
    }

    return true;
  }

  @OnSave
  public void onSave(Result<Boolean> result)
  {
    if (_state == ServiceState.INITIALIZED) {
      _store.put(_url, _user, result.from(Void -> true));
    }
    else if (_state == ServiceState.DELETED) {
      _store.remove(_url, result.from(Void -> true));
    }
  }

  private void validateState()
  {
    if (_state != ServiceState.INITIALIZED) {
      throw new IllegalStateException("user is not initialized, current state: " + _state);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _user + "]";
  }
}
