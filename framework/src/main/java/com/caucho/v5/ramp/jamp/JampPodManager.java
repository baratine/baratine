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

package com.caucho.v5.ramp.jamp;

import io.baratine.service.ServiceRef;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.remote.ChannelManagerService;
import com.caucho.v5.amp.remote.ChannelManagerServiceImpl;
import com.caucho.v5.amp.remote.ChannelServerFactory;
import com.caucho.v5.amp.remote.ChannelServerFactoryImpl;
import com.caucho.v5.amp.remote.RegistryAmpServerShared;
import com.caucho.v5.amp.service.ServiceRefHandle;
import com.caucho.v5.amp.spi.LookupAmp;
import com.caucho.v5.amp.spi.RegistryAmp;
import com.caucho.v5.json.ser.JsonFactory;

/**
 * Manages the supported pods for the jamp servlet.
 */
public class JampPodManager
{
  private final ConcurrentHashMap<String,PodContext> _podMap
    = new ConcurrentHashMap<>();
  
  private ChannelManagerServiceImpl _sessionManager
    = new ChannelManagerServiceImpl();

  public JampPodManager()
  {
  }
  
  protected String getAuthority(String podName)
  {
    return "public://";
  }

  public ChannelManagerServiceImpl getSessionManager()
  {
    return _sessionManager;
  }
  
  PodContext getPodContext(String pathInfo)
  {
    String podName = getPodName(pathInfo);
    
    return getPodContextByName(podName);
  }
    
  PodContext getPodContextByName(String podName)
  {
    PodContext podContext = _podMap.get(podName);
    
    if (podContext == null) {
      PodContext newPodContext = createPodContext(podName);
      
      _podMap.putIfAbsent(podName, newPodContext);
      
      podContext = _podMap.get(podName);
    }
    
    return podContext;
  }
  
  protected String getPodName(String pathInfo)
  {
    return "";
  }
  
  protected ServicesAmp createAmpManager(String podName)
  {
    return Amp.getContextManager();
  }
  
  private PodContext createPodContext(String podName)
  {
    if (true) throw new UnsupportedOperationException();
    
    return null;
    /*
    ChannelServerJampFactory channelFactory;
    
    Supplier<ServiceManagerAmp> managerRef = new PodServiceManagerSupplier(podName);
    
    channelFactory = new ChannelServerJampFactory(managerRef, _sessionManager, podName);
    
    channelFactory.setAuthority(getAuthority(podName));
    
    ChannelServerFactoryImpl wsRegistryFactory;
    
    wsRegistryFactory = createRegistryFactory(managerRef, podName, _sessionManager);
    
    JsonSerializerFactory jsonFactory = createJsonFactory();
    
    return new PodContext(podName,
                          managerRef,
                          channelFactory,
                          wsRegistryFactory,
                          jsonFactory);
                          */
  }
  
  private JsonFactory createJsonFactory()
  {
    JsonFactory jsonFactory = new JsonFactory();
    
    jsonFactory.addSerializer(ServiceRefHandle.class,
                              new JsonSerializerServiceRef());
    
    jsonFactory.addSerializer(ServiceRef.class,
                                new JsonDeserializerServiceRef());
    
    return jsonFactory;
  }
  
  private ChannelServerFactoryImpl
  createRegistryFactory(Supplier<ServicesAmp> manager, 
                        String podName,
                        ChannelManagerService sessionManager)
  {
    return new ChannelServerFactoryImpl(manager, sessionManager, podName);
  }

  public class PodContext
  {
    private final String _podName;
    private final Supplier<ServicesAmp> _ampManagerRef;
    private final RegistryAmp _registryShared;
    //private final ChannelServerJampFactory _sessionRegistryFactory;
    private final ChannelServerFactoryImpl _wsBrokerFactory;
    private final JsonFactory _jsonFactory;
    private final int _podIndex;
    private final QueueJampUnpark _inboxUnpark;
    
    
    PodContext(String podName,
               Supplier<ServicesAmp> ampManagerRef,
               //ChannelServerJampFactory channelRegistryFactory,
               ChannelServerFactoryImpl wsBrokerFactory,
               JsonFactory jsonFactory)
    {
      _podName = podName;
      _ampManagerRef = ampManagerRef;
      //_sessionRegistryFactory = channelRegistryFactory;
      _wsBrokerFactory = wsBrokerFactory;
      _jsonFactory = jsonFactory;
      
      _registryShared = new RegistryAmpServerShared(ampManagerRef, podName);
      
      _inboxUnpark = new QueueJampUnpark();
      
      int p = podName.indexOf(':');
      int podIndex = -1;
      
      if (p > 0) {
        podIndex = Integer.parseInt(podName.substring(p + 1));
      }
      else {
        podIndex = -1;
      }
      
      _podIndex = podIndex;
    }
    
    public boolean isLocal()
    {
      return getAmpManager() != null;
    }

    QueueJampUnpark getUnparkQueue()
    {
      return _inboxUnpark;
    }

    /*
    public boolean isModified()
    {
      ServiceManagerAmp ampManager = _ampManager;
      
      return ampManager == null || ampManager.isClosed();
    }
    */

    public LookupAmp getLookup()
    {
      return _registryShared;
    }

    public JsonFactory getJsonFactory()
    {
      return _jsonFactory;
    }

    /*
    public ChannelServerJampFactory getSessionRegistryFactory()
    {
      return _sessionRegistryFactory;
    }
    */

    public ChannelServerFactory getWsRegistryFactory()
    {
      return _wsBrokerFactory;
    }

    public String getPodName()
    {
      return _podName;
    }
    
    public int getPodIndex()
    {
      return _podIndex;
    }

    public ServicesAmp getAmpManager()
    {
      return _ampManagerRef.get();
    }

    public String getServicePath(String pathInfo)
    {
      int len = _podName.length();
      
      if (len > 0) {
        return pathInfo.substring(len + 1);
      }
      else {
        return pathInfo;
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _podName + "]";
    }
  }
  
  private class PodServiceManagerSupplier implements Supplier<ServicesAmp> {
    private final String _podName;
    private ServicesAmp _manager;
    
    PodServiceManagerSupplier(String podName)
    {
      _podName = podName;
    }
    
    @Override
    public ServicesAmp get()
    {
      ServicesAmp manager = _manager;
      
      if (manager != null && ! manager.isClosed()) {
        return manager;
      }
      
      manager = _manager = createAmpManager(_podName);
      
      return manager;
    }
  }
}
