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

import com.caucho.v5.util.L10N;

import java.net.URL;
import java.util.logging.Logger;

/**
 * Manages an introspected java classes.
 */
public class JavaClassLoader extends JClassLoader {
  protected static final L10N L = new L10N(JavaClassLoader.class);
  protected static final Logger log =
    Logger.getLogger(JavaClassLoader.class.getName());

  private ClassLoader _loader;

  public JavaClassLoader()
  {
    this(Thread.currentThread().getContextClassLoader());
  }

  public JavaClassLoader(ClassLoader loader)
  {
    _loader = loader;
  }

  /**
   * Loads the class.
   */
  protected JClass loadClass(String name)
  {
    // JDK classes are not loaded, but javax are (e.g. for JSF)
    if (name.startsWith("java.") || name.startsWith("java/")) {
      return getStaticClassLoader().forName(name);
    }

    String classPath = name.replace('.', '/') + ".class";
    URL url = _loader.getResource(classPath);

    if (url != null) {
      JavaClass jClass = new JavaClass(this);
      jClass.setURL(url);

      jClass.setThisClass(name.replace('.', '/'));

      return jClass;
    }

    return null;
  }

  /**
   * Parses the parameterized type.
   */
  protected JType parseParameterizedType(String type)
  {
    return new TypeSignatureParser(this, type).nextType();
  }

  public String toString()
  {
    return "JavaClassLoader[]";
  }
}
