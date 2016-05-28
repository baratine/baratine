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

package com.caucho.v5.kraken.archive;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import com.caucho.v5.h3.H3;
import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.h3.OutH3;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kraken.table.KelpManager;
import com.caucho.v5.kraken.table.KrakenImpl;

/**
 * The local file backing for the store
 */
public class ArchiveOnLoad
{
  private static final long INF = Integer.MAX_VALUE;
  
  private KrakenImpl _manager;
  private OutH3 _out;
  
  private byte []_key;
  private byte []_storeKey;
  private byte []_lastStoreKey;
  private boolean _isFirst = true;
  
  ArchiveOnLoad(KrakenImpl manager, WriteStream os)
    throws IOException
  {
    _manager = manager;
    
    OutFactoryH3 serializer = H3.newOutFactory().get();
    
    _out = serializer.out(os);
    
    _key = new byte[KelpManager.KEY_LEN];
    _storeKey = new byte[KelpManager.KEY_LEN];
    _lastStoreKey = new byte[KelpManager.KEY_LEN];
    
    _out.writeString("Baratine-DB-Archive-0.8.0");
    //_out.writeListBegin(-1, null);
  }
  
  public void complete()
    throws IOException
  {
    endStore();
    
    //_out.writeListEnd();
    _out.writeLong(0);
    _out.close();
  }

  public void onLoad(RowCursor cursor)
      throws IOException
  {
    int storeKeyLow = KelpManager.STORE_LOW_LEN;
    int storeKeyHigh = KelpManager.KEY_LEN - storeKeyLow;
    
    //cursor.getBytes(KelpBackingImpl.STORE_HIGH, _storeKey, 0);
    cursor.getBytes(KelpManager.STORE_LOW, _storeKey, storeKeyHigh);
    
    if (! Arrays.equals(_storeKey, _lastStoreKey)) {
      endStore();
      
      beginStore(_storeKey);
    }
    
    _isFirst = false;
    
    /*
    if (_out.writeObjectBegin(ArchiveEntry.class.getName()) < 0) {
      writeEntryClassHeader();
      _out.writeObjectBegin(ArchiveEntry.class.getName());
    }
    */
    
    cursor.getBytes(KelpManager.KEY, _key, 0);

    //_out.writeBytes(_key);
    _out.writeBinary(_key, 0, _key.length);
    
    try (InputStream is = cursor.openInputStream(KelpManager.KEY_OBJECT_INDEX)) {
      if (is != null) {
        _out.writeBinary(is);
      }
      else {
        _out.writeNull();
      }
    }
    
    try (InputStream is = cursor.openInputStream(KelpManager.VALUE_OBJECT_INDEX)) {
      if (is != null) {
        _out.writeBinary(is);
      }
      else {
        _out.writeNull();
      }
    }

    long version = cursor.getLong(KelpManager.VERSION);
    
    _out.writeLong(version);
    
    long accessTimeout = cursor.getLong(KelpManager.ACCESSED_TIMEOUT);
    long lastAccess = cursor.getLong(KelpManager.ACCESSED_TIME);

    if  (accessTimeout < INF) {
      _out.writeLong(lastAccess);
      _out.writeLong(accessTimeout);
    }
    else {
      _out.writeLong(-1);
      _out.writeLong(-1);
    }
    
    long modifiedTimeout = cursor.getLong(KelpManager.MODIFIED_TIMEOUT);
    long lastModified= cursor.getLong(KelpManager.MODIFIED_TIME);

    if  (accessTimeout < INF) {
      _out.writeLong(lastModified);
      _out.writeLong(modifiedTimeout);
    }
    else {
      _out.writeLong(-1);
      _out.writeLong(-1);
    }
    
    //_out.writeObjectEnd();
  }
  
  private void writeEntryClassHeader()
    throws IOException
  {
    _out.writeLong(8);
    _out.writeString("key");
    _out.writeString("key_object");
    _out.writeString("value_object");
    _out.writeString("version");
    
    _out.writeString("accessed_timeout");
    _out.writeString("accessed_time");
    _out.writeString("modified_timeout");
    _out.writeString("modified_time");
  }
  
  public void endStore()
    throws IOException
  {
    if (! _isFirst) {
      //_out.writeListEnd();
      //_out.writeObjectEnd();
      _out.writeLong(0);
      _out.writeLong(0);
    }
    
    _isFirst = true;
  }
  
  public void beginStore(byte []storeKey)
    throws IOException
  {
    System.arraycopy(_storeKey, 0, _lastStoreKey, 0, _storeKey.length);
    
    _isFirst = true;
    
    /*
    if (_out.writeObjectBegin(ArchiveStore.class.getName()) < 0) {
      writeStoreClassHeader();
      _out.writeObjectBegin(ArchiveStore.class.getName());
    }
    */
    
    // StoreHandle handle =  null;//_manager.getStore(storeKey);
    /*
    ObjectStore<?,?> store = null;//handle.getStore();
    
    _out.writeBytes(storeKey);
    _out.writeString(store.getGuid());
    */
    
    //_out.writeListBegin(-1, null);
    _out.writeLong(0);
  }
  
  private void writeStoreClassHeader()
    throws IOException
  {
    //_out.writeClassFieldLength(3);
    _out.writeString("key");
    _out.writeString("guid");
    _out.writeString("entries");
  }
}
