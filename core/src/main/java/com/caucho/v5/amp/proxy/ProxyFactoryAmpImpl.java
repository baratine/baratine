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

package com.caucho.v5.amp.proxy;

import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.manager.ServicesAmpImpl;
import com.caucho.v5.amp.proxy.ProxyGeneratorAmp.ProxyGeneratorFactoryAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.stub.ClassStubSession;
import com.caucho.v5.amp.stub.StubClass;

import io.baratine.service.ServiceException;
import io.baratine.service.ServiceRef;

/**
 * Creates AMP skeletons and stubs.
 */
public class ProxyFactoryAmpImpl implements ProxyFactoryAmp
{
  private static WeakHashMap<ClassLoader,SoftReference<AmpProxyCache>> _cacheMap
    = new WeakHashMap<>();
    
  private final ServicesAmp _ampManager;
  
  private AmpProxyCache _proxyCache;
  
  private ConcurrentHashMap<Class<?>,StubClass> _skeletonMap
    = new ConcurrentHashMap<>();
  
  private ConcurrentHashMap<Class<?>,ClassStubSession> _skeletonChannelMap
    = new ConcurrentHashMap<>();
  
  private MessageFactoryAmp _messageFactory;
      
  public ProxyFactoryAmpImpl(ServicesAmp ampManager)
  {
    Objects.requireNonNull(ampManager);
    
    _ampManager = ampManager;
    
    if (ampManager.isDebug()) {
      ServicesAmpImpl ampManagerImpl = (ServicesAmpImpl) ampManager;
      
      _messageFactory = new MessageFactoryDebug(ampManagerImpl);
    }
    else {
      _messageFactory = new MessageFactoryBase(ampManager);
    }
  }
  
  private String getLocalPath(String path)
  {
    int p = path.indexOf("://");
    
    if (p < 0) {
      return null;
    }
    
    int q = path.indexOf("/", p + 3);
    
    if (q > 0) {
      return path.substring(q);
    }
    else {
      return null;
    }
  }
  
  @Override
  public <T> T createProxy(ServiceRefAmp serviceRef,
                           Class<T> api)

  {
    Objects.requireNonNull(api);
    
    if (ServiceRef.class.isAssignableFrom(api)) {
      throw new IllegalArgumentException(api.toString());
    }
    
    // Thread thread = Thread.currentThread();
    // baratine/8098
    // ClassLoader loader = thread.getContextClassLoader();
    ClassLoader loader = serviceRef.manager().classLoader();
    
    try {
      //thread.setContextClassLoader(_ampManager.getClassLoader());
      
      AmpProxyCache cache = getCache(loader);

      ProxyGeneratorFactoryAmp proxyFactory = null;
      
      synchronized (cache) {
        proxyFactory = cache.getProxy(api.getName());
      }
      
      if (proxyFactory == null) {
        proxyFactory = ProxyGeneratorAmp.create(api, loader);
        
        // proxyFactory.setAccessible(true);
        
        synchronized (cache) {
          cache.putProxy(api.getName(), proxyFactory);
        }
      }
      
      InboxAmp systemInbox = serviceRef.manager().inboxSystem();
      MessageFactoryAmp messageFactory = _messageFactory;

      return (T) proxyFactory.newInstance(serviceRef, systemInbox, messageFactory);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      
      throw ServiceException.createAndRethrow(e.getCause());
    } catch (Throwable e) {
      throw ServiceException.createAndRethrow(e);
    } finally {
      // thread.setContextClassLoader(oldLoader);
    }
  }
  
  private AmpProxyCache getCache(ClassLoader loader)
  {
    synchronized (_cacheMap) {
      SoftReference<AmpProxyCache> cacheRef = _cacheMap.get(loader);
      
      AmpProxyCache cache = null;
      
      if (cacheRef != null) {
        cache = cacheRef.get();
      }
      
      if (cache == null) {
        cache = new AmpProxyCache();
        _cacheMap.put(loader, new SoftReference<AmpProxyCache>(cache));
      }
      
      return cache;
    }
  }

  static class AmpProxyCache {
    private HashMap<String,ProxyGeneratorFactoryAmp> _proxyMap = new HashMap<>();
    
    ProxyGeneratorFactoryAmp getProxy(String name)
    {
      return _proxyMap.get(name);
    }
    
    void putProxy(String name, ProxyGeneratorFactoryAmp proxyFactory)
    {
      _proxyMap.put(name, proxyFactory);
    }
  }
}
