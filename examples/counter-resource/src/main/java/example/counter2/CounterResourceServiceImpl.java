package example.counter2;

import io.baratine.core.Journal;
import io.baratine.core.OnLookup;
import io.baratine.core.Lookup;
import io.baratine.core.Service;
import io.baratine.store.Store;

import java.util.logging.Logger;
import javax.inject.Inject;

@Journal
@Service("public:///counter-resource")
public class CounterResourceServiceImpl
{
  @Inject @Lookup("store:///counter-resource")
  private Store<Long> _store;

  @OnLookup
  public Counter onLookup(String url)
  {
    int i = url.lastIndexOf('/');
    String id = url.substring(i + 1);

    Counter counter = new Counter(id, _store);

    return counter;
  }
}
