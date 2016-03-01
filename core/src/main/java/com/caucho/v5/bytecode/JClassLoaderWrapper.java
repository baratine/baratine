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

import com.caucho.v5.loader.EnvironmentLocal;

import java.util.logging.Level;

/**
 * Managed introspected java classes.
 */
public class JClassLoaderWrapper extends JClassLoader {
  private static final
    EnvironmentLocal<JClassLoaderWrapper> _localClassLoader =
    new EnvironmentLocal<JClassLoaderWrapper>();
  
  private ClassLoader _loader;

  /**
   * Creates the class loader with a specific class loader.
   */
  private JClassLoaderWrapper(ClassLoader loader)
  {
    _loader = loader;
  }

  /**
   * Creates the class loader with the context class loader.
   */
  public static JClassLoaderWrapper create()
  {
    return create(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Creates the class loader with the context class loader.
   */
  public static JClassLoaderWrapper create(ClassLoader loader)
  {
    JClassLoaderWrapper jLoader = _localClassLoader.getLevel(loader);
    
    if (jLoader == null) {
      jLoader = new JClassLoaderWrapper(loader);
      _localClassLoader.set(jLoader, loader);
    }

    return jLoader;
  }

  /**
   * Loads the class.
   */
  protected JClass loadClass(String name)
  {
    try {
      Class cl;

      if (_loader != null)
        cl = Class.forName(name, false, _loader);
      else
        cl = Class.forName(name);

      return new JClassWrapper(cl, this);
    } catch (ClassNotFoundException e) {
      log.log(Level.FINE, e.toString(), e);

      return null;
    }
  }

  public String toString()
  {
    return "JClassLoaderWrapper[" + _loader + "]";
  }
}
