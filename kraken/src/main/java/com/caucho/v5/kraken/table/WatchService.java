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

import io.baratine.db.DatabaseWatch;
import io.baratine.service.Cancel;
import io.baratine.service.Result;

/**
 * manager for table listeners.
 */
public interface WatchService
{
  void addWatch(DatabaseWatch watch, 
                TableKraken table, 
                byte[] key,
                Result<Cancel> result);
  
  void removeWatch(DatabaseWatch watch, TableKraken table, byte[] key);

  void addForeignWatch(TableKraken table, byte[] key, String serverId);

  /**
   * Notify local and remote watches for the given table and key
   * 
   *  @param table the table of the updated row
   *  @param key the key of the updated row
   */
  void notifyWatch(TableKraken table, byte[] key);
  
  /**
   * Notify local watches for the given table and key
   * 
   *  @param table the table of the updated row
   *  @param key the key of the updated row
   */
  void notifyLocalWatch(TableKraken table, byte[] key);
}
