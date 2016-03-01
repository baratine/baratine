/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

package com.caucho.v5.boot;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Manifest;

/**
 * boot class for one-jar.
 */
public class BaratineBoot
{
  private static BootFile _bootFile;
  
  public static void main(String []args)
    throws IOException
  {
    BootFile bootFile = newBootFile();
    
    _bootFile = bootFile;
    
    String urlHandlers = System.getProperty("java.protocol.handler.pkgs");
    String pkg = BaratineBoot.class.getPackage().getName();
    
    if (urlHandlers == null) {
      urlHandlers = pkg + "|" + urlHandlers;
    }
    else {
      urlHandlers = pkg;
    }
    
    System.setProperty("java.protocol.handler.pkgs", urlHandlers);
    
    URL []urls = bootFile.urls();
    
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try (URLClassLoader bootLoader = new URLClassLoader(urls, oldLoader)) {
      thread.setContextClassLoader(bootLoader);
      
      Manifest manifest = bootFile.manifest();
      
      String mainClassName = manifest.getMainAttributes().getValue("Boot-Main-Class");
      
      if (mainClassName == null) {
        System.err.println("Missing main class");
        return;
      }
      
      Class<?> mainClass = Class.forName(mainClassName, false, bootLoader);
      
      Method main = mainClass.getMethod("main", String[].class);
      
      MethodHandle mh = MethodHandles.lookup().unreflect(main);
      
      mh.invokeExact(args);
    } catch (ClassNotFoundException e) {
      System.err.println("Unable to load main class: " + e);
    } catch (NoSuchMethodException e) {
      System.err.println("Unable to find main method: " + e);
    } catch (Throwable e) {
      System.err.println("Unable to execute main method: " + e);
      e.printStackTrace();
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }
  
  public static BootFile bootFile()
  {
    return _bootFile;
  }
  
  private static BootFile newBootFile()
    throws IOException
  {
    //URL url = BaratineBoot.class.getResource(classFileName);
    URL url = BaratineBoot.class.getResource(BaratineBoot.class.getSimpleName() + ".class");
    
    String filePath = url.getPath();
    int p = filePath.indexOf('!');
    
    if (p > 0) {
      filePath = filePath.substring(0, p);
    }
    
    if (filePath.startsWith("file:")) {
      filePath = filePath.substring("file:".length());
    }
    else if (filePath.startsWith("/")) {
    }
    else {
      throw new IllegalStateException("unsupported file path: " + filePath + " for " + url);
    }
    
    Path path = Paths.get(filePath);
    
    BootFile bootFile = new BootFile(path);
    
    return bootFile;
    
  }

  public static URLConnection openConnection(URL url)
  {
    return _bootFile.openConnection(url);
  }
}
