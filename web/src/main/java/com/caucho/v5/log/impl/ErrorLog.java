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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.log.impl;

import java.nio.file.Path;
import java.time.Duration;
import java.util.logging.Level;

import com.caucho.v5.config.ConfigException;

/**
 * Configuration for the error-log pattern (backwards compat).
 */
public class ErrorLog {
  private LogConfig _logConfig = new LogConfig();
  
  public ErrorLog()
    throws ConfigException
  {
    _logConfig.addName("");
    _logConfig.setLevel(Level.INFO);
    _logConfig.setTimestamp("[%Y/%m/%d %H:%M:%S.%s] ");
  }
  
  /**
   * Sets the error log path (compat).
   */
  public void setId(Path path)
  {
    _logConfig.setPath(path);
  }
  
  /**
   * Sets the error log path (compat).
   */
  public void setTimestamp(String timestamp)
  {
    _logConfig.setTimestamp(timestamp);
  }
  
  /**
   * Sets the rotate period (compat).
   */
  public void setRolloverPeriod(Duration period)
  {
    _logConfig.setRolloverPeriod(period);
  }
  
  /**
   * Returns the tag name.
   */
  public String getTagName()
  {
    return "error-log";
  }
}

