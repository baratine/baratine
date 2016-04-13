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
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.files.FileServiceBind;
import com.caucho.v5.io.WriteStream;

import io.baratine.files.BfsFileSync;
import io.baratine.service.OnActive;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

/**
 * Entry to the filesystem.
 */
@ServiceApi(BfsFileSync.class)
public class ProcKraken extends ProcFileBase
{
  private final BartenderSystem _bartender;
  private ServiceRef _serviceRef;
  
  public ProcKraken(BartenderSystem bartender)
  {
    super("/kraken");
    
    _bartender = bartender;
  }

  @OnActive
  public void onStart()
  {
    _serviceRef = ServiceRef.current();
  }
  
  @Override
  public void list(Result<String[]> result)
  {
    result.ok(new String[] { "debug" });
  }

  @Override
  public BfsFileSync lookup(String path)
  {
    if (path == null || path.equals("")) {
      return null;
    }
    
    int p = path.indexOf('/', 1);
    
    String name;
    String tail;
    
    if (p > 0) {
      name = path.substring(1, p);
      tail = path.substring(p);
    }
    else {
      name = path.substring(1);
      tail = null;
    }

    /*
    if (tail != null) {
      return null;
    }
    */

    FileServiceBind bind = null;
    if ("debug".equals(name)) {
      if (tail != null) {
        return new ProcKrakenDebug().lookup(tail);
      }
      else {
        bind = _serviceRef.pin(new ProcKrakenDebug())
            .as(FileServiceBind.class);
      }
    }
    
    return bind;
  }
  
  @Override
  protected boolean fillRead(WriteStream out)
    throws IOException
  {
    out.println("[");
    out.println("]");
    
    return true;
  }
}
