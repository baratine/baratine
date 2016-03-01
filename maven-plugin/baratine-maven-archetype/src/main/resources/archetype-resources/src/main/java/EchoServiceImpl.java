package ${package};

import io.baratine.core.Result;
import io.baratine.core.Service;

@Service("public:///echo")
public class EchoServiceImpl implements EchoService
{
  public void echo(String message, Result<String> result)
  {
    result.complete(message);
  }
}
