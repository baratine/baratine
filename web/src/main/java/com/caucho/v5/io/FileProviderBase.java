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
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;

/**
 * Base file provider
 */
public class FileProviderBase extends FileSystemProvider
{
  @Override
  public String getScheme()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileSystem newFileSystem(URI uri, Map<String, ?> env)
      throws IOException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileSystem getFileSystem(URI uri)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path getPath(URI uri)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public SeekableByteChannel newByteChannel(Path path,
                                            Set<? extends OpenOption> options,
                                            FileAttribute<?>... attrs)
                                                throws IOException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Path dir,
                                                  Filter<? super Path> filter)
                                                      throws IOException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs)
      throws IOException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete(Path path) throws IOException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options)
      throws IOException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void move(Path source, Path target, CopyOption... options)
      throws IOException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSameFile(Path path, Path path2) 
    throws IOException
  {
    return path.toUri().equals(path2.toUri());
  }

  @Override
  public boolean isHidden(Path path) throws IOException
  {
    return false;
  }

  @Override
  public FileStore getFileStore(Path path) throws IOException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException
  {
    // throw new UnsupportedOperationException();
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Path path,
                                                              Class<V> type,
                                                              LinkOption... options)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(Path path,
                                                          Class<A> type,
                                                          LinkOption... options)
                                                              throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String attributes,
                                            LinkOption... options)
                                                throws IOException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAttribute(Path path, String attribute, Object value,
                           LinkOption... options) throws IOException
  {
    throw new UnsupportedOperationException();
  }
}
