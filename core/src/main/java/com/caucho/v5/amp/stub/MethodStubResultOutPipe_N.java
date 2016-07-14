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

package com.caucho.v5.amp.stub;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.util.L10N;

import io.baratine.pipe.PipePub;
import io.baratine.service.ResultChain;
import io.baratine.service.ServiceException;
import io.baratine.service.ServiceExceptionIllegalArgument;

/**
 * Creates MPC skeletons and stubs.
 */
class MethodStubResultOutPipe_N extends MethodStubResult_N
{
  private static final L10N L = new L10N(MethodStubResultOutPipe_N.class);
  private static final Logger log
  = Logger.getLogger(MethodStubResultOutPipe_N.class.getName());

  MethodStubResultOutPipe_N(ServicesAmp ampManager, Method method)
    throws IllegalAccessException
  {
    super(ampManager, method);
  }
  
  @Override
  protected Class<?> getResultClass()
  {
    return PipePub.class;
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object []args)
  {
    Object bean = actor.bean();
    
    if (log.isLoggable(Level.FINER)) {
      log.finest("amp-send to stream " + name() + "[" + bean + "] " + toList(args));
    }
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object []args)
  {
    Object bean = actor.bean();
    
    if (log.isLoggable(Level.FINER)) {
      log.finest("amp-send to stream " + name() + "[" + bean + "] " + toList(args));
    }
    
    ServiceException exn = new ServiceException(L.l(bean + "." + name() + " is a stream method"));
    exn.fillInStackTrace();
    
    result.fail(exn);
  }

  @Override
  public <T> void outPipe(HeadersAmp headers,
                          PipePub<T> result,
                          StubAmp actor,
                          Object []args)
  {
    Object bean = actor.bean();

    if (log.isLoggable(Level.FINEST)) {
      log.finest("stream " + name()+ "[" + bean + "] " + toList(args)
          + "\n  " + result);
    }
    
    try {
      methodHandle().invoke(bean, result, args);
    } catch (IllegalArgumentException e) {
      RuntimeException exn = new ServiceExceptionIllegalArgument(bean + "." + name() + ": " + e.getMessage(), e);
      exn.fillInStackTrace();
      
      result.fail(exn);
    } catch (ArrayIndexOutOfBoundsException e) {
      if (args.length + 1 != method().getParameterTypes().length) {
        String msg = bean + "." + method().getName() + ": " + e.getMessage() + " " + Arrays.asList(args);

        log.log(Level.FINE, msg, e);
        
        RuntimeException exn = new ServiceExceptionIllegalArgument(msg, e);
        
        result.fail(exn);
      }
      else {
        log.log(Level.FINEST, bean + ": " + e.toString(), e);
        
        result.fail(e);
      }
      
    } catch (Throwable e) {
      log.log(Level.FINEST, bean + ": " + e.toString(), e);

      result.fail(e);
    }
  }
}
