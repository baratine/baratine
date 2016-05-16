/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Baratine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Baratine; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Alex Rojkov
 */

package com.caucho.junit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.caucho.v5.util.IntMap;
import io.baratine.service.Startup;

/**
 * Class {@code State} is used to accumulate user code state as the test progresses.
 * State can be altered by using one of the add methods or method clear().
 * <p>
 * State is available for reading using method state().
 * <p>
 * Once state() method is called the internal state variable is cleared.
 */
public class State
{
  private static Logger log = Logger.getLogger(State.class.getName());

  private static String _state = "";
  private static int _clearCount;
  private static int _stateCount;

  private static IntMap _featureMap = new IntMap();

  /**
   * Clears the internal state
   */
  public static void clear()
  {
    _clearCount++;
    _stateCount = 0;
    _state = "";
    _featureMap.clear();
  }

  /**
   * Returns value of the state counter
   *
   * @return
   * @see #addCount()
   */
  public static int getStateCount()
  {
    return _stateCount;
  }

  /**
   * Returns number of times method clear() was called
   *
   * @return
   */
  public static int getClearCount()
  {
    return _clearCount;
  }

  public static int getTestSequence()
  {
    return _clearCount;
  }

  /**
   * Increments state counter
   *
   * @return
   */
  public static int addCount()
  {
    return _stateCount++;
  }

  /**
   * Retrieves next unique id value, based on state counter.
   *
   * @return
   */
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

  /**
   * Fast forward test clock by supplied interval, in seconds
   *
   * @param sec
   */
  public static void addTime(int sec)
  {
    TestTime.addTime(sec, TimeUnit.SECONDS);
  }

  /**
   * Sleeps specified number of milliseconds
   *
   * @param ms
   */
  public static void sleep(long ms)
  {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns state and clears internal state variable
   *
   * @return String state representation
   * @see #addText(String)
   * @see #addState(String)
   */
  public static String state()
  {
    String value = _state;
    _state = "";

    return value;
  }

  public static String stateSorted()
  {
    return sortAndGetState();
  }

  /**
   * Splits state at new line character ('\n'), sorts the resulting array and
   * returns joined array. Array is joined using '\n' as a delimiter between its
   * members.
   *
   * @return
   */
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

  /**
   * Adds String value to internal state
   *
   * @param v String value to add
   */
  public static void addState(String v)
  {
    add(v);
  }

  /**
   * Adds String value to internal state
   *
   * @param v String value to add
   */
  public static void add(String v)
  {
    synchronized (Startup.class) {
      _state += v;
    }
  }

  /**
   * Adds String value to internal state if state is empty. If state is not empty
   * value is prepended with new line character ('\n') and then added.
   *
   * @param v String value to add
   */
  public static void addText(String v)
  {
    if (_state.length() == 0)
      addState(v);
    else
      addState("\n" + v);
  }
}
