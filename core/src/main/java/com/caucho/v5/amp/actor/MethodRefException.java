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

package com.caucho.v5.amp.actor;

import io.baratine.service.Result;
import io.baratine.spi.Headers;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.util.L10N;

/**
 * Sender for an actor ref.
 */
public final class MethodRefException extends MethodRefError
{
  private static final Logger log
    = Logger.getLogger(MethodRefException.class.getName());
  private static final L10N L = new L10N(MethodRefException.class);
  private Throwable _exn;
  
  public MethodRefException(ServiceRefAmp serviceRef, 
                            String methodName,
                            Throwable exn)
  {
    super(serviceRef, methodName);
    
    _exn = exn;
    
    log.log(Level.FINER, exn.toString(), exn);
  }
  
  @Override
  public <T> void query(Headers headers,
                        Result<T> cb, 
                        long timeout, TimeUnit timeUnit,
                        Object... args)
  {
    cb.fail(_exn);
  }
}
