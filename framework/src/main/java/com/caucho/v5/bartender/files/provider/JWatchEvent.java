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

import java.nio.file.WatchEvent;
import java.util.concurrent.atomic.AtomicInteger;

public class JWatchEvent implements WatchEvent<String>
{
  private final String _pathString;

  private AtomicInteger _count = new AtomicInteger(1);

  public static WatchEvent.Kind<String> KIND = new WatchEvent.Kind<String>() {
    public String name()
    {
      return "modify";
    }

    public Class<String> type()
    {
      return String.class;
    }
  };

  public JWatchEvent(String pathString)
  {
    _pathString = pathString;
  }

  @Override
  public Kind<String> kind()
  {
    return KIND;
  }

  @Override
  public int count()
  {
    return _count.get();
  }

  @Override
  public String context()
  {
    return _pathString;
  }

  protected void incrementCount()
  {
    _count.getAndIncrement();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _pathString + "]";
  }

}
