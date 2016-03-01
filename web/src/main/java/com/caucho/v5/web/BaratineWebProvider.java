/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
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
 * @author Scott Ferguson
 */

package com.caucho.v5.web;

import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.web.builder.WebServerBuilderImpl;

import io.baratine.web.WebServer;

public class BaratineWebProvider implements AutoCloseable
{
  private static final Logger log
    = Logger.getLogger(BaratineWebProvider.class.getName());
  
  private static WeakHashMap<ClassLoader,BaratineWebProvider> _providerMap
    = new WeakHashMap<>();

  private static Class<?> _builderClass;
  
  private ClassLoader _classLoader;

  private WebServerBuilderImpl _builder;
  private WebServer _server;
  
  BaratineWebProvider(ClassLoader loader)
  {
    EnvLoader.addCloseListener(this, loader);
    
    _classLoader = loader;
  }
  
  static BaratineWebProvider get()
  {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    
    synchronized (_providerMap) {
      BaratineWebProvider provider = _providerMap.get(classLoader);

      if (provider == null) {
        provider = new BaratineWebProvider(classLoader);
        
        _providerMap.put(classLoader, provider);
      }
      
      return provider;
    }
  }
  
  public static WebServerBuilderImpl builder()
  {
    return get().getBuilder();
  }
  
  WebServerBuilderImpl getBuilder()
  {
    synchronized (this) {
      if (_builder == null || _builder.isClosed()) {
        _builder = server();
      }
      
      return _builder;
    }
  }
  
  WebServerBuilderImpl server()
  {
    try {
      return (WebServerBuilderImpl) _builderClass.newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void close()
  {
    synchronized (_providerMap) {
      if (_providerMap.get(_classLoader) == this) {
        _providerMap.remove(_classLoader);
      };
    }
  }
  
  static {
    Class<?> builderClass = WebServerBuilderImpl.class;
    
    try {
      String name = builderClass.getName();
      name = name.replace("Impl", "Baratine");
      
      builderClass = Class.forName(name);
    } catch (Exception e) {
      log.log(Level.ALL, e.toString(), e);
    }
    
    _builderClass = builderClass;
  }
}
