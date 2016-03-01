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

package com.caucho.v5.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.AccessMode;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import com.caucho.v5.boot.BaratineBoot;
import com.caucho.v5.boot.BootFile;
import com.caucho.v5.boot.BootFile.BootJar;
import com.caucho.v5.util.L10N;

/**
 * The classpath file provider
 */
public class FileProviderBoot extends FileProviderBase
{
  private static final L10N L = new L10N(FileProviderBoot.class);
  
  private BootFile _bootFile;
  
  FileProviderBoot()
  {
    _bootFile = BaratineBoot.bootFile();
  }
  
  @Override
  public String getScheme()
  {
    return "boot";
  }
  
  private BootJar jar(Path path)
  {
    String fileName = path.toUri().getPath();
    
    while (fileName.startsWith("/")) {
      fileName = fileName.substring(1);
    }
    
    if (_bootFile != null) {
      return _bootFile.jar(fileName);
    }
    else {
      return null;
    }
  }

  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException
  {
    BootJar jar = jar(path);
    
    if (jar == null) {
      throw new FileNotFoundException(L.l("{0} does not exist", path));
    }
  }

  @Override
  public InputStream newInputStream(Path path, OpenOption ...options)
      throws IOException
  {
    BootJar jar = jar(path);
    
    if (jar != null) {
      return jar.read();
    }
    else {
      throw new FileNotFoundException(L.l("{0} does not exist", path));
    }
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(Path path,
                                                          Class<A> type,
                                                          LinkOption... options)
                                                              throws IOException
  {
    BootJar jar = jar(path);
    
    if (jar == null) {
      throw new FileNotFoundException(L.l("{0} does not exist", path));
    }
    
    if (! type.equals(BasicFileAttributes.class)) {
      throw new UnsupportedOperationException(type.getName());
    }
    
    PathBase pathBase = (PathBase) path;
    
    return (A) new BasicFileAttributesImpl(pathBase.path(), jar);
  }
  
  private class BasicFileAttributesImpl implements BasicFileAttributes
  {
    private String _path;
    private BootJar _jar;
    
    BasicFileAttributesImpl(String path, BootJar jar)
    {
      _path = path;
      _jar = jar;
    }

    @Override
    public FileTime lastModifiedTime()
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileTime lastAccessTime()
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileTime creationTime()
    {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRegularFile()
    {
      /*
      ClassLoader loader = _loader;
      
      URL url = loader.getResource(_path);

      if (url != null) {
        return true;
      }
      else {
        return false;
      }
      */
      return true;
    }

    @Override
    public boolean isDirectory()
    {
      return false;
    }

    @Override
    public boolean isSymbolicLink()
    {
      return false;
    }

    @Override
    public boolean isOther()
    {
      return false;
    }
    
    @Override
    public long size()
    {
      return _jar.size();
      /*
      ClassLoader loader = _loader;
      
      URL url = loader.getResource(_path);
      
      if (url == null) {
        return -1;
      }
      else {
        try {
          URLConnection conn = url.openConnection();
          
          return conn.getContentLengthLong();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
  */
    }

    @Override
    public Object fileKey()
    {
      throw new UnsupportedOperationException();
    }
  }
}
