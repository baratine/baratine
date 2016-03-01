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
 * The Amp operation timed out.
 */
public class ServiceExceptionQueryTimeout extends ServiceExceptionTimeout
{
  public ServiceExceptionQueryTimeout()
  {
  }
  
  public ServiceExceptionQueryTimeout(String msg)
  {
    super(msg);
  }
  
  public ServiceExceptionQueryTimeout(Throwable exn)
  {
    super(exn);
  }
  
  public ServiceExceptionQueryTimeout(String msg, Throwable exn)
  {
    super(msg, exn);
  }
  
  /**
   * Rethrows an exception to record the full stack trace, both caller
   * and callee.
   */
  @Override
  public ServiceExceptionQueryTimeout rethrow(String msg)
  {
    return new ServiceExceptionQueryTimeout(msg, this);
  }
}
