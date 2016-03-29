/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package com.caucho.v5.jdbc;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.Services;
import io.baratine.service.ServiceRef;

@Service
public class JdbcServiceImpl implements JdbcService
{
  private Logger _logger = Logger.getLogger(JdbcServiceImpl.class.toString());

  private HashMap<UrlAndProps,ServiceRef> _poolMap = new HashMap<>();

  @Inject
  private Services _manager;

  @Override
  public void connect(String url, Properties props, Result<JdbcConnection> result)
  {
    ServiceRef ref = _manager.newService(JdbcConnectionImpl.class).start();

    if (_logger.isLoggable(Level.FINER)) {
      _logger.finer("connect: " + url + "," + ref);
    }

    JdbcConnection conn = ref.as(JdbcConnection.class);

    conn.connect(url, props, result);
  }

  @Override
  public JdbcConnectionSync connectSync(String url, Properties props)
    throws SQLException
  {
    ServiceRef ref = _manager.newService(JdbcConnectionImpl.class).start();

    if (_logger.isLoggable(Level.FINER)) {
      _logger.finer("connectSync: " + url + "," + ref);
    }

    JdbcConnectionSync conn = ref.as(JdbcConnectionSync.class);

    return conn.connectSync(url, props);
  }

  @Override
  public void autoCreatePool(String url, Properties props, Result<JdbcConnection> result)
  {
    ServiceRef ref = getPool(url, props);

    if (_logger.isLoggable(Level.FINER)) {
      _logger.finer("createPool: " + url + "," + ref);
    }

    JdbcConnection conn = ref.as(JdbcConnection.class);

    conn.connect(url, props, result);
  }

  @Override
  public JdbcConnectionSync autoCreatePoolSync(String url, Properties props)
    throws SQLException
  {
    ServiceRef ref = getPool(url, props);

    if (_logger.isLoggable(Level.FINER)) {
      _logger.finer("createPoolSync: " + url + "," + ref);
    }

    JdbcConnectionSync conn = ref.as(JdbcConnectionSync.class);

    return conn.connectSync(url, props);
  }

  private ServiceRef getPool(String url, Properties props)
  {
    UrlAndProps id = new UrlAndProps(url, props);

    ServiceRef ref = _poolMap.get(id);

    if (ref == null) {
      ref = _manager.newService(JdbcConnectionPoolImpl.class).start();

      _poolMap.put(id, ref);
    }

    return ref;
  }

  static class UrlAndProps {
    private String _url;
    private Properties _props;

    public UrlAndProps(String url, Properties props)
    {
      _url = url;
      _props = props;
    }

    @Override
    public int hashCode()
    {
      return _url.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
      UrlAndProps entry = (UrlAndProps) obj;

      return _url.equals(entry._url) && _props.equals(entry._props);
    }
  }
}
