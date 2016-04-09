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
import java.io.InputStream;
import java.io.OutputStream;

import com.caucho.v5.baratine.ServiceApi;
import com.caucho.v5.bartender.files.FileServiceBind;
import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.vfs.TempStream;

import io.baratine.files.BfsFileSync;
import io.baratine.files.Status;
import io.baratine.files.Watch;
import io.baratine.files.WriteOption;
import io.baratine.service.Result;
import io.baratine.service.Service;

/**
 * Base proc filesystem implementation
 */
@ServiceApi(BfsFileSync.class)
public class ProcFileBase
{
  private final String _path;

  /*
  public FileServiceRamp(String cacheName, String path)
  {
    _root = new RootServiceImpl(cacheName);

    _path = path;
  }
  */

  ProcFileBase(String path)
  {
    _path = path;

    if (path.startsWith("//")) {
      path = "bfs:" + path;
    }
    else {
      path = "bfs://" + path;
    }
  }

  public BfsFileSync lookup(String path)
  {
    return null;
  }

  public void bind(String path, @Service FileServiceBind subFile)
  {
    //System.out.println("BIND: " + path + " " + subFile);
  }

  public void getStatus(Result<Status> result)
  {
    result.ok(null);
  }

  public void list(Result<String[]> result)
  {
    result.ok(new String[] { _path });
  }

  public void openRead(Result<InputStream> result)
  {
    try {
      TempStream ts = new TempStream();
      ts.openWrite();

      WriteBuffer out = new WriteBuffer(ts);

      if (fillRead(out)) {
        out.close();

        result.ok(ts.getInputStream());
      }
    } catch (Exception e) {
      result.fail(e);
    }
  }

  protected boolean fillRead(WriteBuffer out)
    throws IOException
  {
    return false;
  }

  /**
   * Open a file for writing.
   */
  public OutputStream openWrite(WriteOption ...options)
  {
    return null;
  }

  /**
   * Remove a file.
   */
  public void remove(Result<Boolean> result)
  {
    result.ok(false);
  }

  /**
   * Remove a file.
   */
  public void removeAll(Result<Boolean> result)
  {
    result.ok(false);
  }

  public void registerWatch(@Service Watch watch)
  {
  }

  public void unregisterWatch(@Service Watch watch)
  {
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _path + "]";
  }
}
