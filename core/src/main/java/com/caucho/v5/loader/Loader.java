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

package com.caucho.v5.loader;

import java.io.InputStream;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.io.Path;
import com.caucho.v5.io.Source;
import com.caucho.v5.util.Crc64;


/**
 * Loads resources.
 */
abstract public class Loader {
  protected static final Logger log
    = Logger.getLogger(Loader.class.getName());
  
  private DynamicClassLoader _loader;

  protected Loader()
  {
    this(Thread.currentThread().getContextClassLoader());
  }
  
  protected Loader(ClassLoader loader)
  {
    if (! (loader instanceof DynamicClassLoader)) {
      // XXX: no L10N for initialization reasons
      
      throw new IllegalStateException("'" + loader + "' must be created in a DynamicClassLoader context");
    }
    
    _loader = (DynamicClassLoader) loader;
  }
  
  public boolean isDirectoryLoader()
  {
    return false;
  }
  
  /**
   * Sets the owning class loader.
   */
  public void setLoader(DynamicClassLoader loader)
  {
    _loader = loader;
  }

  /**
   * Gets the owning class loader.
   */
  public DynamicClassLoader getClassLoader()
  {
    return _loader;
  }

  /**
   * Validates the loader.
   */
  public void validate()
    throws ConfigException
  {
  }
  
  /**
   * Initialize the loader
   */
  @PostConstruct
  public void init()
  {
    if (_loader != null) {
      _loader.addLoader(this);
    }
  }

  /**
   * Loads the class directly, e.g. from OSGi
   */
  protected Class<?> loadClass(String name)
  {
    return null;
  }
  
  /**
   * Returns the class entry.
   *
   * @param name name of the class
   */
  protected ClassEntry getClassEntry(String name, String pathName)
    throws ClassNotFoundException
  {
    // Find the path corresponding to the class
    Path path = getPath(pathName);

    if (path != null && path.length() > 0) {
      return new ClassEntry(_loader, name, path, path,
                            getCodeSource(path));
    }
    else
      return null;
  }
  
  /**
   * Returns the resource
   *
   * @param name name of the resource
   */
  public URL getResource(String name)
  {
    Path path;

    path = getPath(name);

    if (path != null && path.exists()) {
      try {
        return path.getNetURL();
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }

    return null;
  }
  
  /**
   * Returns the resource
   *
   * @param name name of the resource
   */
  public void getResources(Vector<URL> resources, String name)
  {
    Path path;

    path = getPath(name);

    if (path != null && path.canRead()) {
      try {
        resources.add(path.getNetURL());
      } catch (Exception e) {
      }
    }
  }
  
  /**
   * Opens the stream to the resource.
   *
   * @param name name of the resource
   */
  public InputStream getResourceAsStream(String name)
  {
    Source path;

    path = getPath(name);

    if (path != null && path.canRead()) {
      try {
        return path.inputStream();
      } catch (Exception e) {
      }
    }

    return null;
  }
  
  /**
   * Returns a path for the given name.
   */
  public Path getPath(String name)
  {
    return null;
  }
  
  /**
   * Returns the code source for the path.
   */
  protected CodeSource getCodeSource(Path path)
  {
    return null;
    /*
    try {
      if (path instanceof JarPath) {
        JarPath jarPath = (JarPath) path;
        
        path = jarPath.getContainer();
      }
      
      return new CodeSource(path.getNetURL(),
                            (Certificate []) path.getCertificates());
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);

      return null;
    }
    */
  }
  
  protected long getHashCrc(long crc64)
  {
    ArrayList<String> pathList = new ArrayList<String>();
    buildClassPath(pathList);
    
    for (String path : pathList) {
      crc64 = Crc64.generate(path);
    }
    
    return crc64;
  }
  /**
   * Adds the sourcepath of this loader.
   */
  protected void buildClassPath(ArrayList<String> pathList)
  {
  }
  
  /**
   * Adds the sourcepath of this loader.
   */
  protected void buildSourcePath(ArrayList<String> pathList)
  {
    buildClassPath(pathList);
  }

  /**
   * Destroys the loader.
   */
  protected void destroy()
  {
  }

  public void make()
  {
    // TODO Auto-generated method stub
    
  }
}
