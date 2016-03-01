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

import com.caucho.v5.kelp.ArchiveTableKelp;


/**
 * Archiving builder.
 */
public class ArchiveKraken
{
  private TableKraken _table;
  private Path _path;
  private ArchiveTableKelp _archiveKelp;
  
  public ArchiveKraken(TableKraken table,
                       Path path)
  {
    Objects.requireNonNull(table);
    Objects.requireNonNull(path);

    _table = table;
    _path = path;
    
    _archiveKelp = table.getTableKelp().archive(_path);
    _archiveKelp.sql(table.getSql());
  }
  
  public ArchiveKraken zip(boolean isZip)
  {
    _archiveKelp.zip(isZip);
    
    return this;
  }
  
  public void exec()
    throws IOException
  {
    _archiveKelp.exec();
  }
}
