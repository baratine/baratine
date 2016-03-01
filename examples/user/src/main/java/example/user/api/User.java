package example.user.api;

public class User
{
  private long _id;
  private String _firstName;
  private String _lastName;

  public User(long id, String first, String last)
  {
    _id = id;
    _firstName = first;
    _lastName = last;
  }

  public long getId()
  {
    return _id;
  }

  public String getFirstName()
  {
    return _firstName;
  }

  public String getLastName()
  {
    return _lastName;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _id
                                      + "," + _firstName
                                      + "," + _lastName + "]";
  }
}
