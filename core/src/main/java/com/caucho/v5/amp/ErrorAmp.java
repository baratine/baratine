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

package com.caucho.v5.amp;

import io.baratine.service.ServiceExceptionExecution;
import io.baratine.service.ServiceExceptionIllegalArgument;
import io.baratine.service.ServiceExceptionIllegalState;
import io.baratine.service.ServiceExceptionMethodNotFound;
import io.baratine.service.ServiceExceptionNotFound;
import io.baratine.service.ServiceExceptionQueueFull;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Error for amp messages.
 */
@SuppressWarnings("serial")
public class ErrorAmp implements Serializable
{
  private static final HashMap<Class<?>,ErrorCodeAmp> _errorCodeMap
    = new HashMap<>();
    
  private final ErrorCodeAmp _code;
  private final String _message;
  private final Throwable _cause;
  
  private ErrorAmp()
  {
    _code = ErrorCodesAmp.UNKNOWN;
    _message = "unserialized";
    _cause = null;
  }
  
  public ErrorAmp(ErrorCodeAmp code,
                  String message)
  {
    _code = code;
    _message = message;
    _cause = null;
  }
  
  public ErrorAmp(ErrorCodeAmp code,
                  String message,
                  Throwable cause)
  {
    _code = code;
    _message = message;
    _cause = cause;
  }
  
  public ErrorCodeAmp getCode()
  {
    return _code;
  }

  public String getMessage()
  {
    return _message;
  }
  
  public Object getDetail()
  {
    return _cause;
  }
  
  public Throwable getCause()
  {
    return _cause;
  }
  
  public RuntimeException toException()
  {
    return new AmpException(this, getCause());
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _code + "," + _message + "]";
  }

  public static ErrorAmp create(Throwable e)
  {
    ErrorCodeAmp code = _errorCodeMap.get(e.getClass());
    
    if (code == null) {
      code = ErrorCodesAmp.UNKNOWN;
    }
    
    return new ErrorAmp(code, e.toString(), e);
  }
  
  static {
    _errorCodeMap.put(ServiceExceptionNotFound.class, 
                      ErrorCodesAmp.service_not_found);
    _errorCodeMap.put(ServiceExceptionIllegalArgument.class, 
                      ErrorCodesAmp.illegal_argument);
    _errorCodeMap.put(ServiceExceptionExecution.class, 
                      ErrorCodesAmp.execution_exception);
    _errorCodeMap.put(ServiceExceptionMethodNotFound.class, 
                      ErrorCodesAmp.method_not_found);
    _errorCodeMap.put(ServiceExceptionQueueFull.class, 
                      ErrorCodesAmp.queue_full);
    _errorCodeMap.put(ServiceExceptionIllegalState.class, 
                      ErrorCodesAmp.illegal_state);
  }
}
