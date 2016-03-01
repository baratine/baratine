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

package com.caucho.v5.make;

import com.caucho.v5.util.Crc64;
import com.caucho.v5.vfs.PersistentDependency;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Representing a class that might change.
 */
public class ClassDependency implements PersistentDependency {
  private final static Logger log
    = Logger.getLogger(ClassDependency.class.getName());
  
  private Class<?> _cl;
  private String _className;

  private boolean _checkFields = true;
  private boolean _checkStatic = true;
  private boolean _checkProtected = true;
  private boolean _checkPrivate = true;

  private boolean _isDigestModified;
  private long _newDigest;

  /**
   * Creates the class dependency.
   */
  public ClassDependency(Class<?> cl)
  {
    _cl = cl;
    _className = cl.getName();
  }

  /**
   * Create a new dependency with a given digest.
   *
   * @param cl the source class
   * @param digest the MD5 digest
   */
  public ClassDependency(String className, long digest)
  {
    _className = className;
    
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();

      _cl = Class.forName(className, false, loader);
    } catch (ClassNotFoundException e) {
      log.log(Level.FINE, e.toString(), e);
    }
    
    long newDigest = getDigest();

    if (newDigest != digest) {
      if (log.isLoggable(Level.FINE))
        log.fine(className + " class digest is modified (old=" + digest + ",new=" + newDigest + ")");

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
   * Returns true if the underlying resource has changed.
   */
  public boolean logModified(Logger log)
  {
    if (isModified()) {
      log.info(_className + " digest is modified");
      return true;
    }
    else
      return false;
  }

  /**
   * Calculates a MD5 digest of the class.
   */
  public long getDigest()
  {
    try {
      if (_newDigest != 0)
        return _newDigest;
      
      if (_cl == null)
        return -1;

      long digest = 37;

      digest = addDigest(digest, _cl);
      
      _newDigest = digest;
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);

      _newDigest = -1;
    }

    return _newDigest;
  }

  /**
   * Calculates a MD5 digest of the class.
   */
  private long addDigest(long digest, Class<?> cl)
    throws Exception
  {
    if (_cl == null)
      return digest;

    digest = addDigest(digest, cl.getName());

    digest = addDigest(digest, cl.getModifiers());

    Class<?> superClass = cl.getSuperclass();
    if (superClass != null
        && superClass.getName().startsWith("java.")
        && ! superClass.getName().startsWith("javax.")) {
      digest = addDigest(digest, superClass);
    }

    Class<?> []interfaces = cl.getInterfaces();
    Arrays.sort(interfaces, ClassComparator.CMP);
    for (int i = 0; i < interfaces.length; i++)
      digest = addDigest(digest, interfaces[i].getName());

    if (_checkFields) {
      Field []fields = cl.getDeclaredFields();

      Arrays.sort(fields, FieldComparator.CMP);

      for (int i = 0; i < fields.length; i++) {
        int modifiers = fields[i].getModifiers();

        if (Modifier.isPrivate(modifiers) && ! _checkPrivate)
          continue;
        if (Modifier.isProtected(modifiers) && ! _checkProtected)
          continue;
          
        digest = addDigest(digest, fields[i].getName());
        digest = addDigest(digest, fields[i].getModifiers());
        digest = addDigest(digest, fields[i].getType().getName());
        // jpa/0021
        Annotation[] annotations = fields[i].getAnnotations();
        for (Annotation annotation : annotations) {
          digest = addDigest(digest, annotation.annotationType().getName());
        }
      }
    }

    Method []methods = cl.getDeclaredMethods();
    Arrays.sort(methods, MethodComparator.CMP);
      
    for (int i = 0; i < methods.length; i++) {
      Method method = methods[i];
      int modifiers = method.getModifiers();

      if (Modifier.isPrivate(modifiers) && ! _checkPrivate)
        continue;
      if (Modifier.isProtected(modifiers) && ! _checkProtected)
        continue;
      if (Modifier.isStatic(modifiers) && ! _checkStatic)
        continue;
          
      digest = addDigest(digest, method.getName());
      digest = addDigest(digest, method.getModifiers());
      digest = addDigest(digest, method.getName());

      Class<?> []param = method.getParameterTypes();
      for (int j = 0; j < param.length; j++)
        digest = addDigest(digest, param[j].getName());

      digest = addDigest(digest, method.getReturnType().getName());

      Class<?> []exn = method.getExceptionTypes();
      Arrays.sort(exn, ClassComparator.CMP);
      for (int j = 0; j < exn.length; j++)
        digest = addDigest(digest, exn[j].getName());
    }

    return digest;
  }

  /**
   * Returns a string which will recreate the dependency.
   */
  @Override
  public String getJavaCreateString()
  {
    // server/18p3, #5408 - class loader wants the $ for inner classes
    return ("new com.caucho.v5.make.ClassDependency("
        + "\"" + _className + "\""
        + ", " + getDigest() + "L)");
    /*
    return ("new com.caucho.v5.make.ClassDependency("
            + "\"" + _className.replace('$', '.') + "\""
            + ", " + getDigest() + "L)");
            */
  }
  
  /**
   * Adds the int to the digest.
   */
  private static long addDigest(long digest, long v)
  {
    digest = Crc64.generate(digest, (byte) (v >> 24));
    digest = Crc64.generate(digest, (byte) (v >> 16));
    digest = Crc64.generate(digest, (byte) (v >> 8));
    digest = Crc64.generate(digest, (byte) v);

    return digest;
  }
  
  /**
   * Adds the string to the digest using a UTF8 encoding.
   */
  private static long addDigest(long digest, String string)
  {
    return Crc64.generate(digest, string);
  }

  public int hashCode()
  {
    return _className.hashCode();
  }

  public boolean equals(Object o)
  {
    if (o == this)
      return true;
    
    if (! (o instanceof ClassDependency))
      return false;

    ClassDependency depend = (ClassDependency) o;

    return _className.equals(depend._className);
  }

  static class ClassComparator implements Comparator<Class> {
    static final ClassComparator CMP = new ClassComparator();
    
    public int compare(Class a, Class b)
    {
      if (a == b)
        return 0;
      else if (a == null)
        return -1;
      else if (b == null)
        return 1;

      return a.getName().compareTo(b.getName());
    }
  }

  static class FieldComparator implements Comparator<Field> {
    static final FieldComparator CMP = new FieldComparator();
    
    public int compare(Field a, Field b)
    {
      if (a == b)
        return 0;
      else if (a == null)
        return -1;
      else if (b == null)
        return 1;

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
    static final MethodComparator CMP = new MethodComparator();
    
    public int compare(Method a, Method b)
    {
      if (a == b)
        return 0;
      else if (a == null)
        return -1;
      else if (b == null)
        return 1;

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

  public String toString()
  {
    return getClass().getSimpleName() + "[" + _className + "]";
  }
}
