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

import io.baratine.db.Cursor;
import io.baratine.db.DatabaseWatch;

import java.util.Arrays;
import java.util.Objects;

import com.caucho.v5.util.Hex;

/**
 * manager for table listeners.
 */
class WatchForeign implements DatabaseWatch {
  private byte []_key;
  private TableKraken _table;
  private String _serverId;
  
  WatchForeign(byte []key, 
               TableKraken table,
               String serverId)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(table);
    Objects.requireNonNull(serverId);
    
    _key = key;
    _table = table;
    
    _serverId = serverId;
    
  }
  @Override
  public void onChange(Cursor cursor)
  {
    _table.notifyForeignWatch(_key, _serverId);
  }

  @Override
  public int hashCode()
  {
    int hash = _table.hashCode();
    
    return 65521 * hash + Arrays.hashCode(_key);
  }
  
  @Override
  public boolean equals(Object value)
  {
    if (! (value instanceof WatchForeign)) {
      return false;
    }
    
    WatchForeign watch = (WatchForeign) value;
    
    if (! Arrays.equals(_key, watch._key)) {
      return false;
    }
    else if (! _table.equals(watch._table)) {
      return false;
    }
    else if (! _serverId.equals(watch._serverId)) {
      return false;
    }
    else {
      return true;
    }
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _table.getId()
            + "," + Hex.toShortHex(_key)
            + "," + _serverId + "]");
  }
}
