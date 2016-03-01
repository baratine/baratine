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

package com.caucho.v5.javac;

import java.io.*;

import com.caucho.v5.config.DisplayableException;
import com.caucho.v5.config.UserMessageLocation;
import com.caucho.v5.util.*;

/**
 * Wrapper for a compile error.
 */
public class CompileClassNotFound
  extends ClassNotFoundException
  implements UserMessageLocation, DisplayableException
{
  private Throwable _cause;

  /**
   * Create a CompileClassNotFound exception with a message.
   *
   * @param message the exception message
   */
  public CompileClassNotFound(String message)
  {
    super(message);
  }

  /**
   * Create a CompileClassNotFound exception wrapped around a root cause.
   *
   * @param cause the wrapped exception
   */
  public CompileClassNotFound(Exception cause)
  {
    super(cause.getMessage());
    
    _cause = cause;
  }

  /**
   * Returns the root cause of the exception.
   */
  public Throwable getRootCause()
  {
    return _cause;
  }

  /**
   * Returns the root cause of the exception.
   */
  public Throwable getCause()
  {
    return _cause;
  }

  public void print(PrintWriter out)
  {
    out.println(Html.escapeHtml(getMessage()));
  }
}



