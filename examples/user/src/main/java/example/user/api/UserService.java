package example.user.api;

import io.baratine.core.Result;

public interface UserService
{
  void create(long id, String firstName, String lastName, Result<User> result);

  void get(Result<User> result);

  void setName(String firstName, String lastName, Result<Boolean> result);

  void delete(Result<Boolean> result);
}
