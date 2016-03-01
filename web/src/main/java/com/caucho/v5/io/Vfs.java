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

import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.caucho.v5.loader.EnvironmentLocal;

/**
 * Facade to create useful Path and Stream objects.
 *
 * <code><pre>
 * Path path = Vfs.lookup("foo.html");
 * </pre><code>
 *
 * <p>The default scheme is the file scheme.  Other schemes are
 * available using the full url.
 *
 * <code><pre>
 * Path mail = Vfs.lookup("mailto:drofnats@foo.com.test?subject='hi'");
 * Stream body = mail.openWrite();
 * body.writeln("How's it going?");
 * body.close();
 * </pre><code>
 */
public final class Vfs
{
  private static FileSystem _fileSystemClasspath = new FileSystemClasspath();
  private static FileSystem _fileSystemBoot = new FileSystemBoot();
  private static final EnvironmentLocal<Path> _pwd = new EnvironmentLocal<>();
  
  public static Path path(URL url)
  {
    return path(url.toString());
  }
  
  public static Path path(String root)
  {
    int p = root.indexOf(":");
    int q = root.indexOf('/');
    
    String scheme = "";
    String tail = root;
    
    if (p > 0 && p < q) {
      scheme = root.substring(0, p);
      tail = root.substring(p + 1);

      if (scheme.equals("classpath")) {
        FileSystem fileSystem = _fileSystemClasspath;
        
        return fileSystem.getPath(tail);
      }
      else if (scheme.equals("boot")) {
        FileSystem fileSystem = _fileSystemBoot;
        
        return fileSystem.getPath(tail);
      }
    }
    
    if (root.startsWith("/")) {
      return FileSystems.getDefault().getPath(root);
    }
    else if (root.startsWith("file:")) {
      return FileSystems.getDefault().getPath(tail);
    }
    else {
      return Paths.get(root);
    }
  }

  public static void initJNI()
  {
    // TODO Auto-generated method stub
    
  }

  public static void setPwd(Path pwd)
  {
    _pwd.set(pwd);
  }
}
