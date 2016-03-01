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

/**
 * General AMP exception.
 */
public class ServiceException extends RuntimeException
{
  public ServiceException()
  {
  }

  public ServiceException(String msg)
  {
    super(msg);
  }

  public ServiceException(Throwable exn)
  {
    super(exn);
  }

  public ServiceException(String msg, Throwable exn)
  {
    super(msg, exn);
  }
  
  public String getCode()
  {
    String className = getClass().getSimpleName();
    
    if (className.startsWith(ServiceException.class.getSimpleName())) {
      String code = className.substring(ServiceException.class.getSimpleName().length());
      
      return toCode(code);
    }
    else {
      return "exception";
    }
  }
  
  private String toCode(String className)
  {
    StringBuilder sb = new StringBuilder();
    
    for (int i = 0; i < className.length(); i++) {
      char ch = className.charAt(i);
      
      if (Character.isUpperCase(ch) && i != 0) {
        sb.append('-');
      }
      
      sb.append(Character.toLowerCase(ch));
    }
    
    String code = sb.toString();
    
    if (code.length() > 0) {
      return code;
    }
    else {
      return "exception";
    }
  }

  public Throwable unwrap()
  {
    Throwable cause = this;

    while (cause instanceof ServiceException
           && cause.getCause() != null) {
      cause = cause.getCause();
    }

    return cause;
  }

  public static ServiceException createAndRethrow(Throwable exn)
  {
    ServiceException exn1;

    if (exn instanceof ServiceException) {
      exn1 = ((ServiceException) exn).rethrow();
    }
    else {
      exn1 = new ServiceExceptionExecution(exn);
    }

    exn1.fillInStackTrace();

    return exn1;
  }

  public static ServiceException createAndRethrow(String msg, Throwable exn)
  {
    ServiceException exn1;

    if (exn instanceof ServiceException) {
      exn1 = ((ServiceException) exn).rethrow(msg);
    }
    else {
      exn1 = new ServiceExceptionExecution(msg, exn);
    }

    exn1.fillInStackTrace();

    return exn1;
  }

  /**
   * Rethrows an exception to record the full stack trace, both caller
   * and callee.
   */
  public ServiceException rethrow()
  {
    return rethrow(getMessage());
  }

  public ServiceException rethrow(String msg)
  {
    ServiceException exn = new ServiceException(msg, this);

    exn.fillInStackTrace();

    return exn;
  }
}
