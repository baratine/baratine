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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.bytecode.ByteCodeParser;
import com.caucho.v5.bytecode.JavaClass;
import com.caucho.v5.bytecode.attr.Attribute;
import com.caucho.v5.bytecode.attr.OpaqueAttribute;
import com.caucho.v5.loader.SimpleLoader;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.ReadStream;
import com.caucho.v5.vfs.VfsOld;

/**
 * Prints a stack trace using JSR-45
 */
public class ScriptStackTrace
{
  private static final L10N L = new L10N(ScriptStackTrace.class);
  private static final Logger log
    = Logger.getLogger(ScriptStackTrace.class.getName());
  
  private static WeakHashMap<Class,LineMap> _scriptMap
    = new WeakHashMap<Class,LineMap>();
  
  /**
   * Filter a stack trace, replacing names.
   */
  public static void printStackTrace(Throwable e, PrintWriter out)
  {
    StackTraceElement lastHead = null;

    ClassLoader loader = SimpleLoader.create(WorkDir.getLocalWorkDir());
    
    while (true) {
      if (e.getMessage() != null)
        out.println(e.getClass().getName() + ": " + e.getMessage());
      else
        out.println(e.getClass().getName());

      StackTraceElement []trace = e.getStackTrace();
      StackTraceElement nextHead = trace.length > 0 ? trace[0] : null;

      for (int i = 0; i < trace.length; i++) {
        if (trace[i].equals(lastHead))
          break;

        out.print("\tat ");

        printStackTraceElement(trace[i], out, loader);
      }

      lastHead = nextHead;

      Throwable cause = e.getCause();

      if (cause != null) {
        out.print("Caused by: ");
        e = cause;
      }
      else
        break;
    }
  }

  /**
   * Prints a single stack trace element.
   */
  private static void printStackTraceElement(StackTraceElement trace,
                                             PrintWriter out,
                                             ClassLoader loader)
  {
    try {
      LineMap map = getScriptLineMap(trace.getClassName(), loader);

      if (map != null) {
        LineMap.Line line = map.getLine(trace.getLineNumber());
        if (line != null) {
          out.print(trace.getClassName() + "." + trace.getMethodName());
          out.print("(" + line.getSourceFilename() + ":");
          out.println(line.getSourceLine(trace.getLineNumber()) + ")");
          return;
        }
      }
    } catch (Throwable e) {
    }
    
    out.println(trace);
  }

  /**
   * Loads the local line map for a class.
   */
  public static LineMap getScriptLineMap(String className, ClassLoader loader)
  {
    try {
      Class cl = loader.loadClass(className);

      LineMap map = _scriptMap.get(cl);

      if (map == null) {
        map = loadScriptMap(cl);
        _scriptMap.put(cl, map);
      }

      return map;
    } catch (Throwable e) {
      return null;
    }
  }
  

  /**
   * Loads the script map for a class
   */
  private static LineMap loadScriptMap(Class cl)
  {
    ClassLoader loader = cl.getClassLoader();

    if (loader == null)
      return new LineMap(); // null map
    
    try {
      String pathName = cl.getName().replace('.', '/') + ".class";

      InputStream is = loader.getResourceAsStream(pathName);

      if (is == null)
        return null;
      
      try {
        JavaClass jClass = new ByteCodeParser().parse(is);

        Attribute attr = jClass.getAttribute("SourceDebugExtension");

        if (attr == null) {
          int p = cl.getName().indexOf('$');

          if (p > 0) {
            String className = cl.getName().substring(0, p);

            return loadScriptMap(loader.loadClass(className));
          }

          return new LineMap();
        }
        else if (attr instanceof OpaqueAttribute) {
          byte []value = ((OpaqueAttribute) attr).getValue();

          ByteArrayInputStream bis = new ByteArrayInputStream(value);

          ReadStream rs = VfsOld.openRead(bis);
          rs.setEncoding("UTF-8");

          try {
            return parseSmap(rs);
          } finally {
            rs.close();
          }
        }
        else
          throw new IllegalStateException(L.l("Expected opaque attribute at '{0}'",
                                              attr));
      } finally {
        if (is != null)
          is.close();
      }
    } catch (Throwable e) {
      log.finer(e.toString());
      log.log(Level.FINEST, e.toString(), e);

      return new LineMap(); // null map
    }
  }

  /**
   * Parses the SMAP file.
   */
  private static LineMap parseSmap(ReadStream is)
    throws IOException
  {
    String smap = is.readln();

    if (! smap.equals("SMAP"))
      throw new IOException(L.l("Illegal header"));
      

    String outputFile = is.readln().trim();
    String defaultStratum = is.readln().trim();

    String stratum = defaultStratum;
    HashMap<String,String> fileMap = new HashMap<String,String>();

    LineMap lineMap = new LineMap(outputFile);
      
    loop:
    while (true) {
      int ch = is.read();

      if (ch < 0)
        break;

      if (ch != '*')
        throw new IOException(L.l("unexpected character '{0}'",
                                  String.valueOf((char) ch)));

      int code = is.read();
      String value = is.readln();

      switch (code) {
      case 'E':
        break loop;

      case 'S':
        stratum = value.trim();
        break;

      case 'F':
        while ((ch = is.read()) > 0 && ch != '*') {
          if (ch == '+') {
            String first = is.readln().trim();
            String second = is.readln().trim();

            int p = first.indexOf(' ');
            String key = first.substring(0, p);
            String file = first.substring(p + 1).trim();

            if (fileMap.size() == 0)
              fileMap.put("", second);

            fileMap.put(key, second);
          }
          else {
            String first = is.readln().trim();

            int p = first.indexOf(' ');
            String key = first.substring(0, p);
            String file = first.substring(p + 1).trim();

            if (fileMap.size() == 0)
              fileMap.put("", file);

            fileMap.put(key, file);
          }
        }
        if (ch == '*')
          is.unread();
        break;

      case 'L':
        while ((ch = is.read()) != '*' && ch > 0) {
          is.unread();

          String line = is.readln().trim();

          addMap(line, fileMap, lineMap);
        }
        if (ch == '*')
          is.unread();
        break;

      default:
        while ((ch = is.read()) != '*') {
          is.readln();
        }
        if (ch == '*')
          is.unread();
        break;
      }
    }
    
    
    return lineMap;
  }

  private static void addMap(String line,
                             HashMap<String,String> fileMap,
                             LineMap lineMap)
  {
    int colon = line.indexOf(':');

    if (colon < 0)
      return;

    int hash = line.indexOf('#');
    int startLine = 0;
    String fileId = "";
    int repeatCount = 1;

    if (hash < 0)
      startLine = Integer.parseInt(line.substring(0, colon));
    else {
      startLine = Integer.parseInt(line.substring(0, hash));

      int comma = line.indexOf(',', hash);

      if (comma > 0 && comma < colon) {
        fileId = line.substring(hash + 1, comma).trim();
        repeatCount = Integer.parseInt(line.substring(comma + 1, colon));
      }
      else
        fileId = line.substring(hash + 1, colon).trim();
    }

    int outputLine = -1;
    int outputIncrement = 1;

    int comma = line.indexOf(',', colon);

    if (comma > 0) {
      outputLine = Integer.parseInt(line.substring(colon + 1, comma));
      outputIncrement = Integer.parseInt(line.substring(comma + 1));
    }
    else
      outputLine = Integer.parseInt(line.substring(colon + 1));

    String file = fileMap.get(fileId);

    lineMap.addLine(startLine, file, repeatCount, outputLine, outputIncrement);
  }
}
