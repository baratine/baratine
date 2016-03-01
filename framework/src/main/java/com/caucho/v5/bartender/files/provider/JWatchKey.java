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
 * @author Nam Nguyen
 */

package com.caucho.v5.bartender.files.provider;

import io.baratine.files.Watch;
import io.baratine.service.Cancel;

import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.Watchable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JWatchKey implements WatchKey
{
  private JWatchService _watchService;
  private JPath _path;
  private Cancel _watchHandle;

  private Kind<?>[] _events;
  private Modifier[] _modifiers;

  private ArrayList<JWatchEvent> _eventList = new ArrayList<>();

  private State _state = State.READY;

  private enum State {
    READY,
    SIGNALED,
    CLOSED

  };
  
  public JWatchKey(JWatchService watchService,
                   JPath path,
                   Kind<?>[] events,
                   Modifier ... modifiers)
  {
    Objects.requireNonNull(events);

    _watchService = watchService;
    _path = path;
    _events = events;
    _modifiers = modifiers;

    _watchHandle = path.getBfsFile().watch(pathString -> onWatch(pathString));
  }
  
  private void onWatch(String pathString)
  {
    JWatchEvent event = new JWatchEvent(pathString);

    synchronized (this) {
      switch (_state) {
        case READY:
          _eventList.add(event);
          _watchService.queueEvent(this);

          _state = State.SIGNALED;
          break;

        case SIGNALED:
          _eventList.add(event);
          break;

        default:
          break;
      }
    }
  }

  @Override
  public boolean isValid()
  {
    synchronized (this) {
      return _state != State.CLOSED;
    }
  }

  @Override
  public List<WatchEvent<?>> pollEvents()
  {
    ArrayList<JWatchEvent> eventList = new ArrayList<>();

    synchronized (this) {
      eventList.addAll(_eventList);

      _eventList.clear();
    }

    return (List) eventList;
  }

  @Override
  public boolean reset()
  {
    synchronized (this) {
      if (_state == State.CLOSED) {
        return false;
      }
      else if (_eventList.size() > 0) {
        _watchService.queueEvent(this);

        return true;
      }
      else {
        _state = State.READY;

        return true;
      }
    }
  }

  @Override
  public void cancel()
  {
    synchronized (this) {
      _state = State.CLOSED;
    }

    _watchHandle.cancel();

    _watchService.removeWatch(this);
  }

  @Override
  public Watchable watchable()
  {
    return _path;
  }

  protected JPath getPath()
  {
    return _path;
  }

  /*
  protected Watch getWatch()
  {
    return _watch;
  }
  */

  @Override
  public String toString()
  {
    ArrayList<JWatchEvent> eventList = new ArrayList<>();

    synchronized (this) {
      eventList.addAll(_eventList);
    }

    StringBuilder sb = new StringBuilder();

    sb.append('[');
    for (int i = 0; i < _events.length; i++) {
      if (i != 0) {
        sb.append(',');
      }

      sb.append(_events[i]);
    }

    sb.append(']');

    return getClass().getSimpleName() + "[" + _path
                                            + ",state=" + _state
                                            + ",kinds=" + sb
                                            + ",events=" + eventList + "]";
  }
}
