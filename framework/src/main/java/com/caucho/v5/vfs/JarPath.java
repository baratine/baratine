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

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.util.LruCache;

import java.io.IOException;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.jar.Manifest;

/**
 * A filesystem for .jar files.
 */
public class JarPath extends FilesystemPath {
  private static LruCache<PathImpl,JarPath> _jarCache
    = new LruCache<PathImpl,JarPath>(256);
  
  private PathImpl _backing;

  /**
   * Creates a new jar path for the specific file
   *
   * @param root the root of this jar
   * @param userPath the path specified by the user in the lookup()
   * @param path the normalized path
   * @param jarFile the underlying jar
   */
  protected JarPath(FilesystemPath root, String userPath,
                    String path, PathImpl backing)
  {
    super(root, userPath, path);

    if (_root == null)
      _root = this;

    if (backing instanceof JarPath)
      throw new IllegalStateException(backing.toString() + " is already a jar");
    
    _backing = backing;
  }

  /**
   * Creates a new root Jar path.
   */
  public static JarPath create(PathImpl backing)
  {
    backing = backing.unwrap();
    
    if (backing instanceof JarPath) {
      return (JarPath) backing;
    }
    
    JarPath path = _jarCache.get(backing);

    if (path == null) {
      path = new JarPath(null, "/", "/", backing);
      _jarCache.put(backing, path);
    }

    return path;
  }

  public PathImpl fsWalk(String userPath,
                     Map<String,Object> attributes,
                     String path)
  {
    if ("/".equals(userPath) && "/".equals(path))
      return _root;
    else
      return new JarPath(_root, userPath, path, _backing);
  }

  /**
   * Returns the scheme (jar)
   */
  public String getScheme()
  {
    return "jar";
  }

  @Override
  public boolean isPathCacheable()
  {
    return true;
  }

  /**
   * Returns the full url.
   *
   * <p>jar:<container-url>!/entry-path
   */
  public String getURL()
  {
    String path = getFullPath();

    return getScheme() + ":" + getContainer().getURL() + "!" + path;
  }

  /**
   * Returns the underlying file below the jar.
   */
  public PathImpl getContainer()
  {
    return _backing;
  }

  /**
   * Returns any signing certificates.
   */
  @Override
  public Certificate []getCertificates()
  {
    return getJar().getCertificates(getPath());
  }

  /**
   * Returns true if the entry exists in the jar file.
   */
  @Override
  public boolean exists()
  {
    return getJar().exists(getPath());
  }

  /**
   * Returns true if the entry is a directory in the jar file.
   */
  public boolean isDirectory()
  {
    return getJar().isDirectory(getPath());
  }

  /**
   * Returns true if the entry is a file in the jar file.
   */
  @Override
  public boolean isFile()
  {
    return getJar().isFile(getPath());
  }

  @Override
  public long length()
  {
    return getJar().getLength(getPath());
  }

  public long getLastModified()
  {
    return getJar().getLastModified(getPath());
  }
  
  @Override
  public boolean canRead()
  {
    return getJar().canRead(getPath());
  }

  public boolean canWrite()
  {
    return getJar().canWrite(getPath());
  }

  /**
   * Returns a list of the directories in the jar.
   */
  @Override
  public String []list() throws IOException
  {
    return getJar().list(getPath());
  }

  /**
   * Returns the manifest.
   */
  public Manifest getManifest() throws IOException
  {
    return getJar().getManifest();
  }

  /**
   * Returns the dependency checker from the jar.
   */
  public PersistentDependency getDepend()
  {
    return getJar().getDepend();
  }

  @Override
  public StreamImpl openReadImpl() throws IOException
  {
    return getJar().openReadImpl(this);
  }

  public Jar getJar()
  {
    return Jar.create(_backing);
  }

  public void closeJar()
  {
    Jar jar = Jar.getJar(_backing);

    if (jar != null)
      jar.close();
  }

  public String toString()
  {
    return "jar:(" + _backing + ")" + getPath();
  }

  public int hashCode()
  {
    return 65531 * getPath().hashCode() + getContainer().hashCode();
  }

  /**
   * Tests for equality.
   */
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    else if (o == null || ! o.getClass().equals(this.getClass()))
      return false;

    JarPath jarPath = (JarPath) o;

    return (_backing.equals(jarPath._backing)
            && getPath().equals(jarPath.getPath()));
  }
}
