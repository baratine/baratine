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

import java.util.Objects;

import com.caucho.v5.amp.proxy.ProxyHandleAmp;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.spi.StubContainerAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

import io.baratine.service.Result;

/**
 * Stub instance for a bean calls bean methods based on the ClassStub.
 */
public class StubAmpBean extends StubAmpBeanBase
{
  private Object _bean;
  private String _name;
  
  public StubAmpBean(StubClass skel,
                     Object bean,
                     String name,
                     StubContainerAmp container)
  {
    super(skel, name, container);
    
    Objects.requireNonNull(bean);
    
    if (bean instanceof ProxyHandleAmp) {
      throw new IllegalArgumentException(String.valueOf(bean));
    }
    
    _bean = bean;
    
    if (name == null) {
      name = "anon:" + bean().getClass().getSimpleName();
    }
    
    _name = name;
  }
  
  public StubAmpBean(StubClass stubClass,
                      Object bean,
                      ServiceConfig config)
  {
    this(stubClass, bean, config.name(), null);
  }

  @Override
  public String name()
  {
    return _name;
  }
  
  @Override
  public final Object bean()
  {
    return _bean;
  }
  
  @Override
  public void onInit(Result<? super Boolean> result)
  {
    stubClass().onInit(this, result);
    
    beforeBatch();
  }
  
  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
    // afterBatch();
    
    stubClass().shutdown(this, mode);
  }

  @Override
  public void beforeBatchImpl()
  {
    stubClass().beforeBatch(this);
  }
  
  @Override
  public void afterBatchImpl()
  {
    stubClass().afterBatch(this);
    
    afterBatchChildren();
  }
  
  @Override
  public int hashCode()
  {
    return bean().hashCode();
  }
  
  @Override
  public boolean equals(Object o)
  {
    if (! (o instanceof StubAmpBean)) {
      return false;
    }
    
    StubAmpBean actor = (StubAmpBean) o;
    
    return bean().equals(actor.bean());
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _bean + "]";
  }
}
