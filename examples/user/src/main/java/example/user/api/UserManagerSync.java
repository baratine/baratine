package example.user.api;

public interface UserManagerSync
{
  User create(String firstName, String lastName);

  boolean delete(long id);
}
