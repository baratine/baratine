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

import io.baratine.service.Result;

import java.io.IOException;
import java.util.logging.Logger;

import com.caucho.v5.vfs.PathImpl;

/**
 * Service for compiling Java source
 */
public class JavaCompilerServiceImpl implements JavaCompilerService
{
  private static final Logger log
    = Logger.getLogger(JavaCompilerServiceImpl.class.getName());
  
  @Override
  public void compile(JavaCompilerUtil compilerUtil,
                      String []path, 
                      LineMap lineMap,
                      Result<Boolean> result)
    throws IOException, JavaCompileException
  {
    AbstractJavaCompiler compiler;

    for (int i = 0; i < path.length; i++) {
      log.config("Compiling " + path[i]);
    }

    String compilerName = compilerUtil.getCompiler();

    /*
    switch (compilerName) {
    case "internal":
      compiler = new InternalCompilerTools(compilerUtil);
      break;
    case "internal2":
      compiler = new InternalCompilerTools(compilerUtil);
      break;
    case "tools":
      compiler = new InternalCompilerTools(compilerUtil);
      break;
    default:
      compiler = new ExternalCompiler(compilerUtil);
      break;
    }
    */
    compiler = new InternalCompilerTools(compilerUtil);

    compiler.setPath(path);
    compiler.setLineMap(lineMap);

    compiler.runImpl();

    for (int i = 0; i < path.length; i++) {
      PathImpl javaPath = compilerUtil.getSourceDir().lookup(path[i]);

      if (! path[i].endsWith(".java")) {
        continue;
      }

      String className = path[i].substring(0, path[i].length() - 5) + ".class";
      PathImpl classPath = compilerUtil.getClassDir().lookup(className);
      PathImpl smapPath = compilerUtil.getSourceDir().lookup(path[i] + ".smap");

      if (classPath.canRead() && smapPath.canRead()) {
        compilerUtil.mergeSmap(classPath, smapPath);
      }
    }
    
    result.ok(true);
  }
}
