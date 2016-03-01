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

package com.caucho.v5.http.log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.http.protocol.RequestFacade;
import com.caucho.v5.loader.EnvLoader;

/**
 * Represents an log of every top-level request to the server.
 */
abstract public class AccessLogBase implements AccessLog, AutoCloseable
{
  private static final Logger log
    = Logger.getLogger(AccessLogBase.class.getName());
  
  public static final int BUFFER_SIZE = 64 * 1024;

  private Path _path;

  private String _pathFormat;

  //private AccessLogAdmin _admin;

  private boolean _isHostnameDnsLookup;

  protected AccessLogBase()
  {
    //_admin = new AccessLogAdmin(this);

    EnvLoader.addCloseListener(this);
  }

  /**
   * Returns the access-log's path.
   */
  public Path getPath()
  {
    return _path;
  }

  /**
   * Sets the access-log's path.
   */
  public void setPath(Path path)
  {
    _path = path;
  }

  /**
   * Returns the formatted path
   */
  public String getPathFormat()
  {
    return _pathFormat;
  }

  /**
   * Sets the formatted path.
   */
  public void setPathFormat(String pathFormat)
    throws ConfigException
  {
    _pathFormat = pathFormat;
  }

  /**
   * Sets the access-log's path (backwards compatibility).
   */
  public void setId(Path path)
  {
    setPath(path);
  }

  /**
   * The hostname-dns-lookup flag for Apache compatibility.
   */
  public boolean isHostnameDnsLookup()
  {
    return _isHostnameDnsLookup;
  }

  /**
   * The hostname-dns-lookup flag for Apache compatibility.
   */
  public void setHostnameDnsLookup(boolean enable)
  {
    _isHostnameDnsLookup = enable;
  }

  public int getBufferSize()
  {
    return 1024;
  }

  /*
  public void addInit(ContainerProgram init)
  {
    init.configure(this);
    ConfigContext.init(this);
  }
  */

  public boolean isAutoFlush()
  {
    return false;
  }

  /**
   * Initialize the log.
   * @throws IOException 
   */
  public void init() throws IOException
  {
  }

  /**
   * Logs a request using the current format.
   *
   * @param request the http request.
   * @param response the http response.
   */
  @Override
  public abstract void log(RequestFacade request);

  /**
   * Flushes the log.
   */
  public void flush()
  {
  }

  /**
   * Cleanup the log.
   */
  public void close()
    throws IOException
  {
    flush();
  }
}
