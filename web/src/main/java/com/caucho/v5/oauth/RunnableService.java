package com.caucho.v5.oauth;

import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.Workers;

@Service
@Workers(32)
public class RunnableService
{
  public void run(Runnable runnable, Result<Void> result)
  {
    try {
      runnable.run();
      result.ok(null);
    }
    catch (Exception e) {
      result.fail(e);
    }
  }
}
