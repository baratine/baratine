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
 * @author Scott Ferguson
 */

package com.caucho.v5.bartender.files;

import io.baratine.db.Cursor;
import io.baratine.db.DatabaseWatch;
import io.baratine.files.Watch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import com.caucho.v5.util.CacheListener;
import com.caucho.v5.util.HashKey;

/**
 * Saved metadata for a file entry.
 */
public class FileEntry implements CacheListener
{
  private FileServiceRootImpl _root;
  private final String _path;
  private final HashKey _key;
  
  private FileStatusImpl _status;
  private List<String> _list;
  
  private List<WatchImpl> _watchList;
  
  private AtomicLong _writeCount = new AtomicLong();
  private AtomicLong _readCount = new AtomicLong();

  FileEntry(FileServiceRootImpl root,
            String path,
            HashKey key)
  {
    _root = root;
    _path = path;
    _key = key;
  }
  
  public String getPath()
  {
    return _path;
  }
  
  public FileStatusImpl getStatus()
  {
    return _status;
  }
  
  public void setStatus(FileStatusImpl status)
  {
    _status = status;
  }

  public void onChange()
  {
    _status = null;
    // _list = null;
    
    _writeCount.incrementAndGet();
  }
  
  public boolean isReadClean()
  {
    return _readCount.get() == _writeCount.get();
  }
  
  public long getWriteCount()
  {
    return _writeCount.get();
  }
  
  public void setReadCount(long value)
  {
    long prevValue = _readCount.get();
    
    if (prevValue < value) {
      _readCount.compareAndSet(prevValue, value);
    }
  }

  @Override
  public void removeEvent()
  {
    // Used to remove watch
  }
  
  public DatabaseWatch watch(Watch watch)
  {
    return new WatchImpl(watch);
    
    /*
    if (_watchList == null) {
      _watchList = new ArrayList<>();
    }
    
    if (! _watchList.contains(watchImpl)) {
      _watchList.add(watchImpl);
    }
    
    return watchImpl;
    */
  }

  /*
  public DatabaseWatch watch(Watch watch)
  {
    WatchImpl watchImpl = new WatchImpl(watch);
    
    if (_watchList == null) {
      _watchList = new ArrayList<>();
    }
    
    if (! _watchList.contains(watchImpl)) {
      _watchList.add(watchImpl);
    }
    
    return watchImpl;
  }
  
  public void unregisterWatch(Watch watch)
  {
    if (_watchList == null) {
      return;
    }
    
    for (int i = _watchList.size() - 1; i >= 0; i--) {
      WatchImpl watchImpl = _watchList.get(i);
      
      if (watchImpl.getWatch() == watch) {
        _watchList.remove(i);
      }
    }
  }
  */
  
  /*
  public void onWatchNotify()
  {
    if (_watchList == null) {
      return;
    }
    
    onChange();
    
    for (int i = 0; i < _watchList.size(); i++ ){
      WatchImpl watchImpl = _watchList.get(i);
      
      watchImpl.getWatch().onUpdate(_root.getAddress() + _path);
    }
  }
  */

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getPath() + "]";
  }

  private class WatchImpl implements DatabaseWatch {
    private final Watch _watch;

    WatchImpl(Watch watch)
    {
      Objects.requireNonNull(watch);
      
      _watch = watch;
    }

    public Watch getWatch()
    {
      return _watch;
    }

    @Override
    public void onChange(Cursor cursor)
    {
      FileEntry.this.onChange();

      _watch.onUpdate(_root.getAddress() + _path);
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _path + "," + _watch + "]";
    }
  }
}
