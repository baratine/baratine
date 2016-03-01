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

package com.caucho.v5.bartender.link;

import io.baratine.service.ServiceException;
import io.baratine.service.ServiceExceptionIllegalState;
import io.baratine.service.ServiceExceptionUnavailable;

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.MethodRefWrapperClassLoader;
import com.caucho.v5.amp.actor.ServiceRefException;
import com.caucho.v5.amp.actor.ServiceRefWrapper;
import com.caucho.v5.amp.message.MessageWrapperClassLoader;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.http.pod.PodApp;
import com.caucho.v5.util.L10N;

/**
 * ServiceRef for champ: based pod-apps.
 */
public class ServiceRefPodApp extends ServiceRefWrapper
{
  private static final L10N L = new L10N(ServiceRefPodApp.class);
  private static final Logger log
    = Logger.getLogger(ServiceRefPodApp.class.getName());
  
  private final ServiceManagerAmp _rampManager;
  
  private final String _podNodeName;
  private final String _path;
  
  //private final ClassLoader _classLoaderSelf;
  
  private Supplier<ServiceManagerAmp> _podAppSupplier;

  // cached delegate and matching pod-app
  private ServiceRefAmp _delegate;
  private ServiceManagerAmp _podManagerDelegate;
  //private PodApp _podAppSelf;
  
  ServiceRefPodApp(ServiceManagerAmp manager,
                        Supplier<ServiceManagerAmp> podAppSupplier,
                        String podNodeName,
                        String path)
  {
    Objects.requireNonNull(manager);
    Objects.requireNonNull(podAppSupplier);
    
    _rampManager = manager;
    
    _podAppSupplier = podAppSupplier;
    _podNodeName = podNodeName;
    _path = path;
    
    //_podAppSelf = PodApp.getCurrent();
    //_classLoaderSelf = Thread.currentThread().getContextClassLoader();
  }
  
  protected Supplier<ServiceManagerAmp> getPodAppSupplier()
  {
    return _podAppSupplier;
  }
  
  protected ServiceManagerAmp getServiceManager()
  {
    return _rampManager;
  }
  
  protected String getPodNodeName()
  {
    return _podNodeName;
  }

  @Override
  public String address()
  {
    return "pod://" + _podNodeName + _path;
  }
  
  @Override
  public boolean isUp()
  {
    return true;
    
    //PodApp podApp = _podAppSupplier.get();

    //return podApp != null && ! podApp.isModified();
    // PodApp webApp = getWebApp();
    
    // WebAppController controller = getWebAppController();
    
    // XXX: timing issues on failover. This needs to happen
    // quicker or detect the starting state.
    
    //return control != null && control.getWebApp() != null;
    
    //    return control != null;
    
    // return true;
  }
  
  @Override
  public boolean isClosed()
  {
    try {
      ServiceRefAmp delegate = delegate();
      
      return delegate == null || delegate.isClosed();
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
      
      return false;
    }
  }

  @Override
  public void offer(MessageAmp message)
  {
    delegate().offer(new MessageWrapperClassLoader(message, getClassLoader()));
  }
  
  @Override
  public MethodRefAmp getMethod(String name)
  {
    return new MethodRefDelegate(name);
  }
  
  @Override
  public MethodRefAmp getMethod(String name, Type type)
  {
    return new MethodRefDelegateType(name, type);
  }
  
  private ClassLoader getClassLoader()
  {
    ServiceManagerAmp manager = _podAppSupplier.get();
    
    return manager.classLoader();
  }

  @Override
  protected ServiceRefAmp delegate()
  {
    ServiceManagerAmp podManager = _podAppSupplier.get();

    if (podManager == null) {
      ServerBartender server = BartenderSystem.getCurrentSelfServer();
      
      String serverId = server.getDisplayName() + "[" + server.getId() + "]";
      
      ServiceException exn;
      
      exn = new ServiceExceptionUnavailable(L.l("Can't find active pod-app for {0} on server {1}.\n"
                                                 + "Check log for any errors in the deployment.",
                                                 address(), serverId));
      
      exn.fillInStackTrace();
      
      return new ServiceRefException(manager(), serverId, exn);
      
      // return new ServiceRefNull(_rampManager, _hostName + _contextPath);
    }
    
    // XXX: check of podApp changed
    ServiceRefAmp delegate = _delegate;
    
    if (podManager != _podManagerDelegate) {
      delegate = null;
    }
    
    if (delegate != null) {
      return delegate;
    }
    
    /*
    if (podApp.getConfigException() != null) {
      ServiceException exn;
      
      exn = new ServiceExceptionIllegalState(L.l("pod-app failed to load {0} because of\n {1}",
                                                 getAddress(), podApp.getConfigException()));
      
      exn.fillInStackTrace();
      
      return new ServiceRefException(getManager(), getAddress(), exn);
    }
    */

    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(podManager.classLoader());
      
      ServiceRefAmp serviceRef = lookup(podManager, _path);

      _delegate = serviceRef;
      _podManagerDelegate = podManager;
      
      //serviceRef.start();
      
      return serviceRef;
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  protected ServiceRefAmp lookup(ServiceManagerAmp manager, String path)
  {
    ServiceRefAmp serviceRef = manager.service("local://" + path);

    if (serviceRef != null && ! serviceRef.isClosed()) {
      return serviceRef;
    }
    
    ServiceRefAmp serviceRefSession = manager.service("session://" + path);
    
    if (serviceRefSession != null && ! serviceRefSession.isClosed()) {
      return serviceRefSession;
    }
    
    return serviceRef;
  }
  
  @Override
  public ServiceRefAmp onLookup(String path)
  {
    String newPath;
    
    if (_path.equals("")) {
      newPath = path;
    }
    else {
      newPath = _path + path;
    }
    
    return createChild(newPath);
  }
  
  protected ServiceRefAmp createChild(String path)
  {
    return new ServiceRefPodApp(_rampManager,
                                     _podAppSupplier,
                                     getPodNodeName(),
                                     path);
  }
  
  private class MethodRefDelegate extends MethodRefWrapperClassLoader {
    private final String _name;
    private ServiceRefAmp _delegate;
    private MethodRefAmp _methodRef;
    
    MethodRefDelegate(String name)
    {
      _name = name;
    }
    
    @Override
    protected ClassLoader getDelegateClassLoader()
    {
      return getClassLoader();
    }

    @Override
    protected MethodRefAmp getDelegate()
    {
      ServiceRefAmp delegate = ServiceRefPodApp.this.delegate();
      
      if (delegate != _delegate) {
        _methodRef = delegate.getMethod(_name);
        _delegate = delegate;
      }
      
      return _methodRef;
    }
    
  }
  
  private class MethodRefDelegateType extends MethodRefWrapperClassLoader {
    private final String _name;
    private final Type _type;
    private ServiceRefAmp _delegate;
    private MethodRefAmp _methodRef;
    
    MethodRefDelegateType(String name, Type type)
    {
      _name = name;
      _type = type;
    }
    
    @Override
    protected ClassLoader getDelegateClassLoader()
    {
      return getClassLoader();
    }

    @Override
    protected MethodRefAmp getDelegate()
    {
      ServiceRefAmp delegate = ServiceRefPodApp.this.delegate();
      
      if (delegate != _delegate) {
        _methodRef = delegate.getMethod(_name, _type);
        _delegate = delegate;
      }
      
      return _methodRef;
    }
    
  }
}
