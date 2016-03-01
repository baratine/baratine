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

package com.caucho.v5.javac;

import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.util.Alarm;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.vfs.MemoryPath;
import com.caucho.v5.vfs.MergePath;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;

import javax.annotation.PostConstruct;

public class WorkDir {
  private static final EnvironmentLocal<PathImpl> _localWorkDir
    = new EnvironmentLocal<PathImpl>("caucho.work-dir");
  
  private PathImpl _path;

  public WorkDir()
  {
  }
  
  /**
   * Returns the local work directory.
   */
  public static PathImpl getLocalWorkDir()
  {
    return getLocalWorkDir(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Returns the local work directory.
   */
  public static PathImpl getLocalWorkDir(ClassLoader loader)
  {
    PathImpl path = _localWorkDir.get(loader);

    if (path != null)
      return path;
    
    path = getTmpWorkDir();

    _localWorkDir.setGlobal(path);
    
    try {
      path.mkdirs();
    } catch (java.io.IOException e) {
    }

    return path;
  }
  
  /**
   * Returns the user directory from /tmp/
   */
  public static PathImpl getTmpWorkDir()
  {
    String userName = System.getProperty("user.name");
    
    PathImpl path;
    // Windows uses /temp as a work dir
    if (com.caucho.v5.util.CauchoUtil.isWindows()
        && ! CurrentTime.isTest())
      path = VfsOld.lookup("file:/c:/tmp/" + userName);
    else
      path = VfsOld.lookup("file:/tmp/" + userName);
    
    return path;
  }

  /**
   * Sets the work dir.
   */
  public static void setLocalWorkDir(PathImpl path)
  {
    setLocalWorkDir(path, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Sets the work dir.
   */
  public static void setLocalWorkDir(PathImpl path, ClassLoader loader)
  {
    try {
      if (path instanceof MergePath)
        path = ((MergePath) path).getWritePath();

      if (path instanceof MemoryPath) {
        String pathName = path.getPath();

        path = WorkDir.getTmpWorkDir().lookup("qa/" + pathName);
      }
    
      // path.mkdirs();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    _localWorkDir.set(path, loader);
  }
  
  /**
   * Sets the value.
   */
  public void setValue(PathImpl path)
  {
    _path = path;
  }
  
  /**
   * @deprecated
   */
  public void setId(PathImpl path)
    throws java.io.IOException
  {
    setValue(path);
  }

  /**
   * Stores self.
   */
  @PostConstruct
  public void init()
  {
    setLocalWorkDir(_path);
  }
}

