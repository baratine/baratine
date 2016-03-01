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

package com.caucho.v5.log.impl;

import java.util.ArrayList;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;

import com.caucho.v5.config.ConfigException;

/**
 * Environment-specific java.util.logging.Logger configuration.
 */
public class LoggerConfig {
  private Logger _logger;
  
  private ArrayList<String> _names = new ArrayList<>();
  private Level _level = null;
  private Boolean _useParentHandlers;
  
  private Filter _filter;
  
  private ArrayList<Handler> _handlerList
    = new ArrayList<Handler>();
  
  private boolean _isSkipInit;  
  
  public LoggerConfig()
  {
  }
  
  public LoggerConfig(boolean isSkipInit)
  {
    this();
    
    _isSkipInit = isSkipInit;
  }

  /**
   * Sets the name of the logger to configure.
   */
  //@ConfigRest
  public void addName(String name)
  {
    _names.add(name);
  }

  /**
   * Sets the use-parent-handlers
   */
  public void setUseParentHandlers(boolean useParentHandlers)
  {
    _useParentHandlers = new Boolean(useParentHandlers);
  }
  
  /**
   * Sets the output level.
   */
  //@ConfigArg(0)
  public void setLevel(Level level)
    throws ConfigException
  {
    _level = level;
  }
  
  void clearLevel()
  {
    _level = null;
  }
  
  
  public void add(Handler handler)
  {
    _handlerList.add(handler);
  }
  
  public void add(Filter filter)
  {
    _filter = filter;
  }

  /**
   * Initialize the logger
   */
  @PostConstruct
  public void init()
  {
    if (! _isSkipInit) {
      initImpl();
    }
  }
  
  /**
   * Should be run with system classloader
   */
  public void initImpl()
  {
    if (_level == null) {
      return;
    }
    
    if (_names.size() == 0) {
      _names.add("");
    }
    
    for (String name : _names) {
      _logger = Logger.getLogger(name);
    
      if (_level != null) {
        _logger.setLevel(_level);
      }

      if (_useParentHandlers != null) {
        _logger.setUseParentHandlers(_useParentHandlers.booleanValue());
      }
    
      for (Handler handler : _handlerList) {
        _logger.addHandler(handler);
      }
    
      if (_filter != null) {
        _logger.setFilter(_filter);
      }
    }
  }
}
