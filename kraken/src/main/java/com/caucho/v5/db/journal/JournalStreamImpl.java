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

package com.caucho.v5.db.journal;

import java.util.Objects;

import com.caucho.v5.util.L10N;

/**
 * Interface to the low level stream.
 * 
 * Each record is:
 *   (short sublen, data, crc32)+
 */
public class JournalStreamImpl implements JournalStream
{
  private static final L10N L = new L10N(JournalStreamImpl.class);
  
  private final JournalGroup _journal;
  
  private JournalSegment _segment;
  
  private boolean _isSaveRequired;
  private boolean _isSaveStarted;
  
  private int _entryCount;
  
  private boolean _isEntry;
  private boolean _isValid;

  private boolean _isCheckpointAssigned;
  
  public JournalStreamImpl(JournalGroup journal)
  {
    _journal = journal;
  }
  
  @Override
  public long getReplaySequence()
  {
    return _journal.getReplaySequence();
  }

  @Override
  public void replay(ReplayCallback callback)
  {
    Objects.requireNonNull(callback);
    
    try {
      _journal.startReplay();
      
      for (JournalSegment segment : _journal.getReplaySegments()) {
        segment.replay(callback);
      }
    } finally {
      callback.completed();
    }
    
    _isSaveRequired = true;
  }
  
  @Override
  public void start()
  {
    _isValid = true;
  }
  
  @Override
  public void write(byte[] buffer, int offset, int length)
  {
    if (! _isValid) {
      return;
    }
    
    JournalSegment segment = getSegment();
    
    if (! _isEntry) {
      _isEntry = true;
      segment.startWrite();
    }

    if (! segment.write(buffer, offset, length)) {
      segment.close();
      _isSaveRequired = true;
      
      _segment = null;
      /*
      _segment = _journal.openSegment();
      _segment.openWrite();
      _segment.startWrite();
      */

      _isEntry = false;
      _isValid = false;
    }
  }

  @Override
  public boolean complete()
  {
    if (! _isValid) {
      return false;
    }

    boolean isValid = _isValid;
    _isValid = false;
    
    _isEntry = false;
    
    JournalSegment segment = _segment;
    
    if (segment != null) {
      segment.completeWrite();
    }
    
    _entryCount++;
    
    if (_journal.getCheckpointEntryMax() < _entryCount) {
      _isSaveRequired = true;
      _entryCount = 0;
    }
    
    return isValid;
  }

  @Override
  public void flush()
  {
    JournalSegment segment = _segment;
    
    if (segment != null) {
      segment.flush();
    }
    
    
    // System.out.println("Journal-Flush:");
  }
  
  @Override
  public void close()
  {
    JournalSegment segment = _segment;
    _segment = null;

    if (segment != null) {
      segment.close();
    }
  }

  @Override
  public boolean isSaveRequired()
  {
    if (_isSaveRequired
        && ! _isCheckpointAssigned
        && ! _isSaveStarted) {
      _isCheckpointAssigned = true;

      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public boolean saveStart()
  {
    if (_isSaveStarted) {
      return false;
    }
    
    _isSaveStarted = true;
    _isSaveRequired = false;
    _isCheckpointAssigned = false;
    _entryCount = 0;
    
    JournalSegment segment = getSegment();
    
    segment.checkpointStart();
    _journal.setSequence(segment.getSequence());
      
    return true;
  }
  
  synchronized private JournalSegment getSegment()
  {
    JournalSegment segment = _segment;
    
    if (segment == null) {
      segment = _journal.openSegment();
      segment.openWrite();
      _segment = segment;
    }
    
    return segment;
    
  }

  @Override
  public boolean saveEnd()
  {
    if (! _isSaveStarted) {
      throw new IllegalStateException(L.l("Journal checkpoint ended with no matching save. {0}", _journal));
    }
    
    _isSaveStarted = false;
    
    if (_segment != null) {
      _segment.checkpointEnd();
    
      // close out previous journals
      _journal.checkpoint();
    }
    
    return true;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _journal + "]";
  }
}
