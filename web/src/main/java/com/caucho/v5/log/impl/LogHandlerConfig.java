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
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.types.Bytes;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.log.PathHandler;
import com.caucho.v5.log.PatternFormatter;
import com.caucho.v5.util.L10N;

/**
 * Configuration for the <log-handler> tag.
 */
//@Configurable
public class LogHandlerConfig 
{
  private static final L10N L = new L10N(LogHandlerConfig.class);

  private ArrayList<String> _names = new ArrayList<>();
  private Level _level;
  
  private Formatter _formatter;
  
  private Filter _filter;

  private Handler _handler;
  
  private String _timestamp;
  private PathHandler _pathHandler;
  
  private boolean _isSkipInit;

  public LogHandlerConfig()
  {
  }

  public LogHandlerConfig(boolean isSkipInit)
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
    // backward compat
    if (name.equals("/"))
      name = "";
    
    addName(name);
  }
  
  //@ConfigRest
  public void addName(String name)
  {
    _names.add(name);
  }
  
  /*
  public String getName()
  {
    return _name;
  }
  */

  /**
   * Sets the path
   */
  //@ConfigArg(0)
  public void setPath(Path path)
  {
    getPathHandler().setPath(path);
  }

  /**
   * Sets the path-format
   */
  public void setPathFormat(String pathFormat)
  {
    getPathHandler().setPathFormat(pathFormat);
  }

  /**
   * Sets the archive-format
   */
  public void setArchiveFormat(String archiveFormat)
  {
    getPathHandler().setArchiveFormat(archiveFormat);
  }

  /**
   * Sets the rollover-period
   */
  public void setRolloverPeriod(Duration rolloverPeriod)
  {
    getPathHandler().setRolloverPeriod(rolloverPeriod);
  }

  /**
   * Sets the rollover-size
   */
  public void setRolloverSize(Bytes size)
  {
    getPathHandler().setRolloverSize(size);
  }

  /**
   * Sets the rollover-count
   */
  public void setRolloverCount(int count)
  {
    getPathHandler().setRolloverCount(count);
  }
  
  private PathHandler getPathHandler()
  {
    if (_pathHandler == null) {
      _pathHandler = new PathHandler();
    }
    
    return _pathHandler;
  }

  /**
   * Sets the use-parent-handlers
   */
  public void setUseParentHandlers(boolean useParentHandlers)
    throws ConfigException
  {
  }

  /**
   * Sets the output level.
   */
  public void setLevel(Level level)
    throws ConfigException
  {
    _level = level;
  }

  /**
   * Sets the output level.
   */
  public String getLevel()
  {
    if (_level != null)
      return _level.getName();
    else
      return Level.ALL.getName();
  }

  /**
   * Sets the timestamp.
   */
  public void setTimestamp(String timestamp)
  {
    _timestamp = timestamp;
  }

  /**
   * A format string uses EL expressions and the EL variable `log', which is an
   * instance of LogRecord.
   */
  /*
  public void setFormat(RawString format)
  {
    if (_formatter == null) {
      _formatter = new ELFormatter();
    }

    if (_formatter instanceof ELFormatter)
      ((ELFormatter)_formatter).setFormat(format);
  }
  */

  /**
   * A format string uses EL expressions and the EL variable `log', which is an
   * instance of LogRecord.
   */
  /*
  public String getFormat()
  {
    if (_formatter != null && _formatter instanceof ELFormatter)
      return ((ELFormatter)_formatter).getFormat();
    else
      return null;
  }
  */

  /**
   * Sets the formatter.
   */
  public void setFormatter(Formatter formatter)
  {
    _formatter = formatter;
  }
  
  public void add(Formatter formatter)
  {
    _formatter = formatter;
  }
  
  public void setPattern(String pattern)
  {
    if (! "".equals(pattern)) {
      _formatter = new PatternFormatter(pattern);
    }
  }

  /**
   * Sets the filter.
   */
  public void setFilter(Filter filter)
  {
    _filter = filter;
  }
  
  // @Configurable
  public void add(Handler handler)
  {
    _handler = handler;
  }

  /**
   * Initialize the log-handler
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
    throws ConfigException
  {
    if (_handler != null) {
    }
    else if (_pathHandler != null) {
    }
    else {
      setPath(Paths.get("."));
    }

    /*
    if (_formatter instanceof ELFormatter) {
      ((ELFormatter)_formatter).init();
    }
    */

    if (_formatter != null) {
    }
    else if (_handler != null && _handler.getFormatter() != null) {
    }
    else {
      _formatter = new PatternFormatter(PatternFormatter.DEFAULT_PATTERN);
    }
    
    if (_pathHandler != null) {
      _pathHandler.init();

      _handler = _pathHandler;
    }
    
    if (_handler == null) {
      throw new ConfigException(L.l("<log-handler> requires a configured log handler"));
    }
    
    // env/02s9
    if (_level != null) {
      _handler.setLevel(_level);
    }

    /* JDK defaults to Level.ALL
    if (_level != null)
      _handler.setLevel(_level);
    else
      _handler.setLevel(Level.INFO);
    */

    if (_formatter != null) {
      _handler.setFormatter(_formatter);
    }

    if (_filter != null) {
      _handler.setFilter(_filter);
    }
    
    if (_names.size() == 0) {
      _names.add("");
    }

    for (String name : _names) {
      Logger logger = Logger.getLogger(name);

      if (! (logger instanceof EnvironmentLogger)) {
        if (_handler instanceof AutoCloseable) {
          EnvLoader.addCloseListener((AutoCloseable) _handler);
        }
      }

      logger.addHandler(_handler);
    }
  }

  static Level toLevel(String level)
    throws ConfigException
  {
    try {
      return Level.parse(level.toUpperCase());
    } catch (Exception e) {
      throw new ConfigException(L.l("'{0}' is an unknown log level.  Log levels are:\noff - disable logging\nsevere - severe errors only\nwarning - warnings\ninfo - information\nconfig - configuration\nfine - fine debugging\nfiner - finer debugging\nfinest - finest debugging\nall - all debugging",
                                    level));
    }
  }
}
