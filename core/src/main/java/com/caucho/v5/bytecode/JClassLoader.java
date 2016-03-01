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

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Manages an introspected java classes.
 */
abstract public class JClassLoader {
  protected static final Logger log =
    Logger.getLogger(JClassLoader.class.getName());
  
  private static JClassLoaderWrapper _staticClassLoader;

  private static final HashMap<String,JClass> _staticClassMap =
    new HashMap<String,JClass>();
  
  private final HashMap<String,SoftReference<JClass>> _classMap =
    new HashMap<String,SoftReference<JClass>>();

  /**
   * Returns the matching JClass.
   */
  public JClass forName(String name)
  {
    SoftReference<JClass> jClassRef = _classMap.get(name);

    JClass jClass = jClassRef != null ? jClassRef.get() : null;

    if (jClass == null) {
      jClass = _staticClassMap.get(name);

      if (jClass == null) {
        if (name.startsWith("[")) {
          JClass subClass = descriptorToClass(name, 1);
          jClass = new JClassArray(subClass);
        }
        else
          jClass = loadClass(name);
      }
      
      _classMap.put(name, new SoftReference<JClass>(jClass));
    }

    return jClass;
  }

  /**
   * Closes the class loader.
   */
  public void close()
  {
    _classMap.clear();
  }

  /**
   * Returns the matching JClass.
   */
  public static JClass systemForName(String name)
  {
    return getSystemClassLoader().forName(name);
  }

  /**
   * Returns the wrapped system class loader.
   */
  static JClassLoader getSystemClassLoader()
  {
    if (_staticClassLoader == null)
      _staticClassLoader = JClassLoaderWrapper.create(ClassLoader.getSystemClassLoader());

    return _staticClassLoader;
  }

  /**
   * Returns the matching JClass.
   */
  public static JClass localForName(String name)
  {
    JClassLoaderWrapper jLoader = JClassLoaderWrapper.create();
    
    return jLoader.forName(name);
  }

  /**
   * Loads the class.
   */
  abstract protected JClass loadClass(String name);

  /**
   * Returns the static class loader.
   */
  public static JClassLoader getStaticClassLoader()
  {
    return getSystemClassLoader();
  }

  public JClass descriptorToClass(String name, int i)
  {
    switch (name.charAt(i)) {
    case 'V': return forName("void");
    case 'Z': return forName("boolean");
    case 'C': return forName("char");
    case 'B': return forName("byte");
    case 'S': return forName("short");
    case 'I': return forName("int");
    case 'J': return forName("long");
    case 'F': return forName("float");
    case 'D': return forName("double");
    case '[':
      return forName(name.substring(i));
      
    case 'L':
      {
        int tail = name.indexOf(';', i);

        if (tail < 0)
          throw new IllegalStateException(name);

        String className = name.substring(i + 1, tail).replace('/', '.');

        return forName(className);
      }
      
    default:
      throw new UnsupportedOperationException(name.substring(i));
    }
  }

  public String toString()
  {
    return "JClassLoader[]";
  }

  static {
    _staticClassMap.put("void", JClass.VOID);
    _staticClassMap.put("boolean", JClass.BOOLEAN);
    _staticClassMap.put("byte", JClass.BYTE);
    _staticClassMap.put("short", JClass.SHORT);
    _staticClassMap.put("int", JClass.INT);
    _staticClassMap.put("long", JClass.LONG);
    _staticClassMap.put("float", JClass.FLOAT);
    _staticClassMap.put("double", JClass.DOUBLE);
    _staticClassMap.put("char", JClass.CHAR);
    _staticClassMap.put("java.lang.String", JClass.STRING);
    _staticClassMap.put("java.lang.Object", JClass.OBJECT);
  }
}
