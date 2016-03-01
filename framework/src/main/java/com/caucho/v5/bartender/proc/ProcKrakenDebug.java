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

package com.caucho.v5.bartender.proc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;

import com.caucho.v5.baratine.ServiceApi;
import com.caucho.v5.bartender.files.FileServiceBind;
import com.caucho.v5.kelp.DebugKelp;
import com.caucho.v5.kraken.KrakenSystem;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.TableManagerKraken;
import com.caucho.v5.vfs.WriteStream;

import io.baratine.files.BfsFileSync;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

/**
 * Entry to the filesystem.
 */
@ServiceApi(BfsFileSync.class)
public class ProcKrakenDebug extends ProcFileBase
{
  private TableManagerKraken _tableManager;

  public ProcKrakenDebug()
  {
    super("/kraken/pages");
    
    _tableManager = KrakenSystem.current().getTableManager(); 
  }
  
  @Override
  public void list(Result<String[]> result)
  {
    ArrayList<String> tableNames = new ArrayList<>();
    
    for (TableKraken table : _tableManager.getTables()) {
      tableNames.add(table.getId());
    }
    
    Collections.sort(tableNames);
    
    String []tableNameArray = new String[tableNames.size()];
    tableNames.toArray(tableNameArray);
    
    result.ok(tableNameArray);
  }

  @Override
  public BfsFileSync lookup(String path)
  {
    if (path == null || path.equals("")) {
      return null;
    }
    
    String name = path.substring(1);
    
    TableKraken table = _tableManager.getTable(name);
    
    if (table == null) {
      return null;
    }
    
    ProcKrakenDebugTable debugTable = new ProcKrakenDebugTable(name);
    
    ServiceRef serviceRef = ServiceRef.current();
    
    return serviceRef.pin(debugTable)
                     .as(FileServiceBind.class);
  }
  
  @Override
  protected boolean fillRead(WriteStream out)
    throws IOException
  {
    Path path = _tableManager.getStorePath();
    
    out.println("\"");
    
    new DebugKelp().debug(out, path);

    out.println("\"");
    
    return true;
  }
}
