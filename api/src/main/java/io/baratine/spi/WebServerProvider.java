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

package io.baratine.spi;

import java.lang.ref.SoftReference;
import java.util.ServiceLoader;
import java.util.WeakHashMap;

import io.baratine.client.ServiceClient.Builder;
import io.baratine.web.WebServerBuilder;

/**
 * Provider for AMP services.
 * Normally services and actors do not need to access their underlying messages.
 * 
 * There may be some special circumstances for debugging, logging, auditing, security where a service might need to access
 * this information.
 * 
 * Example:
 * 
 * <pre>
 * //Inside of a amp service
 * AmpProvider ampProvider = Amp.getProvider();
 * AmpMessage message = ampProvider.getCurrentMessage();
 * 
 * if (message.getType()==SEND) {
 *     //do something special
 * }
 * 
 * String securityToken = message.getHeaders().get("MY_SPECIAL_SECURITY_TOKEN");
 * </pre>
 */

abstract public class WebServerProvider implements AutoCloseable
{
  private static final WeakHashMap<ClassLoader,SoftReference<WebServerProvider>>
    _providerMap = new WeakHashMap<>();

  private static final WebServerProvider _systemProvider;

  private ClassLoader _classLoader;
  
  public WebServerProvider()
  {
    _classLoader = Thread.currentThread().getContextClassLoader();
  }

  public Builder newClient(String url)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  /**
   * Gets the current <code>ServiceManagerProvider</code> for the system.
   *
   * @return instance of <code>ServiceManagerProvider</code> for the system.
   */
  public static WebServerProvider current()
  {
    WebServerProvider systemProvider = _systemProvider;
    
    if (systemProvider != null) {
      return systemProvider;
    }
    
    Thread thread = Thread.currentThread();
    ClassLoader loader = thread.getContextClassLoader();
    
    return getProvider(loader);
  }
  
  public void close()
  {
    synchronized (_providerMap) {
      _providerMap.remove(_classLoader);
    }
  }
  
  private static WebServerProvider getProvider(ClassLoader loader)
  {
    synchronized (_providerMap) {
      SoftReference<WebServerProvider> providerRef = _providerMap.get(loader);
      WebServerProvider provider = null;

      if (providerRef != null) {
        provider = providerRef.get();
      }

      if (provider == null) {
        for (WebServerProvider serviceProvider
              : ServiceLoader.load(WebServerProvider.class, loader)) {
          provider = serviceProvider;
          break;
        }

        if (provider == null) {
          throw new UnsupportedOperationException(WebServerProvider.class.getName()
                                                  + " has no available providers in " + loader);
        }

        _providerMap.put(loader, new SoftReference<>(provider));
      }

      return provider;
    }
  }

  public WebServerBuilder webBuilder()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  static {
    WebServerProvider systemProvider = null;
    
    try {
      systemProvider = getProvider(WebServerProvider.class.getClassLoader());
    } catch (Throwable e) {
    }
    
    _systemProvider = systemProvider;
  }
}
