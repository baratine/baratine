package example.counter2;

import io.baratine.core.Result;

public interface CounterService
{
  void getCount(Result<Long> result);

  void incrementAndGet(Result<Long> result);

  void decrementAndGet(Result<Long> result);

  void addAndGet(long value, Result<Long> result);
}
