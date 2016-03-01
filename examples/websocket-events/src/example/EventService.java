package example;

import io.baratine.core.Result;

public interface EventService
{
  public void setListener(EventListener listener, Result<Boolean> result);
}
