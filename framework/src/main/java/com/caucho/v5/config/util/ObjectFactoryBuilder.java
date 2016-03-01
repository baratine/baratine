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
 * @author Alex Rojkov
 */
package com.caucho.v5.config.util;

import com.caucho.v5.util.L10N;

import sun.misc.Unsafe;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ObjectFactoryBuilder
{
  private static final Logger log
    = Logger.getLogger(ObjectFactoryBuilder.class.getName());
  private static final L10N L = new L10N(ObjectFactoryBuilder.class);

  public static ObjectFactoryBuilder _allocationFactory;
  private static Throwable _exception;

  static {
    init();
  }

  public static ObjectFactoryBuilder getInstance()
  {
    return _allocationFactory;
  }

  private static void init()
  {
    initUnsafe();

    if (_allocationFactory == null) {
      initReflectionFactory();
    }

    Throwable e = _exception;

    _exception = null;

    if (_allocationFactory == null) {
      throw new IllegalStateException("can't create object allocation factory",
                                      e);
    }
  }

  private static void initUnsafe()
  {
    try {
      Class c = Class.forName("com.caucho.v5.config.util.ObjectFactoryBuilderUnsafe");
      _allocationFactory = (ObjectFactoryBuilder) c.newInstance();
      log.finest(L.l("creating object allocation factory {0}:",
                     _allocationFactory));
    } catch (Throwable t) {
      _exception = t;
      if (log.isLoggable(Level.FINER))
        log.log(Level.FINER, t.getMessage(), t);
    }
  }

  private static void initReflectionFactory()
  {
    try {
      Class c = Class.forName(
        "com.caucho.v5.config.util.ObjectFactoryBuilderReflection");
      _allocationFactory = (ObjectFactoryBuilder) c.newInstance();
      log.finest(L.l("creating object allocation factory {0}:",
                     _allocationFactory));
    } catch (Throwable t) {
      _exception = t;
      if (log.isLoggable(Level.FINER))
        log.log(Level.FINER, t.getMessage(), t);
    }
  }

  abstract public <X> ObjectFactory<X> build(Class<X> c);
}

