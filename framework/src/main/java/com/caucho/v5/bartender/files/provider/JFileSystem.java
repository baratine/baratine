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

package com.caucho.v5.bartender.files.provider;

import io.baratine.files.BfsFileSync;
import io.baratine.service.ServiceRef;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Set;

public class JFileSystem extends FileSystem
{
  private static String NAME_SEPARATOR = "/";

  private JFileSystemProvider _provider;
  private ServiceRef _root;

  private boolean _isOpen = true;

  private ArrayList<JWatchService> _watchServiceList
    = new ArrayList<>();

  public JFileSystem(JFileSystemProvider provider, ServiceRef root)
  {
    _provider = provider;
    _root = root;
  }

  @Override
  public FileSystemProvider provider()
  {
    return _provider;
  }

  @Override
  public boolean isReadOnly()
  {
    return false;
  }

  @Override
  public String getSeparator()
  {
    return NAME_SEPARATOR;
  }

  @Override
  public Iterable<Path> getRootDirectories()
  {
    JPath path = getRoot();

    ArrayList<Path> list = new ArrayList<>();
    list.add(path);

    return list;
  }

  @Override
  public Iterable<FileStore> getFileStores()
  {
    // TODO
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<String> supportedFileAttributeViews()
  {
    // TODO
    throw new UnsupportedOperationException();
  }

  @Override
  public Path getPath(String first, String... more)
  {
    StringBuilder sb = new StringBuilder();

    sb.append(first);

    for (String sub : more) {
      sb.append(NAME_SEPARATOR);

      sb.append(sub);
    }

    String fullPath = sb.toString();

    JPath path = new JPath(this, fullPath);

    return path;
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern)
  {
    // TODO
    throw new UnsupportedOperationException();
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService()
  {
    // TODO
    throw new UnsupportedOperationException();
  }

  @Override
  public WatchService newWatchService() throws IOException
  {
    JWatchService watchService = new JWatchService(this);

    synchronized (this) {
      _watchServiceList.add(watchService);
    }

    return watchService;
  }

  @Override
  public void close() throws IOException
  {
    ArrayList<JWatchService> watchServiceList = new ArrayList<>();

    synchronized (this) {
      if (! _isOpen) {
        return;
      }

      _isOpen = false;

      watchServiceList.addAll(_watchServiceList);
      _watchServiceList.clear();
    }

    for (JWatchService service : watchServiceList) {
      service.close();
    }
  }

  @Override
  public boolean isOpen()
  {
    synchronized (this) {
      return _isOpen;
    }
  }

  protected BfsFileSync lookupFile(String fullPath)
  {
    return _root.service(fullPath).as(BfsFileSync.class);
  }

  protected JPath getRoot()
  {
    String root = NAME_SEPARATOR;

    JPath path = new JPath(this, root);

    return path;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _root + ", isOpen=" + _isOpen + "]";
  }
}
