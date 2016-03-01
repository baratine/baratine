package example;

import io.baratine.core.Result;

public interface Hello
{
  void hello(String arg, Result<String> result);
}
