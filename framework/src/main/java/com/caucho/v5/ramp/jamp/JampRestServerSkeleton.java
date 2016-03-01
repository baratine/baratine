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

import io.baratine.service.MethodRef;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Objects;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.json.ser.JsonSerializerFactory;

/**
 * JampServlet responds to JAMP websocket connections.
 */
public class JampRestServerSkeleton
{
  private ServiceRefAmp _serviceRef;
  private final JsonSerializerFactory _factory;
  
  private HashMap<String,JampMethodRest> _methodMap = new HashMap<>();
    
  public JampRestServerSkeleton(ServiceRefAmp service,
                                JsonSerializerFactory factory)
  {
    Objects.requireNonNull(factory);
    
    _serviceRef = service;
    _factory = factory;
    
    /*
    for (MethodRef method : service.getMethods()) {
      buildMethod(_methodMap, method);
    }

    if (_methodMap.size() == 0) {
      for (MethodRef method : service.getMethods()) {
        JampMethodBuilder builder = new JampMethodBuilder(method);
        
        _methodMap.put(method.getName(), builder.build());
      }
    }
    */
  }
  
  protected JampMethodRest buildMethod(HashMap<String,JampMethodRest> methodMap,
                                       MethodRef method)
  {
    return null;
  }
  
  public JampMethodRest getMethod(String methodName)
  {
    JampMethodRest method = _methodMap.get(methodName);
    
    if (method == null || method.isClosed()) {
      MethodRefAmp methodRef = _serviceRef.getMethod(methodName);
      
      if (methodRef == null) {
        return null;
      }
      
      JampMethodBuilder builder = new JampMethodBuilder(methodRef);
      
      builder.setJsonFactory(_factory);
      
      //method = buildMethod(_methodMap, methodRef);
      // proxy method
      method = new JampMethodProxy(builder);
    }
    
    return method;
  }
  
  protected <T> T getAnnotation(Annotation []anns, Class<T> api)
  {
    if (anns == null) {
      return null;
    }
    
    for (Annotation ann : anns) {
      if (api.equals(ann.annotationType())) {
        return (T) ann;
      }
    }
    
    return null;
  }
}
