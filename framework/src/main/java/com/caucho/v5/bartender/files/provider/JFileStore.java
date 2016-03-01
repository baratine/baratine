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

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

public class JFileStore extends FileStore
{
  private final JFileSystemProvider _provider;

  public JFileStore(JFileSystemProvider provider)
  {
    _provider = provider;
  }

  @Override
  public String name()
  {
    return "bfs";
  }

  @Override
  public String type()
  {
    return "bfs";
  }

  @Override
  public boolean isReadOnly()
  {
    return false;
  }

  @Override
  public long getTotalSpace() throws IOException
  {
    return -1;
  }

  @Override
  public long getUsableSpace() throws IOException
  {
    return -1;
  }

  @Override
  public long getUnallocatedSpace() throws IOException
  {
    return -1;
  }

  @Override
  public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type)
  {
    return false;
  }

  @Override
  public boolean supportsFileAttributeView(String name)
  {
    return true;
  }

  @Override
  public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type)
  {
    return null;
  }

  @Override
  public Object getAttribute(String attribute) throws IOException
  {
    return null;
  }
}
