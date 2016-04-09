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

package com.caucho.v5.kraken.table;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Path;
import java.util.Objects;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.kelp.DatabaseKelp;
import com.caucho.v5.kelp.DatabaseKelpBuilder;
import com.caucho.v5.store.temp.TempStore;
import com.caucho.v5.subsystem.RootDirectorySystem;

/**
 * Builder for the local database manager.
 */
public class KelpManagerBuilder
{
  private TableManagerKraken _tableManager;
  
  private DatabaseKelpBuilder _builder;
  private DatabaseKelp _db;

  private TempStore _tempStore;

  private String _podName;
  
  public KelpManagerBuilder(TableManagerKraken tableManager)
  {
    _tableManager = tableManager;
    
    _builder = new DatabaseKelpBuilder();
    
    _builder.rampManager(AmpSystem.currentManager());
    
    // _builder.segmentSize(8 * 1024 * 1024);
    // _builder.journalSegmentSize(8 * 1024 * 1024);
    
    // variable 256k - 64M
    _builder.segmentSizeMin(256 * 1024);
    _builder.segmentSizeMax(64 * 1024 * 1024);
    
    _builder.journalSegmentSize(1024 * 1024);
    
    //_builder.gcThreshold(32);
    //_builder.gcThreshold(16);
    //_builder.gcThreshold(8);
    _builder.gcThreshold(8);
    
    _builder.memorySize(defaultCapacity());
    
    String clusterId = tableManager.serverSelf().getClusterId();
    
    _podName = clusterId + "_hub";
  }

  public String getPodName()
  {
    return _podName;
  }
  
  public void setMemoryMax(long size)
  {
    _builder.memorySize(size);
  }
  
  public long getMemoryMax()
  {
    if (_db != null) {
      return _db.getMemoryMax();
    }
    else {
      return _builder.getMemorySize();
    }
  }
  
  public TempStore getTempStore()
  {
    return _tempStore;
  }

  private static long defaultCapacity()
  {
    long meg = 1024 * 1024;
    
    long minSize = 1 * meg;
    long maxSize = 128 * meg;

    long maxMemory = getMaxMemory();
    
    long memorySize;
    
    memorySize = ((maxMemory / meg) / 8) * meg;

    memorySize = Math.max(memorySize, minSize);

    if (maxSize < memorySize) {
      memorySize = maxSize; 
    }
    
    int blockCount = (int) (memorySize / DatabaseKelp.BLOCK_SIZE);
    
    int size = 256;
    
    // force to be a smaller power of 2
    for (; 2 * size <= blockCount; size *= 2) {
    }
    
    return Math.max(1024 * 1024, size * DatabaseKelp.BLOCK_SIZE);
  }

  private static long getMaxMemory()
  {
    try {
      MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
      MemoryUsage heap = null;
      
      if (memoryBean != null) {
        heap = memoryBean.getHeapMemoryUsage();
      }
      
      if (heap != null) {
        return Math.max(heap.getMax(), heap.getCommitted());
      }
      else {
        Runtime runtime = Runtime.getRuntime();
        
        return Math.max(runtime.maxMemory(), runtime.totalMemory());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return Runtime.getRuntime().maxMemory();
  }
  
  TableManagerKraken getManager()
  {
    return _tableManager;
  }
  
  public DatabaseKelp getDatabase()
  {
    return _db;
  }

  public KelpManager build()
  {
    Path dataDirectory = RootDirectorySystem.getCurrentDataDirectory();
    Path dir = dataDirectory.resolve("kraken");
   
    _builder.path(dir.resolve("store.db"));
    
    _tempStore = _tableManager.getTempStore();
    Objects.requireNonNull(_tempStore);
    
    _builder.tempStore(_tempStore);
    
    _db = _builder.build();
    
    // _metaUpdateTable = createMetaTableUpdate();
    
    // _metaTable = createMetaTable();
    
    // loadMetaTables(_metaTable);
    
    // XXX: check proper serviceRef
    // _metaTable.addListener(new MetaTableListener());
    
    return new KelpManager(this);
  }
}
