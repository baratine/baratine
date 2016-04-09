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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Archiving builder.
 */
public class RestoreKrakenManager
{
  private static final Logger log
    = Logger.getLogger(RestoreKrakenManager.class.getName());
    
  private Path _path;
  private KrakenImpl _manager;
  
  public RestoreKrakenManager(KrakenImpl manager,
                              Path path)
  {
    Objects.requireNonNull(manager);
    Objects.requireNonNull(path);

    _manager = manager;
    _path = path;
  }
  
  public void exec()
    throws IOException
  {
    try (DirectoryStream<Path> dir = Files.newDirectoryStream(_path)) {
      for (Path subPath : dir) {
        try {
          String tableName = subPath.getFileName().toString();
          boolean isZip = false;
        
          if (tableName.endsWith(".gz")) {
            tableName = tableName.substring(tableName.length() - 3);
            isZip = true;
          }
        
          RestoreKraken restore = _manager.restore(tableName, subPath);
          restore.zip(isZip);
        
          restore.exec();
        } catch (Exception e) {
          log.log(Level.WARNING, e.toString(), e);
        }
      }
    }
  }
}
