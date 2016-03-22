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

package com.caucho.v5.bartender.files;

import io.baratine.files.BfsFileSync;
import io.baratine.files.Status;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.vfs.StreamSourceInputStream;

/**
 * Admin deployment to the filesystem.
 */

@Service("public:///bartender-files")
public class FilesDeployServiceImpl
{
  private static final Logger log
    = Logger.getLogger(FilesDeployServiceImpl.class.getName());

  private ServiceRef _rootServiceRef;

  FilesDeployServiceImpl(ServiceRef rootServiceRef)
  {
    _rootServiceRef = rootServiceRef;
  }

  public void list(String path, Result<String[]> result)
  {
    BfsFileSync file = lookupFile(path);

    file.list(result);
  }

  public void getStatus(String path, Result<Status> result)
  {
    BfsFileSync file = lookupFile(path);

    file.getStatus(result);
  }

  public boolean putFile(String path, StreamSource data)
  {
    // XXX: need admin logging
    log.info("putFile " + path);

    if (data == null) {
      return false;
    }

    BfsFileSync file = lookupFile(path);

    try (OutputStream os = file.openWrite()) {
      IoUtil.copy(data.getInputStream(), os);

    } catch (IOException e) {
      log.log(Level.WARNING, e.toString(), e);

      return false;
    }

    return true;
  }

  public void removeFile(String path, Result<Boolean> result)
  {
    // XXX: need admin logging
    log.info("removeFile " + path);

    BfsFileSync file = lookupFile(path);

    file.remove(result);
  }

  public void removeAll(String path, Result<Boolean> result)
  {
    // XXX: need admin logging
    log.info("removeAll " + path);

    BfsFileSync file = lookupFile(path);
    
    file.removeAll(result);
  }

  public void copyFile(String src, String dest, Result<Boolean> result)
  {
    // XXX: need admin logging
    log.info("copyFile " + src + " to " + dest);

    //copyFileImpl(src, dest, result);

    getStatus(src, result.of((statusSrc,r) -> {
        getStatus(dest, r.of((statusDest,r2) -> {
           copyFile(statusSrc, statusDest, r2);
        }));
    }));
  }

  private void copyFile(Status statusSrc, Status statusDest,
                        Result<Boolean> result)
  {
    String src = statusSrc.getPath();
    String dest = statusDest.getPath();

    if (statusSrc.getType() == Status.FileType.FILE) {
      if (statusDest.getType() == Status.FileType.DIRECTORY
          || dest.endsWith("/")) {
        copyFileToDir(src, dest, result);
      }
      else {
        copyFileImpl(src, dest, result);
      }
    }
    else {
      result.ok(false);
    }
  }

  private void copyFileToDir(String src, String dir, Result<Boolean> result)
  {
    String tail = src;

    int pos = tail.lastIndexOf("/");
    if (pos >= 0) {
      tail = tail.substring(pos + 1);
    }

    if (dir.endsWith("/")) {
      dir = dir + tail;
    }
    else {
      dir = dir + "/" + tail;
    }

    copyFileImpl(src, dir, result);
  }

  private void copyFileImpl(String src, String dest, Result<Boolean> result)
  {
    getFile(src, result.of(stream -> {
        putFile(dest, stream);

        return true;
    }));
  }

  public void copyAll(String src, String dest, Result<Boolean> result)
  {
    // XXX: need admin logging
    log.info("copyAll " + src + " to " + dest);

    copyAllImpl(src, dest, result);
  }

  private void copyAllImpl(String src, String dest, Result<Boolean> result)
  {
    getStatus(src, result.of((statusSrc,r) -> {
        getStatus(dest, r.of((statusDest,r2) -> {
            copyAllImpl(statusSrc, statusDest, r2);
        }));
    }));
  }

  private void copyAllImpl(Status statusSrc,
                           Status statusDest,
                           Result<Boolean> result)
  {
    String src = statusSrc.getPath();
    String dest = statusDest.getPath();

    if (statusSrc.getType() == Status.FileType.FILE) {
      if (statusDest.getType() == Status.FileType.DIRECTORY) {
        copyFileToDir(src, dest, result);
      }
      else {
        copyFileImpl(src, dest, result);
      }
    }
    else if (statusSrc.getType() == Status.FileType.DIRECTORY) {
      if (statusDest.getType() == Status.FileType.FILE) {
        result.ok(false);
      }
      else {
        copyDir(src, dest, result);
      }
    }
    else {
      result.ok(false);
    }
  }

  private void copyDir(String src, String dest, Result<Boolean> result)
  {
    String tail = src;

    int pos = tail.lastIndexOf("/");
    if (pos >= 0) {
      tail = tail.substring(pos + 1);
    }

    if (dest.endsWith("/")) {
      dest = dest + tail;
    }
    else {
      dest = dest + "/" + tail;
    }

    String dir = dest;

    list(src, result.of((files,r) -> {
        copyFilesToDir(files, 0, dir, r);
    }));
  }

  private void copyFilesToDir(String []files, int index, String dir,
                              Result<Boolean> result)
  {
    if (index < files.length) {
      copyAllImpl(files[index], dir, result.of((isSuccessful,r) -> {
          copyFilesToDir(files, index + 1, dir, r);
      }));
    }
    else {
      result.ok(true);
    }
  }

  public void moveFile(String src, String dest, Result<Boolean> result)
  {
    // XXX: need admin logging
    log.info("moveFile " + src + " to " + dest);

    getFile(src, result.of((stream,r) -> {
        putFile(dest, stream);
        removeFile(src, (isDeleted,e) -> r.handle(isDeleted, e));
    }));
  }

  public void moveAll(String src, String dest, Result<Boolean> result)
  {
    // XXX: need admin logging
    log.info("moveAll " + src + " to " + dest);

    getStatus(dest, result.of((Status status,Result<Boolean> r) -> {
        if (status.getType() == Status.FileType.NULL) {
          copyAllImpl(src, dest, r.of((isSuccessful,r2) -> {
              removeAll(src, r2);
          }));
        }
        else {
          r.ok(false);
        }
    }));
  }

  public void getFile(String path, Result<StreamSource> result)
  {
    BfsFileSync file = lookupFile(path);

    file.openRead(new GetFileResult(result, path));
  }

  BfsFileSync lookupFile(String path)
  {
    if (path.startsWith("bfs:")) {
      path = path.substring("bfs:".length());
    }

    if (path.startsWith("//")) {
    }
    else if (path.startsWith("/")) {
      path = "//" + path;
    }

    return _rootServiceRef.lookup(path).as(BfsFileSync.class);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }

  static class GetFileResult extends Result.Wrapper<InputStream,StreamSource>
  {
    private final String _path;

    GetFileResult(Result<StreamSource> result, String path)
    {
      super(result);

      _path = path;
    }

    @Override
    public void ok(InputStream is)
    {
      if (log.isLoggable(Level.FINER)) {
        log.finer("getFile " + _path + " -> " + is);
      }

      if (is != null) {
        delegate().ok(new StreamSource(new StreamSourceInputStream(is)));
      }
      else {
        delegate().ok(null);
      }
    }
  }
}
