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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.Direct;
import com.caucho.v5.util.L10N;

import io.baratine.db.BlobReader;
import io.baratine.files.BfsFile;
import io.baratine.files.Status;
import io.baratine.files.Watch;
import io.baratine.files.WriteOption;
import io.baratine.service.Cancel;
import io.baratine.service.Result;
import io.baratine.service.Service;

/**
 * Entry to the filesystem.
 */
public class BfsFileImpl implements BfsFileAmp
{
  private static final Logger log
    = Logger.getLogger(BfsFileImpl.class.getName());

  private static final L10N L = new L10N(BfsFileImpl.class);

  private final FileServiceRootImpl _root;

  private final String _path;
  private final String _relPath;

  // private ArrayList<String> _fileList;
  //private ArrayList<WatchImpl> _watchList;

  private BfsFileAmp _service;

  //private boolean _isUpdate;

  private int _hash;
  
  // private ArrayList<String> _list;

  BfsFileImpl(FileServiceRootImpl root, 
              String fullPath,
              String relPath)
  {
    Objects.requireNonNull(root);

    _root = root;
    _path = fullPath;
    _relPath = relPath;
    
    _hash = root.hash(fullPath);

    /*
    if (path.startsWith("//")) {
      path = "bfs:" + path;
    }
    else {
      String podName = root.getPodName();

      if (podName.startsWith("cluster_hub.")) {
        int p = podName.indexOf('.');

        podName = podName.substring(p + 1);
      }

      path = "bfs://" + podName + path;
    }
    */
    
    _service = _root.getServiceRef().pin(this).as(BfsFileAmp.class);
  }

  BfsFileAmp getService()
  {
    if (_service == null) {
    }
    
    return _service;
  }

  @Override
  @Direct
  public void lookup(String path, Result<BfsFile> result)
  {
    if (path.startsWith("/")
        || path.contains("//")
        || path.contains("..")
        || path.endsWith("/")) {
      throw new IllegalArgumentException(L.l("'{0}' is an invalid path", path));
    }

    result.ok(_root.lookup(_relPath + "/" + path));
  }

  @Override
  //@Direct
  public void getStatus(Result<Status> result)
  {
    // System.out.println("  GS-IMPL: " + result);
    if (true) {
      getStatusSafe(result);
      return;
    }
    // XXX: work on caching: bartender/a012
    FileStatusImpl status; // _status;
      
    if (_root.isOwner(_hash)
        && (status = _root.getStatusEntry(_path)) != null) {
      result.ok(toStatusResult(status));
    }
    else {
      getService().getStatusSafe(result);
    }
  }

  @Override
  public void getStatusSafe(Result<Status> result)
  {
    FileStatusImpl status = null; // _status;
    
    // XXX: work on caching: bartender/a012
    if (! _root.isOwner(_hash)) {
      _root.getStatus(_path, result);
    }
    else if ((status = _root.getStatusEntry(_path)) != null) {
      result.ok(toStatusResult(status));
    }
    else {
      _root.getStatus(_path,
                      result.of(s->{
                        _root.setStatusEntry(_path, s);
                        return toStatusResult(s);
                      }));
    }
  }
  
  private Status toStatusResult(Status status)
  {
    return status;
    /*
    if (status.getLength() < 0) {
      return null;
    }
    else {
      return status;
    }
    */
  }

  @Override
  public void list(Result<String[]> result)
  {
    _root.listImpl(_path, result.of(v->listResult(v)));
  }

  private String []listResult(Object value)
  {
    if (value instanceof ArrayList) {
      ArrayList<String> list = (ArrayList) value;
      
      return listResult(list);
    }
    else if (value == null) {
      return new String[0];
    }
    else {
      log.warning(L.l("broken directory value: {0}", value));
      
      return new String[0];
    }
  }
  
  private String []listResult(ArrayList<String> list)
  {
    if (list != null) {
      String []valueArray = new String[list.size()];
      list.toArray(valueArray);
      
      return valueArray;
    }
    else {
      throw new IllegalStateException();
    }
  }

  @Override
  //@Direct
  public void openRead(Result<InputStream> result)
  {
    if (true) {
      openReadSafe(result);
      return;
    }
    //if (_root.isPrimary(_hash)) {
    
    FileEntry entry = _root.getOwnerEntry(_path);
    
    if (entry != null && entry.isReadClean()) {
      _root.openReadFile(_path, result);
    }
    else {
      getService().openReadSafe(result);
    }
  }

  @Override
  public void openReadSafe(Result<InputStream> result)
  {
    _root.openReadFile(_path, result);
  }

  @Override
  @Direct
  public void openReadBlob(Result<BlobReader> result)
  {
    //if (_root.isPrimary(_hash)) {
    
    FileEntry entry = _root.getOwnerEntry(_path);
    
    if (entry != null && entry.isReadClean()) {
      _root.openReadBlob(_path, result);
    }
    else {
      getService().openReadBlobSafe(result);
    }
  }

  @Override
  public void openReadBlobSafe(Result<BlobReader> result)
  {
    _root.openReadBlob(_path, result);
  }

  /**
   * Open a file for writing.
   */
  @Override
  //@Direct
  public void openWrite(Result<OutputStream> result,
                        WriteOption ...options)
  {
    result.ok(_root.openWriteFile(_path, options));
  }

  /**
   * Remove a file.
   */
  @Override
  public void remove(Result<Boolean> result)
  {
    _root.remove(_path, result);
  }

  /**
   * Remove a directory and all sub-directories
   */
  @Override
  public void removeAll(Result<Boolean> result)
  {
    _root.removeAll(_path, result);
  }

  /**
   * Copies to a destination file
   */
  @Override
  @Direct
  public void copyTo(String relPath,
                     Result<Boolean> result,
                     WriteOption ...options)
  {
    _root.copyTo(_path, toAbsolute(relPath), result, options);
  }

  /**
   * Renames to a destination file
   */
  @Override
  @Direct
  public void renameTo(String relPath,
                     Result<Boolean> result,
                     WriteOption ...options)
  {
    _root.renameTo(_path, toAbsolute(relPath), result, options);
  }
  
  private String toAbsolute(String relPath)
  {
    if (relPath.indexOf("//") >= 0
        || relPath.indexOf("..") >= 0
        || relPath.endsWith("/")) {
      throw new IllegalArgumentException(L.l("'{0}' is invalid relative path", relPath));
    }
    
    if (relPath.startsWith("/")) {
      return relPath;
    }
    else {
      int p = _path.lastIndexOf('/');
      
      if (p > 0) {
        return _path.substring(0, p + 1) + relPath;
      }
      else {
        return "/" + relPath;
      }
    }
  }

  @Override
  public void watch(@Service Watch watch, Result<Cancel> result)
  {
    _root.watch(_path, watch, result);
  }

  void addFile(String tail,
               Result<Boolean> result)
  {
    clearStatus();
    
    //_root.addDirectory(_path, tail, (Result) result);
    
    // list(result.from((list,r)->listAddFile(list, tail, r)));
  }

  void removeFile(String tail, Result<Boolean> result)
  {
    clearStatus();
    
    // list(result.from((list,r)->listRemoveFile(list, tail, r)));
    
    _root.removeDirectory(_path, tail, result.of(v->true));
  }

  void clearStatus()
  {
  }

  void onChange()
  {
    clearStatus();

    /*
    if (_watchList != null) {
      for (WatchImpl watch : _watchList) {
        // XXX: need to add pod
        watch.getWatch().onUpdate("bfs://" + _path);
      }
    }
    */

    // XXX: sync issues while pending; needs to load, bfs/1032
    // _fileList = null;
    //_status = null;
    //_root.list(_path, new ListUpdate());
  }

  /*
  void onDirChange()
  {
    clearStatus();

    if (! _isUpdate) {
      _isUpdate = true;

      // _root.list(_path, new ListUpdate());
      
      _isUpdate = false;
    }
  }
  */

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _path + "]";
  }
}
