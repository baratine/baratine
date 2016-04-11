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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.Objects;

/**
 * Base path
 */
public class PathBase implements Path
{
  private FileSystem _fileSystem;
  private String _path;
  
  PathBase(FileSystem fileSystem,
           String path)
  {
    Objects.requireNonNull(fileSystem);
    Objects.requireNonNull(path);
    
    _fileSystem = fileSystem;
    _path = path;
  }
  
  public String path()
  {
    return _path;
  }

  @Override
  public FileSystem getFileSystem()
  {
    return _fileSystem;
  }

  @Override
  public boolean isAbsolute()
  {
    return true;
  }

  @Override
  public Path getRoot()
  {
    return new PathBase(_fileSystem, "");
  }

  @Override
  public Path getFileName()
  {
    return this; // ??
  }

  @Override
  public Path getParent()
  {
    int p = _path.lastIndexOf('/');
    
    if (p > 0) {
      return new PathBase(_fileSystem, _path.substring(0, p));
    }
    else {
      return this;
    }
  }

  @Override
  public int getNameCount()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path getName(int index)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path subpath(int beginIndex, int endIndex)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean startsWith(Path other)
  {
    if (! (other instanceof PathBase)) {
      return false;
    }
    
    PathBase otherBase = (PathBase) other;
    
    return (otherBase._fileSystem == _fileSystem
            && _path.startsWith(otherBase._path));
  }

  @Override
  public boolean startsWith(String other)
  {
    return _path.startsWith(other);
  }

  @Override
  public boolean endsWith(Path other)
  {
    return false;
  }

  @Override
  public boolean endsWith(String other)
  {
    return false;
  }

  @Override
  public Path normalize()
  {
    return this;
  }

  @Override
  public Path resolve(Path other)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path resolve(String other)
  {
    String path;

    if (_path.endsWith(_fileSystem.getSeparator()))
      path = _path + other;
    else if (other.startsWith(_fileSystem.getSeparator()))
      path = _path + other;
    else
    path = _path + _fileSystem.getSeparator() + other;

    return new PathBase(_fileSystem, path);
  }

  @Override
  public Path resolveSibling(Path other)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path resolveSibling(String other)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path relativize(Path other)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public URI toUri()
  {
    try {
      return new URI(_fileSystem.provider().getScheme() + ":///" + _path);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Path toAbsolutePath()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path toRealPath(LinkOption... options) throws IOException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public File toFile()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public WatchKey register(WatchService watcher, Kind<?>[] events,
                           Modifier... modifiers) throws IOException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public WatchKey register(WatchService watcher, Kind<?>... events)
      throws IOException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<Path> iterator()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public int compareTo(Path other)
  {
    return toUri().compareTo(other.toUri());
  }
  
  @Override
  public String toString()
  {
    return _fileSystem.provider().getScheme() + "://" + _path;
  }
}
