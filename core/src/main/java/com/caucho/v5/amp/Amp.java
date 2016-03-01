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

package com.caucho.v5.amp;

import java.lang.ref.SoftReference;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.manager.AmpProvider2;
import com.caucho.v5.amp.spi.AmpProvider;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.loader.EnvironmentLocal;

/**
 * Manages an AMP domain.
 */
public final class Amp
{
  private static final Logger log = Logger.getLogger(Amp.class.getName());
  
  private static final EnvironmentLocal<ServiceManagerAmp> _contextManager
    = new EnvironmentLocal<>();
  
  private static final WeakHashMap<ClassLoader,SoftReference<ServiceManagerAmp>> _contextMap
  = new WeakHashMap<>();
  
  private Amp() {}
  
  public static ServiceManagerAmp newManager()
  {
    ServiceManagerBuilderAmp builder = newManagerBuilder();
    
    builder.contextManager(false);
    
    return builder.start();
  }
  
  public static ServiceManagerAmp getContextManager()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    return getContextManager(loader);
  }
  
  /*
  public static ServiceManagerAmp newContextManager()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    synchronized (_contextManager) {
      ServiceManagerAmp manager = _contextManager.getLevel(loader);
    
      if (manager == null) {
        manager = newManager();
        
        _contextManager.set(manager);
      }
      
      return manager;
    }
  }
  */
  
  public static ServiceManagerAmp getContextManager(ClassLoader loader)
  {
    ServiceManagerAmp manager = _contextManager.getLevel(loader);
    
    if (manager == null) {
      SoftReference<ServiceManagerAmp> managerRef = _contextMap.get(loader);

      if (managerRef != null) {
        return managerRef.get();
      }
      
      /*
      if (log.isLoggable(Level.FINEST)) {
        RuntimeException exn =  new IllegalStateException(String.valueOf(loader));
        //exn.fillInStackTrace();
        log.log(Level.FINEST, exn.toString(), exn);
      }
      */
    }
    
    return manager;
    /*
    if (manager == null) {
      ServiceManagerBuilderAmp managerBuilder = newManagerBuilder();
      
      String name = Environment.getEnvironmentName(loader);

      managerBuilder.classLoader(loader);
      managerBuilder.name(name);

      manager = managerBuilder.build();
      
      _contextManager.setIfAbsent(manager);
      
      manager = _contextManager.get(loader);
    }
    
    return manager;
    */
  }

  public static void setContextManager(ServiceManagerAmp manager)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    setContextManager(manager, loader);
  }

  public static void setContextManager(ServiceManagerAmp manager, 
                                       ClassLoader loader)
  {
    Objects.requireNonNull(manager);
    
    if (loader instanceof EnvironmentClassLoader) {
      _contextManager.set(manager, loader);
    }
    else {
      _contextMap.put(loader, new SoftReference<>(manager));
    }
  }
  
  public static ServiceManagerBuilderAmp newManagerBuilder()
  {
    AmpProvider provider = getProvider();
    
    return provider.createManagerBuilder();
  }
  
  private static AmpProvider getProvider()
  {
    return new AmpProvider2();
  }

  public static InboxAmp getCurrentInbox()
  {
    OutboxAmp outbox = OutboxAmp.current();
    
    if (outbox != null) {
      return outbox.inbox();
    }
    else {
      return null;
    }
  }

  public static ServiceManagerAmp getCurrentManager()
  {
    OutboxAmp outbox = OutboxAmp.current();
    
    if (outbox == null) {
      // return null;
      return getContextManager();
    }
    
    InboxAmp inbox = outbox.inbox();

    if (inbox != null) {
      ServiceManagerAmp manager = inbox.manager();
      
      if (manager != null) {
        return manager;
      }
    }

    return getContextManager();
  }
}
