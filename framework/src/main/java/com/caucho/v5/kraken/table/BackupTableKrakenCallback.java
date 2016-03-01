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

import io.baratine.service.Result;

import java.util.Objects;

import com.caucho.v5.io.StreamSource;
import com.caucho.v5.kelp.BackupKelp;

/**
 * Service for handling the distributed cache
 */
public class BackupTableKrakenCallback implements BackupKelp
{
  private final TablePod _tablePod;
  
  public BackupTableKrakenCallback(TablePod tablePod)
  {
    Objects.requireNonNull(tablePod);
    
    _tablePod = tablePod;
  }

  @Override
  public void onPut(byte[] tableKey, 
                    byte []rowKey, 
                    StreamSource source,
                    Result<Boolean> result)
  {
    _tablePod.put(rowKey, source, result);
  }

  @Override
  public void onRemove(byte[] tableKey, 
                       byte []rowKey,
                       long version,
                       Result<? super Boolean> result)
  {
    _tablePod.remove(rowKey, version, result);
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _tablePod.getName()
            + ",pod=" + _tablePod.getPodName() + "]");
  }
}
