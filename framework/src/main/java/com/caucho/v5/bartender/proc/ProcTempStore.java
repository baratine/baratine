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

import com.caucho.v5.baratine.ServiceApi;
import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.store.temp.TempStoreSystem;
import com.caucho.v5.store.temp.TempStore;

import io.baratine.files.BfsFileSync;

/**
 * /proc/temp-store
 */
@ServiceApi(BfsFileSync.class)
public class ProcTempStore extends ProcFileBase
{
  private TempStore _tempStore;

  public ProcTempStore()
  {
    super("/temp-store");
    
    TempStoreSystem tempSystem = TempStoreSystem.current();
    
    if (tempSystem != null) {
      _tempStore = tempSystem.tempStore();
    }
  }
  
  @Override
  protected boolean fillRead(WriteBuffer out)
    throws IOException
  {
    out.print("{");
    
    if (_tempStore != null) {
      out.print("\n  \"size\" : " + _tempStore.getSize());
      out.print(",\n  \"used\" : " + _tempStore.getSizeUsed());
      out.print(",\n  \"free\" : " + _tempStore.getSizeFree());
    }
    
    out.println("\n}");
    
    return true;
  }
}
