package example;

import io.baratine.core.Result;
import io.baratine.core.Service;

@Service("public:///hello")
public class HelloImpl implements Hello
{
  public void hello(String arg, Result<String> result)
  {
    if (arg == null) {
      arg = "<no-arg>";
    }

    result.ok("Hello[" + arg + "]");
  }
}
