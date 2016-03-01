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

import java.io.IOException;
import java.util.ArrayList;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.files.FilesDeployService;
import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;

import io.baratine.files.Status;

public class BfsCpCommand extends BfsCommand
{
  private static final L10N L = new L10N(BfsCpCommand.class);

  @Override
  public String name()
  {
    return "cp";
  }

  @Override
  public String getDescription()
  {
    return "copy bfs files within bfs";
  }

  @Override
  protected void initBootOptions()
  {
    addFlagOption("recursive", "recursively copy files and directories").tiny("r").tiny("R");

    super.initBootOptions();
  }

  @Override
  public String getUsageTailArgs()
  {
    return " <src> ... <dst>";
  }

  @Override
  public int getTailArgsMinCount()
  {
    return 2;
  }

  @Override
  protected ExitCode doCommandImpl(ArgsCli args,
                               ServiceManagerClient client)
  {
    FilesDeployService service = client.service("remote:///bartender-files")
                                       .as(FilesDeployService.class);

    ArrayList<String> srcList = new ArrayList<String>(args.getTailArgs());

    boolean isRecursive = args.getArgFlag("recursive");

    try {
      if (srcList.size() == 2) {
        String src = srcList.get(0);
        String outputFile = srcList.get(1);

        if (isRecursive) {
          service.copyAll(src, outputFile);
        }
        else {
          copyFile(service, src, outputFile, false);
        }
      }
      else {
        String outputDir = srcList.remove(srcList.size() - 1);
        Status status = service.getStatus(outputDir);

        if (status.getType() != Status.FileType.DIRECTORY) {
          System.err.println(L.l("destination must be a directory: {0}", outputDir));
        }
        else {
          for (String src : srcList) {
            if (isRecursive) {
              service.copyAll(src, outputDir);
            }
            else {
              copyFile(service, src, outputDir, true);
            }
          }
        }
      }
    }
    catch (IOException e) {
      System.err.println(e);
    }

    return ExitCode.OK;
  }

  private void copyFile(FilesDeployService service, String src, String dest,
                        boolean isDestDir)
    throws IOException
  {
    service.copyFile(src, dest);

    /*
    String tail = src;

    int index = src.lastIndexOf("/");
    if (index > 0) {
      tail = src.substring(index + 1);
    }

    if (dest.endsWith("/")) {
      dest += tail;
    }
    else if (dest.equals(".") || isDestDir) {
      dest += "/" + tail;
    }

    copyFileImpl(service, src, dest);
    */
  }

  private void copyFileImpl(FilesDeployService service, String src, String dest)
    throws IOException
  {
    boolean result = service.copyFile(src, dest);

    if (! result) {
      throw new IOException(L.l("failed to copy {0} to {1}", src, dest));
    }

    /*
    StreamSource is = null;
    StreamSource os = null;

    try {
      is = service.getFile(src);

      service.putFile(dest, is);
    }
    finally {
      if (is != null) {
        is.close();
      }

      if (os != null) {
        os.close();
      }
    }
    */
  }
}
