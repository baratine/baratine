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
import io.baratine.files.Watch;
import io.baratine.files.WriteOption;
import io.baratine.service.Cancel;
import io.baratine.service.ResultFuture;
import io.baratine.service.ServiceRef;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.vfs.FilesystemPath;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsStreamOld;

/**
 * Virtual path based on an expansion repository
 */
public class BfsPath extends FilesystemPath
{
  private ServiceRef _rootServiceRef;
  private BfsPathRoot _root;
  private BfsFileSync _file;
  
  protected BfsPath()
  {
    super((BfsPath) null, "/", "/");
  }
  
  protected BfsPath(BfsPathRoot rootPath,
                    ServiceRef rootServiceRef,
                    String userPath,
                    String newPath)
  {
    super(rootPath, userPath, newPath);
    
    Objects.requireNonNull(rootPath);
    Objects.requireNonNull(rootServiceRef);
   
    _root = rootPath;
    _rootServiceRef = rootServiceRef;
    
    if (newPath.startsWith("/") && ! newPath.startsWith("//")) {
      newPath = "//" + newPath;
    }
    
    _file = rootServiceRef.service(newPath).as(BfsFileSync.class);
    
    Objects.requireNonNull(_file);
  }

  @Override
  public String getScheme()
  {
    return "bfs";
  }
  
  protected BfsPathRoot getRootPath()
  {
    return _root;
  }
  
  protected ServiceRef getRootServiceRef()
  {
    return _rootServiceRef;
  }

  /**
   * schemeWalk is called by Path for a scheme lookup like file:/tmp/foo
   *
   * @param userPath the user's lookup() path
   * @param attributes the user's attributes
   * @param filePath the actual lookup() path
   * @param offset offset into filePath
   */
  @Override
  public PathImpl schemeWalk(String userPath,
                         Map<String,Object> attributes,
                         String filePath,
                         int offset)
  {
    String canonicalPath = filePath;

    if (offset < filePath.length() && filePath.charAt(offset) == '/') {
//      canonicalPath = normalizePath("/", filePath, offset, _separatorChar);
    }
    else {
      canonicalPath = normalizePath(_pathname, filePath, offset,
                                    _separatorChar);
    }

    /*
    if (canonicalPath.startsWith("/") && ! canonicalPath.startsWith("//")) {
      canonicalPath = "//" + canonicalPath;
    }
    */

    return fsWalk(userPath, attributes, canonicalPath);
  }

  @Override
  public PathImpl fsWalk(String userPath, 
                     Map<String, Object> newAttributes,
                     String newPath)
  {
    if (newPath.startsWith("bfs:")) {
      newPath = newPath.substring("bfs:".length());
    }
    
    return new BfsPath(getRootPath(), getRootServiceRef(), userPath, newPath);
  }
  
  @Override
  public boolean exists()
  {
    update();
    
    Status status = _file.getStatus();
    
    if (status == null) {
      return false;
    }
    else {
      return status.getType() != Status.FileType.NULL;
    }
  }
  
  @Override
  public boolean isFile()
  {
    update();
    
    Status status = _file.getStatus();
    
    if (status == null) {
      return false;
    }
    else {
      return status.getType() == Status.FileType.FILE;
    }
  }
  
  @Override
  public boolean isDirectory()
  {
    update();
    
    Status status = _file.getStatus();
    
    if (status == null) {
      return false;
    }
    else {
      return status.getType() == Status.FileType.DIRECTORY;
    }
  }
  
  @Override
  public boolean canRead()
  {
    return isFile();
  }
  
  @Override
  public boolean canWrite()
  {
    return true;
  }
  
  @Override
  public long length()
  {
    update();
    
    Status status = _file.getStatus();
    
    if (status != null) {
      return status.getLength();
    }
    else {
      return -1;
    }
  }

  @Override
  public long getCrc64()
  {
    update();
    
    Status status = _file.getStatus();
    
    if (status != null) {
      return status.getChecksum();
    }
    else {
      return 0;
    }
  }
  
  @Override
  public long getLastModified()
  {
    update();
    
    Status status = _file.getStatus();
    
    if (status != null) {
      return status.getLastModifiedTime();
    }
    else {
      return -1;
    }
  }
  
  @Override
  public boolean remove()
  {
    ResultFuture<Boolean> future = new ResultFuture<>();
    
    _file.remove(future);
    
    return future.get(1, TimeUnit.SECONDS);
  }
  
  //
  // directory
  //
  
  @Override
  public String []list()
  {
    return _file.list();
  }
 
  //
  // file opening
  //
  
  @Override
  public StreamImpl openReadImpl()
    throws IOException
  {
    update();
    
    // return _file.openRead();
    
    InputStream is = _file.openRead();
    
    return new VfsStreamOld(is);
  }
  
  @Override
  public StreamImpl openWriteImpl()
    throws IOException
  {
    update();
    
    // return _file.openRead();
    
    OutputStream os = _file.openWrite(WriteOption.Standard.CLOSE_WAIT_FOR_PUT);
    
    return new VfsStreamOld(os);
  }
  
  //
  // watches
  //

  /**
   * Adds a watch for changes to the path
   */
  @Override
  public Cancel watch(Watch watch)
  {
    return _file.watch(watch);
  }
  
  /**
   * Removes a watch for changes to the path
   */
  /*
  @Override
  public void removeWatch(Watch watch)
  {
    _file.unregisterWatch(watch);
  }
  */
  
  private void update()
  {
    // _repository.update();
  }
}