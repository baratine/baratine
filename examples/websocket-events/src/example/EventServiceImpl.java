package example;

import io.baratine.core.Lookup;
import io.baratine.core.OnInit;
import io.baratine.core.Result;
import io.baratine.core.Service;
import io.baratine.timer.TimerScheduler;
import io.baratine.timer.TimerService;

import javax.inject.Inject;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service("session:///events")
public class EventServiceImpl
{
  private EventListener _listener;

  @Inject
  @Lookup("timer:")
  private TimerService _timer;

  @OnInit
  public void init()
  {
    _timer.runEvery(h -> raise(), 1, TimeUnit.SECONDS, Result.ignore());
  }

  public void setListener(@Service EventListener listener)
  {
    _listener = listener;
  }

  public void raise()
  {
    if (_listener != null)
      _listener.onEvent("Time: " + new Date());
  }
}
