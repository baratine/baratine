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

package com.caucho.v5.bytecode;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.util.Base64Util;

/**
 * Representing a class that might change.
 */
public class JClassDependency //implements PersistentDependency
{
  private final static Logger log
    = Logger.getLogger(JClassDependency.class.getName());
  
  private final String _className;

  private boolean _checkFields = true;
  private boolean _checkStatic = true;
  private boolean _checkProtected = true;
  private boolean _checkPrivate = true;

  private boolean _isDigestModified;

  /**
   * Creates the class dependency.
   */
  public JClassDependency(JClass cl)
  {
    _className = cl.getName();
  }

  /**
   * Create a new dependency with a given digest.
   *
   * @param cl the source class
   * @param digest the MD5 digest
   */
  public JClassDependency(JClass cl, String digest)
  {
    _className = cl.getName();

    String newDigest = getDigest();

    if (! newDigest.equals(digest)) {
      if (log.isLoggable(Level.FINE))
        log.fine(_className + " digest is modified.");

      _isDigestModified = true;
    }
  }

  /**
   * Create a new dependency with a given digest.
   *
   * @param cl the source class
   * @param digest the MD5 digest
   */
  public JClassDependency(String className, String digest)
  {
    _className = className;
    
    String newDigest = getDigest();

    if (! newDigest.equals(digest)) {
      if (log.isLoggable(Level.FINE))
        log.fine(_className + " digest is modified.");

      _isDigestModified = true;
    }
  }
  
  /**
   * Returns true if the underlying resource has changed.
   */
  public boolean isModified()
  {
    return _isDigestModified;
  }
  
  /**
   * Log the reason for modification
   */
  public boolean logModified(Logger log)
  {
    if (isModified()) {
      log.info(_className + " is modified");
      return true;
    }
    else
      return false;
  }

  /**
   * Calculates a MD5 digest of the class.
   */
  public String getDigest()
  {
    try {
      if (_className == null || "".equals(_className))
        return "";
      
      DynamicClassLoader loader
        = (DynamicClassLoader) Thread.currentThread().getContextClassLoader();

      ClassLoader tmpLoader = loader.getNewTempClassLoader();
      
      Class cl = Class.forName(_className, false, tmpLoader);
      
      if (cl == null)
        return "";

      MessageDigest digest = MessageDigest.getInstance("MD5");

      addDigest(digest, cl.getName());

      addDigest(digest, cl.getModifiers());

      Class superClass = cl.getSuperclass();
      if (superClass != null)
        addDigest(digest, superClass.getName());

      Class []interfaces = cl.getInterfaces();
      for (int i = 0; i < interfaces.length; i++)
        addDigest(digest, interfaces[i].getName());

      Field []fields = cl.getDeclaredFields();

      Arrays.sort(fields, new FieldComparator());

      if (_checkFields) {
        for (Field field : fields) {
          if (Modifier.isPrivate(field.getModifiers())
              && ! _checkPrivate)
            continue;
          if (Modifier.isProtected(field.getModifiers())
              && ! _checkProtected)
            continue;
          
          addDigest(digest, field.getName());
          addDigest(digest, field.getModifiers());
          addDigest(digest, field.getType().getName());

          addDigest(digest, field.getAnnotations());
        }
      }

      Method []methods = cl.getDeclaredMethods();
      Arrays.sort(methods, new MethodComparator());
      
      for (int i = 0; i < methods.length; i++) {
        Method method = methods[i];

        if (Modifier.isPrivate(method.getModifiers()) && ! _checkPrivate)
          continue;
        if (Modifier.isProtected(method.getModifiers()) && ! _checkProtected)
          continue;
        if (Modifier.isStatic(method.getModifiers()) && ! _checkStatic)
          continue;
          
        addDigest(digest, method.getName());
        addDigest(digest, method.getModifiers());
        addDigest(digest, method.getName());

        Class []param = method.getParameterTypes();
        for (int j = 0; j < param.length; j++)
          addDigest(digest, param[j].getName());

        addDigest(digest, method.getReturnType().getName());

        Class []exn = method.getExceptionTypes();
        for (int j = 0; j < exn.length; j++)
          addDigest(digest, exn[j].getName());

        addDigest(digest, method.getAnnotations());
      }
      
      byte []digestBytes = new byte[256];
      
      int len = digest.digest(digestBytes, 0, digestBytes.length);
      
      return digestToBase64(digestBytes, len);
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);

      return "";
    }
  }

  /**
   * Returns a string which will recreate the dependency.
   */
  public String getJavaCreateString()
  {
    return ("new com.caucho.bytecode.JClassDependency(\"" +
            _className + "\", \"" + getDigest() + "\")");
  }
  
  /**
   * Adds the annotations to the digest using a UTF8 encoding.
   */
  private static void addDigest(MessageDigest digest, Annotation []annList)
  {
    if (annList == null)
      return;

    for (Annotation ann : annList)
      addDigest(digest, ann);
  }
  
  /**
   * Adds the annotations to the digest using a UTF8 encoding.
   */
  private static void addDigest(MessageDigest digest, Annotation ann)
  {
    addDigest(digest, ann.annotationType().getName());
  }
  
  /**
   * Adds the int to the digest.
   */
  private static void addDigest(MessageDigest digest, int v)
  {
    digest.update((byte) (v >> 24));
    digest.update((byte) (v >> 16));
    digest.update((byte) (v >> 8));
    digest.update((byte) v);
  }
  
  /**
   * Adds the string to the digest using a UTF8 encoding.
   */
  private static void addDigest(MessageDigest digest, String string)
  {
    if (string == null)
      return;
    
    int len = string.length();
    for (int i = 0; i < len; i++) {
      int ch = string.charAt(i);
      if (ch < 0x80)
        digest.update((byte) ch);
      else if (ch < 0x800) {
        digest.update((byte) (0xc0 + (ch >> 6)));
        digest.update((byte) (0x80 + (ch & 0x3f)));
      }
      else {
        digest.update((byte) (0xe0 + (ch >> 12)));
        digest.update((byte) (0x80 + ((ch >> 6) & 0x3f)));
        digest.update((byte) (0x80 + (ch & 0x3f)));
      }
    }
  }
  
  private String digestToBase64(byte []digest, int len)
  {
    StringBuilder cb = new StringBuilder();

    Base64Util.encode(cb, digest, 0, len);

    return cb.toString();
  }

  public boolean isEqual(Object o)
  {
    if (o == this)
      return true;
    
    if (! (o instanceof JClassDependency))
      return false;

    JClassDependency depend = (JClassDependency) o;

    return _className.equals(depend._className);
  }

  static class FieldComparator implements Comparator<Field> {
    public int compare(Field a, Field b)
    {
      if (a == b)
        return 0;
      else if (a == null)
        return -1;
      else if (b == null)
        return 1;
      else if (a.equals(b))
        return 0;

      int cmp = a.getName().compareTo(b.getName());
      if (cmp != 0)
        return cmp;
      
      cmp = a.getDeclaringClass().getName().compareTo(b.getDeclaringClass().getName());
      if (cmp != 0)
        return cmp;
      
      return a.getType().getName().compareTo(b.getType().getName());
    }
  }

  static class MethodComparator implements Comparator<Method> {
    public int compare(Method a, Method b)
    {
      if (a == b)
        return 0;
      else if (a == null)
        return -1;
      else if (b == null)
        return 1;
      else if (a.equals(b))
        return 0;

      int cmp = a.getName().compareTo(b.getName());
      if (cmp != 0)
        return cmp;

      Class []paramA = a.getParameterTypes();
      Class []paramB = b.getParameterTypes();

      if (paramA.length < paramB.length)
        return -1;
      else if (paramB.length < paramA.length)
        return 1;

      for (int i = 0; i < paramA.length; i++) {
        cmp = paramA[i].getName().compareTo(paramB[i].getName());
        if (cmp != 0)
          return cmp;
      }
      
      cmp = a.getDeclaringClass().getName().compareTo(b.getDeclaringClass().getName());
      if (cmp != 0)
        return cmp;
      
      return a.getReturnType().getName().compareTo(b.getReturnType().getName());
    }
  }
}
