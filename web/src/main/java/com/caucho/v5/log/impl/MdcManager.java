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

package com.caucho.v5.log.impl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.loader.EnvironmentLocal;

/**
 * Manages thread-context.
 */
public class MdcManager
{
  private static final Logger log = Logger.getLogger(MdcManager.class.getName());
  
  private static final ConcurrentHashMap<String,MdcClient> _mdcClientMap
    = new ConcurrentHashMap<>();
  
  private static final ConcurrentHashMap<String,MdcService> _mdcServiceMap
    = new ConcurrentHashMap<>();
    
  private static final EnvironmentLocal<MdcLocal> _mdcLocal
    = new EnvironmentLocal<>();
    
  private static final String []_mdcClassNames = new String[] {
    "org.slf4j.MDC",
    "org.apache.log4j.MDC",
  };
  
  public static MdcService get(String key)
  {
    MdcClient client = _mdcClientMap.get(key);
    
    if (client == null) {
      client = new MdcClient(key);
      
      _mdcClientMap.putIfAbsent(key, client);
      
      client = _mdcClientMap.get(key);
      
      MdcService service = _mdcServiceMap.get(key);
      
      if (service != null) {
        client.setService(service);
      }
    }
    
    return client;
  }
  
  public static void put(String key, MdcService service)
  {
    _mdcServiceMap.putIfAbsent(key, service);
    
    service = _mdcServiceMap.get(key);
    
    MdcClient client = _mdcClientMap.get(key);
    
    if (client != null) {
      client.setService(service);
    }
  }
  
  public interface MdcService
  {
    public String get();
  }
  
  private static class MdcClient implements MdcService
  {
    private String _key;
    private MdcService _service;
    
    MdcClient(String key)
    {
      _key = key;
    }
    
    public void setService(MdcService service)
    {
      _service = service;
    }
    
    @Override
    public String get()
    {
      MdcService service = _service;
      
      if (service != null) {
        return service.get();
      }
      
      MdcLocal local = _mdcLocal.getLevel();
      
      if (local == null) {
        local = new MdcLocal();
        _mdcLocal.set(local);
      }
      
      return local.get(_key);
    }
  }
  
  private static class MdcLocal
  {
    private MethodHandle _get;
    private MethodHandle _getAll;
    
    MdcLocal()
    {
      try {
        Class<?> mdcClass = getMdcClass();
      
        if (mdcClass != null) {
          MethodType getType = MethodType.methodType(Object.class,
                                                     String.class);
      
          _get = MethodHandles.lookup().findStatic(mdcClass, "get", getType);
        }
      } catch (Exception e) {
        System.out.println(getClass().getSimpleName() + ": " + e);
        // log.log(Level.FINER, e.toString(), e);
      }
    }
    
    private Class<?> getMdcClass()
    {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      
      for (String className : _mdcClassNames) {
        try {
          return Class.forName(className, false, loader);
        } catch (Exception e) {
        }
      }
      
      return null;
    }
    
    public String get(String key)
    {
      Object value = null;
      
      if (_get != null) {
        try {
          value = _get.invokeExact(key);
        } catch (Throwable e) {
          if (log.isLoggable(Level.FINER)) {
            e.printStackTrace();
          }
        }
      }
      
      if (value != null) {
        return String.valueOf(value);
      }
      else {
        return "";
      }
    }
  }
  
}
