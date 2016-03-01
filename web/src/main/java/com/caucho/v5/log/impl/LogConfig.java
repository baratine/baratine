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

import java.nio.file.Path;
import java.time.Duration;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;

import javax.annotation.PostConstruct;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.types.Bytes;

/**
 * Environment-specific configuration.
 */
public class LogConfig
{
  private LogHandlerConfig _logHandlerConfig = new LogHandlerConfig();
  private LoggerConfig _loggerConfig = new LoggerConfig();
  
  private boolean _isSkipInit;

  public LogConfig()
  {
    _loggerConfig.clearLevel();
  }
  
  public LogConfig(boolean isSkipInit)
  {
    this();
    
    _isSkipInit = isSkipInit;
  }

  /**
   * Sets the name of the logger to configure.
   *
   * @deprecated Use setName()
   */
  public void setId(String name)
  {
    _logHandlerConfig.addName(name);
    _loggerConfig.addName(name);
  }

  /**
   * Sets the name of the logger to configure.
   */
  //@ConfigRest
  public void addName(String name)
  {
    _logHandlerConfig.addName(name);
    _loggerConfig.addName(name);
  }

  //@ConfigArg(1)
  public LogConfig setPath(Path path)
  {
    _logHandlerConfig.setPath(path);
    
    return this;
  }

  public void setRolloverPeriod(Duration period)
  {
    _logHandlerConfig.setRolloverPeriod(period);
  }

  public void setRolloverSize(Bytes bytes)
  {
    _logHandlerConfig.setRolloverSize(bytes);
  }

  public void setArchiveFormat(String format)
  {
    _logHandlerConfig.setArchiveFormat(format);
  }

  public void setPathFormat(String format)
  {
    _logHandlerConfig.setPathFormat(format);
  }
  
  public void setRolloverCount(int count)
  {
    _logHandlerConfig.setRolloverCount(count);
  }

  /**
   * Sets the mbean-name of the logger to configure.
   */
  public void setMbeanName(String name)
  {
    // _logHandlerConfig.setMbeanName(name);
    // _loggerConfig.setMbeanName(name);
  }

  /**
   * Sets the use-parent-handlers
   */
  public void setUseParentHandlers(boolean useParentHandlers)
    throws ConfigException
  {
    _logHandlerConfig.setUseParentHandlers(useParentHandlers);
    _loggerConfig.setUseParentHandlers(useParentHandlers);
  }

  /**
   * Sets the output level.
   */
  //@ConfigArg(0)
  public LogConfig setLevel(Level level)
    throws ConfigException
  {
    _logHandlerConfig.setLevel(level);
    _loggerConfig.setLevel(level);
    
    return this;
  }

  /**
   * Sets the timestamp.
   */
  public void setTimestamp(String timestamp)
  {
    _logHandlerConfig.setTimestamp(timestamp);
  }

  /**
   * A format string uses EL expressions and the EL variable `log', which is an
   * instance of LogRecord.
   */
  /*
  public void setFormat(RawString format)
  {
    _logHandlerConfig.setFormat(format);
  }
  */

  /**
   * A pattern string uses log4j expressions.
   */
  public LogConfig setPattern(String pattern)
  {
    _logHandlerConfig.setPattern(pattern);
    
    return this;
  }

  /**
   * Sets the formatter.
   */
  public void setFormatter(Formatter formatter)
  {
    _logHandlerConfig.setFormatter(formatter);
  }

  /**
   * Sets the formatter.
   */
  public void add(Formatter formatter)
  {
    _logHandlerConfig.setFormatter(formatter);
  }

  /**
   * Adds a handler
   */
  public void addHandler(Handler handler)
  {
    _logHandlerConfig.add(handler);
  }

  /**
   * Adds a handler
   */
  public void add(Handler handler)
  {
    _logHandlerConfig.add(handler);
  }

  /**
   * Adds a logger.
   */
  public void addLogger(LoggerConfig logger)
  {
  }

  /**
   * Initialize the log
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
    _logHandlerConfig.init();
    _loggerConfig.init();
  }

  /*
  class LogAdmin extends AbstractManagedObject implements LoggerMXBean {
    public String getType()
    {
      return "Logger";
    }

    public String getName()
    {
      return LogConfig.this.getName();
    }

    public String getLevel()
    {
      return LogConfig.this.getLevel();
    }

    public void setLevel(String level)
    {
      if (_subLogger != null) {
        _subLogger.setLevel(level);
        _subLogger.getLogger().setLevel(_subLogger.getLevel());
      }

      for (int i = 0; i < _subLoggers.size(); i++) {
        SubLogger subLogger = _subLoggers.get(i);
        
        subLogger.setLevel(level);
        subLogger.getLogger().setLevel(_subLogger.getLevel());
      }
    }
  }
  */
}
