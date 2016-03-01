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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.util.L10N;

/**
 * The classpath scheme.
 */
public class ClasspathPath extends FilesystemPath {
  protected static L10N L = new L10N(ClasspathPath.class);

  /**
   * Creates a new classpath sub path.
   *
   * @param root the classpath filesystem root
   * @param userPath the argument to the calling lookup()
   * @param newAttributes any attributes passed to http
   * @param path the full normalized path
   * @param query any query string
   */
  public ClasspathPath(FilesystemPath root,
                       String userPath,
                       String path)
  {
    super(root, userPath, path);

    if (_root == null)
      _root = this;
  }
  
  /**
   * Lookup the actual path relative to the filesystem root.
   *
   * @param userPath the user's path to lookup()
   * @param attributes the user's attributes to lookup()
   * @param path the normalized path
   *
   * @return the selected path
   */
  public PathImpl fsWalk(String userPath,
                        Map<String,Object> attributes,
                        String path)
  {
    return new ClasspathPath(_root, userPath, path);
  }

  /**
   * Returns the scheme, http.
   */
  @Override
  public String getScheme()
  {
    return "classpath";
  }

  /**
   * Returns true if the file exists.
   */
  @Override
  public boolean exists()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    return loader.getResource(getTrimPath()) != null;
  }

  /**
   * Returns true if the file exists.
   */
  @Override
  public boolean isFile()
  {
    return exists();
  }

  /**
   * Returns true if the file is readable.
   */
  @Override
  public boolean canRead()
  {
    return exists();
  }

  /**
   * Returns the last modified time.
   */
  public boolean isDirectory()
  {
    return false;
  }
  
  @Override
  public long length()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    URL url = loader.getResource(getPath());
    
    if (url == null) {
      return 0;
    }
    else {
      PathImpl path = lookup(url.toString());
      
      return path.length();
    }
    
  }

  /**
   * Returns a read stream for a GET request.
   */
  @Override
  public StreamImpl openReadImpl() throws IOException
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    if (loader == null) {
      loader = ClassLoader.getSystemClassLoader();
    }

    InputStream is = loader.getResourceAsStream(getTrimPath());

    if (is == null) {
      throw new FileNotFoundException(getURL());
    }
    
    return new VfsStreamOld(is, null);
  }
  
  protected String getTrimPath()
  {
    String path = getPath();
    
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    
    return path;
  }
  
  /**
   * Returns the string form of the http path.
   */
  public String toString()
  {
    return getURL();
  }
}
