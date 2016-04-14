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

import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;

import io.baratine.service.ResultChain;

/**
 * Custom actor method.
 */
class MethodStubCustom extends MethodAmpBase
{
  private final StubAmp _actor;
  private final MethodRefAmp _ampMethod;
  
  MethodStubCustom(StubAmp actor,
                       MethodRefAmp ampMethod)
  {
    _actor = actor;
    _ampMethod = ampMethod;
  }

  public boolean isActive()
  {
    return _actor.isUp();
  }

  @Override
  public String name()
  {
    return _ampMethod.getName();
  }

  /*
  @Override
  public RampActor getActor()
  {
    return _actor;
  }
  */

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object []args)
  {
    _ampMethod.send(headers, args);
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object []args)
  {
    _ampMethod.query(headers, result, args);
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + name() + "," + _actor + "]";
  }
}
