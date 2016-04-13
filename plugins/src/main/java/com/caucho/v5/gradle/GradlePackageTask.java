/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package com.caucho.v5.gradle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;

import com.caucho.v5.boot.BaratineBoot;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.TempBuffer;

/**
 * gradle plugin
 */
public class GradlePackageTask extends DefaultTask
{
  private static final Logger log
    = Logger.getLogger(GradlePackageTask.class.getName());
  
  private String mainClassName;

  public void setMainClassName(String name)
  {
    mainClassName = name;
  }
  
  public String getMainClassName()
  {
    return mainClassName;
  }

  public void setMainClass(String name)
  {
    mainClassName = name;
  }
  
  public String getMainClass()
  {
    return mainClassName;
  }
  
  @TaskAction 
  public void bootJar()
  {
    Project project = getProject();
    
    try {
      GradleBaratineExtension ext;
      
      ext = project.getExtensions().findByType(GradleBaratineExtension.class);
      
      String mainClassName = null;
      
      if (ext != null) {
        mainClassName = ext.getMainClassName();
      }
      
      mainClassName = (String) project.getProperties().get("mainClassName");
      
      /*
      if (mainClassName == null) {
        Object value = project.getExtensions().getExtraProperties().get("mainClass");
        
        if (value instanceof String) {
          mainClassName = (String) value;
        }
      }
      */
      
      if (mainClassName == null) {
        project.getLogger().warn("mainClassName is required");
        return;
      }
      
      setMainClassName(mainClassName);
      
      Task jar = project.getTasks().findByName("jar");
      
      project.getTasks().withType(Jar.class, new MyAction(project, ext));

      //System.out.println("JAR: " + jar);
    
      //Configuration runtime = project.getConfigurations().getByName("runtime");
    } catch (Exception e) {
      e.printStackTrace();;
    }
  }
  
  class MyAction implements Action<Jar>
  {
    private Project _project;
    private GradleBaratineExtension _ext;
    
    MyAction(Project project, GradleBaratineExtension ext)
    {
      _project = project;
      _ext = ext;
    }
    
    @Override
    public void execute(Jar jar)
    {
      File archiveFile = jar.getArchivePath();
      Path archivePath = archiveFile.toPath();
      
      String name = archiveFile.getName();
      File parent = archiveFile.getParentFile().getAbsoluteFile();
      
      int p = name.lastIndexOf('.');
      String prefix = name.substring(0, p);
      String ext = name.substring(p);
      
      String outName = prefix + "-boot" + ext;
      
      Path outFile = parent.toPath().resolve(outName);
      
      try (OutputStream fOs = Files.newOutputStream(outFile)) { 
        Manifest manifest = new Manifest();
        Attributes attr = manifest.getMainAttributes();
        attr.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attr.putValue("Main-Class", BaratineBoot.class.getName());
        attr.putValue("Boot-Main-Class", getMainClassName());
        
        try (JarOutputStream zOs = new JarOutputStream(fOs, manifest)) {
          zOs.setLevel(0);
          
          ZipEntry entry = new ZipEntry("main/main.jar");
          entry.setSize(archiveFile.length());
          entry.setCompressedSize(archiveFile.length());
          entry.setMethod(ZipEntry.STORED);
          entry.setCrc(calculateCrc(archivePath));
          
          zOs.putNextEntry(entry);
          
          Files.copy(archivePath, zOs);
          
          writeDependencies(zOs);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    
    private void writeDependencies(JarOutputStream zOs)
      throws IOException
    {
      Configuration targetConfig;
      
      targetConfig = _project.getConfigurations().getByName("runtime");
      
      for (File lib : targetConfig.resolve()) {
        if (isBootJar(lib)) {
          copyBootJar(zOs, lib);
        }
        
        String name = lib.getName();
        
        zOs.setLevel(0);
        
        ZipEntry entry = new ZipEntry("lib/" + name);
        
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(lib.length());
        entry.setCrc(calculateCrc(lib.toPath()));
        
        zOs.putNextEntry(entry);
        
        Files.copy(lib.toPath(), zOs);
      }
    }
    
    private void copyBootJar(JarOutputStream zOs, File bootJar)
      throws IOException
    {
      try (FileInputStream fIn = new FileInputStream(bootJar)) {
        zOs.setLevel(9);

        try (ZipInputStream zIn = new ZipInputStream(fIn)) {
          ZipEntry entry;
          
          String pkg = BaratineBoot.class.getPackage().getName().replace('.', '/') + "/";
          
          while ((entry = zIn.getNextEntry()) != null) {
            String name = entry.getName();
            if (! name.startsWith(pkg)) {
              continue;
            }
            
            ZipEntry entryOut = new ZipEntry(name);
            entryOut.setSize(entry.getSize());
            
            zOs.putNextEntry(entryOut);
            
            if (name.startsWith(pkg)) {
              IoUtil.copy(zIn, zOs);
            }
          }
        }
      }
    }
    
    private long calculateCrc(Path path)
      throws IOException
    {
      TempBuffer tBuf = TempBuffer.create();
      byte []buffer = tBuf.buffer();
      
      long result = 0;
      
      try (InputStream is = Files.newInputStream(path)) {
        CRC32 crc = new CRC32();
        crc.reset();
        
        int sublen = 0;
        
        while ((sublen = is.read(buffer, 0, buffer.length)) > 0) {
          crc.update(buffer, 0, sublen);
        }
        
        result = crc.getValue();
      }
      
      tBuf.freeSelf();
      
      return result;
    }
    
    private boolean isBootJar(File file)
    {
      try (ZipFile zipFile = new ZipFile(file)) {
        Class<?> bootClass = BaratineBoot.class;
        String bootClassName = bootClass.getName().replace('.', '/') + ".class";
        
        ZipEntry entry = zipFile.getEntry(bootClassName);
        
        if (entry != null) {
          return true;
        }
      } catch (IOException e) {
        log.log(Level.FINEST, e.toString(), e);
      }
      
      return false;
    }
  }
}
