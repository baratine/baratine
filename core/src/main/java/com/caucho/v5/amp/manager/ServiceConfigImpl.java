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


class ServiceConfigImpl implements ServiceConfig
{
  private final String _address;
  private final String _name;
  private final int _capacity;
  private final int _initial;
  private final int _workers;
  private final long _timeout;
  private final QueueFullHandler _queueFullHandler;
  private final boolean _isExport;
  private final boolean _isAutoStart;
  private final boolean _isJournal;
  private final int _journalMaxCount;
  private final long _journalTimeout;

  ServiceConfigImpl(ServiceBuilderImpl builder)
  {
    _address = builder.address();
    
    if (_address != null) {
      _name = _address;
    }
    else {
      _name = builder.name();
    }
    
    _workers = builder.workers();
    
    _initial = builder.queueSize();
    _capacity = builder.queueSizeMax();
    
    _timeout = builder.queueTimeout();
    _queueFullHandler = builder.queueFullHandler();
    
    _isExport = builder.isPublic();
    _isAutoStart = builder.autoStart();
    _isJournal = builder.isJournal();
    _journalMaxCount = builder.journalMaxCount();
    _journalTimeout = builder.journalDelay();
  }
  
  @Override
  public String address()
  {
    return _address;
  }
  
  @Override
  public String name()
  {
    return _name;
  }
  
  @Override
  public int getQueueCapacity()
  {
    return _capacity;
  }

  @Override
  public int getQueueInitialSize()
  {
    return _initial;
  }

  /*
  @Override
  public Guardian getGuardian()
  {
    // TODO Auto-generated method stub
    return null;
  }
  */

  @Override
  public int getMaxWorkers()
  {
    return _workers;
  }

  @Override
  public long getOfferTimeout()
  {
    return _timeout;
  }

  @Override
  public QueueFullHandler getQueueFullHandler()
  {
    return _queueFullHandler;
  }

  @Override
  public boolean isPublic()
  {
    return _isExport;
  }

  @Override
  public boolean isAutoStart()
  {
    return _isAutoStart;
  }

  @Override
  public boolean isJournal()
  {
    return _isJournal;
  }

  @Override
  public int getJournalMaxCount()
  {
    return _journalMaxCount;
  }

  @Override
  public long getJournalDelay()
  {
    return _journalTimeout;
  }
}
