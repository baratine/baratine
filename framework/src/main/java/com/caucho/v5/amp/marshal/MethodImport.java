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

package com.caucho.v5.amp.marshal;

import java.util.Objects;

import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.MethodAmpBase;
import com.caucho.v5.amp.stub.ParameterAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.service.Result;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class MethodImport extends MethodAmpBase
{
  private final ServiceRefImport _serviceRef;
  private final MethodAmp _delegate;
  
  private PodImport _moduleImport;
  private final ModuleMarshal [] _marshal;
  private final ModuleMarshal _resultMarshal;
  private ParameterAmp[] _paramTypes;
  private Class<?> _returnType;

  public MethodImport(ServiceRefImport serviceRef,
                      MethodAmp method,
                      Class<?> retType)
  {
    Objects.requireNonNull(method);
    
    _serviceRef = serviceRef;
    _delegate = method;
    
    _moduleImport = serviceRef.getModuleImport();
    Objects.requireNonNull(_moduleImport);
    
    _marshal = _moduleImport.getArg().marshalArgs(delegate().parameters());
    _paramTypes = _moduleImport.getArg().marshalParamTypes(delegate().parameters());
    
    _resultMarshal = _moduleImport.marshalResult(delegate().getReturnType(),
                                                 retType);
    
    _returnType = _moduleImport.getResult().marshalType(delegate().getReturnType());
  }

  public MethodAmp delegate()
  {
    return _delegate;
  }
  
  @Override
  public ParameterAmp []parameters()
  {
    return _paramTypes;
  }
  
  @Override
  public Class<?> getReturnType()
  {
    return _returnType;
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object []args)
  {
    Object []newArgs = new Object[args.length];
    
    int sublen = Math.min(newArgs.length, _marshal.length);
    
    for (int i = 0; i < sublen; i++) {
      newArgs[i] = _marshal[i].convert(args[i]);
    }
    
    for (int i = sublen; i < newArgs.length; i++) {
      // should be a generic?
      newArgs[i] = args[i];
    }
    
    delegate().send(headers, actor, newArgs);
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    StubAmp actor,
                    Object []args)
  {
    Object []newArgs = new Object[args.length];
    
    int sublen = Math.min(newArgs.length, _marshal.length);
    
    for (int i = 0; i < sublen; i++) {
      newArgs[i] = _marshal[i].convert(args[i]);
    }
    
    for (int i = sublen; i < newArgs.length; i++) {
      // should be a generic?
      newArgs[i] = args[i];
    }

    ClassLoader loader = _moduleImport.getImportLoader();
    
    ResultImport queryRefImport
      = new ResultImport(result, _resultMarshal, loader);
    
    delegate().query(headers, queryRefImport, actor, newArgs);
  }
}
