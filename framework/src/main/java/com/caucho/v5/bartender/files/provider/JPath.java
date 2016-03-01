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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Iterator;

import com.caucho.v5.util.L10N;

/**
 * BFS implementation of JDK Path.
 */
public class JPath implements Path
{
  private static final L10N L = new L10N(JPath.class);

  private final JFileSystem _fileSystem;
  private final String _fullPath;

  private final BfsFileSync _file;

  public JPath(JFileSystem system, String fullPath)
  {
    fullPath = normalizePathString(fullPath);

    _fileSystem = system;
    _fullPath = fullPath;

    _file = system.lookupFile(fullPath);
  }

  public JPath(JFileSystem system, String fullPath, BfsFileSync file)
  {
    fullPath = normalizePathString(fullPath);

    _fileSystem = system;
    _fullPath = fullPath;

    _file = file;
  }

  private static String normalizePathString(String path)
  {
    if (path.startsWith("bfs:")) {
      path = path.substring("bfs:".length());
    }
    else if (! path.startsWith("//")) {
      path = "//" + path;
    }

    if (! path.equals("///") && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    return path;
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
    return _fileSystem.getRoot();
  }

  @Override
  public Path getFileName()
  {
    return this;
  }

  @Override
  public Path getParent()
  {
    if (isRoot()) {
      return null;
    }

    int index = _fullPath.lastIndexOf('/');

    String parentFullPath;

    if (index == 2) {
      parentFullPath = "/";
    }
    else {
      parentFullPath = _fullPath.substring(0, index);
    }

    return new JPath(_fileSystem, parentFullPath);
  }

  private boolean isRoot()
  {
    return _fullPath.equals("///");
  }

  @Override
  public int getNameCount()
  {
    int count = 0;

    int i = 2;
    int len = _fullPath.length();

    while (0 < i && i < len) {
      char ch = _fullPath.charAt(i++);

      if (ch != '/') {
        count++;

        i = _fullPath.indexOf('/', i);
      }
    }

    return count;
  }

  @Override
  public Path getName(int index)
  {
    if (index < 0) {
      throw new IllegalArgumentException(L.l("index {0} is out of range for path: {1}", index, toUri()));
    }

    int i = 2;

    if (_fullPath.charAt(i) == '/') {
      i++;
    }

    while (index-- > 0) {
      int j = _fullPath.indexOf('/', i);

      if (j < 0) {
        throw new IllegalArgumentException(L.l("index {0} is out of range for path: {1}", index, toUri()));
      }

      i = j + 1;
    }

    int end = _fullPath.indexOf('/', i);
    if (end < 0) {
      end = _fullPath.length();
    }

    String path = _fullPath.substring(0, end);
    JPath jPath = new JPath(_fileSystem, path);

    return jPath;
  }

  @Override
  public Path subpath(int beginIndex, int endIndex)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean startsWith(Path other)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean startsWith(String other)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean endsWith(Path other)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean endsWith(String other)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path normalize()
  {
    return this;
  }

  @Override
  public Path resolve(Path other)
  {
    if (other.isAbsolute()) {
      return other;
    }
    else {
      throw new IllegalStateException(L.l("path is not absolute: {0}", other));
    }
  }

  @Override
  public Path resolve(String other)
  {
    String fullPath;

    if (_fullPath.endsWith("///")) {
      fullPath = _fullPath + other;
    }
    else {
      fullPath = _fullPath + _fileSystem.getSeparator() + other;
    }

    BfsFileSync child = _file.lookup(other);

    return new JPath(_fileSystem, fullPath, child);
  }

  @Override
  public Path resolveSibling(Path other)
  {
    if (other.isAbsolute()) {
      return other;
    }
    else {
      throw new IllegalStateException(L.l("path is not absolute: {0}", other));
    }
  }

  @Override
  public Path resolveSibling(String other)
  {
    Path parentPath = getParent();

    return parentPath.resolve(other);
  }

  @Override
  public Path relativize(Path other)
  {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException();
  }

  @Override
  public URI toUri()
  {
    try {
      return new URI("bfs:" + _fullPath);
    }
    catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Path toAbsolutePath()
  {
    return this;
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
  public WatchKey register(WatchService watcher, Kind<?>... events)
      throws IOException
  {
    return register(watcher, events, new Modifier[0]);
  }

  @Override
  public WatchKey register(WatchService watcher, Kind<?>[] events,
                           Modifier... modifiers) throws IOException
  {
    if (events.length == 0) {
      throw new IllegalArgumentException(L.l("no events specified to watch on: {0}", toUri()));
    }

    JWatchService jWatcher = (JWatchService) watcher;

    WatchKey key = jWatcher.register(this, events, modifiers);

    return key;
  }

  @Override
  public Iterator<Path> iterator()
  {
    ArrayList<Path> list = new ArrayList<>();

    int start = 2;

    if (_fullPath.charAt(start) == '/') {
      start++;
    }

    while (true) {
      int p = _fullPath.indexOf('/', start);

      if (p < 0) {
        break;
      }
      else {
        String path = _fullPath.substring(0, p);
        JPath jPath = new JPath(_fileSystem, path);

        list.add(jPath);

        start = p + 1;
      }
    }

    list.add(this);

    return list.iterator();
  }

  @Override
  public int compareTo(Path other)
  {
    JPath jPath = (JPath) other;

    int value = toUri().compareTo(jPath.toUri());

    if (value < 0) {
      return -1;
    }
    else if (value > 0) {
      return 1;
    }
    else {
      return 0;
    }
  }

  protected BfsFileSync getBfsFile()
  {
    return _file;
  }

  @Override
  public String toString()
  {
    return toUri().toString();
  }
}
