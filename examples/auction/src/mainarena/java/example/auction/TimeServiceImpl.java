package example.auction;

import io.baratine.core.ResourceService;

@ResourceService("public:///time")
public class TimeServiceImpl implements TimeService
{
  @Override
  public long getTime()
  {
    return System.currentTimeMillis();
  }
}
