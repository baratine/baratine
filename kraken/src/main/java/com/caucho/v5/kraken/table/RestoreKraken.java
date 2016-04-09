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
import java.nio.file.Path;
import java.util.Objects;

/**
 * Archiving builder.
 */
public class RestoreKraken
{
  private String _tableName;
  private Path _path;
  private Boolean _isZip;
  private KrakenImpl _manager;
  
  public RestoreKraken(KrakenImpl manager,
                       String tableName,
                       Path path)
  {
    Objects.requireNonNull(manager);
    Objects.requireNonNull(tableName);
    Objects.requireNonNull(path);

    _manager = manager;
    _tableName = tableName;
    _path = path;
  }
  
  public RestoreKraken zip(boolean isZip)
  {
    _isZip = isZip;
    
    return this;
  }
  
  public void exec()
    throws IOException
  {
    TableKraken table = _manager.getTable(_tableName);
    
    if (table == null) {
      table = rebuildTable();
      Objects.requireNonNull(table);
    }
    
    RestoreKrakenTable restore = table.restore(_path);
    
    if (_isZip != null) {
      restore.zip(_isZip);
    }
    
    restore.exec();
  }
  
  private TableKraken rebuildTable()
    throws IOException
  {
    RestoreKrakenHeader parser = new RestoreKrakenHeader(_path);
    
    if (_isZip != null) {
      parser.zip(_isZip);
    }
    
    parser.exec();
    
    String tableName = parser.getTableName();
    String sql = parser.getSql();
    
    TableKraken table = _manager.createTable(tableName, sql);
    
    return table;
  }
}
