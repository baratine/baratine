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

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.stub.ParameterAmp;


/**
 * manages a module
 */
public class PodImport
{
  private final ClassLoader _exportLoader;
  private final ClassLoader _importLoader;

  //private ServiceManagerAmp _exportManager;
  private ServiceManagerAmp _importManager;

  private RampImport _rampArg;
  private RampImport _rampResult;
  
  public PodImport(ClassLoader exportLoader, 
                   ClassLoader importLoader)
  {
    _exportLoader = exportLoader;
    _importLoader = importLoader;
    
    if (importLoader == exportLoader) {
      throw new IllegalStateException();
    }
    
    //_exportManager = Amp.getContextManager(_exportLoader);
    
    // ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    
    _rampArg = new RampImport(importLoader, exportLoader);
    _rampResult = new RampImport(exportLoader, importLoader);
  }
  
  public RampImport getArg()
  {
    return _rampArg;
  }
  
  public RampImport getResult()
  {
    return _rampResult;
  }

  public ClassLoader getExportLoader()
  {
    return _exportLoader;
  }

  public ClassLoader getImportLoader()
  {
    return _importLoader;
  }

  public ServiceManagerAmp getImportManager()
  {
    if (_importManager == null) {
      _importManager = Amp.getContextManager(_importLoader);
    }
    
    return _importManager;
  }

  public ModuleMarshal[] marshalArgs(ParameterAmp[] sourceTypes)
  {
    return getArg().marshalArgs(sourceTypes);
  }

  /**
   * Create marshal to a target argument.
   */
  ModuleMarshal marshalArg(Class<?> argType)
  {
    return getArg().marshal(argType);
  }
  
  ModuleMarshal marshalArg(Class<?> callerType, 
                           Class<?> declaredCalleeType)
  {
    return getArg().marshal(callerType, declaredCalleeType);
  }

  public ModuleMarshal marshalResult(Class<?> type)
  {
    return getResult().marshal(type);
  }

  public ModuleMarshal marshalResult(Class<?> calleeType, Class<?> callerType)
  {
    if (callerType == null) {
      return getResult().marshal(calleeType);
    }
    else {
      return getResult().marshal(calleeType, callerType);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[ex:" + _exportLoader + ",im:" + _importLoader + "]";
  }
  
  enum MarshalType {
    ARG,
    RESULT;
  }
}
