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
import java.util.ArrayList;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.files.FilesDeployService;
import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.StreamSourcePath;
import com.caucho.v5.vfs.VfsOld;

import io.baratine.files.Status;

public class BfsPutCommand extends BfsCommand
{
  private static final L10N L = new L10N(BfsPutCommand.class);

  public String name()
  {
    return "put";
  }

  @Override
  public String getDescription()
  {
    return "copy a local file/directory into bfs";
  }

  @Override
  public String getUsageTailArgs()
  {
    return " <localsrc> <target>";
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

    ArrayList<String> argList = args.getTailArgs();
    String dst = argList.get(argList.size() - 1);

    for (int i = 0; i < argList.size() - 1; i++) {
      String src = argList.get(i);

      try {
        put(service, src, dst);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }

    return ExitCode.OK;
  }

  private void put(FilesDeployService service, String src, String dst)
    throws IOException
  {
    Status dstStatus = service.getStatus(dst);

    boolean isDstDirectory = dstStatus.getType() == Status.FileType.DIRECTORY;
    boolean isDstExists = dstStatus.getType() != Status.FileType.NULL;

    if (dst.endsWith("/")) {
      isDstDirectory = true;
    }

    put(service, src, dst, isDstDirectory, isDstExists);
  }

  private void put(FilesDeployService service, String src, String dst,
                   boolean isDstDirectory, boolean isDstExists)
    throws IOException
  {
    PathImpl path = VfsOld.lookup(src);

    if (! path.canRead()) {
      System.out.println(L.l("cannot read local file: {0}", path));
    }
    else if (isDstDirectory) {
      if (path.isDirectory()) {
        putDir(service, path, src, dst, isDstDirectory, isDstExists);
      }
      else {
        putFileToDir(service, path, dst);
      }
    }
    else if (isDstExists) {
      if (path.isDirectory()) {
        System.out.println(L.l("cannot copy {0} to {1}: destination must be a directory", src, dst));
      }
      else {
        putFile(service, path, dst);
      }
    }
    else {
      if (path.isDirectory()) {
        putDir(service, path, src, dst, isDstDirectory, isDstExists);
      }
      else {
        putFile(service, path, dst);
      }
    }
  }

  private void putFile(FilesDeployService service, PathImpl path, String dst)
    throws IOException
  {
    StreamSource ss = new StreamSource(new StreamSourcePath(path));

    service.putFile(dst, ss);
  }

  private void putFileToDir(FilesDeployService service, PathImpl path, String dst)
    throws IOException
  {
    String tail = path.getTail();

    if (dst.endsWith("/")) {
      dst = dst + tail;
    }
    else {
      dst = dst + "/" + tail;
    }

    putFile(service, path, dst);
  }

  private void putDir(FilesDeployService service, PathImpl srcPath,
                      String src, String dst,
                      boolean isDstDirectory, boolean isDstExists)
    throws IOException
  {
    String []files = srcPath.list();

    String tail = srcPath.getTail();

    for (String file : files) {
      String target;

      if (dst.endsWith("/")) {
        target = dst + tail + "/" + file;
      }
      else {
        target = dst + "/" + tail + "/" + file;
      }

      put(service, src + "/" + file, target);
    }
  }
}
