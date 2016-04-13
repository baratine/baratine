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
 * @author Alex Rojkov
 */

package com.caucho.v5.cli.baratine;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.cli.spi.CommandBase;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.Version;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.ReadStreamOld;
import com.caucho.v5.vfs.VfsOld;
import com.caucho.v5.vfs.WriteStreamOld;

import io.baratine.service.Result;

/**
 * Command to create a standalone application.
 * 
 * bin/baratine package -o myapp.jar myapp.bar
 */
public class PackageCommand extends CommandBase<ArgsCli>
{
  private static final L10N L = new L10N(PackageCommand.class);
  
  @Override
  protected void initBootOptions()
  {
    super.initBootOptions();

    addValueOption("output-file", 
                   "path", "path of the generationed application jar").tiny("o").required();
  }

  @Override
  public int getTailArgsMinCount()
  {
    return 0;
  }
  
  @Override
  public String getDescription()
  {
    return "creates a standalone application";
  }
  
  @Override
  public String getUsageTailArgs()
  {
    return " [service1.bar service2.bar...]";
  }

  @Override
  protected ExitCode doCommandImpl(ArgsCli args)
      throws CommandArgumentException
  {
    String pathName = args.getArg("output-file");
    Objects.requireNonNull(pathName);

    PathImpl outPath = VfsOld.lookup(pathName);
    
    ArrayList<PathImpl> barFiles = new ArrayList<>();
    
    for (String barName : args.getTailArgs()) {
      PathImpl barPath = VfsOld.lookup(barName);
      
      if (! barPath.canRead()) {
        throw new ConfigException(L.l("{0} is an unreadable file", barPath));
      }
      
      barFiles.add(barPath);
    }
    
    createPackage(outPath, barFiles);

    return ExitCode.OK;
  }
  
  private void createPackage(PathImpl outPath, Iterable<PathImpl> barFiles)
  {
    ArrayList<String> baratineJars = new ArrayList<>();
    HashSet<String> fileSet = new HashSet<>();
    
    try (WriteStreamOld out = outPath.openWrite()) {
      try (ZipOutputStream zOut = new ZipOutputStream(out)) {
        createManifest(zOut, fileSet);
        
        copyBaratineJar(zOut, baratineJars, fileSet, Result.class);
        //copyBaratineJar(zOut, baratineJars, fileSet, Hessian2Output.class);
        //copyBaratineJar(zOut, baratineJars, fileSet, HttpServlet.class);
        copyBaratineJar(zOut, baratineJars, fileSet, PackageCommand.class);
        
        for (PathImpl barFile : barFiles) {
          writeBar(zOut, barFile);
        }
      }
    } catch (IOException e) {
      throw ConfigException.wrap(e);
    }
  }
  
  private void createManifest(ZipOutputStream zOut, HashSet<String> fileSet)
    throws IOException
  {
    ZipEntry metaInfEntry = new ZipEntry("META-INF/");
    zOut.putNextEntry(metaInfEntry);
    zOut.closeEntry();
    
    fileSet.add("meta-inf/");
    
    fileSet.add("meta-inf/manifest.mf");
    
    ZipEntry manifestEntry = new ZipEntry("META-INF/MANIFEST.MF");
    zOut.putNextEntry(manifestEntry);
    try (WriteStreamOld out = VfsOld.openWrite(zOut)) {
      out.setDisableCloseSource(true);
      writeManifest(out);
    }
    zOut.closeEntry();
  }
  
  private void writeManifest(WriteStreamOld out)
    throws IOException
  {
    out.println("Manifest-Version: 1.0");
    out.println("Created-By: Baratine " + Version.getVersion());
    out.println("Main-Class: " + BaratineCommandLinePackage.class.getName());
  }
    
  
  private void copyBaratineJar(ZipOutputStream out,
                               ArrayList<String> jarList,
                               HashSet<String> fileSet,
                               Class<?> sourceClass)
    throws IOException
  {
    String classPath = sourceClass.getCanonicalName().replace('.', '/') + ".class";
    
    URL url = sourceClass.getClassLoader().getResource(classPath);
    
    if (url == null) {
      return;
    }
    
    String urlName = url.toString();
    
    if (! urlName.startsWith("jar:")) {
      return;
    }
    
    int p = urlName.indexOf('!');
    
    if (p < 0) {
      return;
    }

    String jarPath = urlName.substring(4, p);
    
    if (jarList.contains(jarPath)) {
      return;
    }
    
    jarList.add(jarPath);
    
    try (ReadStreamOld is = VfsOld.lookup(jarPath).openRead()) {
      try (ZipInputStream zIn = new ZipInputStream(is)) {
        ZipEntry entry;
        
        while ((entry = zIn.getNextEntry()) != null) {
          String entryName = entry.getName();
          
          if (entryName.equalsIgnoreCase("meta-inf/manifest.mf")) {
            continue;
          }
          
          String entryNameLower = entryName.toLowerCase();
          if (fileSet.contains(entryNameLower)) {
            continue;
          }
          
          fileSet.add(entryNameLower);
          
          if (entry.isDirectory()) {
            out.putNextEntry(entry);
            out.closeEntry();
          }
          else {
            out.putNextEntry(entry);
            IoUtil.copy(zIn, out);
            out.closeEntry();
          }
        }
      }
    }
  }
  
  private void writeBar(ZipOutputStream zOut, PathImpl path)
    throws IOException
  {
    String tail = path.getTail();
    String name = "com/caucho/package/" + tail;
    
    ZipEntry entry = new ZipEntry(name);
    
    zOut.putNextEntry(entry);
    
    path.writeToStream(zOut);
    
    zOut.closeEntry();
  }
}
