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

package io.baratine.service;

import java.lang.reflect.Constructor;


/**
 * Wrapped execution exception.
 */
public class ServiceExceptionExecution extends ServiceException
{
  public ServiceExceptionExecution()
  {
  }
  
  public ServiceExceptionExecution(String msg)
  {
    super(msg);
  }
  
  public ServiceExceptionExecution(Throwable exn)
  {
    super(exn);
  }
  
  public ServiceExceptionExecution(String msg, Throwable exn)
  {
    super(msg, exn);
  }
  
  /**
   * Rethrows an exception to record the full stack trace, both caller
   * and callee.
   */
  @Override
  public ServiceExceptionExecution rethrow(String msg)
  {
    return new ServiceExceptionExecution(msg, this);
  }
  
  public static RuntimeException wrap(Throwable exn)
  {
    if (exn instanceof ServiceException) {
      return ((ServiceException) exn).rethrow();
    }
    else if (exn instanceof RuntimeException) {
      try {
        Constructor<?> ctor = exn.getClass().getConstructor(String.class,Throwable.class);
        
        return (RuntimeException) ctor.newInstance(exn.getMessage(), exn);
      } catch (Exception e) {
        return new ServiceExceptionExecution(exn);
      }
    }
    else {
      return new ServiceExceptionExecution(exn);
    }
  }
}
