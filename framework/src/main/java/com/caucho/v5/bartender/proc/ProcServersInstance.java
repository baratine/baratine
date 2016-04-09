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
import com.caucho.v5.bartender.files.FileStatusImpl;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.io.WriteBuffer;

import io.baratine.files.BfsFileSync;
import io.baratine.files.Status;
import io.baratine.service.Result;

/**
 * Entry to the filesystem.
 */
@ServiceApi(BfsFileSync.class)
public class ProcServersInstance extends ProcFileBase
{
  private final ServerHeartbeat _server;

  public ProcServersInstance(ServerHeartbeat server,
                             String id)
  {
    super(id);

    _server = server;
  }

  @Override
  public void getStatus(Result<Status> result)
  {
    String path = _server.getClusterId();
    Status.FileType type = Status.FileType.FILE;
    long length = 0;
    long version = 0;
    long modifiedTime = 0;
    long checkSum = 0;

    result.ok(new FileStatusImpl(path, type, length, version,
                                       modifiedTime, checkSum, null));
  }

  @Override
  protected boolean fillRead(WriteBuffer out)
    throws IOException
  {
    out.print("{ \"server\" : \"" + _server.getId() + "\"");
    out.print(",\n  \"state\" : \"" + _server.getState() + "\"");
    out.print(",\n  \"cluster\" : \"" + _server.getClusterId() + "\"");
    out.print(",\n  \"rack\" : \"" + _server.getRack().getId() + "\"");

    out.print(",\n  \"display-name\" : \"" + _server.getDisplayName() + "\"");

    if (_server.getSeedIndex() > 0) {
      out.print(",\n  \"seed\" : " + _server.getSeedIndex());
    }

    if (_server.getExternalId() != null) {
      out.print(",\n  \"ext\" : \"" + _server.getExternalId() + "\"");
    }

    out.print("\n}");

    return true;
  }
}
