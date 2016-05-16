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

package com.caucho.v5.util;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * debug utilities
 */
public class DebugUtil {
  private static Logger log = Logger.getLogger(DebugUtil.class.getName());
  private static L10N L = new L10N(DebugUtil.class);

  private static Object _javaLangAccess;
  private static Method _getStackTraceDepth;
  private static Method _getStackTraceElement;
  
  private final static boolean isDebug;

  public static String callerEntry(int depth)
  {
    if (_getStackTraceElement == null) {
      return null;
    }
    
    try {
      Throwable exn = new Throwable();
      int stackTraceDepth = (Integer) _getStackTraceDepth.invoke(_javaLangAccess, exn);
      
      if (stackTraceDepth < depth) {
        return null;
      }
      
      StackTraceElement elt = (StackTraceElement) _getStackTraceElement.invoke(_javaLangAccess, exn, depth);
      
      if (elt == null) {
        return null;
      }
      
      String className = elt.getClassName();
      String methodName = elt.getMethodName();
      
      String fileName = elt.getFileName();
      int line = elt.getLineNumber();
      
      if (fileName != null) {
        return className + "." + methodName + "(" + fileName + ":" + line + ")";
      }
      else {
        return className + "." + methodName;
      }
    } catch (Exception e) {
      return null;
    }
  }

  public static boolean isDebug()
  {
    return isDebug;
  }
  
  static {
    Object javaLangAccessObj = null;
    Method mGetStackTraceDepth = null;
    Method mGetStackTraceElement = null;
    
    try {
      String sharedSecrets = "sun.misc.SharedSecrets";
      Class<?> sharedSecretsCl = Class.forName(sharedSecrets);
      
      String javaLangAccess = "sun.misc.JavaLangAccess";
      Class<?> javaLangAccessCl = Class.forName(javaLangAccess);
      
      Method mGetJavaLangAccess = sharedSecretsCl.getMethod("getJavaLangAccess");
      
      javaLangAccessObj = mGetJavaLangAccess.invoke(null);
      
      mGetStackTraceDepth = javaLangAccessCl.getMethod("getStackTraceDepth", Throwable.class);
      mGetStackTraceElement = javaLangAccessCl.getMethod("getStackTraceElement", Throwable.class, int.class);
    } catch (Throwable e) {
      log.log(Level.FINER, e.getMessage(), e);
    }

    _javaLangAccess = javaLangAccessObj;
    _getStackTraceDepth = mGetStackTraceDepth;
    _getStackTraceElement = mGetStackTraceElement;

    /*
    log.log(Level.FINER, L.l("init {0} with {1} {2} {3}",
                             DebugUtil.class.getSimpleName(),
                             _javaLangAccess,
                             _getStackTraceDepth,
                             _getStackTraceElement));
                             */
  }

  //_isDebug
  static {
    isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().
      getInputArguments().toString().indexOf("-agentlib:jdwp") > 0;
  }
}
