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

package com.caucho.v5.kelp;

import static com.caucho.v5.kelp.DatabaseKelp.BLOCK_SIZE;

import java.io.IOException;
import java.nio.file.Path;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.store.temp.TempStore;
import com.caucho.v5.store.temp.TempStoreBuilder;
import com.caucho.v5.util.L10N;

/**
 * The store manages the block-based persistent store file.  Each table
 * will have its own store file, table.db.
 *
 * The store is log-based around segments. Each segment is write-only
 * until it is filled and the garbage-collected.
 */
public class DatabaseKelpBuilder
{
  private static final L10N L = new L10N(DatabaseKelpBuilder.class);
  
  static final int SEGMENT_SIZE = 1024 * 1024;
  static final int SEGMENT_TAIL = 2 * BLOCK_SIZE;
  
  private Path _path;
  
  private int _segmentSize = SEGMENT_SIZE;
  private int _segmentSizeMin = -1;
  private int _segmentSizeMax = -1;
  
  private int _segmentSizeFactorNew = 64;
  private int _segmentSizeFactorGc = 8;
  
  private int _btreeNodeSize = 8 * BLOCK_SIZE;
  
  private int _journalSegmentSize = 2 * 1024 * 1024;
  
  private int _deltaMax = 16;
  private long _memorySize = 4 * 1024 * 1024;
  
  private ServiceManagerAmp _rampManager;

  private int _gcThreshold = 4;
  private int _gcMaxCollect = -1;

  private int _blobInlineMax = 2 * 1024;
  private int _blobPageSizeMax;

  private int _deltaLeafMax = 256;
  private int _deltaTreeMax = 8;

  private boolean _isValidate = false; // true;

  private TempStore _tempStore;

  public DatabaseKelpBuilder()
  {
  }
  
  public DatabaseKelpBuilder path(Path path)
  {
    _path = path;
    
    return this;
  }
  
  public DatabaseKelpBuilder segmentSize(int size)
  {
    if (size % BLOCK_SIZE != 0) {
      throw new IllegalArgumentException(L.l("segment must be a multiple of the block size"));
    }
    
    int blocks = size / BLOCK_SIZE;
    
    if (blocks < 8) {
      throw new IllegalArgumentException(L.l("segment must have at least 8 blocks"));
    }
    
    _segmentSize = size;
    
    return this;
  }
  
  public DatabaseKelpBuilder segmentSizeMin(int size)
  {
    if (Integer.bitCount(size) != 1) {
      throw new IllegalArgumentException(L.l("segment 0x{0} must be a power of 2", 
                                             Integer.toHexString(size)));
    }
    
    int blocks = size / BLOCK_SIZE;
    
    if (blocks < 8) {
      throw new IllegalArgumentException(L.l("segment must have at least 8 blocks"));
    }
    
    _segmentSizeMin = size;
    
    return this;
  }
  
  public int getSegmentSizeMin()
  {
    if (_segmentSizeMin > 0) {
      return _segmentSizeMin;
    }
    else {
      return _segmentSize;
    }
  }
  
  public DatabaseKelpBuilder segmentSizeMax(int size)
  {
    if (Integer.bitCount(size) != 1) {
      throw new IllegalArgumentException(L.l("segment 0x{0} must be a power of 2", 
                                             Integer.toHexString(size)));
    }
    
    int blocks = size / BLOCK_SIZE;
    
    if (blocks < 8) {
      throw new IllegalArgumentException(L.l("segment must have at least 8 blocks"));
    }
    
    _segmentSizeMax = size;
    
    return this;
  }
  
  public DatabaseKelpBuilder segmentSizeFactorNew(int factor)
  {
    if (Integer.bitCount(factor) != 1) {
      throw new IllegalArgumentException(L.l("segment factor must be a power of 2", 
                                             Integer.toHexString(factor)));
    }
    
    _segmentSizeFactorNew = factor;
    
    return this;
  }
  
  public int getSegmentSizeFactorNew()
  {
    return _segmentSizeFactorNew;
  }
  
  public DatabaseKelpBuilder segmentSizeFactorGc(int factor)
  {
    if (Integer.bitCount(factor) != 1) {
      throw new IllegalArgumentException(L.l("segment factor must be a power of 2", 
                                             Integer.toHexString(factor)));
    }
    
    _segmentSizeFactorGc = factor;
    
    return this;
  }
  
  public int getSegmentSizeFactorGc()
  {
    return _segmentSizeFactorGc;
  }
  
  public int getSegmentSizeMax()
  {
    if (_segmentSizeMax > 0) {
      return _segmentSizeMax;
    }
    else {
      return _segmentSize;
    }
  }

  public int getSegmentSize()
  {
    return _segmentSize;
  }
  
  public DatabaseKelpBuilder journalSegmentSize(int size)
  {
    if (size % BLOCK_SIZE != 0) {
      throw new IllegalArgumentException(L.l("segment must be a multiple of the block size"));
    }
    
    int blocks = size / BLOCK_SIZE;
    
    if (blocks < 8) {
      throw new IllegalArgumentException(L.l("segment must have at least 8 blocks"));
    }
    
    _journalSegmentSize = size;
    
    return this;
  }

  public int getJournalSegmentSize()
  {
    return _journalSegmentSize;
  }
  
  public DatabaseKelpBuilder btreeNodeSize(int size)
  {
    size = (size + BLOCK_SIZE - 1);
    size -= size % BLOCK_SIZE;
    
    _btreeNodeSize = size;
    
    if (size <= 0) {
      throw new IllegalArgumentException(L.l("{0} is an invalid btreeNodeSize.",
                                             size));
    }
    
    return this;
  }
  
  public int getBtreeNodeLength()
  {
    return _btreeNodeSize;
  }
  
  public DatabaseKelpBuilder deltaMax(int max)
  {
    if (max < 0) {
      throw new IllegalArgumentException();
    }
      
    _deltaMax = max;
    
    return this;
  }
  
  public int getDeltaMax()
  {
    return _deltaMax;
  }
  
  public DatabaseKelpBuilder blobInlineMax(int max)
  {
    if (max < 0) {
      throw new IllegalArgumentException();
    }
      
    _blobInlineMax = max;
    
    return this;
  }
  
  public int getBlobInlineMax()
  {
    return _blobInlineMax;
  }
  
  public DatabaseKelpBuilder blobPageSizeMax(int max)
  {
    if (max < 0) {
      throw new IllegalArgumentException();
    }
      
    _blobPageSizeMax = max;
    
    return this;
  }
  
  public int getBlobPageSizeMax()
  {
    return _blobPageSizeMax;
  }
  
  public DatabaseKelpBuilder gcThreshold(int value)
  {
    if (value < 0) {
      throw new IllegalArgumentException();
    }
    
    _gcThreshold = value;
    
    return this;
    
  }

  public int getGcThreshold()
  {
    return _gcThreshold;
  }
  
  public DatabaseKelpBuilder gcMaxCollect(int value)
  {
    if (value < 1) {
      throw new IllegalArgumentException();
    }
    
    _gcMaxCollect = value;
    
    return this;
    
  }

  public int getGcMaxCollect()
  {
    if (_gcMaxCollect > 0) {
      return _gcMaxCollect;
    }
    else {
      return 2 * _gcThreshold;
    }
  }
  
  public DatabaseKelpBuilder memorySize(long memorySize)
  {
    _memorySize = memorySize;
    
    return this;
  }
  
  public long getMemorySize()
  {
    return _memorySize;
  }
  
  public DatabaseKelpBuilder rampManager(ServiceManagerAmp manager)
  {
    _rampManager = manager;
    
    return this;
  }
  
  public ServiceManagerAmp getManager()
  {
    return _rampManager;
  }

  public DatabaseKelpBuilder deltaLeafMax(int max)
  {
    _deltaLeafMax = max;
    
    return this;
  }

  public int getDeltaLeafMax()
  {
    return _deltaLeafMax;
  }

  public DatabaseKelpBuilder deltaTreeMax(int max)
  {
    _deltaTreeMax = max;
    
    return this;
  }

  public int getDeltaTreeMax()
  {
    return _deltaTreeMax;
  }

  public DatabaseKelpBuilder validate(boolean isValidate)
  {
    _isValidate  = isValidate;
    
    return this;
  }

  public boolean isValidate()
  {
    return _isValidate;
  }

  public DatabaseKelpBuilder tempStore(TempStore tempStore)
  {
    _tempStore = tempStore;

    return this;
  }
  
  public TempStore getTempStore()
  {
    return _tempStore;
  }
  
  public DatabaseKelp build()
  {
    if (_path == null) {
      throw new ConfigException(L.l("database requires a configured path"));
    }

    if (_rampManager == null) {
      _rampManager = Amp.newManager();
    }
    
    if (_tempStore == null) {
      Path tmpPath = _path.resolveSibling(_path.getFileName() + ".tmp");
      
      TempStoreBuilder tempBuilder = new TempStoreBuilder(tmpPath);
      _tempStore = tempBuilder.build();
    }
    
    if (_blobPageSizeMax <= 0) {
      _blobPageSizeMax = Math.max(getSegmentSizeMax() / 4, BLOCK_SIZE * 8);
      _blobPageSizeMax = Math.min(getSegmentSizeMax() - BLOCK_SIZE, _blobPageSizeMax);
    }
    
    if (getSegmentSizeMax() - BLOCK_SIZE < _blobPageSizeMax) {
      throw new ConfigException(L.l("blob-page-size-max {0} is too large for segment-size {1}",
                                    _blobPageSizeMax, getSegmentSizeMin()));
    }
    
    if (getSegmentSizeMin() - BLOCK_SIZE < _btreeNodeSize) {
      throw new ConfigException(L.l("btree-node-size max {0} is too large for segment-size {1}",
                                    _blobPageSizeMax, getSegmentSizeMin()));
    }
    
    
    // Row row = _rowBuilder.build();
    
    try {
      return new DatabaseKelp(_path,
                               this);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
