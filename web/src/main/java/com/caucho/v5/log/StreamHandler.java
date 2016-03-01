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

package com.caucho.v5.log;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.logging.Formatter;

import javax.annotation.PostConstruct;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.types.Bytes;
import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.log.impl.LogHandlerBase;
import com.caucho.v5.log.impl.RotateLog;

/**
 * Configures a log handler
 */
//@Configurable
public class StreamHandler extends LogHandlerBase
{
  private final RotateLog _pathLog = new RotateLog();

  private Formatter _formatter;
  //private String _timestamp;
  //private TimestampFilter _timestampFilter;

  private WriteBuffer _os;

  public StreamHandler(OutputStream os)
  {
    //_timestamp = "[%Y/%m/%d %H:%M:%S.%s] ";
    
    PatternFormatter formatter = new PatternFormatter();
    formatter.init();
    
    _formatter = formatter;
    
    _os = new WriteBuffer(os);
  }

  /**
   * Sets the path-format
   */
  public void setPathFormat(String pathFormat)
  {
    _pathLog.setPathFormat(pathFormat);
  }

  /**
   * Sets the archive-format
   */
  public void setArchiveFormat(String archiveFormat)
  {
    _pathLog.setArchiveFormat(archiveFormat);
  }

  /**
   * Sets the rollover-period
   */
  public void setRolloverPeriod(Duration rolloverPeriod)
  {
    _pathLog.setRolloverPeriod(rolloverPeriod);
  }

  /**
   * Sets the rollover-size
   */
  public void setRolloverSize(Bytes size)
  {
    _pathLog.setRolloverSize(size);
  }

  /**
   * Sets the rollover-count
   */
  public void setRolloverCount(int count)
  {
    _pathLog.setRolloverCount(count);
  }

  /**
   * Sets the timestamp.
   */
  /*
  public void setTimestamp(String timestamp)
  {
    _timestamp = timestamp;
  }
  
  @Override
  public TimestampFilter getTimestampFilter()
  {
    return _timestampFilter;
  }
  */

  /**
   * Sets the formatter.
   */
  @Override
  public void setFormatter(Formatter formatter)
  {
    _formatter = formatter;
  }
  
  @Override
  public Formatter getFormatter()
  {
    return _formatter;
  }

  /**
   * Initialize the log.
   */
  @PostConstruct
  public void init()
    throws ConfigException
  {
    try {
      //_pathLog.init();
      
      super.init();

      //WriteBuffer os = _pathLog.getRotateStream().getStream();

      /*
      if (_timestamp != null) {
        _timestampFilter = new TimestampFilter();
        _timestampFilter.setTimestamp(_timestamp);
      }
      */

      /*
      String encoding = System.getProperty("file.encoding");

      if (encoding != null) {
        os.setEncoding(encoding);
      }
      */

      //os.setDisableClose(true);

      //_os = os;
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
  }
  
  /*
  protected QueueService createQueue()
  {
    return _pathLog.getRotateStream().getQueue();
  }
  */
 
  @Override
  protected void deliverLog(String msg)
  {
    synchronized (_os) {
      try {
        _os.print(msg);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  @Override
  protected void processFlush()
  {
    synchronized (_os) {
      try {
        _os.flush();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Returns the hash code.
   */
  @Override
  public int hashCode()
  {
    /*
    if (_os == null || _os.getPath() == null) {
      return super.hashCode();
    }
    else {
      return _os.getPath().hashCode();
    }
    */
    return super.hashCode();
  }

  /**
   * Test for equality.
   */
  /*
  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    else if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PathHandler handler = (PathHandler) o;

    if (_os == null || handler._os == null)
      return false;
    else
      return _os.getPath().equals(handler._os.getPath());
  }
  */

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _pathLog + "]";
  }
}
