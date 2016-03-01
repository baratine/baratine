package ${package};

import io.baratine.core.Result;

public interface EchoService
{
  public void echo(String message, Result<String> result);
}
