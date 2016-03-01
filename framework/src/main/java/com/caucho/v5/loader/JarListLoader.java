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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.io.Dependency;
import com.caucho.v5.vfs.Depend;
import com.caucho.v5.vfs.Jar;
import com.caucho.v5.vfs.JarPath;
import com.caucho.v5.vfs.PathImpl;

/**
 * Class loader which checks for changes in class files and automatically
 * picks up new jars.
 */
abstract public class JarListLoader extends Loader implements Dependency {
  private static final Logger log
    = Logger.getLogger(JarListLoader.class.getName());
  
  private ArrayList<PathImpl> _pathList = new ArrayList<>();
  private ArrayList<PathImpl> _newPathList = new ArrayList<>();
  
  // list of the jars in the directory
  private ArrayList<JarEntry> _jarList = new ArrayList<JarEntry>();
  
  // list of dependencies
  private DependencyContainer _dependencyList = new DependencyContainer();

  // Entry map
  private JarMap _pathMap;

  /**
   * Creates a new jar list loader.
   */
  public JarListLoader()
  {
  }
  
  public JarListLoader(ClassLoader loader)
  {
    super(loader);
  }

  /**
   * Sets the owning class loader.
   */
  @Override
  public void setLoader(DynamicClassLoader loader)
  {
    super.setLoader(loader);
 }
  
  protected void loadPaths(ArrayList<PathImpl> pathSet)
  {
  }
  
  /**
   * True if any of the loaded classes have been modified.  If true, the
   * caller should drop the classpath and create a new one.
   */
  @Override
  public boolean isModified()
  {
    if (_dependencyList.isModified()) {
      return true;
    }

    synchronized (_pathList) {
      _newPathList.clear();

      loadPaths(_newPathList);
      
      Collections.sort(_newPathList);

      return ! _newPathList.equals(_pathList);
    }
  }
  
  /**
   * True if any of the loaded classes have been modified.  If true, the
   * caller should drop the classpath and create a new one.
   */
  @Override
  public boolean logModified(Logger log)
  {
    if (_dependencyList.logModified(log)) {
      return true;
    }
    else if (isModified()) {
      log.info(this + " has modified jar files");
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Validates the loader.
   */
  @Override
  public void validate()
    throws ConfigException
  {
    for (int i = 0; i < _jarList.size(); i++) {
      _jarList.get(i).validate();
    }
  }

  @Override
  public void init()
  {
    super.init();
    
    fillJars();

    for (int i = 0; i < _jarList.size(); i++) {
      getClassLoader().addURL(_jarList.get(i).getJarPath());
    }
  }

  /**
   * Find all the jars in this directory and add them to jarList.
   */
  private void fillJars()
  {
    synchronized (_pathList) {
      _pathList.clear();
    //_jarList.clear();

      loadPaths(_pathList);
      
      Collections.sort(_pathList);

      for (PathImpl jar : _pathList) {
        addJar(jar);
      }
    }
  }

  protected Iterable<JarEntry> getJarList()
  {
    return _jarList;
  }

  protected boolean isJarCacheEnabled()
  {
    DynamicClassLoader loader = getClassLoader();

    if (loader != null)
      return loader.isJarCacheEnabled();
    else
      return false;
  }
  
  protected void addJar(PathImpl jar)
  {
    if (! jar.exists()) {
      log.fine(jar.getTail() + " does not exist"
                  + " (path=" + jar.getNativePath() + ")");
      return;
    }
    else if (! jar.canRead()) {
      log.warning(jar.getTail() + " is unreadable"
                  + " (uid=" + jar.getUser() + " mode="
                  + String.format("%o", jar.getMode())
                  + " path=" + jar.getNativePath() + ")");
      return;
    }

    JarPath jarPath = JarPath.create(jar);
    JarEntry jarEntry = new JarEntry(jarPath);

    if (getClassLoader() != null) {
      if (! getClassLoader().addURL(jarPath)) {
        return;
      }
    }

    // skip duplicates
    if (_jarList.contains(jarEntry)) {
      return;
    }
    
    _jarList.add(jarEntry);

    // _dependencyList.add(new Depend(jarPath));
    _dependencyList.add(new Depend(jar));

    if (_pathMap == null && isJarCacheEnabled()) {
      _pathMap = new JarMap();
    }

    if (_pathMap != null) {
      _pathMap.scan(jar, jarEntry);
    }
  }

  /**
   * Fill data for the class path.  fillClassPath() will add all 
   * .jar and .zip files in the directory list.
   */
  @Override
  protected void buildClassPath(ArrayList<String> pathList)
  {
    for (int i = 0; i < _jarList.size(); i++) {
      JarEntry jarEntry = _jarList.get(i);
      JarPath jar = jarEntry.getJarPath();
      
      String path = jar.getContainer().getNativePath();

      if (! pathList.contains(path)) {
        pathList.add(path);
      }
    }
  }

  /**
   * Returns the class entry.
   *
   * @param name name of the class
   */
  @Override
  protected ClassEntry getClassEntry(String name, String pathName)
    throws ClassNotFoundException
  {
    if (_pathMap != null) {
      JarMap.JarList jarEntryList = _pathMap.get(pathName);

      if (jarEntryList != null) {
        JarEntry jarEntry = jarEntryList.getEntry();

        PathImpl path = jarEntry.getJarPath();
        PathImpl filePath = path.lookup(pathName);

        return createEntry(name, pathName, jarEntry, filePath);
      }
    }
    else {
      // Find the path corresponding to the class
      for (int i = 0; i < _jarList.size(); i++) {
        JarEntry jarEntry = _jarList.get(i);
        JarPath path = jarEntry.getJarPath();
        Jar jar = path.getJar();
        
        try {
          ZipEntry zipEntry = jar.getZipEntry(pathName);

        // if (filePath.canRead() && filePath.getLength() > 0) {
        
          if (zipEntry != null && zipEntry.getSize() > 0) {
            PathImpl filePath = path.lookup(pathName);
          
            return createEntry(name, pathName, jarEntry, filePath);
          }
        } catch (IOException e) {
          log.log(Level.FINER, e.toString(), e);
        }
      }
    }

    return null;
  }

  private ClassEntry createEntry(String name,
                                 String pathName,
                                 JarEntry jarEntry,
                                 PathImpl filePath)
  {
    String pkg = "";
    int p = pathName.lastIndexOf('/');
    if (p > 0)
      pkg = pathName.substring(0, p + 1);

    ClassEntry entry = new ClassEntry(getClassLoader(), name, filePath,
                                      filePath,
                                      jarEntry.getCodeSource(pathName));

    ClassPackage classPackage = jarEntry.getPackage(pkg);

    entry.setClassPackage(classPackage);

    return entry;
  }
  
  /**
   * Adds resources to the enumeration.
   */
  @Override
  public void getResources(Vector<URL> vector, String name)
  {
    if (_pathMap != null) {
      String cleanName = name;
      
      if (cleanName.endsWith("/"))
        cleanName = cleanName.substring(0, cleanName.length() - 1);
      
      JarMap.JarList jarEntryList = _pathMap.get(cleanName);

      for (; jarEntryList != null; jarEntryList = jarEntryList.getNext()) {
        JarEntry jarEntry = jarEntryList.getEntry();
        PathImpl path = jarEntry.getJarPath();

        path = path.lookup(name);

        try {
          URL url = new URL(path.getURL());

          if (! vector.contains(url))
            vector.add(url);
        } catch (Exception e) {
          log.log(Level.WARNING, e.toString(), e);
        }
      }
    }
    else {
      for (int i = 0; i < _jarList.size(); i++) {
        JarEntry jarEntry = _jarList.get(i);
        PathImpl path = jarEntry.getJarPath();

        path = path.lookup(name);

        if (path.exists()) {
          try {
            URL url = new URL(path.getURL());

            if (! vector.contains(url))
              vector.add(url);
          } catch (Exception e) {
            log.log(Level.WARNING, e.toString(), e);
          }
        }
      }
    }
  }

  /**
   * Find a given path somewhere in the classpath
   *
   * @param pathName the relative resourceName
   *
   * @return the matching path or null
   */
  @Override
  public PathImpl getPath(String pathName)
  {
    if (_pathMap != null) {
      String cleanPathName = pathName;
      
      if (cleanPathName.endsWith("/"))
        cleanPathName = cleanPathName.substring(0, cleanPathName.length() - 1);
      
      JarMap.JarList jarEntryList = _pathMap.get(cleanPathName);

      if (jarEntryList != null) {
        return jarEntryList.getEntry().getJarPath().lookup(pathName);
      }
    }
    else {
      for (int i = 0; i < _jarList.size(); i++) {
        JarEntry jarEntry = _jarList.get(i);
        PathImpl path = jarEntry.getJarPath();

        PathImpl filePath = path.lookup(pathName);

        if (filePath.exists())
          return filePath;
      }
    }

    return null;
  }

  /**
   * Closes the jars.
   */
  protected void clearJars()
  {
    synchronized (this) {
      ArrayList<JarEntry> jars = new ArrayList<JarEntry>(_jarList);
      _jarList.clear();

      if (_pathMap != null)
        _pathMap.clear();
    
      for (int i = 0; i < jars.size(); i++) {
        JarEntry jarEntry = jars.get(i);

        JarPath jarPath = jarEntry.getJarPath();

        jarPath.closeJar();
      }
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _jarList + "]";
  }
}
