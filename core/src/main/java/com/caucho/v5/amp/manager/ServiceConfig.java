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

package com.caucho.v5.amp.manager;

import io.baratine.service.QueueFullHandler;

import java.util.concurrent.TimeUnit;

public interface ServiceConfig
{
  String address();
  
  String name();
  
  int getQueueCapacity();
  
  int getQueueInitialSize();
  
  long getOfferTimeout();
  
  QueueFullHandler getQueueFullHandler();
  
  int getMaxWorkers();
  
  boolean isPublic();

  boolean isAutoStart();
  
  boolean isJournal();
  int getJournalMaxCount();
  long getJournalDelay();
  
  /*
  Guardian getGuardian();
  
  interface Guardian
  {
  }
  */
  
  /*
  public static class Builder
  {
    private String _name;
    
    private int _capacity = -1;
    private int _initial = -1;
    private int _maxWorkers = 1;
    private long _timeout = -1;
    private QueueFullHandler _queueFullHandler;
    private boolean _isExport;
    private boolean _isAutoStart;
    
    private boolean _isJournal;
    private int _journalMaxCount = -1;
    private long _journalTimeout = -1;
    
    protected Builder()
    {
    }
    
    public static Builder create()
    {
      return new Builder();
    }
    
    public Builder capacity(int capacity)
    {
      _capacity = capacity;
      
      return this;
    }
    
    public Builder initial(int initial)
    {
      _initial = initial;
      
      return this;
    }
    
    public Builder workers(int workers)
    {
      _maxWorkers = workers;
      
      return this;
    }

    public Builder name(String name)
    {
      _name = name;

      return this;
    }
    
    String getName()
    {
      return _name;
    }
    
    public Builder export(boolean isExport)
    {
      _isExport = isExport;
      
      return this;
    }
    
    public Builder autoStart(boolean isAutoStart)
    {
      _isAutoStart = isAutoStart;
      
      return this;
    }
    
    public boolean isAutoStart()
    {
      return _isAutoStart;
    }
    
    public Builder journal(boolean isJournal)
    {
      _isJournal = isJournal;
      
      return this;
    }
    
    public Builder journalMaxCount(int count)
    {
      _journalMaxCount = count;

      return this;
    }

    public int getJournalMaxCount()
    {
      return _journalMaxCount;
    }
    
    public Builder journalDelay(long timeout, TimeUnit unit)
    {
      if (timeout >= 0) {
        _journalTimeout = unit.toMillis(timeout);
      }
      else {
        _journalTimeout = -1;
      }
      
      return this;
    }

    public long getJournalTimeout()
    {
      return _journalTimeout;
    }
    
    public Builder offerTimeout(long timeout, TimeUnit unit)
    {
      _timeout = unit.toMillis(timeout);
      
      return this;
    }
    
    public Builder queueFullHandler(QueueFullHandler handler)
    {
      _queueFullHandler = handler;
      
      return this;
    }
    
    public ServiceConfig build()
    {
      return new ServiceConfigImpl(this,
                                   _initial,
                                   _capacity,
                                   _maxWorkers,
                                   _isExport,
                                   _isAutoStart,
                                   _isJournal,
                                   _journalMaxCount,
                                   _journalTimeout,
                                   _timeout,
                                   _queueFullHandler);
    }
  }
  */
}
