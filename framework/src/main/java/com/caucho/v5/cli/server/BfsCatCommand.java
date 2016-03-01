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
 * @author Alex Rojkov
 */

package com.caucho.v5.cli.server;

import java.io.IOException;
import java.io.InputStream;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.files.FilesDeployService;
import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.WriteStream;

public class BfsCatCommand extends BfsCommand
{
  private static final L10N L = new L10N(BfsCatCommand.class);

  @Override
  public String name()
  {
    return "cat";
  }

  @Override
  public String getDescription()
  {
    return "echo a bfs file";
  }

  @Override
  public String getUsageTailArgs()
  {
    return " [<pattern>]";
  }

  @Override
  public int getTailArgsMinCount()
  {
    return 1;
  }

  @Override
  protected ExitCode doCommandImpl(ArgsCli args,
                               ServiceManagerClient client)
  {
    FilesDeployService files = client.service("remote:///bartender-files")
                                     .as(FilesDeployService.class);

    String path = args.getTail(0);

    StreamSource ss = files.getFile(path);
    
    WriteStream out = args.getOut();

    if (ss == null) {
      System.out.println(L.l("file does not exist: {0}", path));
    }
    else {
      try (InputStream is = ss.getInputStream()) {
        out.writeStream(is);
      } catch (IOException e) {
        throw ConfigException.wrap(e);
      }
    }

    return ExitCode.OK;
  }
}
