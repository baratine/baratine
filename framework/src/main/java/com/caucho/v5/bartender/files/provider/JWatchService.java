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

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.caucho.v5.util.L10N;

/**
 * BFS implementation of JDK WatchService.
 */
public class JWatchService implements WatchService
{
  private static final L10N L = new L10N(JWatchService.class);

  private final JFileSystem _fileSystem;

  private ArrayList<JWatchKey> _watchList
    = new ArrayList<>();

  private LinkedBlockingQueue<JWatchKey> _eventQueue
    = new LinkedBlockingQueue<>();

  private AtomicBoolean _isClosed = new AtomicBoolean();

  public JWatchService(JFileSystem fileSystem)
  {
    _fileSystem = fileSystem;
  }

  @Override
  public WatchKey poll()
  {
    if (_isClosed.get()) {
      throw new ClosedWatchServiceException();
    }

    return _eventQueue.poll();
  }

  @Override
  public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException
  {
    if (_isClosed.get()) {
      throw new ClosedWatchServiceException();
    }

    return _eventQueue.poll(timeout, unit);
  }

  @Override
  public WatchKey take() throws InterruptedException
  {
    if (_isClosed.get()) {
      throw new ClosedWatchServiceException();
    }

    return _eventQueue.take();
  }

  @Override
  public void close() throws IOException
  {
    ArrayList<JWatchKey> watchList = new ArrayList<>();

    if (_isClosed.getAndSet(true)) {
      return;
    }

    watchList.addAll(_watchList);
    _watchList.clear();

    for (WatchKey key : watchList) {
      key.cancel();
    }
  }

  protected JWatchKey register(JPath path, Kind<?>[] events, Modifier ... modifiers)
  {
    JWatchKey key = new JWatchKey(this, path, events, modifiers);

    synchronized (this) {
      _watchList.add(key);
    }

    return key;
  }

  protected void removeWatch(JWatchKey watchKey)
  {
    synchronized (this) {
      _watchList.remove(watchKey);
    }
  }

  protected void queueEvent(JWatchKey event)
  {
    synchronized (this) {
      if (_eventQueue.contains(event)) {
        throw new IllegalStateException(L.l("watch key already signaled: {0}", event));
      }

      _eventQueue.add(event);
    }
  }

  protected ArrayList<JWatchKey> getWatchList()
  {
    ArrayList<JWatchKey> watchList = new ArrayList<>();

    synchronized (this) {
      watchList.addAll(_watchList);
    }

    return watchList;
  }

  protected ArrayList<JWatchKey> getEventList()
  {
    ArrayList<JWatchKey> eventList = new ArrayList<>();

    synchronized (this) {
      eventList.addAll(_eventQueue);
    }

    return eventList;
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append('[');

    int limit = 5;

    int count = 0;
    for (WatchKey key : getWatchList()) {
      if (count++ != 0) {
        sb.append(',');
      }

      if (count > limit) {
        sb.append("...");
        break;
      }

      sb.append(key);
    }

    sb.append(']');
    String watchStr = sb.toString();

    sb.setLength(0);
    sb.append('[');

    count = 0;
    for (JWatchKey event : getEventList()) {
      if (count++ != 0) {
        sb.append(',');
      }

      if (count > limit) {
        sb.append("...");
        break;
      }

      sb.append(event);
    }

    sb.append(']');
    String eventStr = sb.toString();

    return getClass().getSimpleName() + "[" + _fileSystem
                                            + ",watch=" + watchStr
                                            + ",event=" + eventStr + "]";
  }
}
