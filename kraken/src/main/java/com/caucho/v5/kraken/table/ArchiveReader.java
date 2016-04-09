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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import com.caucho.v5.h3.H3;
import com.caucho.v5.h3.InH3;
import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * The local file backing for the store
 */
public class ArchiveReader
{
  private static final L10N L = new L10N(ArchiveReader.class);
  private static final long INF = Integer.MAX_VALUE;
  
  private InputStream _is;
  private KrakenImpl _manager;
  private KelpManager _backing;
  private InH3 _hIn;
  
  private String []_storeKeys;
  private String []_entryKeys;

  // private ObjectStore<?,?> _store;

  ArchiveReader(KrakenImpl manager,
                KelpManager backing, 
                InputStream is)
    throws IOException
  {
    Objects.requireNonNull(manager);
    Objects.requireNonNull(backing);
    Objects.requireNonNull(is);
    
    _manager = manager;
    _backing = backing;
    _is = is;
    OutFactoryH3 serializer = H3.newOutFactory().get();
    _hIn = serializer.in(is);
  }
  
  public void restore()
    throws IOException
  {
    String dbVersion = _hIn.readString();

    int len = (int) _hIn.readLong();
    
    if (len < -1) {
      System.out.println("EMPTY:");
      return;
    }
    
    if (len >= 0) {
      for (int i = 0; i < len; i++) {
        restoreStore();
      }
    }
    else {
      // XXX:
      /*
      while (! _hIn.isEnd()) {
        restoreStore();
      }
      */
    }
    
    _backing.waitForFlush();
  }
  
  private void restoreStore()
    throws IOException
  {
    if (_storeKeys == null) {
      fillStoreKeys();
    }
    
    //_hIn.readObjectBeginInternal();
    
    byte []key = (byte[]) _hIn.readObject(); // _hIn.readBytes();
    String guid = _hIn.readString();
    
    // XXX: _store = _manager.getStore(guid);
    
    // XXX: validate key
    
    int len = _hIn.readInt();
    
    if (len < -1) {
      return;
    }
    
    if (len >= 0) {
      for (int i = 0; i < len; i++) {
        restoreEntry();
      }
    }
    else {
      while (_hIn.readLong() > 0) {
        restoreEntry();
      }
    }
  }
  
  private void restoreEntry()
    throws IOException
  {
    if (_entryKeys == null) {
      fillEntryKeys();
    }
    
    //_hIn.readObjectBeginInternal();
    
    byte []key = (byte[]) _hIn.readObject(); // _hIn.readBytes();
    
    RowCursor row = null; // XXX: _backing.openPut(key, _store.getKey());
    
    /*
    try (InputStream is = _hIn.readInputStream()) {
      if (is != null) {
        try (OutputStream os = row.openOutputStream(KelpManager.KEY_OBJECT_INDEX)) {
          IoUtil.copy(is, os);
        }
      }
    }
    
    try (InputStream is = _hIn.readInputStream()) {
      if (is != null) {
        try (OutputStream os = row.openOutputStream(KelpManager.VALUE_OBJECT_INDEX)) {
          IoUtil.copy(is, os);
        }
      }
    }
    */
    
    long version = _hIn.readLong();
    
    row.setLong(KelpManager.VERSION, version);
    
    int valueHash = 3;
    row.setInt(KelpManager.VALUE_HASH, valueHash);
    
    long accessTimeout = _hIn.readLong();
    long accessTime = _hIn.readLong();
    long modifiedTimeout = _hIn.readLong();
    long modifiedTime = _hIn.readLong();
    
    if (accessTimeout < 0) {
      accessTimeout = INF;
    }
    
    if (accessTime < 0) {
      accessTime = CurrentTime.currentTime();
    }
    
    if (modifiedTimeout < 0) {
      modifiedTimeout = INF;
    }
    
    if (modifiedTime < 0) {
      modifiedTime = CurrentTime.currentTime();
    }
    
    row.setLong(KelpManager.ACCESSED_TIME, accessTime);
    row.setLong(KelpManager.ACCESSED_TIMEOUT, accessTimeout);
    row.setLong(KelpManager.MODIFIED_TIME, modifiedTime);
    row.setLong(KelpManager.MODIFIED_TIMEOUT, modifiedTimeout);
    
    // _backing.completePut(row);
    
    // RowEntry entry;
    // XXX: entry = _manager.createEntry(key, _store);
    
    // entry.clear();
  }
  
  private void fillStoreKeys()
    throws IOException
  {
    _storeKeys = null;//_hIn.readObjectDefinitionInternal();
  
    if (! "key".equals(_storeKeys[0])) {
      throw new IllegalStateException(L.l("Key must be first store element."));
    }
  
   // _storeFields = 
     //   System.out.println("SKEY: " + Arrays.asList(_storeKeys));
  }
  
  private void fillEntryKeys()
    throws IOException
  {
    _entryKeys = null;//_hIn.readObjectDefinitionInternal();
  
    if (! "key".equals(_entryKeys[0])) {
      throw new IllegalStateException(L.l("Key must be first entry element."));
    }
  
   // _storeFields = 
     //   System.out.println("SKEY: " + Arrays.asList(_storeKeys));
  }

    /*
    Event event;
    
    if ((event = _in.next()) != Event.START_ARRAY) {
      throw error(L.l("Expected '[' {0} at {1}",
                      Event.START_ARRAY,
                      event));
    }
    
    while ((event = _in.next()) != Event.END_ARRAY && event != null) {
      restoreStoreMap(event);
    }
  }
    */
  
  /*
  public void restoreStoreMap(Event event)
    throws IOException
  {
    if (event != Event.START_OBJECT) {
      throw error(L.l("Expected '{' {0} at {1}",
                      Event.START_OBJECT,
                      event));
    }
    
    String storeGuid = null;
    byte []storeKey = null;
    
    while ((event = _in.next()) == Event.VALUE_STRING && event != null) {
      String key = _in.getString();
      
      switch (key) {
      case STORE_KEY: {
        storeGuid = _in.readString();
        break;
      }
      
      case STORE_KEY_HASH: {
        storeKey = _in.readBinary();
        break;
      }
      
      case STORE_ENTRIES: {
        restoreStoreEntries(storeGuid, storeKey);
        break;
      }
        
      default:
        throw error(L.l("Unknown key {0}", key));
      }
    }
    
    if (event != Event.END_OBJECT) {
      throw error(L.l("Expected '}' {0} at {1}",
                      Event.END_OBJECT,
                      event));
    }
  }
  
  public void restoreStoreEntries(String guid, byte []storeKey)
    throws IOException
  {
    KrakenStore store;
    
    if (storeKey != null) {
      store = _manager.getStore(storeKey).getStore();
    }
    else {
      store = _manager.getStore(guid).getStore();
    }
    
    Event event;
    
    if ((event = _in.next()) != Event.START_ARRAY) {
      throw error(L.l("Expected '[' {0} at {1}",
                      Event.START_ARRAY,
                      event));
    }
    
    while ((event = _in.next()) != Event.END_ARRAY && event != null) {
      restoreEntryMap(event, store);
    }
  }
  
  public void restoreEntryMap(Event event,
                              KrakenStore store)
    throws IOException
  {
    if (event != Event.START_OBJECT) {
      throw error(L.l("Expected '{' {0} at {1}",
                      Event.START_OBJECT,
                      event));
    }
    
    byte []keyHash = null;
    Object key = null;
    Object value = null;
    long version = 0;
    
    ObjectStore objStore = (ObjectStore) store;
    
    while ((event = _in.next()) == Event.VALUE_STRING && event != null) {
      String eventKey = _in.getString();
      
      switch (eventKey) {
      case ENTRY_KEY_HASH: {
        keyHash = _in.readBinary();
        break;
      }
      
      case ENTRY_KEY: {
        key = _in.readString();
        break;
      }
      
      case ENTRY_VALUE: {
        value = _in.readString();
        break;
      }
      
      case ENTRY_VERSION: {
        version = _in.readLong();
        break;
      }
      
      default:
        throw error(L.l("Unknown key {0}", key));
      }
    }
    
    if (keyHash != null) {
      // objStore.openPutEntry(keyHash);
      
      objStore.put(key, value);
    }
    
    if (event != Event.END_OBJECT) {
      throw error(L.l("Expected '}' {0} at {1}",
                      Event.END_OBJECT,
                      event));
    }
  }
  
  private IOException error(String msg)
  {
    return new IOException(msg);
  }
  */
}

