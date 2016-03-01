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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.kraken.KrakenSystem;
import com.caucho.v5.kraken.table.ArchiveKrakenManager;
import com.caucho.v5.kraken.table.RestoreKrakenManager;
import com.caucho.v5.kraken.table.TableManagerKraken;
import com.caucho.v5.subsystem.RootDirectorySystem;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.PathImpl;

import io.baratine.service.Result;
import io.baratine.service.Service;

/**
 * Admin deployment to the filesystem.
 */

@Service("public:///management")
public class AdminServiceImpl implements AdminService
{
  private static final L10N L = new L10N(AdminServiceImpl.class);
  private static final Logger log
    = Logger.getLogger(AdminServiceImpl.class.getName());
  
  private BartenderSystem _bartender;

  public AdminServiceImpl(BartenderSystem bartender)
  {
    _bartender = bartender;
  }

  public void reportPdf(Result<String> result)
  {
    /*
    String path = "/system/report/latest";
    
    Path bfsPath = Vfs.lookup("bfs://" + path);
    
    try {
    AdminPdfBuilder builder = new AdminPdfBuilder();
    
    GraphBuilderAdminPdf graphBuilder = builder.graphBuilder("Test Graph");
    graphBuilder.build();
    
    builder.scoreboard();
    
    builder.threadDump();
    
    builder.profile();
    
    builder.build(bfsPath);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    
    result.ok(path);
    */
    result.ok("fail");
  }

  @Override
  public void backup(String tag, Result<String> result)
  {
    Path data = RootDirectorySystem.getCurrentDataDirectory();
    
    Path backupDir = data.resolve("backup");
    
    if (! isValidTag(tag)) {
      throw new IllegalArgumentException(L.l("tag={0}", tag));
    }
    
    Path path = backupDir.resolve(tag);
    
    String msg = L.l("backup saved in {0}", path);
    
    TableManagerKraken manager = KrakenSystem.current().getTableManager();
    
    ArchiveKrakenManager archive = manager.archive(path);
    
    archive.exec();
    
    result.ok(msg);
  }

  @Override
  public void backupLoad(String tag, Result<String> result)
  {
    try {
      Path data = RootDirectorySystem.getCurrentDataDirectory();
    
      Path backupDir = data.resolve("backup");
    
      if (! isValidTag(tag)) {
        throw new IllegalArgumentException(L.l("tag={0}", tag));
      }
    
      Path path = backupDir.resolve(tag);
    
      if (! Files.isDirectory(path)) {
        throw new IllegalArgumentException(L.l("tag={0} is an unknown backup directory", tag));
      }
    
      TableManagerKraken manager = KrakenSystem.current().getTableManager();
    
      RestoreKrakenManager restore = manager.restore(path);
    
      restore.exec();
    
      String msg = L.l("backup restored from {0}", path);
    
      result.ok(msg);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  private boolean isValidTag(String tag)
  {
    if (tag == null || tag.isEmpty()) {
      return false;
    }
    
    for (int i = 0; i < tag.length(); i++) {
      char ch = tag.charAt(i);
      
      if (Character.isJavaIdentifierPart(ch)) {
      }
      else if (ch == '-' || ch == '.' || ch == '+') {
      }
      else {
        return false;
      }
    }
    
    return true;
  }
}
