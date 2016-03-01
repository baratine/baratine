package example.user.api;


public interface UserServiceSync
{
  User create(long id, String firstName, String lastName);

  User get();

  boolean setName(String firstName, String lastName);

  boolean delete();
}
