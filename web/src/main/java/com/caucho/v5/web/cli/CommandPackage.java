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

package com.caucho.v5.web.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.caucho.v5.boot.BaratineBoot;
import com.caucho.v5.cli.args.ArgsBase;
import com.caucho.v5.cli.args.CommandBase;
import com.caucho.v5.cli.args.CommandLineException;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.TempOutputStream;
import com.caucho.v5.io.Vfs;
import com.caucho.v5.util.L10N;

public class CommandPackage extends CommandBase<ArgsBase>
{
  private static final L10N L = new L10N(CommandPackage.class);
  
  @Override
  protected void initBootOptions()
  {
    super.initBootOptions();
    
    addValueOption("output-file", "file", "output file for the boot jar").tiny("o");
    addValueOption("main-class", "file", "main class name for the boot").tiny("m");
  }

  @Override
  protected  ExitCode doCommandImpl(ArgsBase args)
    throws CommandLineException
  {
    String fileName = args.getArg("output-file", "boot.jar");
    
    String mainClass = args.getArg("main-class");
    
    if (mainClass == null) {
      throw new ConfigException(L.l("'main-class' is required for package."));
    }
    
    Path path = Vfs.path(fileName);
    
    try {
      Files.createDirectories(path.getParent());
    } catch (Exception e) {
      throw ConfigException.wrap(L.l("package exception: " + e), e);
    }
    
    try (OutputStream os = Files.newOutputStream(path)) {
      Manifest manifest = new Manifest();
      Attributes attr = manifest.getMainAttributes();
      attr.put(Attributes.Name.MANIFEST_VERSION, "1.0");
      attr.putValue("Main-Class", BaratineBoot.class.getName());
      attr.putValue("Boot-Main-Class", mainClass);
      
      try (JarOutputStream zOs = new JarOutputStream(os, manifest)) {
        writeBoot(zOs, args);
        
        copyClassPath(zOs, args);
      }
    } catch (Exception e) {
      throw ConfigException.wrap(L.l("package exception: " + e), e);
    }

    return ExitCode.OK;
  }
  
  private void writeBoot(ZipOutputStream zOs, ArgsBase args)
    throws IOException
  {
    String shortName = BaratineBoot.class.getSimpleName() + ".class";
    String className = BaratineBoot.class.getName().replace('.', '/') + ".class";
    
    URL url = BaratineBoot.class.getResource(shortName);
    
    if (url.toString().startsWith("file:")) {
      String urlString = url.toString();
      int urlLen = urlString.length();
      
      String prefix = urlString.substring(0, urlLen - className.length());
      
      Path rootPath = Vfs.path(prefix); 
      String pkgName = BaratineBoot.class.getPackage().getName().replace('.', '/');
      
      Path bootPath = rootPath.resolve(pkgName);
      
      IoUtil.walk(bootPath, p->writeBoot(zOs, rootPath, p));
    }
  }
  
  private void writeBoot(ZipOutputStream zOs, Path rootPath, Path path)
  {
    try {
      String suffix = path.toString().substring(rootPath.toString().length() + 1);
    
      ZipEntry entry = new ZipEntry(suffix);
    
      zOs.putNextEntry(entry);
      Files.copy(path, zOs);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
  
  private void copyClassPath(ZipOutputStream zOs, ArgsBase args)
    throws Exception
  {
    HashSet<URL> classPath = new HashSet<>();
    
    copyClassPath(zOs, args, getClass().getClassLoader(), classPath);
  }
  
  private void copyClassPath(ZipOutputStream zOs, 
                             ArgsBase args,
                             ClassLoader loader,
                             HashSet<URL> classPath)
    throws Exception
  {
    if (! (loader instanceof URLClassLoader)) {
      return;
    }
    
    URLClassLoader urlLoader = (URLClassLoader) loader;
    
    for (URL url : urlLoader.getURLs()) {
      if (classPath.contains(url)) {
        continue;
      }
      
      classPath.add(url);
      
      zOs.setLevel(0);
      
      Path path = Paths.get(url.toURI());
      
      if (path.toString().endsWith(".jar")) {
        String pathName = path.toString();
        int p = pathName.lastIndexOf('/');
        
        ZipEntry entry = new ZipEntry("lib/" + pathName.substring(p + 1));
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(Files.size(path));
        //entry.setCompressedSize(entry.getSize());
        
        try (InputStream is = Files.newInputStream(path)) {
          entry.setCrc(calculateCrc(is));
        }
        
        zOs.putNextEntry(entry);
        
        Files.copy(path, zOs);
      }
      else {
        TempOutputStream tos = new TempOutputStream();
        
        try (ZipOutputStream zOsJar = new ZipOutputStream(tos)) {
          IoUtil.walk(path, p->buildJar(zOsJar, path, p));
        }
        
        tos.close();
        
        // "item-" + classPath.size() + ".jar";
        String name = pathName(path);
        
        if (name == null) {
          name = "lib/item-" + classPath.size() + ".jar";
        }
        else {
          name = "lib/" + name + "-" + classPath.size() + ".jar";
        }
        
        zOs.setLevel(0);
        
        ZipEntry entry = new ZipEntry(name);

        entry.setMethod(ZipEntry.STORED);
        entry.setSize(tos.getLength());
        entry.setCompressedSize(tos.getLength());
        
        try (InputStream is = tos.openInputStreamNoFree()) {
          entry.setCrc(calculateCrc(is));
        }
        
        zOs.putNextEntry(entry);
        
        try (InputStream is = tos.getInputStream()) {
          IoUtil.copy(is, zOs);
        }

        zOs.closeEntry();
      }
    }
  }
  
  private long calculateCrc(InputStream is)
    throws IOException
  {
    TempBuffer tBuf = TempBuffer.create();
    byte []buffer = tBuf.buffer();
    
    long result = 0;
    
    CRC32 crc = new CRC32();
    crc.reset();

    int sublen = 0;

    while ((sublen = is.read(buffer, 0, buffer.length)) > 0) {
      crc.update(buffer, 0, sublen);
    }

    result = crc.getValue();

    tBuf.freeSelf();
    
    return result;
  }
  
  private String pathName(Path path)
  {
    String pathName = path.toString();
    
    int p = pathName.length();
    int q;
    
    while ((q = pathName.lastIndexOf('/', p - 1)) >= 0) {
      String tail = pathName.substring(q + 1, p);
      
      if (! tail.equals("bin") 
          && ! tail.equals("classes")
          && ! tail.equals("build")) {
        return tail;
      }

      p = q;
    }
    
    return null;
  }
  
  private void buildJar(ZipOutputStream zOs, 
                        Path root, 
                        Path path)
  {
    try {
      String name = path.toString().substring(root.toString().length());
    
      while (name.startsWith("/")) {
        name = name.substring(1);
      }
    
      ZipEntry entry = new ZipEntry(name);
    
      zOs.putNextEntry(entry);
      
      Files.copy(path, zOs);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
