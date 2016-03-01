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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.loader;

import java.util.ArrayList;

import javax.annotation.PostConstruct;

import com.caucho.v5.config.ConfigArg;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.Configurable;
import com.caucho.v5.config.types.FileSetType;
import com.caucho.v5.config.types.PathPatternType;
import com.caucho.v5.vfs.PathImpl;

/**
 * Class loader which checks for changes in class files and automatically
 * picks up new jars.
 */
@Configurable
public class LibraryLoader extends JarListLoader {
  // Configured path.
  private PathImpl _path;
  
  private FileSetType _fileSet;

  /**
   * Creates a new directory loader.
   */
  public LibraryLoader()
  {
  }
  
  public LibraryLoader(ClassLoader loader)
  {
    super(loader);
  }

  /**
   * Creates a new directory loader.
   */
  public LibraryLoader(ClassLoader loader, PathImpl path)
  {
    this(loader);
    
    _path = path;

    try {
      init();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * The library loader's path.
   */
  @ConfigArg(0)
  public void setPath(PathImpl path)
  {
    _path = path;
  }

  /**
   * The library loader's path.
   */
  public PathImpl getPath()
  {
    return _path;
  }

  /**
   * Sets a file set.
   */
  public void setFileset(FileSetType fileSet)
  {
    _fileSet = fileSet;
  }

  /**
   * Create a new class loader
   *
   * @param parent parent class loader
   * @param dir directories which can handle dynamic jar addition
   */
  public static DynamicClassLoader create(ClassLoader parent, PathImpl path)
  {
    DynamicClassLoader loader = new DynamicClassLoader(parent);

    LibraryLoader dirLoader = new LibraryLoader(loader, path);
    dirLoader.init();

    loader.init();
    
    return loader;
  }

  /**
   * Initialize
   */
  @PostConstruct
  @Override
  public void init()
    throws ConfigException
  {
    try {
      if (_fileSet != null) {
      }
      else if (_path.getPath().endsWith(".jar")
               || _path.getPath().endsWith(".zip")) {
        _fileSet = new FileSetType();
        _fileSet.setDir(_path.getParent());
        _fileSet.addInclude(new PathPatternType(_path.getTail()));
      }
      else {
        _fileSet = new FileSetType();
        _fileSet.setDir(_path);
        _fileSet.addInclude(new PathPatternType("*.jar"));
        _fileSet.addInclude(new PathPatternType("*.zip"));
      }
    } catch (ConfigException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
    
    super.init();
  }

  /**
   * Find all the jars in this directory and add them to jarList.
   */
  @Override
  protected void loadPaths(ArrayList<PathImpl> pathSet)
  {
    _fileSet.getPaths(pathSet);
  }

  public PathImpl getCodePath()
  {
    return _fileSet.getDir();
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

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _fileSet + "]";
  }
}
