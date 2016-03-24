/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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
 * @author Scott Ferguson
 */

package com.caucho.v5.vfs;

import com.caucho.v5.io.Dependency;
import com.caucho.v5.util.CurrentTime;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Contains a set of dependencies.
 */
public class BasicDependencyContainer
  implements Dependency
{
  private static Logger _log;

  private ArrayList<Dependency> _dependencyList = new ArrayList<Dependency>();

  // Marks if the last check returned modified
  private boolean _isModified;

  // The interval for checking for a dependency.
  private long _checkInterval = 2000L;

  // When the dependency check last occurred
  private long _lastCheckTime = 0;

  private volatile boolean _isChecking;

  /**
   * Adds a dependency.
   */
  public BasicDependencyContainer add(Dependency dependency)
  {
    if (dependency == this)
      throw new IllegalArgumentException("Can't add self as a dependency.");

    if (! _dependencyList.contains(dependency))
      _dependencyList.add(dependency);

    // server/1d0w
    // XXX: _lastCheckTime = 0;

    return this;
  }

  /**
   * Removes a dependency.
   */
  public BasicDependencyContainer remove(Dependency dependency)
  {
    if (dependency == this)
      throw new IllegalArgumentException("Can't remove self as a dependency.");

    _dependencyList.remove(dependency);

    return this;
  }

  /**
   * Sets the check modification check interval in milliseconds.
   * Negative values mean never check. 0 means always check.
   *
   * @param checkInterval how often the dependency should be checked
   */
  public void setCheckInterval(long checkInterval)
  {
    if (checkInterval < 0 || checkInterval > Long.MAX_VALUE / 2)
      _checkInterval = Long.MAX_VALUE / 2;
    else
      _checkInterval = checkInterval;

    _lastCheckTime = 0;
  }

  /**
   * Gets the check modification check interval.
   * Negative values mean never check. 0 means always check.
   */
  public long getCheckInterval()
  {
    return _checkInterval;
  }

  /**
   * Sets the modified.
   */
  public void setModified(boolean isModified)
  {
    _isModified = isModified;
    _lastCheckTime = 0;
  }

  /**
   * Resets the check interval.
   */
  public void resetDependencyCheckInterval()
  {
    _lastCheckTime = 0;
  }

  /**
   * Clears the modified flag and sets the last check time to now.
   */
  public void clearModified()
  {
    _isModified = false;

    _lastCheckTime = CurrentTime.currentTime();
  }

  /**
   * Returns true if the underlying dependencies have changed.
   */
  public boolean isModified()
  {
    synchronized (this) {
      if (_isChecking || _isModified) {
        return _isModified;
      }

      _isChecking = true;
    }

    try {
      long now;

      now = CurrentTime.currentTime();

      if (now < _lastCheckTime + _checkInterval)
        return _isModified;

      _lastCheckTime = now;

      for (int i = _dependencyList.size() - 1; i >= 0; i--) {
        Dependency dependency = _dependencyList.get(i);

        if (dependency.isModified()) {
          dependency.logModified(log());

          _isModified = true;

          return _isModified;
        }
      }

      // _isModified = false;

      return _isModified;
    } finally {
      _isChecking = false;
    }
  }

  /**
   * Log the reason for the modification
   */
  public boolean logModified(Logger log)
  {
    for (int i = _dependencyList.size() - 1; i >= 0; i--) {
      Dependency dependency = _dependencyList.get(i);

      if (dependency.logModified(log))
        return true;
    }

    return false;
  }

  /**
   * Returns true if the underlying dependencies have changed, forcing a check.
   */
  public boolean isModifiedNow()
  {
    _lastCheckTime = 0;

    return isModified();
  }

  private Logger log()
  {
    if (_log == null)
      _log = Logger.getLogger(BasicDependencyContainer.class.getName());

    return _log;
  }

  public String toString()
  {
    return "BasicDependencyContainer" + _dependencyList;
  }
}
