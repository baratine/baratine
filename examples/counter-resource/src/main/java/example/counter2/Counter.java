package example.counter2;

import java.util.logging.Logger;
import java.util.logging.Level;

import io.baratine.core.Modify;
import io.baratine.core.OnLoad;
import io.baratine.core.OnSave;
import io.baratine.core.Result;
import io.baratine.store.Store;

public class Counter implements CounterService
{
  private static final Logger log
    = Logger.getLogger(Counter.class.getName());

  private Store<Long> _store;

  private String _id;
  private String _countKey;
  private long _count;

  public Counter(String id, Store<Long> store)
  {
    _id = id;
    _store = store;

    _countKey = "/" + _id + "/count";
  }

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
    if (log.isLoggable(Level.FINE)) {
      log.fine(getClass().getSimpleName() + ".addAndGet: id=" + _id
               + " " + _count + " + " + increment + " " + result);
    }

    _count += increment;

    result.complete(_count);
  }

  @OnLoad
  public void onLoad(Result<Boolean> result)
  {
    if (log.isLoggable(Level.FINE)) {
      log.fine(getClass().getSimpleName() + ".onLoad0: id=" + _id);
    }

    getStore().get(_countKey, result.from(v->onLoadComplete(v)));
  }

  private boolean onLoadComplete(Long longValue)
  {
    if (longValue != null) {
      _count = longValue.longValue();
    }

    if (log.isLoggable(Level.FINE)) {
      log.fine(getClass().getSimpleName() + ".onLoad1: id=" + _id + " done, count=" + _count);
    }

    return true;
  }

  @OnSave
  public void onSave(Result<Boolean> result)
  {
    if (log.isLoggable(Level.FINE)) {
      log.fine(getClass().getSimpleName() + ".onSave0: id=" + _id + ", count=" + _count);
    }

    getStore().put(_countKey, _count);

    result.complete(true);

    if (log.isLoggable(Level.FINE)) {
      log.fine(getClass().getSimpleName() + ".onSave1: id=" + _id + " done");
    }
  }

  @SuppressWarnings("unchecked")
  private Store<Long> getStore()
  {
    return _store;
  }
}
