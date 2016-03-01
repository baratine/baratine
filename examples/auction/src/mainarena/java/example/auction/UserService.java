package example.auction;

public interface UserService
{
  public boolean login(String name, String email);
  
  public boolean create(String name, String email);
}
