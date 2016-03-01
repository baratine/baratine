/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights - reserved
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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.loader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.PostConstruct;

import com.caucho.v5.config.ConfigArg;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.Configurable;
import com.caucho.v5.io.Dependency;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.PathImpl;

/**
 * Class loader which checks for changes in class files and automatically
 * picks up new jars.
 */
@Configurable
public class TreeLoader extends JarListLoader implements Dependency
{
  private static final L10N L = new L10N(TreeLoader.class);
  
  // Directory which may have jars dynamically added
  private PathImpl _dir;
  
  // list of the matching paths
  private Set<PathImpl> _pathList = new TreeSet<>();

  // list of the matching paths
  private Set<PathImpl> _newPathList = new TreeSet<>();

  /**
   * Creates a new directory loader.
   */
  public TreeLoader()
  {
  }

  /**
   * Creates a new directory loader.
   */
  public TreeLoader(ClassLoader loader)
  {
    super(loader);
  }

  /**
   * Creates a new directory loader.
   */
  public TreeLoader(ClassLoader loader, PathImpl dir)
  {
    super(loader);
    
    _dir = dir;

    init();
  }

  /**
   * The directory loader's path.
   */
  @ConfigArg(0)
  public void setPath(PathImpl path)
  {
    _dir = path;
  }

  /**
   * The directory loader's path.
   */
  public PathImpl getPath()
  {
    return _dir;
  }

  /**
   * Create a new class loader
   *
   * @param parent parent class loader
   * @param dir directories which can handle dynamic jar addition
   */
  public static DynamicClassLoader create(ClassLoader parent, PathImpl dir)
  {
    DynamicClassLoader loader = new DynamicClassLoader(parent);

    TreeLoader treeLoader = new TreeLoader(loader, dir);

    loader.addLoader(treeLoader);

    loader.init();
    
    return loader;
  }

  /**
   * Initialize
   */
  @PostConstruct
  @Override
  public void init()
  {
    if (_dir == null)
      throw new ConfigException(L.l("<tree-loader> requires a 'path' attribute"));
    
    _dir.getLastModified();
    
    super.init();
  }
  
  /**
   * Find all the jars in this directory and add them to jarList.
   */
  @Override
  protected void loadPaths(ArrayList<PathImpl> paths)
  {
    loadPaths(paths, _dir);
  }
  
  /**
   * Find all the jars in this directory and add them to jarList.
   */
  protected void loadPaths(ArrayList<PathImpl> paths, PathImpl dir)
  {
    try {
      for (String fileName : dir.list()) {
        PathImpl path = dir.lookup(fileName);

        if (fileName.endsWith(".jar") || fileName.endsWith(".zip")) {
          paths.add(path);
        }
        else if (path.isDirectory()) {
          loadPaths(paths, path);
        }
      }
      
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public PathImpl getCodePath()
  {
    return _dir;
  }

  /**
   * Destroys the loader, closing the jars.
   */
  @Override
  protected void destroy()
  {
    super.destroy();
    
    clearJars();
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + _dir + "]";
  }
}
