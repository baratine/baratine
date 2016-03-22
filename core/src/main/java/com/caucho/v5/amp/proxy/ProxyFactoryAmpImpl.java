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

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.manager.ServiceManagerAmpImpl;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.stub.ClassStub;
import com.caucho.v5.amp.stub.ClassStubSession;

import io.baratine.service.ServiceException;
import io.baratine.service.ServiceRef;

/**
 * Creates AMP skeletons and stubs.
 */
public class ProxyFactoryAmpImpl implements ProxyFactoryAmp
{
  private static WeakHashMap<ClassLoader,SoftReference<AmpProxyCache>> _cacheMap
    = new WeakHashMap<>();
    
  private final ServiceManagerAmp _ampManager;
  
  private AmpProxyCache _proxyCache;
  
  private ConcurrentHashMap<Class<?>,ClassStub> _skeletonMap
    = new ConcurrentHashMap<>();
  
  private ConcurrentHashMap<Class<?>,ClassStubSession> _skeletonChannelMap
    = new ConcurrentHashMap<>();
  
  private MessageFactoryAmp _messageFactory;
      
  public ProxyFactoryAmpImpl(ServiceManagerAmp ampManager)
  {
    Objects.requireNonNull(ampManager);
    
    _ampManager = ampManager;
    
    if (ampManager.isDebug()) {
      ServiceManagerAmpImpl ampManagerImpl = (ServiceManagerAmpImpl) ampManager;
      
      _messageFactory = new MessageFactoryDebug(ampManagerImpl);
    }
    else {
      _messageFactory = new MessageFactoryBase(ampManager);
    }
  }

  /*
  @Override
  public ActorAmp createSkeleton(Object bean,
                                 String path,
                                 String childPath,
                                 ActorContainerAmp container,
                                 ServiceConfig config)
  {
    ClassStub skel;
    
    if (path != null && (path.startsWith("pod://") || path.startsWith("public://"))) {
      skel = createPodSkeleton(bean.getClass(), path, config);
    }
    else {
      skel = createSkeleton(bean.getClass(), path, config);
    }
    
    if (container != null) {
      return new StubAmpBeanChild(skel, bean, path, childPath, container);
    }
    else {
      if (path == null && config != null) {
        path = config.name(); 
      }
      
      return new StubAmpBean(skel, bean, path, container);
    }
  }
  */
  
  protected ClassStub createPodSkeleton(Class<?> beanClass, 
                                            String path,
                                            ServiceConfig config)
  {
    return createSkeleton(beanClass, path, config);
    
    // XXX: 
    /*
    String localPath = getLocalPath(path);
    
    PodApp podApp = PodApp.getCurrent();

    if (podApp == null) {
      return createSkeleton(beanClass, path, config);
    }
    
    NodePodAmp currentNode = podApp.getPodNode();

    int hash = HashPod.hash(localPath);
    NodePodAmp serviceNode = currentNode.getPod().getNode(hash);
    
    if (currentNode.getIndex() >= 0 
        && currentNode.nodeIndex() == serviceNode.nodeIndex()) {
      return createSkeleton(beanClass, path, config);
    }
    else {
      return new SkeletonClassForeign(_ampManager, beanClass, config);
    }
    */
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
  
  protected ClassStub createSkeleton(Class<?> beanClass, 
                                         String path,
                                         ServiceConfig config)
  {
    ClassStub skel = _skeletonMap.get(beanClass);
    
    if (skel == null) {
      skel = new ClassStub(_ampManager, beanClass, config);
      skel.introspect();
      _skeletonMap.putIfAbsent(beanClass, skel);
      skel = _skeletonMap.get(beanClass);
    }
    
    return skel;
    
  }

  /*
  @Override
  public ActorAmp createSkeletonSession(Object bean,
                                        String key,
                                        ContextSession context,
                                        ServiceConfig config)
  {
    Class<?> beanClass = bean.getClass();
    
    ClassStubSession skel = _skeletonChannelMap.get(beanClass);
    
    if (skel == null) {
      skel = new ClassStubSession(_ampManager, beanClass, config);
      skel.introspect();
      _skeletonChannelMap.putIfAbsent(beanClass, skel);
      skel = _skeletonChannelMap.get(beanClass);
    }
    
    return new ActorSkeletonSession(skel, bean, key, context); 
  }

  @Override
  public ActorAmp createSkeletonMain(Class<?> api,
                                     String path,
                                     ServiceConfig config)
  {
    ClassStub skel = new ClassStub(_ampManager, api, config);
    skel.introspect();
    
    // XXX: need different actor
    return new StubAmpBeanBase(skel, path, null);
  }
  */
  
  @Override
  public <T> T createProxy(ServiceRefAmp serviceRef,
                           Class<T> api)

  {
    Objects.requireNonNull(api);
    
    if (ServiceRef.class.isAssignableFrom(api)) {
      throw new IllegalArgumentException(api.toString());
    }
    
    Thread thread = Thread.currentThread();
    // baratine/8098
    // ClassLoader loader = thread.getContextClassLoader();
    ClassLoader loader = serviceRef.manager().classLoader();
    
    try {
      //thread.setContextClassLoader(_ampManager.getClassLoader());
      
      AmpProxyCache cache = getCache(loader);

      Constructor<?> proxyCtor = null;
      
      synchronized (cache) {
        proxyCtor = cache.getProxy(api.getName());
      }
      
      if (proxyCtor == null) {
        proxyCtor = ProxyGeneratorAmp.create(api, loader);
        
        proxyCtor.setAccessible(true);
        
        synchronized (cache) {
          cache.putProxy(api.getName(), proxyCtor);
        }
      }
      
      InboxAmp systemInbox = serviceRef.manager().inboxSystem();
      MessageFactoryAmp messageFactory = _messageFactory;
      
      return (T) proxyCtor.newInstance(serviceRef, systemInbox, messageFactory);
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
    private HashMap<String,Constructor<?>> _proxyMap = new HashMap<>();
    private HashMap<String,Constructor<?>> _reproxyMap = new HashMap<>();
    
    Constructor<?> getProxy(String name)
    {
      return _proxyMap.get(name);
    }
    
    void putProxy(String name, Constructor<?> ctor)
    {
      _proxyMap.put(name, ctor);
    }

    Constructor<?> getReproxy(String name)
    {
      return _reproxyMap.get(name);
    }
    
    void putReproxy(String name, Constructor<?> ctor)
    {
      _reproxyMap.put(name, ctor);
    }
  }
}
