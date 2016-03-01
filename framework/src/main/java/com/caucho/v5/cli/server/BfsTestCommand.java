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
 * @author Nam Nguyen
 */

package com.caucho.v5.cli.server;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.files.FilesDeployService;
import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;

import io.baratine.files.Status;

public class BfsTestCommand extends BfsCommand
{
  private static final L10N L = new L10N(BfsTestCommand.class);

  public String name()
  {
    return "test";
  }

  @Override
  public String getDescription()
  {
    return "returns 0 if the file exists, is zero-lengthed, or is a directory";
  }

  @Override
  public String getUsageTailArgs()
  {
    return " file";
  }

  @Override
  public int getTailArgsMinCount()
  {
    return 1;
  }

  @Override
  protected void initBootOptions()
  {
    addFlagOption("exists", "test if the file exists").tiny("e");
    addFlagOption("zero", "test if the file is zero-lengthed").tiny("z");
    addFlagOption("directory", "test if the file is a directory").tiny("d");

    super.initBootOptions();
  }

  @Override
  protected ExitCode doCommandImpl(ArgsCli args,
                               ServiceManagerClient client)
  {
    FilesDeployService service = client.service("remote:///bartender-files")
                                       .as(FilesDeployService.class);

    String path = args.getTail(0);

    boolean isTestExists = args.getArgFlag("exists");
    boolean isTestZero = args.getArgFlag("zero");
    boolean isTestDir = args.getArgFlag("directory");

    if (! isTestExists && ! isTestZero && ! isTestDir) {
      throw new CommandArgumentException(L.l("flag not set"));
    }

    Status status = service.getStatus(path);
    Status.FileType type = status.getType();

    String trueResult = "0";
    String falseResult = "1";

    if (isTestExists) {
      if (type == Status.FileType.FILE
          || type == Status.FileType.DIRECTORY) {
        System.out.println(trueResult);
      }
      else {
        System.out.println(falseResult);
      }
    }
    else if (isTestZero) {
      if (type == Status.FileType.FILE && status.getLength() == 0) {
        System.out.println(trueResult);
      }
      else {
        System.out.println(falseResult);
      }
    }
    else {
      if (type == Status.FileType.DIRECTORY) {
        System.out.println(trueResult);
      }
      else {
        System.out.println(falseResult);
      }
    }

    return ExitCode.OK;
  }
}
