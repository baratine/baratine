package example.user.api;

import io.baratine.core.Result;

public interface UserManager
{
  void create(String firstName, String lastName, Result<User> result);

  void delete(long id, Result<Boolean> result);
}
