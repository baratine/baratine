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

import io.baratine.service.ServiceRef;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;

/**
 * Handle to an amp instance.
 */
abstract public class ProxyUtilsAmp implements ServiceRef
{
  private final ServiceRefAmp _serviceRef;
  private final InboxAmp _systemMailbox;
  
  protected ProxyUtilsAmp(ServiceRefAmp serviceRef,
                             InboxAmp systemMailbox)
  {
    System.out.println("PUA: " + serviceRef + " " + this);
    _serviceRef = serviceRef;
    _systemMailbox = systemMailbox;
  }
  
  /*
  protected final RampMailbox __caucho_getCurrentContext()
  {
    return _systemMailbox.getCurrent();
  }
  */

  protected final ServiceRefAmp __caucho_getServiceRef()
  {
    return _serviceRef;
  }
  
  protected final MethodAmp __caucho_getMethod(String methodName)
  {
    return __caucho_getServiceRef().getMethod(methodName).method();
  }
  
  public static final MethodAmp __caucho_getMethod(ServiceRefAmp serviceRef,
                                                         String methodName)
  {
    return serviceRef.getMethod(methodName).method();
  }
  
  public static final MethodAmp __caucho_getMethod(ServiceRefAmp serviceRef,
                                                         String methodName,
                                                         Class<?> retType)
  {
    return serviceRef.getMethod(methodName, retType).method();
  }
  
  public static boolean __caucho_toBoolean(Object value)
  {
    return Boolean.TRUE.equals(value);
  }
  
  public static byte __caucho_toByte(Object value)
  {
    return ((Byte) value).byteValue();
  }
  
  public static short __caucho_toShort(Object value)
  {
    return ((Short) value).shortValue();
  }
  
  public static char __caucho_toChar(Object value)
  {
    return ((Character) value).charValue();
  }
  
  public static int __caucho_toInt(Object value)
  {
    return ((Integer) value).intValue();
  }
  
  public static long __caucho_toLong(Object value)
  {
    return ((Long) value).longValue();
  }
  
  public static float __caucho_toFloat(Object value)
  {
    return ((Float) value).floatValue();
  }
  
  public static double __caucho_toDouble(Object value)
  {
    return ((Double) value).doubleValue();
  }
  
  public static boolean equalsProxy(ServiceRefAmp serviceRef,
                                    Object value)
  {
    if (value == serviceRef) {
      return true;
    }
    else if (value == null || ! (value instanceof ProxyHandleAmp)) {
      return false;
    }
    
    ProxyHandleAmp proxy = (ProxyHandleAmp) value;
    
    return proxy.__caucho_getServiceRef().equals(serviceRef);
  }
  
  public static <T> T makeProxy(Object object,
                                Class<T> api,
                                InboxAmp inboxSystem)
  {
    if (object == null) {
      return null;
    }
    else if (object instanceof ProxyHandleAmp 
             && api.isAssignableFrom(object.getClass())) {
      return (T) object;
    }
    
    if (Object.class.equals(api)) {
      api = (Class<T>) object.getClass();
    }

    // XXX: possible issues with generic baratine/5041
    if (api.isSynthetic() && api.toString().indexOf("Lambda") >= 0) {
      Class<?> []apis = api.getInterfaces();
      
      if (apis.length > 0) {
        api = (Class) apis[0];
      }
    }
    
    InboxAmp caller = Amp.getCurrentInbox();

    if (caller != null && caller.manager() != null) {
      return (T) caller.serviceRef().pin(object).as(api);
    }
    else if (inboxSystem != null && inboxSystem.manager() != null) {
      return (T) inboxSystem.serviceRef().pin(object).as(api);
    }
    else {
      ServicesAmp manager = ServicesAmp.current();
      
      return (T) manager.inboxSystem().serviceRef().pin(object).as(api);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + __caucho_getServiceRef() + "]";
  }
}
