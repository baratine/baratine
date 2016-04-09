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

import java.nio.file.Files;
import java.nio.file.Path;

import io.baratine.service.Result;

/**
 * Archiving service.
 */
public class ArchiveServiceImpl implements ArchiveService
{
  private KrakenImpl _manager;
  
  ArchiveServiceImpl(KrakenImpl manager)
  {
    _manager = manager;
  }
  
  @Override
  public void archive(ArchiveKrakenManager archive, Result<Boolean> result)
  {
    try {
      Path path = archive.getPath();
    
      Files.createDirectories(path);
      
      Iterable<TableKraken> tables = _manager.getTableService().getTables();
      
      for (TableKraken table : tables) {
        String podName = table.getPodName();
        String name = table.getName();
        
        Path tablePath;
        
        if (archive.isZip()) {
          tablePath = path.resolve(podName + '.' + name + ".gz");
        }
        else {
          tablePath = path.resolve(podName + '.' + name);
        }
        
        ArchiveKraken builder = table.archive(tablePath);
        builder.zip(archive.isZip());
        
        builder.exec();
      }
    
      result.ok(true);
    } catch (Exception e) {
      result.fail(e);
    }
  }
}
