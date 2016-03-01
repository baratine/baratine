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

import java.util.ArrayList;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.files.FilesDeployService;
import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;

public class BfsRmCommand extends BfsCommand
{
  private static final L10N L = new L10N(BfsRmCommand.class);

  @Override
  public String name()
  {
    return "rm";
  }

  @Override
  public String getDescription()
  {
    return "remove bfs files";
  }

  @Override
  protected void initBootOptions()
  {
    addFlagOption("recursive", "recursively delete files and directories").tiny("r").tiny("R");

    super.initBootOptions();
  }

  @Override
  public String getUsageTailArgs()
  {
    return " file ...";
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

    ArrayList<String> argList = args.getTailArgs();

    boolean isRecursive = args.getArgFlag("recursive");

    for (String path : argList) {
      boolean result;

      if (isRecursive) {
        result = files.removeAll(path);
      }
      else {
        result = files.removeFile(path);
      }

      if (! result) {
        System.out.println(L.l("unable to remove file or directory: {0}", path));
      }

    }

    return ExitCode.OK;
  }
}
