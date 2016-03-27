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

package com.caucho.v5.amp.vault;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.StubClass;
import com.caucho.v5.amp.stub.MethodAmpBase;

import io.baratine.service.OnLoad;
import io.baratine.service.OnSave;
import io.baratine.service.Result;

/**
 * Actor skeleton for a resource entity.
 */
public class StubAssetSolo extends StubClass
{
  private static final Logger log
    = Logger.getLogger(StubAssetSolo.class.getName());
  
  private VaultDriver<?, ?> _driver;

  public StubAssetSolo(ServicesAmp ampManager, 
                            Class<?> type,
                            ServiceConfig configService,
                            VaultDriver<?,?> driver)
  {
    super(ampManager, type, type);
    
    _driver = driver;
  }
  
  @Override
  public void introspect()
  {
    super.introspect();
    
    introspectOnLoad();
  }
  
  private void introspectOnLoad()
  {
    VaultDriver<?,?> driver = _driver;
    
    if (driver == null || ! driver.isPersistent()) {
      return;
    }
    
    try {
      if (! isImplemented(OnLoad.class)) {
        onLoad(new MethodOnLoadSolo(driver));
      }
      
      if (! isImplemented(OnSave.class)) {
        onSave(new MethodOnSaveSolo(driver));
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }
  
  private static class MethodOnLoadSolo extends MethodAmpBase
  {
    private VaultDriver<?,?> _driver;
    
    MethodOnLoadSolo(VaultDriver<?,?> driver)
    {
      _driver = driver;
    }
    
    @Override
    public String name()
    {
      return "@onLoad";
    }
    
    @Override
    public void query(HeadersAmp headers,
                      Result<?> result,
                      StubAmp actor)
    {
      Object bean = actor.bean();
    
      try {
        Serializable id = 0;
        
        ((VaultDriver) _driver).load(id, bean, result);
      } catch (Throwable e) {
        result.fail(e);
      }
    }
  }
  
  private static class MethodOnSaveSolo extends MethodAmpBase
  {
    private VaultDriver<?,?> _driver;
    
    MethodOnSaveSolo(VaultDriver<?,?> driver)
    {
      _driver = driver;
    }
    
    @Override
    public String name()
    {
      return "@OnSave";
    }
    
    @Override
    public void query(HeadersAmp headers,
                      Result<?> result,
                      StubAmp actor)
    {
      Object bean = actor.bean();
    
      try {
        Serializable id = 0;
        
        ((VaultDriver) _driver).save(id, bean, Result.ignore());
        
        result.ok(null);
      } catch (Throwable e) {
        result.fail(e);
      }
    }
  }
}
