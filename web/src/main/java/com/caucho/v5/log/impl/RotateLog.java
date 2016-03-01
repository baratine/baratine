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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import javax.annotation.PostConstruct;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.types.Bytes;
import com.caucho.v5.util.L10N;

/**
 * Configuration for a rotating log
 */
public class RotateLog
{
  private final static L10N L = new L10N(RotateLog.class);
  
  private Path _path;
  private String _pathFormat;
  private String _archiveFormat;
  
  private Duration _rolloverPeriod;
  private Bytes _rolloverSize;
  private int _rolloverCount = -1;
  
  private RotateStream _rotateStream;
  
  private String _timestamp;

  /**
   * Gets the output path.
   */
  public Path getPath()
  {
    return _path;
  }

  /**
   * Sets the output path.
   */
  public void setPath(Path path)
  {
    _path = path;
  }

  /**
   * Gets the output path.
   */
  public String getPathFormat()
  {
    return _pathFormat;
  }

  /**
   * Sets the output path.
   */
  public void setPathFormat(String path)
  {
    _pathFormat = path;
  }

  /**
   * Sets the output path (backward compat).
   */
  public void setHref(Path path)
  {
    setPath(path);
  }

  /**
   * Sets the rollover period.
   */
  public void setRolloverPeriod(Duration period)
  {
    _rolloverPeriod = period;
  }

  /**
   * Sets the rollover size.
   */
  public void setRolloverSize(Bytes size)
  {
    _rolloverSize = size;
  }

  /**
   * Sets the rollover size.
   */
  public void setRolloverSizeBytes(long size)
  {
    _rolloverSize = new Bytes(size);
  }

  /**
   * Sets the rollover count
   */
  public int getRolloverCount()
  {
    return _rolloverCount;
  }

  /**
   * Sets the rollover count.
   */
  public void setRolloverCount(int count)
  {
    _rolloverCount = count;
  }

  /**
   * Sets the timestamp
   */
  public String getTimestamp()
  {
    return _timestamp;
  }

  /**
   * Sets the timestamp.
   */
  /*
  public void setTimestamp(String timestamp)
  {
    _timestamp = timestamp;
  }
  */

  /**
   * Gets the archive format
   */
  public String getArchiveFormat()
  {
    return _archiveFormat;
  }

  /**
   * Sets the archive format.
   */
  public void setArchiveFormat(String format)
  {
    _archiveFormat = format;
  }

  /**
   * Returns the rotated stream.
   */
  public RotateStream getRotateStream()
  {
    return _rotateStream;
  }

  /**
   * Returns the tag name.
   */
  public String getTagName()
  {
    return "rotate-log";
  }

  /**
   * Initialize the log.
   */
  @PostConstruct
  public void init()
    throws ConfigException, IOException
  {
    if (_path != null) {
      _rotateStream = RotateStream.create(_path);
    }
    else if (_pathFormat != null) {
      _rotateStream = RotateStream.create(_pathFormat);
    }
    else {
      throw new ConfigException(L.l("'path' is a required attribute of <{0}>.  Each <{0}> must configure the destination stream.", getTagName()));
    }

    if (_path != null
        && Files.exists(_path)
        && ! Files.isReadable(_path)
        && (_rolloverPeriod != null
           || _rolloverSize != null
           || _archiveFormat != null)) {
      throw new ConfigException(L.l("log path '{0}' is not readable and therefore cannot be rotated.", _path.toUri()));
    }

    RolloverLogBase rolloverLog = _rotateStream.getRolloverLog();

    if (_rolloverPeriod != null) {
      rolloverLog.setRolloverPeriod(_rolloverPeriod);
    }

    if (_rolloverSize != null) {
      rolloverLog.setRolloverSize(_rolloverSize);
    }
    
    _rotateStream.setMaxRolloverCount(_rolloverCount);
    
    if (_archiveFormat != null) {
      rolloverLog.setArchiveFormat(_archiveFormat);
    }

    _rotateStream.init();
  }
}
