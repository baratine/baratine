package example.counter;

import java.util.logging.Logger;

import javax.inject.Inject;

import io.baratine.core.Journal;
import io.baratine.core.Lookup;
import io.baratine.core.Modify;
import io.baratine.core.OnLoad;
import io.baratine.core.OnSave;
import io.baratine.core.Result;
import io.baratine.core.Service;
import io.baratine.store.Store;

@Journal
@Service("public:///counter")
public class CounterServiceImpl implements CounterService
{
  private static final Logger log
    = Logger.getLogger(CounterServiceImpl.class.getName());

  @Inject @Lookup("store:///counter")
  private Store<Long> _store;

  private long _count;
  private long _saveCount;

  private boolean _isLog = true;

  public void getCount(Result<Long> result)
  {
    result.complete(_count);
  }

  @Modify
  public void incrementAndGet(Result<Long> result)
  {
    result.complete(++_count);
  }

  @Modify
  public void decrementAndGet(Result<Long> result)
  {
    result.complete(--_count);
  }

  @Modify
  public void addAndGet(long increment, Result<Long> result)
  {
    _count += increment;

    result.complete(_count);
  }

  /**
   * Don't need <code>{@link @Modify}</code> because don't care about logging
   * state.
   */
  public void setLogging(boolean isLog)
  {
    _isLog = isLog;
  }

  @OnLoad
  public void onLoad(Result<Boolean> result)
  {
    if (_isLog) {
      log.info("CounterServiceImpl.onLoad0");
    }

    _store.get(
      "count",
      longValue -> {
        if (longValue != null) {
          _count = longValue.longValue();
        }

        if (_isLog) {
          log.info("CounterServiceImpl.onLoad1: done, value=" + longValue);
        }

        result.complete(true);
    });
  }

  @OnSave
  public void onSave(Result<Boolean> result)
  {
    if (_isLog) {
      log.info("CounterServiceImpl.onSave0: count=" + _count);
    }

    _saveCount++;

    _store.put("count", _count);

    if (_isLog) {
      log.info("CounterServiceImpl.onSave1: done, saveCount=" + _saveCount);
    }

    result.complete(true);
  }

}
