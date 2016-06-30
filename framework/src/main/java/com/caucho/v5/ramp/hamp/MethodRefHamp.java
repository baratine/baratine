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

package com.caucho.v5.ramp.hamp;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.service.ServiceRefLazyInvalid;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.stub.ParameterAmp;
import com.caucho.v5.deploy2.DeployHandle2;
import com.caucho.v5.http.pod.PodContainer;
import com.caucho.v5.http.pod.PodLoader;

/**
 * Method ref for a hamp reader.
 */
public class MethodRefHamp
{
  private static final Logger log
    = Logger.getLogger(MethodRefHamp.class.getName());
  
  private static final MarshalHamp []NULL_ARGS = new MarshalHamp[0];
  
  private String _address;
  private String _methodName;
  private String _podCaller;

  private ChannelAmp _channelIn;
  private PodContainer _podContainer;
  
  private MethodRefAmp _delegate;
  private MarshalHamp []_args;
  private MarshalHamp _argTail;

  MethodRefHamp(String address,
                String methodName,
                String podCaller,
                ChannelAmp registryIn, 
                PodContainer podContainer)
  {
    _address = address;
    _methodName = methodName;
    _podCaller = podCaller;
    
    _channelIn = registryIn;
    _podContainer = podContainer;
    
    // lookup();
  }
  
  void lookup()
  {
    MethodRefAmp delegate = _delegate;
    
    if (delegate != null && ! delegate.isClosed()) {
      return;
    }
    
    _delegate = null;
    
    ServiceRefAmp serviceRef;
    try {
      serviceRef = _channelIn.service(_address);

      if (serviceRef.isClosed()) {
        serviceRef = new ServiceRefLazyInvalid(_channelIn.services(),
                                               _channelIn, _address);
      }

      delegate = serviceRef.methodByName(_methodName);
      
      introspect(delegate);
    } catch (Throwable e) {
      log.log(Level.FINE, e.toString(), e);

      serviceRef = new ServiceRefLazyInvalid(_channelIn.services(),
                                             _channelIn, _address);
      delegate = serviceRef.methodByName(_methodName);
      
      introspect(delegate);
    }
  }

  private void introspect(MethodRefAmp delegate)
  {
    Objects.requireNonNull(delegate);
    
    ParameterAmp []paramTypes = delegate.parameters();

    if (paramTypes == null) {
      _args = NULL_ARGS;
      _argTail = MarshalHampBase.IDENTITY;
    }
    else if (delegate.isVarArgs()) {
      _args = new MarshalHamp[paramTypes.length - 1];
      
      for (int i = 0; i < paramTypes.length - 1; i++){
        _args[i] = new MarshalHampType(paramTypes[i].rawClass());
      }
      
      Class<?> tailClass = paramTypes[paramTypes.length - 1].rawClass();
      
      _argTail = new MarshalHampType(tailClass.getComponentType());
    }
    else {
      _args = new MarshalHamp[paramTypes.length];
      
      for (int i = 0; i < paramTypes.length; i++){
        _args[i] = new MarshalHampType(paramTypes[i].rawClass());
      }
      _argTail = MarshalHampBase.IDENTITY;
    }
    
    _delegate = delegate;
  }
  
  boolean isVarArgs()
  {
    // baratine/a14e
    return _delegate.isVarArgs();
    //return false;
  }
  
  MethodRefAmp getMethod()
  {
    return _delegate;
  }
  
  ClassLoader getClassLoader()
  {
    ClassLoader serviceLoader;
    serviceLoader = getMethod().serviceRef().services().classLoader();
    
    if (_podCaller != null) {
      String podId;
      int p = _podCaller.indexOf('.');
      
      if (p > 0) {
        podId = "pods/" + _podCaller.substring(0, p);
      }
      else {
        podId = "pods/" + _podCaller;
      }
      
      DeployHandle2<PodLoader> handle;
      
      if (_podContainer != null) {
        handle = _podContainer.getPodLoaderHandle(podId);
      }
      else {
        handle = null;
      }

      if (handle != null) {
        PodLoader podLoader = handle.request();
        
        if (podLoader != null) {
          serviceLoader = handle.request().buildClassLoader(serviceLoader);
        }
      }
    }
    
    return serviceLoader;
  }

  MarshalHamp []getMarshalArg()
  {
    return _args;
  }
  
  MarshalHamp getMarshalTail()
  {
    return _argTail;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _delegate + ",argc=" + _args.length + "]";
  }
}
