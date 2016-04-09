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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableListener;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.query.ExprKelp;
import com.caucho.v5.kraken.query.CursorKraken;
import com.caucho.v5.util.Hex;

import io.baratine.db.Cursor;
import io.baratine.db.DatabaseWatch;

/**
 * manager for table listeners.
 */
class WatchTable implements TableListener
{
  private TableKraken _table;
  
  private HashMap<WatchKey,ArrayList<WatchEntry>> _entryMapLocal
    = new HashMap<>();
  
  private HashMap<WatchKey,ArrayList<WatchEntry>> _entryMapRemote
    = new HashMap<>();
  
  // private WatchKey _watchKey = new WatchKey();
  
  private ExprKelp []_results;

  WatchTable(TableKraken table)
  {
    _table = table;

    _results = new ExprKelp[0];
  }
  
  TableKraken table()
  {
    return _table;
  }

  void addWatchLocal(DatabaseWatch watch, byte []key)
  {
    addWatch(_entryMapLocal, watch, key);
  }

  void removeWatchLocal(DatabaseWatch watch, byte[] key)
  {
    removeWatch(_entryMapLocal, watch, key);
  }

  void addWatchForeign(DatabaseWatch watch, byte []key)
  {
    WatchKey watchKey = new WatchKey(key);
    
    ArrayList<WatchEntry> list = getWatchList(_entryMapRemote, watchKey);
    
    WatchEntry watchEntry = new WatchEntry(watch, key);

    if (! list.contains(watchEntry)) {
      list.add(watchEntry);
    }
  }

  private void addWatch(HashMap<WatchKey,ArrayList<WatchEntry>> entryMap,
                        DatabaseWatch watch, 
                        byte []key)
  {
    WatchKey watchKey = new WatchKey(key);

    ArrayList<WatchEntry> list = getWatchList(entryMap, watchKey);

    WatchEntry watchEntry = new WatchEntry(watch, key);
    
    list.add(watchEntry);
  }

  private void removeWatch(HashMap<WatchKey,ArrayList<WatchEntry>> entryMap,
                           DatabaseWatch watch, 
                           byte []key)
  {
    WatchKey watchKey = new WatchKey(key);

    ArrayList<WatchEntry> list = entryMap.get(watchKey);
    
    if (list != null) {
      for (int i = list.size() - 1; i >= 0; i--) {
        WatchEntry entry = list.get(i);
        
        if (entry.isMatch(key, watch)) {
          list.remove(i);
        }
      }
    }
  }
  
  private ArrayList<WatchEntry> 
  getWatchList(HashMap<WatchKey,ArrayList<WatchEntry>> entryMap,
               WatchKey watchKey)
  {
    ArrayList<WatchEntry> list = entryMap.get(watchKey);

    if (list == null) {
      list = new ArrayList<>();
      entryMap.put(watchKey, list);
    }

    return list;
  }

  /**
   * Notification on a table put.
   * 
   * @param key the key of the updated row
   * @param type the notification type (local/remote)
   */
  @Override
  public void onPut(byte[] key, TypePut type)
  {
    //_watchKey.init(key);
    WatchKey watchKey = new WatchKey(key);
   
    switch (type) {
    case LOCAL:
      ArrayList<WatchEntry> listLocal = _entryMapLocal.get(watchKey);
      onPut(listLocal, key);
      break;
      
    case REMOTE:
    {
      int hash = _table.getPodHash(key);
      TablePodNodeAmp node = _table.getTablePod().getNode(hash);
      
      if (node.isSelfCopy()) {
        // copies are responsible for their own local watch events
        onPut(key, TypePut.LOCAL);
      }
      
      if (node.isSelfOwner()) {
        // only the owner sends remote watch events
        /*
        System.out.println("NSO: " +  BartenderSystem.getCurrentSelfServer().getDisplayName()
                           + " " + node.isSelfOwner() + " " + node + " " + Hex.toHex(key));
                           */
        ArrayList<WatchEntry> listRemote = _entryMapRemote.get(watchKey);
        onPut(listRemote, key);
      }
      break;
    }
      
    default:
      throw new IllegalArgumentException(String.valueOf(type));
    }
  }
  
  /**
   * Notify all watches with the updated row for the given key.
   * 
   * @param list watch list
   * @param key the updated row's key
   */
  private void onPut(ArrayList<WatchEntry> list, byte []key)
  {
    if (list != null) {
      int size = list.size();

      for (int i = 0; i < size; i++) {
        WatchEntry entry = list.get(i);

        RowCursor rowCursor = _table.cursor();
        rowCursor.setKey(key, 0);

        EnvKelp envKelp = null; 
        
        CursorKraken cursor = new CursorKraken(table(), envKelp, rowCursor, _results);

        entry.onPut(cursor);
      }
    }
  }

  @Override
  public void onRemove(byte[] key, TypePut type)
  {
    //_watchKey.init(key);
    WatchKey watchKey = new WatchKey(key);

    ArrayList<WatchEntry> list = _entryMapLocal.get(watchKey);

    if (list != null) {
      int size = list.size();

      for (int i = 0; i < size; i++) {
        WatchEntry entry = list.get(i);

        RowCursor rowCursor = _table.cursor();
        rowCursor.setKey(key, 0);

        EnvKelp envKelp = null;

        CursorKraken cursor = new CursorKraken(table(), envKelp, rowCursor, _results);

        entry.onRemove(cursor);
      }
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _table + "]";
  }
  
  private class WatchEntry {
    private DatabaseWatch _watch;
    private byte []_key;
    
    private byte []_key2;
    
    WatchEntry(DatabaseWatch watch, byte []key)
    {
      Objects.requireNonNull(watch);
      Objects.requireNonNull(key);
      
      _watch = watch;
      _key = key;
      
      _key2 = new byte[key.length];
      System.arraycopy(key, 0, _key2, 0, key.length);
    }

    void onPut(Cursor cursor)
    {
      _watch.onChange(cursor);
    }
    
    void onRemove(Cursor cursor)
    {
      _watch.onChange(cursor);
    }
    
    boolean isMatch(byte []key, DatabaseWatch watch)
    {
      return _watch == watch && Arrays.equals(key, _key);
    }
    
    @Override
    public int hashCode()
    {
      return Arrays.hashCode(_key);
    }
    
    @Override
    public boolean equals(Object value)
    {
      if (! (value instanceof WatchEntry)) {
        return false;
      }
      
      WatchEntry entry = (WatchEntry) value;
      
      if (! Arrays.equals(_key, entry._key)) {
        return false;
      }
      
      if (! _watch.equals(entry._watch)) {
        return false;
      }
      
      return true;
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _watch + "," + Hex.toShortHex(_key) + "]";
    }
  }
  
  private class WatchKey {
    private byte []_key;
    
    WatchKey()
    {
    }
    
    WatchKey(byte []key)
    {
      init(key);
    }
    
    void init(byte []key)
    {
      Objects.requireNonNull(key);
      
      _key = key;
    }
    
    @Override
    public int hashCode()
    {
      return Arrays.hashCode(_key);
    }
    
    public boolean equals(Object o)
    {
      if (! (o instanceof WatchKey)) {
        return false;
      }
      
      WatchKey key = (WatchKey) o;
      
      return Arrays.equals(_key, key._key);
    }
  }
}
