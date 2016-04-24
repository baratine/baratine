package com.caucho.junit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.caucho.v5.util.IntMap;
import io.baratine.service.Startup;

public class State
{
  private static Logger log = Logger.getLogger(State.class.getName());

  private static String _state = "";
  private static int _clearCount;
  private static int _stateCount;

  private static IntMap _featureMap = new IntMap();

  public static void clear()
  {
    _clearCount++;
    _stateCount = 0;
    _state = "";
    _featureMap.clear();
  }

  public static int getStateCount()
  {
    return _stateCount;
  }

  public static int getClearCount()
  {
    return _clearCount;
  }

  public static int getTestSequence()
  {
    return _clearCount;
  }

  public static int addCount()
  {
    return _stateCount++;
  }

  public static int generateId()
  {
    return addCount();
  }

  public static void setFeature(String name, boolean hasFeature)
  {
    _featureMap.put(name, hasFeature ? 1 : 0);
  }

  public static void setFeature(String name)
  {
    _featureMap.put(name, 1);
  }

  public static boolean hasFeature(String name)
  {
    return _featureMap.get(name) > 0;
  }

  public static void addTime(int sec)
  {
    TestTime.addTime(sec, TimeUnit.SECONDS);
  }

  public static String getState()
  {
    String value = _state;
    _state = "";

    return value;
  }

  public static String stateSorted()
  {
    return sortAndGetState();
  }

  public static String sortAndGetState()
  {
    if (_state.length() == 0) {
      return _state;
    }

    String[] states = _state.split("\\n");
    List<String> list = new ArrayList();
    for (int counter = 0; counter < states.length; ++counter) {
      list.add(states[counter]);
    }

    Collections.sort(list);

    Iterator<String> iterator = list.iterator();
    String value = "";
    while (iterator.hasNext()) {
      value += iterator.next() + "\n";
    }

    _state = "";
    return value;
  }

  public static void addState(String v)
  {
    add(v);
  }

  public static void add(String v)
  {
    synchronized (Startup.class) {
      _state += v;
    }
  }

  public static void addText(String v)
  {
    if (_state.length() == 0)
      add(v);
    else
      add("\n" + v);
  }

  public void setValue(String v)
  {
    addText(v);
  }
}
