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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.files.FilesDeployService;
import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;
import com.caucho.v5.vfs.WriteStreamOld;

import io.baratine.files.Status;

public class BfsGetCommand extends BfsCommand
{
  private static final L10N L = new L10N(BfsGetCommand.class);

  @Override
  public String name()
  {
    return "get";
  }

  @Override
  public String getDescription()
  {
    return "copy bfs files to the local file system";
  }

  @Override
  public String getUsageTailArgs()
  {
    return " <bfs-file> <localdst>";
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
        get(service, src, dst);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }

    return ExitCode.OK;
  }

  private void get(FilesDeployService service, String src, String dst)
    throws IOException
  {
    PathImpl dstPath = VfsOld.lookup(dst);

    get(service, src, dst, dstPath);
  }

  private void get(FilesDeployService service, String src, String dst,
                   PathImpl dstPath)
    throws IOException
  {
    Status srcStatus = service.getStatus(src);
    Status.FileType srcType = srcStatus.getType();

    boolean isDstDirectory = dstPath.isDirectory() || dst.endsWith("/");

    if (srcType == Status.FileType.FILE) {
      if (isDstDirectory) {
        getFileToDir(service, src, dst, dstPath);
      }
      else {
        getFile(service, src, dst, dstPath);
      }
    }
    else if (srcType == Status.FileType.DIRECTORY) {
      if (! dstPath.exists()) {
        getDirToNewDir(service, src, dst, dstPath);
      }
      else if (isDstDirectory) {
        getDir(service, src, dst, dstPath);
      }
      else {
        System.out.println(L.l("cannot copy {0} to {1}: destination must be a directory",
                               src, dst));
      }
    }
    else {
      System.out.println(L.l("cannot read file: {0}", src));
    }
  }

  private void getFileToDir(FilesDeployService service, String src, String dst,
                            PathImpl dstPath)
    throws IOException
  {
    if (! dstPath.exists()) {
      System.out.println(L.l("cannot copy {0} to {1}: destination directory does not exist",
                             src, dst));
    }
    else {
      String tail = getTail(src);

      PathImpl target = dstPath.lookup(tail);

      getFile(service, src, target.getFullPath(), target);
    }
  }

  private void getFile(FilesDeployService service, String src, String dst,
                       PathImpl dstPath)
    throws IOException
  {
    StreamSource ss = service.getFile(src);

    if (ss != null) {
      try (InputStream is = ss.getInputStream()) {
        try (WriteStreamOld os = dstPath.openWrite()) {
          os.writeStream(is);
        }
        catch (FileNotFoundException e) {
          System.out.println(L.l("cannot write to file: {0}", dst));
        }
      }
    }
    else {
      System.out.println(L.l("cannot read file: {0}", src));
    }
  }

  private void getDir(FilesDeployService service, String src, String dst,
                      PathImpl dstPath)
    throws IOException
  {
    String []files = service.list(src);

    String tail = getTail(src);

    PathImpl dir = dstPath.lookup(tail);
    dir.mkdir();

    if (! dir.exists()) {
      System.out.println(L.l("cannot create directory: {0}", dir.getFullPath()));
    }
    else {
      for (String file : files) {
        PathImpl target = dir.lookup(file);

        get(service, src + "/" + file, target.getFullPath(), target);
      }
    }
  }

  private void getDirToNewDir(FilesDeployService service, String src, String dst,
                              PathImpl dstPath)
    throws IOException
  {
    String []files = service.list(src);

    dstPath.mkdirs();

    for (String file : files) {
      PathImpl target = dstPath.lookup(file);

      get(service, src + "/" + file, target.getFullPath(), target);
    }
  }

  private String getTail(String path)
  {
    String tail = path;

    int p = path.lastIndexOf("/");

    if (p >= 0) {
      return path.substring(p + 1);
    }

    return tail;
  }
}
