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

package com.caucho.v5.bartender.xa;

import java.lang.reflect.Type;
import java.util.Objects;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.service.ServiceRefWrapper;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;

/**
 * Wrapper for XA resources
 */
public class ServiceRefXA extends ServiceRefWrapper
{
  private ServiceRefAmp _delegate;
  private InboxXA _inbox;
  
  ServiceRefXA(ServiceRefAmp delegate)
  {
    Objects.requireNonNull(delegate);
    
    _delegate = delegate;
    _inbox = new InboxXA(delegate);
  }
  
  @Override
  protected ServiceRefAmp delegate()
  {
    return _delegate;
  }
  
  @Override
  public InboxAmp inbox()
  {
    return _inbox;
  }
  
  @Override
  public MethodRefAmp getMethod(String name)
  {
    MethodRefAmp methodRef = _delegate.getMethod(name);
    
    return new MethodRefXA(this, methodRef);
  }
  
  @Override
  public MethodRefAmp getMethod(String name, Type type)
  {
    MethodRefAmp methodRef = _delegate.getMethod(name, type);
    
    return new MethodRefXA(this, methodRef);
  }
  
  @Override
  public void offer(MessageAmp message)
  {
    message.invoke(_inbox, getActor());
  }
}
