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

package com.caucho.v5.ramp.jamp;

import io.baratine.web.RequestWeb;
import io.baratine.web.SessionContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.remote.ChannelServer;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * Broker for returning JAMP services.
 */
public class ChannelContextJampImpl implements SessionContext
{
  //private HashMap<Contextual<?>,Object> _beanMap;

  private HashMap<String, List<String>> _headers;

  // private HttpServletRequest _request;

  private ChannelServer _channelServer;

  private boolean _isSecure;
  
  // 
  // HTTP interface
  //
  
  public ChannelContextJampImpl(ChannelAmp channelBroker)
  {
    if (channelBroker instanceof ChannelServer) {
      _channelServer = (ChannelServer) channelBroker;
    }
  }

  // @Override
  public Map<String,List<String>> getHeaders()
  {
    return _headers;
  }
  
  // @Override
  public boolean isSecure()
  {
    return _isSecure;
  }

  public void initRequest(RequestWeb req)
  {
    _isSecure = req.secure() != null;
    
    HashMap<String,List<String>> headers = new HashMap<>();
    
    /*
    Enumeration<String> e = req.getHeaderNames();
    while (e.hasMoreElements()) {
      String name = e.nextElement();
      
      ArrayList<String> values = new ArrayList<>();
      
      Enumeration<String> valueEnum = req.getHeaders(name);
      while (valueEnum.hasMoreElements()) {
        values.add(valueEnum.nextElement());
      }
      
      headers.put(name.toLowerCase(), values);
    }
    */
    
    _headers = headers;
  }

  // @Override
  public void login(String user, String []roles)
  {
    // onLogin(user);
    _channelServer.onLogin(user);
  }

  // @Override
  public void logout()
  {
    // clearLogin();
  }
  
  //
  // cdi scope support
  //
  
  /*
  public <T> T get(Contextual<T> bean)
  {
    if (_beanMap == null) {
      return null;
    }
    
    return (T) _beanMap.get(bean);
  }
  
  public <T> void put(Contextual<T> bean, T value)
  {
    if (_beanMap == null) {
      _beanMap = new HashMap<>();
    }
    
    _beanMap.put(bean, value);
  }
  */
  
  public void shutdown(ShutdownModeAmp mode)
  {
    // super.shutdown(mode);
    
    //HashMap<Contextual<?>, Object> beanMap = _beanMap;
    //_beanMap = null;
    /*
    if (beanMap != null) {
      for (Map.Entry<Contextual<?>,Object> entry : beanMap.entrySet()) {
        closeBean(entry.getKey(), entry.getValue());
      }
    }
    */
  }
  
  /*
  private <T> void closeBean(Contextual<T> bean, Object value)
  {
    bean.destroy((T) value, null);
  }
  */
  
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
