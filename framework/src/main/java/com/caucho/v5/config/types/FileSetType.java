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

package com.caucho.v5.config.types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.NoAspect;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;

/**
 * Abstract type building a path pattern.  The pattern follows ant.
 */

@NoAspect
public class FileSetType {
  static final L10N L = new L10N(PathPatternType.class);
  static final Logger log = Logger.getLogger(PathPatternType.class.getName());

  private PathImpl _dir;
  private String _userPathPrefix = "";
  
  private String _valuePath;
  
  private ArrayList<PathPatternType> _includeList;
  
  private ArrayList<PathPatternType> _excludeList
    = new ArrayList<PathPatternType>();

  /**
   * Sets the starting directory.
   */
  public void setDir(PathImpl dir)
  {
    _dir = dir;
  }

  /**
   * Gets the starting directory.
   */
  public PathImpl getDir()
  {
    return _dir;
  }

  /**
   * Adds an include pattern.
   */
  public void addInclude(PathPatternType pattern)
  {
    if (_includeList == null)
      _includeList = new ArrayList<PathPatternType>();

    _includeList.add(pattern);
  }
  
  public void addText(String text)
  {
    if ("".equals(text))
      return;

    // for fileset="foo/**/*.xml" find the prefix to minimize the search
    int starP = text.indexOf('*');
    
    if (starP > 0) {
      int colonP = text.indexOf(':');
      int slashP = text.lastIndexOf('/', starP);
      
      if (colonP > 0 && colonP < slashP) {
        String prefix = text.substring(0, slashP);
        
        // server/6p04
        _dir = VfsOld.lookup(prefix);
        
        addInclude(new PathPatternType(text.substring(slashP + 1)));
        return;
      }
      else if (slashP >= 0) {
        String prefix = text.substring(0, slashP);
        
        addInclude(new PathPatternType(prefix, text));
        return;
      }
    }
    
    addInclude(new PathPatternType(text));
  }

  /**
   * Adds an exclude pattern.
   */
  public void addExclude(PathPatternType pattern)
  {
    _excludeList.add(pattern);
  }

  public void add(FileSetType fileSet)
  {
    if (fileSet == this)
      return;
    
    if (_dir == null) {
      _dir = fileSet.getDir();
    }

    if (! isSameDir(fileSet)) {
      throw new IllegalArgumentException(L.l("fileset directory: mismatch can't add {0} to {1}",
                                             fileSet,
                                             this));
    }

    if (fileSet._includeList != null) {
      for (PathPatternType include : fileSet._includeList){
        addInclude(include);
      }
    }
    
    if (fileSet._excludeList != null) {
      for (PathPatternType exclude : fileSet._excludeList){
        addExclude(exclude);
      }
    }
  }
  
  public void addInverse(FileSetType fileSet)
  {
    if (fileSet == this)
      return;
    
    if (_dir == null) {
      _dir = fileSet.getDir();
    }
    
    if (! isSameDir(fileSet))
      throw new IllegalArgumentException(L.l("fileset directory mismatch: can't add {0} to {1}",
                                             fileSet,
                                             this));

    if (fileSet._includeList != null) {
      for (PathPatternType include : fileSet._includeList){
        addExclude(include);
      }
    }
    
    if (fileSet._excludeList != null) {
      for (PathPatternType exclude : fileSet._excludeList){
        addInclude(exclude);
      }
    }
  }

  /**
   * Sets the user-path prefix for better error reporting.
   */
  public void setUserPathPrefix(String prefix)
  {
    if (prefix != null && ! prefix.equals("") && ! prefix.endsWith("/"))
      _userPathPrefix = prefix + "/";
    else
      _userPathPrefix = prefix;
  }

  /**
   * Initialize the type.
   */
  @PostConstruct
  public void init()
    throws ConfigException
  {
    if (_dir == null)
      _dir = VfsOld.lookup();
    
  }

  /**
   * Returns the set of files.
   */
  public ArrayList<PathImpl> getPaths()
  {
    return getPaths(new ArrayList<>());
  }

  /**
   * Returns the set of files.
   */
  public ArrayList<PathImpl> getPaths(ArrayList<PathImpl> paths)
  {
    PathListCallback cb = new PathListCallback(paths);

    filterPaths(cb);
    
    Collections.sort(paths);

    return paths;
  }
  
  /**
   * Returns the set of files.
   */
  public void filterPaths(PathCallback cb)
  {
    String dirPath = _dir.getPath();
    
    if (! dirPath.endsWith("/")) {
      dirPath += "/";
    }
    
    filterPaths(_dir, dirPath, cb);
  }
  
  /**
   * Returns the set of files.
   */
  public void filterPaths(PathImpl path, String prefix, PathCallback cb)
  {
    if (! path.exists() || ! path.canRead()) {
      return;
    }
    
    if (! isValidPrefix(path, prefix)) {
      return;
    }
    
    if (path.isDirectory()) {
      try {
        String []list = path.list();

        for (int i = 0; i < list.length; i++) {
          String name = list[i];
          
          if (".".equals(name) || "..".equals(name))
            continue;
          
          // jsp/187j
          PathImpl subpath = path.lookup("./" + name);
          
          filterPaths(subpath, prefix, cb);
        }
      } catch (IOException e) {
        log.log(Level.WARNING, e.toString(), e);
      }
    }
    
    if (path.exists()) {
      // server/2438 - logging on unreadable
      //  if (path.canRead()) {
      if (isMatch(path, prefix)) {
        String suffix = "";
        String fullPath = path.getPath();

        if (prefix.length() < fullPath.length())
          suffix = path.getPath().substring(prefix.length());

        path.setUserPath(_userPathPrefix + suffix);

        cb.onMatch(path);
      }
    }
  }
  
  private boolean isValidPrefix(PathImpl path, String prefix)
  {
    String suffix = "";
    String fullPath = path.getPath();

    if (prefix.length() < fullPath.length())
      suffix = path.getPath().substring(prefix.length());

    if (_includeList == null || _includeList.size() == 0) {
      return true;
    }
    
    for (PathPatternType pattern : _includeList) {
      if (pattern.isValidPrefix(suffix)) {
        return true;
      }
    }
    
    return false;
  }

  /**
   * Returns the set of files.
   */
  public boolean isMatch(PathImpl path, String prefix)
  {
    String suffix = "";
    String fullPath = path.getPath();
    
    if (prefix.length() < fullPath.length()) {
      suffix = fullPath.substring(prefix.length());
    }

    for (int i = 0; i < _excludeList.size(); i++) {
      PathPatternType pattern = _excludeList.get(i);

      if (pattern.isMatch(suffix))
        return false;
    }

    if (_includeList == null)
      return true;
    
    for (int i = 0; i < _includeList.size(); i++) {
      PathPatternType pattern = _includeList.get(i);

      if (pattern.isMatch(suffix)) {
        return true;
      }
    }

    return false;
  }

  private boolean isSameDir(FileSetType fileSet)
  {
    if (fileSet == null)
      throw new NullPointerException();

    if (_dir == fileSet._dir)
      return true;

    if (_dir != null && _dir.equals(fileSet._dir))
      return true;

    return false;
  }
  
  public ArrayList<PathImpl> getRoots()
  {
    ArrayList<PathImpl> roots = new ArrayList<PathImpl>();
    
    if (_includeList == null || _includeList.size() == 0) {
      roots.add(_dir);
      
      return roots;
    }
    
    for (PathPatternType pattern : _includeList) {
      String prefix = pattern.getPrefix();
      
      if (prefix == null || "".equals(prefix)) {
        roots.add(_dir);
        
        return roots;
      }
      
      roots.add(_dir.lookup(prefix));
    }
    
    return roots;
  }

  @Override
  public String toString()
  {
    return "FileSetType[" + _dir + "]";
  }
  
  public interface PathCallback {
    public void onMatch(PathImpl path);
  }
  
  private static class PathListCallback implements PathCallback {
    private List<PathImpl> _list;
    
    private PathListCallback(List<PathImpl> list)
    {
      _list = list;
    }
    
    public List<PathImpl> getList()
    {
      return _list;
    }
    
    @Override
    public void onMatch(PathImpl path)
    {
      if (! _list.contains(path)) {
        _list.add(path);
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + _list;
    }
  }
}
