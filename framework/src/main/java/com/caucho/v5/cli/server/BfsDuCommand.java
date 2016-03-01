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

import java.util.ArrayList;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.files.FilesDeployService;
import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;

import io.baratine.files.Status;

public class BfsDuCommand extends BfsCommand
{
  private static final L10N L = new L10N(BfsDuCommand.class);

  public String name()
  {
    return "du";
  }

  @Override
  public String getDescription()
  {
    return "display sizes of files and directories";
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
  protected void initBootOptions()
  {
    addFlagOption("human", "list sizes in human-readable format").tiny("h");
    addFlagOption("summary", "sum up sizes in directory").tiny("s");

    super.initBootOptions();
  }

  @Override
  protected ExitCode doCommandImpl(ArgsCli args,
                               ServiceManagerClient client)
  {
    FilesDeployService service = client.service("remote:///bartender-files")
                                       .as(FilesDeployService.class);

    ArrayList<String> argList = args.getTailArgs();

    boolean isHuman = args.getArgFlag("human");
    boolean isSummary = args.getArgFlag("summary");

    for (String path : argList) {
      printUsage(service, path, isHuman, isSummary);
    }

    return ExitCode.OK;
  }

  private void printUsage(FilesDeployService service,
                          String path, boolean isHuman, boolean isSummary)
  {
    Status status = service.getStatus(path);

    if (status.getType() == Status.FileType.FILE) {
      printUsageFile(path, status.getLength(), isHuman);
    }
    else if (status.getType() == Status.FileType.DIRECTORY) {
      if (isSummary) {
        long total = getUsage(service, path, status);

        printUsageFile(path, total, isHuman);
      }
      else {
        String []list = service.list(path);

        for (String file : list) {
          printUsage(service, path + "/" + file, isHuman, isSummary);
        }
      }
    }
    else {
      System.out.println(L.l("no such file or directory: {0}", path));
    }
  }

  private void printUsageFile(String path, long size, boolean isHuman)
  {
    StringBuilder sb = new StringBuilder();

    if (isHuman) {
      if (size < 1024) {
        sb.append(size);

        sb.append("B");
      }
      else if (size < 1024 * 1024) {
        long major = size / 1024;

        sb.append(major);

        if (major < 10) {
          sb.append(".");

          long remainder = size % 1024;

          String str = String.valueOf(remainder);

          sb.append(str.charAt(0));
        }

        sb.append("K");
      }
      else if (size < 1024 * 1024 * 1024) {
        long major = size / (1024 * 1024);

        sb.append(major);

        if (major < 10) {
          sb.append(".");

          long remainder = size % (1024 * 1024);

          String str = String.valueOf(remainder);

          sb.append(str.charAt(0));
        }

        sb.append("M");
      }
      else {
        long major = size / (1024 * 1024 * 1024);

        sb.append(major);

        if (major < 10) {
          sb.append(".");

          long remainder = size % (1024 * 1024 * 1024);

          String str = String.valueOf(remainder);

          sb.append(str.charAt(0));
        }

        sb.append("G");
      }
    }
    else {
      sb.append(size);
    }

    System.out.print(sb);

    int padding = 9;

    for (int i = sb.length(); i < padding; i++) {
      System.out.print(" ");
    }

    System.out.print(" ");

    System.out.println(path);
  }

  private long getUsage(FilesDeployService service, String path)
  {
    Status status = service.getStatus(path);

    return getUsage(service, path, status);
  }

  private long getUsage(FilesDeployService service, String path, Status status)
  {
    if (status.getType() == Status.FileType.FILE) {
      return status.getLength();
    }
    else if (status.getType() == Status.FileType.DIRECTORY) {
      long len = 0;

      String []list = service.list(path);

      for (String file : list) {
        len += getUsage(service, path + "/" + file);
      }

      return len;
    }
    else {
      System.out.println(L.l("no such file or directory: {0}", path));

      return 0;
    }
  }
}
