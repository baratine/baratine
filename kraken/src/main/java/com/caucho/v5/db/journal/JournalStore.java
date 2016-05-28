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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.store.io.InStore;
import com.caucho.v5.store.io.OutStore;
import com.caucho.v5.store.io.StoreBuilder;
import com.caucho.v5.store.io.StoreReadWrite;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.ConcurrentArrayList;
import com.caucho.v5.util.L10N;

/**
 * The store manages the block-based journal store file.
 * 
 * Journals are shared with the same file. Each journal has a unique
 * key.
 */
public class JournalStore
{
  final static long JOURNAL_MAGIC;

  private static final L10N L = new L10N(JournalStore.class);
  
  static final int BLOCK_SIZE = 8 * 1024;
  static final int KEY_LENGTH = 32;
  
  private final StoreReadWrite _store;
  
  private final Path _path;
  
  private final int _segmentSize;
  private final int _blocksPerSegment;
  
  private final int _headLength = 256;
  private final long _tailAddress;
  
  private ConcurrentHashMap<String,JournalGroup> _journalMap
    = new ConcurrentHashMap<>();
    
  private ConcurrentArrayList<JournalSegment> _segmentList
    = new ConcurrentArrayList<>(JournalSegment.class);
    
  private ArrayList<JournalSegment> _freeSegmentList
    = new ArrayList<>();
    
  private JournalSegment _systemSegment;
  private long _address;
  
  JournalStore(Builder builder)
    throws IOException
  {
    _path = builder.getPath();
    
    StoreBuilder storeBuilder = new StoreBuilder(_path);
    storeBuilder.mmap(builder.isMmap());
    storeBuilder.services(builder.services());
    
    _store = storeBuilder.build();
    
    long segmentSize = builder.getSegmentSize();
    segmentSize += (BLOCK_SIZE - 1);
    segmentSize -= segmentSize % BLOCK_SIZE;
    
    if (segmentSize > Integer.MAX_VALUE / 2) {
      throw new IllegalArgumentException();
    }
      
    _segmentSize = (int) segmentSize;
    _blocksPerSegment = (int) (segmentSize / BLOCK_SIZE);
    
    _tailAddress = _segmentSize - BLOCK_SIZE;
    
    if (Files.isReadable(_path)) {
      _store.init();
      initImpl();
    }
    else {
      _store.create();
      createImpl();
    }
  }

  /**
   * Creates an independent store.
   */
  public static JournalStore create(Path path)
    throws IOException
  {
    return create(path, true);
  }

  /**
   * Creates an independent store.
   */
  public static JournalStore createNoMmap(Path path)
    throws IOException
  {
    return create(path, false);
  }

  /**
   * Creates an independent store.
   */
  public static JournalStore createMmap(Path path)
    throws IOException
  {
    return create(path, true);
  }

  /**
   * Creates an independent store.
   */
  public static JournalStore create(Path path, boolean isMmap)
    throws IOException
  {
    // RampManager rampManager = Ramp.newManager();
    
    long segmentSize = 4 * 1024 * 1024;
    
    JournalStore.Builder builder = JournalStore.Builder.create(path);
    
    builder.segmentSize(segmentSize);
    // builder.rampManager(rampManager);
    builder.mmap(isMmap);
    
    JournalStore store = builder.build();

    return store;
  }

  public int getSegmentSize()
  {
    return _segmentSize;
  }

  public long getTailAddress()
  {
    return _tailAddress;
  }
  
  public long getHeadLength()
  {
    return _headLength;
  }

  public JournalGroup openJournal(String name)
  {
    JournalGroup journal = _journalMap.get(name);
    
    if (journal == null) {
      journal = new JournalGroup(this, name);
      
      _journalMap.putIfAbsent(name, journal);
      
      journal = _journalMap.get(name);
    }
    
    return journal;
  }
  
  public JournalStream openJournalStream(String name)
  {
    return new JournalStreamImpl(openJournal(name));
  }
  
  ArrayList<JournalSegment> getReplaySegments(byte []key)
  {
    ArrayList<JournalSegment> list = new ArrayList<>();
    
    for (JournalSegment segment : _segmentList) {
      if (segment.getSequence() > 0
          && Arrays.equals(key, segment.getKey())) {
        list.add(segment);
      }
    }
    
    return list;
  }

  public JournalSegment openSegment(JournalGroup journal, long sequence)
  {
    return allocateSegment(journal.getKey(), sequence);
  }

  /**
   * Creates the store.
   * @throws SQLException 
   */
  private void createImpl()
  {
    _systemSegment = allocateSegment(new byte[32], 1);
    
    _segmentList.add(_systemSegment);
    
    TempBuffer tBuf = TempBuffer.createLarge();
    byte []buffer = tBuf.buffer();
    
    Arrays.fill(buffer, (byte) 0);
    
    int offset = 0;
    
    BitsUtil.writeLong(buffer, offset, JOURNAL_MAGIC);
    offset += 8;
    
    BitsUtil.writeLong(buffer, offset, _segmentSize);
    offset += 8;
    
    BitsUtil.writeLong(buffer, offset, _tailAddress);
    offset += 8;
    
    BitsUtil.writeInt(buffer, offset, KEY_LENGTH);
    offset += 4;
    
    try (OutStore os = _store.openWrite(0, buffer.length)) {
      os.write(0, buffer, 0, buffer.length);
    }
    
    tBuf.free();
  }

  void initImpl()
  {
    int segmentCount = (int) (_store.fileSize() / _segmentSize);
    
    _address = segmentCount * _segmentSize;
    
    TempBuffer tBuf = TempBuffer.createLarge();
    byte []buffer = tBuf.buffer();
    
    for (int i = 1; i < segmentCount; i++) {
      long segmentAddress = i * _segmentSize;

      try (InStore is = _store.openRead(segmentAddress, _segmentSize)) {
        is.read(segmentAddress + _tailAddress, buffer, 0, buffer.length);
        
        int offset = 0;
        
        long initSequence = BitsUtil.readLong(buffer, offset);
        offset += 8;
        
        byte []key = new byte[KEY_LENGTH];
        System.arraycopy(buffer, offset, key, 0, key.length);
        offset += key.length;
        
        long sequence = BitsUtil.readLong(buffer, offset);
        offset += 8;
        
        JournalSegment segment
          = new JournalSegment(this, key, segmentAddress, 
                               initSequence, sequence);
        
        _segmentList.add(segment);
        
        if (initSequence == 0) {
          _freeSegmentList.add(segment);
        }
      }
    }
  }
  
  InStore openRead(long offset, int size)
  {
    return _store.openRead(offset, size);
  }
  
  OutStore openWrite(long offset, int size)
  {
    return _store.openWrite(offset, size);
  }

  void free(JournalSegment segment)
  {
    _freeSegmentList.add(segment);
  }
  
  private synchronized JournalSegment allocateSegment(byte []key,
                                                      long sequence)
  {
    long address = _address;
    
    JournalSegment segment;
    
    if (_freeSegmentList.size() > 0) {
      segment = _freeSegmentList.remove(0);
      _segmentList.remove(segment);
      
      address = segment.getAddress();
    }
    else {
      _address += _segmentSize;
    }
    
    try (OutStore os = _store.openWrite(address, _segmentSize)) {
      segment = new JournalSegment(this, key, address,
                                   sequence, sequence);
      
      TempBuffer tBuf = TempBuffer.create();

      byte []buffer = tBuf.buffer();
      Arrays.fill(buffer, (byte) 0);

      System.arraycopy(key, 0, buffer, 8, key.length);

      os.write(address, buffer, 0, buffer.length);

      segment.writeTail(os);
      
      tBuf.free();
      
      _segmentList.add(segment);
      
      return segment;
    }
  }
  
  public void close()
  {
    for (JournalGroup journal : _journalMap.values()) {
      try {
        journal.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    
    _store.close();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _path + "]";
  }
  
  public static class Builder {
    private final Path _path;
    private long _segmentSize = 4 * 1024 * 1024;
    private boolean _isMmap = true;
    private ServicesAmp _rampManager;
    
    public Builder(Path path)
    {
      Objects.requireNonNull(path);
      
      _path = path;
    }
    
    public static Builder create(Path path)
    {
      return new Builder(path);
    }
    
    public Path getPath()
    {
      return _path;
    }
    
    public Builder segmentSize(long segmentSize)
    {
      if ((segmentSize % BLOCK_SIZE) != 0 || segmentSize <= 0) {
        throw new IllegalArgumentException(L.l("0x{0} is an invalid segment size",
                                               Long.toHexString(segmentSize)));
      }

      _segmentSize = segmentSize;
      
      return this;
    }
    
    public long getSegmentSize()
    {
      return _segmentSize;
    }
    
    public Builder mmap(boolean isMmap)
    {
      _isMmap = isMmap;
      
      return this;
    }
    
    public boolean isMmap()
    {
      return _isMmap;
    }
    
    public Builder rampManager(ServicesAmp manager)
    {
      _rampManager = manager;
      
      return this;
    }
    
    public ServicesAmp services()
    {
      return _rampManager;
    }
    
    public JournalStore build()
      throws IOException
    {
      if (_rampManager == null) {
        _rampManager = ServicesAmp.newManager().get();
      }
      
      return new JournalStore(this);
    }
  }
  
  static {
    byte []magicBytes = "BarJo080".getBytes();
    
    JOURNAL_MAGIC = BitsUtil.readLong(magicBytes, 0);
  }
}
