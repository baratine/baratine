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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import com.caucho.v5.util.ConcurrentArrayList;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.Fnv256;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;

/**
 * The store manages the block-based journal store file.
 * 
 * Journals are shared with the same file. Each journal has a unique
 * key.
 */
public class JournalGroup
{
  private static final L10N L = new L10N(JournalGroup.class);
  
  private final JournalStore _store;
  private final String _name;
  private final byte []_key;
  
  private final ArrayList<JournalSegment> _replaySegments
    = new ArrayList<JournalSegment>();
  
  private final ConcurrentArrayList<JournalSegment> _openSegments
    = new ConcurrentArrayList<>(JournalSegment.class);
    
  private long _sequence = 0;
  private int _checkpointEntryMax = Integer.MAX_VALUE / 2;
  
  private boolean _isClosed;
  
  JournalGroup(JournalStore blockStore,
               String name)
  {
    _store = blockStore;
    _name = name;
    
    _key = createKey(name);
    
    _replaySegments.addAll(_store.getReplaySegments(_key));
    
    Collections.sort(_replaySegments, new ReplayComparator());
    
    for (JournalSegment segment : _replaySegments) {
      _sequence = Math.max(segment.getSequence(), _sequence);
    }
  }
  
  public ArrayList<JournalSegment> getReplaySegments()
  {
    return _replaySegments;
  }

  public void startReplay()
  {
    closeSegments();
  }

  public long getReplaySequence()
  {
    return _sequence;
  }

  public void setCheckpointEntryMax(int max)
  {
    if (max <= 0) {
      max = Integer.MAX_VALUE / 2;
    }
    
    _checkpointEntryMax = max;
  }

  public int getCheckpointEntryMax()
  {
    return _checkpointEntryMax;
  }
  
  void checkpoint()
  {
    if (_replaySegments.size() == 0) {
      return;
    }
    
    ArrayList<JournalSegment> replaySegments
      = new ArrayList<>(_replaySegments);
      
    _replaySegments.clear();
    
    for (JournalSegment segment : replaySegments) {
      segment.checkpointClose();
      
      _store.free(segment);
      // XXX: segment - checkpointClose
    }
  }
  
  public JournalSegment openSegment()
  {
    if (_isClosed) {
      throw new IllegalStateException();
    }
    
    _sequence = Math.max(_sequence + 1, CurrentTime.getCurrentTime());
    
    JournalSegment segment = _store.openSegment(this, _sequence);
    
    segment.startGroup(this);
    
    _openSegments.add(segment);
    
    return segment;
  }
  
  void setSequence(long sequence)
  {
    if (sequence != _sequence + 1) {
      throw new IllegalStateException(L.l("Can't assign sequence {0} with current {1}",
                                          sequence, _sequence));
    }
    
    _sequence = sequence;
  }
  
  void closeSegment(JournalSegment segment)
  {
    if (_openSegments.remove(segment)) {
      segment.close();
      
      _replaySegments.add(segment);
    }
  }

  public byte[] getKey()
  {
    return _key;
  }
  
  private byte []createKey(String name)
  {
    Fnv256 hashGen = new Fnv256();
    
    for (int i = 0; i < name.length(); i++) {
      hashGen.update((byte) name.charAt(i));
    }
    
    return hashGen.getDigest();
  }
  
  @Override
  public int hashCode()
  {
    return ((_key[0] << 24)
           + (_key[1] << 16)
           + (_key[2] << 8)
           + (_key[3]));
  }
  
  public boolean equals(Object o)
  {
    if (! (o instanceof JournalGroup)) {
      return false;
    }
    
    JournalGroup group = (JournalGroup) o;
    
    return Arrays.equals(_key, group._key);
  }

  public void close()
  {
    _isClosed = true;
    
    closeSegments();
  }
  
  public void closeSegments()
  {
    for (JournalSegment segment : _openSegments) {
      try {
        segment.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    
    _openSegments.clear();
  }
  
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _name
            + "," + Hex.toHex(_key, 0, 4)
            + "]");
  }
  
  static class ReplayComparator implements Comparator<JournalSegment>
  {
    @Override
    public int compare(JournalSegment a, JournalSegment b)
    {
      return Long.signum(a.getSequence() - b.getSequence());
    }
    
  }
}
