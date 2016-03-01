/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.io;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

/**
 * The classpath scheme.
 */
public class FileSystemBoot extends FileSystem
{
  private FileProviderBoot _provider = new FileProviderBoot();
  
  @Override
  public FileSystemProvider provider()
  {
    return _provider;
  }

  @Override
  public void close() throws IOException
  {
  }

  @Override
  public boolean isOpen()
  {
    return true;
  }

  @Override
  public boolean isReadOnly()
  {
    return true;
  }

  @Override
  public String getSeparator()
  {
    return "/";
  }

  @Override
  public Iterable<Path> getRootDirectories()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterable<FileStore> getFileStores()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<String> supportedFileAttributeViews()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path getPath(String first, String... more)
  {
    return new PathBase(this, first);
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public WatchService newWatchService() throws IOException
  {
    throw new UnsupportedOperationException();
  }
}
