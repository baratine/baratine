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
    System.err.println("RunnableService.run0");

    runnable.run();

    System.err.println("RunnableService.run1");

    result.ok(null);
  }
}
