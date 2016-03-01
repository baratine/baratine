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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.config.impl;

import com.caucho.v5.config.UserMessage;

/**
 * Wrapper for checked exceptions found during configuration.
 * 
 * Generally should be replaced with a more informative exception.
 */
@SuppressWarnings("serial")
public class RuntimeExceptionConfig
  extends RuntimeException
{
  /**
   * Create a null exception
   */
  public RuntimeExceptionConfig()
  {
  }

  /**
   * Creates an exception with a message
   */
  public RuntimeExceptionConfig(String msg)
  {
    super(msg);
  }

  /**
   * Creates an exception with a message and throwable
   */
  public RuntimeExceptionConfig(String msg, Throwable e)
  {
    super(msg, e);
  }

  /**
   * Creates an exception with a throwable
   */
  public RuntimeExceptionConfig(Throwable e)
  {
    super(getMessage(e), e);
  }

  private static String getMessage(Throwable e)
  {
    if (e instanceof UserMessage) {
      return e.getMessage();
    }
    else {
      return e.toString();
    }
  }

  /*
  public void print(PrintWriter out)
  {
    out.println(Html.escapeHtml(getMessage()));
  }
  */
}
