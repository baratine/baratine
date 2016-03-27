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

import java.io.IOException;
import java.nio.file.Path;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.db.journal.JournalStore;
import com.caucho.v5.kelp.segment.SegmentKelp;
import com.caucho.v5.kelp.segment.SegmentKelpBuilder;
import com.caucho.v5.kelp.segment.SegmentService;
import com.caucho.v5.kelp.segment.SegmentServiceImpl;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.store.io.StoreReadWrite;
import com.caucho.v5.store.temp.TempStore;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;



/**
 * btree-based database
 */
public class DatabaseKelp
{
  private static final L10N L = new L10N(DatabaseKelp.class);

  static final int TYPE_INDEX = 60;
  
  static final long PAGE_MASK = (1L << TYPE_INDEX) - 1;
  static final long TYPE_MASK = ~PAGE_MASK;
  
  static final long NODE_MASK = 0x0L << TYPE_INDEX;
  static final long BLOB_MASK = 0x1L << TYPE_INDEX;
  
  public static final int BLOCK_SIZE = 8192;
  
  private final Path _path;
  // private final Row _row;
  
  //private TableKelp _table;
  
  private final int _segmentSizeMin;
  private final int _segmentSizeMax;
  
  private final int _segmentSizeFactorNew;
  private final int _segmentSizeFactorGc;
  
  private final int _btreeNodeLength;
  private final int _deltaMax;
  private final int _gcThreshold;
  private final int _gcMinCollect;
  private final int _gcMaxCollect;
  private final int _blobInlineMax;
  private final long _memoryMax;
  
  private final int _deltaLeafMax;
  private final int _deltaTreeMax;
  
  private ServicesAmp _rampManager;
  
  private DatabaseServiceKelp _dbService;

  private JournalStore _journalStore;

  private Lifecycle _lifecycle = new Lifecycle();

  private final boolean _isValidate;

  private final int _blobPageSizeMax;
  
  private StoreReadWrite _store;

  private SegmentService _segmentService;

  private TempStore _tempStore;

  DatabaseKelp(Path path,
               DatabaseKelpBuilder builder)
    throws IOException
  {
    _path = path;
    // _row = rowBuilder.build(this);
    
    // _table = new TableKelp(this, "table", _row);
    
    _segmentSizeMin = builder.getSegmentSizeMin();
    _segmentSizeMax = builder.getSegmentSizeMax();
    
    _segmentSizeFactorNew = builder.getSegmentSizeFactorNew();
    _segmentSizeFactorGc = builder.getSegmentSizeFactorGc();
    
    if (_segmentSizeMax < _segmentSizeMin) {
      throw new IllegalArgumentException(L.l("Invalid segment size <{0},{1}>",
                                             _segmentSizeMin, _segmentSizeMax));
    }
    
    _btreeNodeLength = builder.getBtreeNodeLength();
    _deltaMax = builder.getDeltaMax();
    _gcThreshold = builder.getGcThreshold();
    _gcMinCollect = 2;
    _gcMaxCollect = builder.getGcMaxCollect();
    _blobInlineMax = builder.getBlobInlineMax();
    _blobPageSizeMax = builder.getBlobPageSizeMax();
    _memoryMax = builder.getMemorySize();
    _deltaLeafMax = builder.getDeltaLeafMax();
    _deltaTreeMax = builder.getDeltaTreeMax();
    
    _isValidate = builder.isValidate();
    
    _tempStore = builder.getTempStore();
    /*
    if (BLOCK_SIZE <= _inlineBlobMax - _row.getLength()) {
      throw new IllegalStateException(L.l("Inline blob size '{0}' is too large",
                                          _inlineBlobMax));
    }
    */
    
    _rampManager = builder.getManager(); // Ramp.newManager();
    
    _dbService = _rampManager.newService(new DatabaseServiceKelpImpl(this))
                             .as(DatabaseServiceKelp.class);
    
    SegmentKelpBuilder segmentBuilder = new SegmentKelpBuilder();
    segmentBuilder.path(path);
    segmentBuilder.ampManager(_rampManager);
    
    int lastSize = _segmentSizeMin;
    
    for (int size = _segmentSizeMin; size <= _segmentSizeMax; size *= 4) {
      segmentBuilder.segmentSize(size);
      lastSize = size;
    }
    
    if (lastSize < _segmentSizeMax) {
      segmentBuilder.segmentSize(_segmentSizeMax);
    }
    
    SegmentServiceImpl segmentServiceImpl = segmentBuilder.build();
    
    _store = segmentServiceImpl.store();
    
    _segmentService = _rampManager.newService(segmentServiceImpl)
                                  .as(SegmentService.class);
    
    String tail = path.getFileName().toString();
    
    Path journalPath = path.resolveSibling(tail + ".log");
    
    JournalStore.Builder journalBuilder;
    journalBuilder = JournalStore.Builder.create(journalPath);
    
    journalBuilder.segmentSize(builder.getJournalSegmentSize());
    journalBuilder.rampManager(_rampManager);
    
    _journalStore = journalBuilder.build();
    
    _lifecycle.toActive();
    
    // checkpoint();
  }
  
  public Path getPath()
  {
    return _path;
  }

  ServicesAmp getRampManager()
  {
    return _rampManager;
  }
  
  StoreReadWrite getSegmentStore()
  {
    return _store;
  }
  
  DatabaseServiceKelp getDatabaseService()
  {
    return _dbService;
  }
  
  SegmentService getSegmentService()
  {
    return _segmentService;
  }

  public int getSegmentSizeMin()
  {
    return _segmentSizeMin;
  }

  public int getSegmentSizeMax()
  {
    return _segmentSizeMax;
  }

  public int getSegmentSizeFactorNew()
  {
    return _segmentSizeFactorNew;
  }

  public int getSegmentSizeFactorGc()
  {
    return _segmentSizeFactorGc;
  }

  public TableKelp getMetaTable()
  {
    // TODO Auto-generated method stub
    return null;
  }
  
  JournalStore getJournalStore()
  {
    return _journalStore;
  }

  TempStore getTempStore()
  {
    return _tempStore;
  }

  public Iterable<TableKelp> getTables()
  {
    return getDatabaseService().getTablesDirect();
  }
  
  public TableKelp getTable(String name)
  {
    return getDatabaseService().getTableByNameDirect(name);
  }

  public TableKelp findTable(byte[] tableKey)
  {
    return getDatabaseService().getTableByKeyDirect(tableKey);
  }

  public void loadTable(byte[] tableKey, Result<TableKelp> result)
  {
    getDatabaseService().loadTable(tableKey, result);
  }

  int getBlockSize()
  {
    return BLOCK_SIZE;
  }
  
  /**
   * Disk delta max.
   */
  public int getDeltaMax()
  {
    return _deltaMax;
  }
  
  /**
   * Memory delta max.
   */
  public int getDeltaLeafMax()
  {
    return _deltaLeafMax;
  }

  public int getDeltaTreeMax()
  {
    return _deltaTreeMax;
  }

  public int getGcMinCollect()
  {
    return _gcMinCollect;
  }

  public int getGcMaxCollect()
  {
    return _gcMaxCollect ;
  }

  public int getBlobInlineMax()
  {
    return _blobInlineMax;
  }

  public int getBlobPageSizeMax()
  {
    return _blobPageSizeMax;
  }

  public long getMemoryMax()
  {
    return _memoryMax;
  }
  
  public long getMemorySize()
  {
    long size = 0;
    
    for (TableKelp table : getTables()) {
      size += table.getMemorySize();
    }

    return size;
  }
  
  public int getMaxNodeLength()
  {
    return _btreeNodeLength;
  }

  public int getGcThreshold()
  {
    return _gcThreshold ;
  }

  public boolean isValidate()
  {
    return _isValidate ;
  }

  public TableBuilderKelp createTable(String string)
  {
    return new TableBuilderKelp(this, string);
  }
  
  public void checkpoint()
  {
    //_pageService.checkpoint();
  }
  
  public void checkpoint(Result<Boolean> cont)
  {
    //_pageService.checkpoint(cont);
  }

  Iterable<SegmentKelp> getSegments()
  {
    //return _readWrite.getSegments();
    
    return null;
  }
  
  //
  // close
  //
  
  public void close()
  {
    close(ShutdownModeAmp.GRACEFUL);
  }
  
  public void closeImmediate()
  {
    close(ShutdownModeAmp.IMMEDIATE);
  }
    
  public void close(ShutdownModeAmp mode)
  {
    if (! _lifecycle.toDestroy()) {
      return;
    }
    
    //_dbService.close(mode, Result.ignore());
    _dbService.close(mode);
  }

  public void closeImpl()
  {
    getSegmentService().close(Result.ignore());
    
    _journalStore.close();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _path + "]";
  }
}
