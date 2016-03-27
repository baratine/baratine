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

package com.caucho.v5.amp.service;

import java.lang.reflect.Type;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.inbox.InboxWrapper;
import com.caucho.v5.amp.manager.ServiceManagerAmpWrapper;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;

/**
 * Dynamic service ref that can handle the delegate changing.
 */
abstract public class ServiceRefDynamic extends ServiceRefWrapper
{
  private ServicesAmp _manager = new ServiceManagerDynamic();
  private InboxAmp _inbox = new InboxDynamic();
  
  @Override
  public ServicesAmp manager()
  {
    return _manager;
  }
  
  @Override
  public MethodRefAmp getMethod(String methodName)
  {
    return new MethodRefDynamic(this, methodName);
  }

  @Override
  public MethodRefAmp getMethod(String methodName, Type returnType)
  {
    return new MethodRefDynamic(this, methodName, returnType);
  }
  
  @Override
  public InboxAmp inbox()
  {
    return _inbox;
  }

  @Override
  public ServiceRefAmp lookup(String path)
  {
    return new ServiceRefDynamicChild(path);
  }
  
  private class ServiceRefDynamicChild extends ServiceRefDynamic {
    private String _path;
    
    ServiceRefDynamicChild(String path)
    {
      _path = path;
    }
    
    public ServiceRefAmp delegate()
    {
      return (ServiceRefAmp) ServiceRefDynamic.this.delegate().lookup(_path);
    }
  }
  
  private class ServiceManagerDynamic extends ServiceManagerAmpWrapper {
    @Override
    public ServicesAmp delegate()
    {
      return (ServicesAmp) ServiceRefDynamic.this.delegate().manager();
    }
    
    public InboxAmp getInbox()
    {
      return _inbox;
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + delegate() + "]";
    }
  }
  
  private class InboxDynamic extends InboxWrapper {
    @Override
    public InboxAmp delegate()
    {
      return ServiceRefDynamic.this.delegate().inbox();
    }
  }
  /*
  
  private class InboxServiceManagerDynamic extends InboxWrapper {
    @Override
    public InboxAmp getDelegate()
    {
      return ServiceRefDynamic.this.getDelegate().getManager().getSystemInbox();
    }
  }
  */
}
