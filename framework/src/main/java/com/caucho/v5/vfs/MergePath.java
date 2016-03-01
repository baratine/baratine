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

package com.caucho.v5.vfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.make.DependencyList;
import com.caucho.v5.util.CauchoUtil;

/**
 * A merging of several Paths used like a CLASSPATH.  When the MergePath
 * is opened for read, the first path in the list which contains the file will
 * be the opened file.  When the MergePath is opened for write, the first path
 * in the list is used for the write.
 *
 * <p>In the following example, "first" has priority over "second".
 * If test.xml exists in both "first" and "second", the open will
 * return "first/test.xml".
 *
 * <code><pre>
 * MergePage merge = new MergePath();
 * merge.addMergePath(Vfs.lookup("first");
 * merge.addMergePath(Vfs.lookup("second");
 *
 * Path path = merge.lookup("test.xml");
 * ReadStream is = path.openRead();
 * </pre></code>
 *
 * <p>MergePath corresponds to the "merge:" Vfs schema
 * <code><pre>
   Path path = Vfs.lookup("merge:(../custom-foo;foo)");
 * </pre></code>
 */
public class MergePath extends FilesystemPath {
  private ArrayList<PathImpl> _pathList;

  /**
   * Creates a new merge path.
   */
  public MergePath()
  {
    this(null, "/", null, "/");
  }

  public MergePath(String userPath, String path, PathImpl ... paths)
  {
    this(null, userPath, null, path);

    if (paths != null) {
      for (PathImpl p : paths) {
        addMergePath(p);
      }
    }
  }

  public MergePath(PathImpl ... paths)
  {
    this(paths[0].getUserPath(), paths[0].getFullPath(), paths);
  }

  /**
   * @param path canonical path
   */
  protected MergePath(MergePath root,
                      String userPath, Map<String,Object> attributes,
                      String path)
  {
    super(root, userPath, path);

    if (root == null) {
      _root = this;

      _pathList = new ArrayList<PathImpl>();
    }
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
    int length = filePath.length();

    if (length <= offset || filePath.charAt(offset) != '(')
      return super.schemeWalk(userPath, attributes, filePath, offset);

    MergePath mergePath = createMergePath();
    mergePath.setUserPath(userPath);

    int head = ++offset;
    int tail = head;
    while (tail < length) {
      int ch = filePath.charAt(tail);

      if (ch == ')') {
        if (head + 1 != tail) {
          String subPath = filePath.substring(head, tail);

          if (subPath.startsWith("(") && subPath.endsWith(")"))
            subPath = subPath.substring(1, subPath.length() - 1);

          mergePath.addMergePath(VfsOld.lookup(subPath));
        }

        if (tail + 1 == length)
          return mergePath;
        else
          return mergePath.fsWalk(userPath, attributes, filePath.substring(tail + 1));
      }
      else if (ch == ';') {
        String subPath = filePath.substring(head, tail);

        if (subPath.startsWith("(") && subPath.endsWith(")"))
          subPath = subPath.substring(1, subPath.length() - 1);

        mergePath.addMergePath(VfsOld.lookup(subPath));

        head = ++tail;
      }
      else if (ch == '(') {
        int depth = 1;

        for (tail++; tail < length; tail++) {
          if (filePath.charAt(tail) == '(')
            depth++;
          else if (filePath.charAt(tail) == ')') {
            tail++;
            depth--;
            if (depth == 0)
              break;
          }
        }

        if (depth != 0)
          return new NotFoundPath(getSchemeMap(), filePath);
      }
      else
        tail++;
    }

    return new NotFoundPath(getSchemeMap(), filePath);
  }

  protected MergePath createMergePath()
  {
    return new MergePath();
  }

  /**
   * Adds a new path to the end of the merge path.
   *
   * @param path the new path to search
   */
  public void addMergePath(PathImpl path)
  {
    if (! (path instanceof MergePath)) {
      // Need to normalize so directory paths ends with a "./"
      // XXX:
      //if (path.isDirectory())
      //  path = path.lookup("./");

      ArrayList<PathImpl> pathList = ((MergePath) _root)._pathList;

      if (! pathList.contains(path))
        pathList.add(path);
    }
    else if (((MergePath) path)._root == _root)
      return;
    else {
      MergePath mergePath = (MergePath) path;
      ArrayList<PathImpl> subPaths = mergePath.getMergePaths();
      String pathName = "./" + mergePath._pathname + "/";

      for (int i = 0; i < subPaths.size(); i++) {
        PathImpl subPath = subPaths.get(i);

        addMergePath(subPath.lookup(pathName));
      }
    }
  }

  /**
   * Adds the classpath as paths in the MergePath.
   */
  public void addClassPath()
  {
    addClassPath(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Adds the classpath for the loader as paths in the MergePath.
   *
   * @param loader class loader whose classpath should be used to search.
   */
  public void addClassPath(ClassLoader loader)
  {
    String classpath = null;

    if (loader instanceof DynamicClassLoader)
      classpath = ((DynamicClassLoader) loader).getClassPath();
    else
      classpath = CauchoUtil.getClassPath();

    addClassPath(classpath);
  }

  /**
   * Adds the classpath for the loader as paths in the MergePath.
   *
   * @param loader class loader whose classpath should be used to search.
   */
  public void addResourceClassPath(ClassLoader loader)
  {
    String classpath = null;

    if (loader instanceof DynamicClassLoader)
      classpath = ((DynamicClassLoader) loader).getResourcePathSpecificFirst();
    else
      classpath = CauchoUtil.getClassPath();

    addClassPath(classpath);
  }

  /**
   * Adds the classpath as paths in the MergePath.
   */
  public void addLocalClassPath()
  {
    addLocalClassPath(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Adds the classpath for the loader as paths in the MergePath.
   *
   * @param loader class loader whose classpath should be used to search.
   */
  public void addLocalClassPath(ClassLoader loader)
  {
    String classpath = null;

    if (loader instanceof DynamicClassLoader)
      classpath = ((DynamicClassLoader) loader).getLocalClassPath();
    else
      classpath = System.getProperty("java.class.path");

    addClassPath(classpath);
  }

  /**
   * Adds the classpath for the loader as paths in the MergePath.
   *
   * @param classpath class loader whose classpath should be used to search.
   */
  public void addClassPath(String classpath)
  {
    char sep = CauchoUtil.getPathSeparatorChar();
    int head = 0;
    int tail = 0;
    while (head < classpath.length()) {
      tail = classpath.indexOf(sep, head);

      String segment = null;
      if (tail < 0) {
        segment = classpath.substring(head);
        head = classpath.length();
      }
      else {
        segment = classpath.substring(head, tail);
        head = tail + 1;
      }

      if (segment.equals(""))
        continue;
      else if (segment.endsWith(".jar") || segment.endsWith(".zip"))
        addMergePath(JarPath.create(VfsOld.lookup(segment)));
      else
        addMergePath(VfsOld.lookup(segment));
    }
  }

  /**
   * Return the list of paths searched in the merge path.
   */
  public ArrayList<PathImpl> getMergePaths()
  {
    return ((MergePath) _root)._pathList;
  }

  /**
   * Walking down the path just extends the path.  It won't be evaluated
   * until opening.
   */
  public PathImpl fsWalk(String userPath,
                     Map<String,Object> attributes,
                     String path)
  {
    ArrayList<PathImpl> pathList = getMergePaths();

    if (! userPath.startsWith("/") || pathList.size() == 0)
      return new MergePath((MergePath) _root, userPath, attributes, path);

    String bestPrefix = null;
    for (int i = 0; i < pathList.size(); i++) {
      PathImpl subPath = pathList.get(i);
      String prefix = subPath.getPath();

      if (path.startsWith(prefix) &&
          (bestPrefix == null || bestPrefix.length() < prefix.length())) {
        bestPrefix = prefix;
      }
    }

    if (bestPrefix != null) {
      path = path.substring(bestPrefix.length());
      if (! path.startsWith("/"))
        path = "/" + path;

      return new MergePath((MergePath) _root, userPath, attributes, path);
    }

    return pathList.get(0).lookup(userPath, attributes);
  }

  /**
   * Returns the scheme of the best path.
   */
  public String getScheme()
  {
    return getBestPath().getScheme();
  }

  /**
   * Returns the full path name of the best path.
   */
  /*
  @Override
  public String getFullPath()
  {
    Path path = getBestPath();

    return path.getFullPath();
  }
  */

  /**
   * Returns the full native path name of the best path.
   */
  /*
  @Override
  public String getNativePath()
  {
    Path path = getBestPath();

    return path.getNativePath();
  }
  */

  /**
   * Returns the URL of the best path.
   */
  /*
  @Override
  public String getURL()
  {
    Path path = getBestPath();

    if (! path.exists()) {
      path = getWritePath();
    }

    return path.getURL();
  }
  */

  /**
   * Returns the relative path into the merge path.
   */
  public String getRelativePath()
  {
    if (_pathname.startsWith("/"))
      return "." + _pathname;
    else
      return _pathname;
  }

  /**
   * True if any file matching this path exists.
   */
  public boolean exists()
  {
    return getBestPath().exists();
  }

  /**
   * Returns equivalent of struct stat.st_mode if appropriate.
   */
  @Override
  public int getMode()
  {
    return getBestPath().getMode();
  }

  /**
   * Tests if the path is marked as executable
   */
  @Override
  public boolean isExecutable()
  {
    return getBestPath().isExecutable();
  }

  /**
   * True if the best path is a directory.
   */
  public boolean isDirectory()
  {
    return getBestPath().isDirectory();
  }

  /**
   * True if the best path is a file.
   */
  public boolean isFile()
  {
    return getBestPath().isFile();
  }

  /**
   * Returns the length of the best path.
   */
  public long length()
  {
    return getBestPath().length();
  }

  /**
   * Returns the last modified time of the best path.
   */
  public long getLastModified()
  {
    return getBestPath().getLastModified();
  }

  /**
   * Returns true if the best path can be read.
   */
  public boolean canRead()
  {
    return getBestPath().canRead();
  }

  /**
   * Returns true if the best path can be written to.
   */
  public boolean canWrite()
  {
    // overriding by shadowing
    // return getBestPath().canWrite();
    return getBestPath().canRead();
  }

  /**
   * Returns all the resources matching the path.
   */
  public ArrayList<PathImpl> getResources(String pathName)
  {
    ArrayList<PathImpl> list = new ArrayList<PathImpl>();

    String pathname = _pathname;
    // XXX: why was this here?
    if (pathname.startsWith("/"))
      pathname = "." + pathname;

    ArrayList<PathImpl> pathList = ((MergePath) _root)._pathList;
    for (int i = 0; i < pathList.size(); i++) {
      PathImpl path = pathList.get(i);

      path = path.lookup(pathname);

      ArrayList<PathImpl> subResources = path.getResources(pathName);
      for (int j = 0; j < subResources.size(); j++) {
        PathImpl newPath = subResources.get(j);

        if (! list.contains(newPath))
          list.add(newPath);
      }
    }

    return list;
  }

  /**
   * Returns all the resources matching the path.
   */
  public ArrayList<PathImpl> getResources()
  {
    ArrayList<PathImpl> list = new ArrayList<PathImpl>();

    String pathname = _pathname;
    // XXX: why?
    if (pathname.startsWith("/"))
      pathname = "." + pathname;

    ArrayList<PathImpl> pathList = ((MergePath) _root)._pathList;
    for (int i = 0; i < pathList.size(); i++) {
      PathImpl path = pathList.get(i);

      path = path.lookup(pathname);

      ArrayList<PathImpl> subResources = path.getResources();
      for (int j = 0; j < subResources.size(); j++) {
        PathImpl newPath = subResources.get(j);

        if (! list.contains(newPath))
          list.add(newPath);
      }
    }

    return list;
  }

  /**
   * List the merged directories.
   */
  public String []list() throws IOException
  {
    ArrayList<String> list = new ArrayList<String>();

    String pathname = _pathname;
    // XXX:??
    if (pathname.startsWith("/"))
      pathname = "." + pathname;

    ArrayList<PathImpl> pathList = ((MergePath) _root)._pathList;
    for (int i = 0; i < pathList.size(); i++) {
      PathImpl path = pathList.get(i);

      path = path.lookup(pathname);

      if (path.isDirectory()) {
        String[]subList = path.list();

        for (int j = 0; subList != null && j < subList.length; j++) {
          if (! list.contains(subList[j]))
            list.add(subList[j]);
        }
      }
    }

    return (String []) list.toArray(new String[list.size()]);
  }

  /**
   * XXX: Probably should mkdir in the first path
   */
  @Override
  public boolean mkdir()
    throws IOException
  {
    return getWritePath().mkdir();
  }

  /**
   * XXX: Probably should mkdir in the first path
   */
  @Override
  public boolean mkdirs()
    throws IOException
  {
    return getWritePath().mkdirs();
  }

  /**
   * Remove the matching path.
   */
  public boolean remove()
    throws IOException
  {
    return getBestPath().remove();
  }

  /**
   * Renames the path.
   */
  public boolean renameTo(PathImpl path)
    throws IOException
  {
    return getBestPath().renameTo(path);
  }

  /**
   * Opens the best path for reading.
   */
  @Override
  public StreamImpl openReadImpl() throws IOException
  {
    StreamImpl stream = getBestPath().openReadImpl();
    //stream.setPath(this);
    return stream;
  }

  /**
   * Opens the best path for writing.  XXX: If the best path doesn't
   * exist, this should probably create the file in the first path.
   */
  @Override
  public StreamImpl openWriteImpl() throws IOException
  {
    StreamImpl stream = getWritePath().openWriteImpl();
    //stream.setPath(this);

    return stream;
  }

  /**
   * Opens the best path for reading and writing.  XXX: If the best path
   * doesn't exist, this should probably create the file in the first path.
   */
  @Override
  public StreamImpl openReadWriteImpl() throws IOException
  {
    StreamImpl stream = getWritePath().openReadWriteImpl();
    //stream.setPath(this);
    return stream;
  }

  /**
   * Opens the best path for appending.  XXX: If the best path
   * doesn't exist, this should probably create the file in the first path.
   */
  @Override
  public StreamImpl openAppendImpl() throws IOException
  {
    StreamImpl stream = getWritePath().openAppendImpl();
    //stream.setPath(this);
    return stream;
  }

  /**
   * Opens the best path for random access.
   */
  @Override
  public RandomAccessStream openFileRandomAccess() throws IOException
  {
    RandomAccessStream stream = getWritePath().openFileRandomAccess();
    // stream.setPath(this);
    return stream;
  }

  /**
   * Returns the first matching path.
   */
  public PathImpl getWritePath()
  {
    ArrayList<PathImpl> pathList = getPathList();

    if (pathList.size() == 0)
      return new NotFoundPath(getSchemeMap(), _pathname);
    else {
      return pathList.get(0);
    }
  }

  /**
   * Creates a dependency.
   */
  @Override
  public PersistentDependency createDepend()
  {
    ArrayList<PathImpl> pathList = ((MergePath) _root)._pathList;

    if (pathList.size() == 1)
      return (PersistentDependency) pathList.get(0).createDepend();

    DependencyList dependList = new DependencyList();

    for (int i = 0; i < pathList.size(); i++) {
      PathImpl path = pathList.get(i);

      PathImpl realPath = path.lookup(_pathname);

      dependList.add((PersistentDependency) realPath.createDepend());
    }

    return dependList;
  }

  /**
   * Returns the first matching path.
   */
  public PathImpl getBestPath()
  {
    /*
    if (_bestPath != null)
      return _bestPath;
      */

    ArrayList<PathImpl> pathList = getPathList();
    for (int i = 0; i < pathList.size(); i++) {
      PathImpl path = pathList.get(i);

      if (path.exists()) {
        return path;
      }
    }

    if (pathList.size() > 0) {
      return pathList.get(0);
    }

    return new NotFoundPath(getSchemeMap(), _userPath);
  }

  protected ArrayList<PathImpl> getPathList()
  {
    if (_pathList == null) {
      String pathname = _pathname;
    //  XXX:??
      if (pathname.startsWith("/"))
        pathname = "." + pathname;

      ArrayList<PathImpl> pathList = new ArrayList<PathImpl>();

      ArrayList<PathImpl> rootPathList = ((MergePath) _root)._pathList;
      for (int i = 0; i < rootPathList.size(); i++) {
        PathImpl rootPath = rootPathList.get(i);

        PathImpl realPath = rootPath.lookup(pathname);
        realPath.setUserPath(_userPath);

        pathList.add(realPath);
      }

      _pathList = pathList;
    }

    return _pathList;
  }

  @Override
  public PathImpl copy()
  {
    MergePath root = (MergePath) _root;

    if (root == this) {
      root = null;
    }
    else {
      root = (MergePath) root.copy();
    }

    MergePath copy = new MergePath(root, _userPath, null, _pathname);

    ArrayList<PathImpl> pathList = getPathList();

    for (PathImpl path : pathList) {
      copy.addMergePath(path.copy());
    }

    return copy;
  }

  /**
   * Returns a name for the path
   */
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _pathname + "]";
  }
}
