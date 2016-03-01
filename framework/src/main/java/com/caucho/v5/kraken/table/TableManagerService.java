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

import io.baratine.service.Direct;
import io.baratine.service.Result;

import com.caucho.v5.kelp.TableBuilderKelp;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kraken.query.TableBuilderKraken;


/**
 * Manages the distributed cache
 */
public interface TableManagerService
{
  void createTable(TableKelp table, Result<TableKraken> result);

  void createTableSql(String name, String sql, Result<TableKraken> result);
  
  void loadTable(String name, Result<TableKraken> result);

  void loadTableByKey(byte[] tableKey, Result<TableKraken> result);

  void buildTable(String sql,
                  TableBuilderKraken builderKraken,
                  TableBuilderKelp builderKelp, 
                  Result<TableKraken> result);
  
  @Direct 
  void getTableByKey(byte[] tableKey, Result<TableKraken> result);

  @Direct
  void getTableByName(String name, Result<TableKraken> result);
  
  //
  // lifecycle
  //

  boolean startLocal();
 
  void startCluster();

  void onServerStart();

  void startRequestUpdates();
  
}
