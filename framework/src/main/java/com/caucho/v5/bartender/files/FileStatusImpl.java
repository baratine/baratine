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

import io.baratine.files.Status;
import io.baratine.service.ServiceNode;

/**
 * Entry to the filesystem.
 */
public class FileStatusImpl implements Status
{
  private final String _path;
  private final Status.FileType _fileType;
  private final long _length;
  private final long _version;
  private final long _modifiedTime;
  private final long _checksum;
  private final ServiceNode _node;

 public FileStatusImpl(String path,
                       Status.FileType fileType,
                       long version,
                       long length,
                       long modifiedTime,
                       long checksum,
                       ServiceNode node)
  {
    _path = path;
    _fileType = fileType;

    _length = length;
    _version = version;
    _modifiedTime = modifiedTime;
    _checksum = checksum;

    _node = node;
  }

  @Override
  public String getPath()
  {
    return _path;
  }

  @Override
  public Status.FileType getType()
  {
    return _fileType;
  }

  @Override
  public long getLength()
  {
    return _length;
  }

  @Override
  public long getChecksum()
  {
    return _checksum;
  }

  @Override
  public long getVersion()
  {
    return _version;
  }

  @Override
  public long getLastModifiedTime()
  {
    return _modifiedTime;
  }

  @Override
  public ServiceNode getNode()
  {
    return _node;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getPath() + "]";
  }
}
