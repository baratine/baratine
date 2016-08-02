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

import io.baratine.config.Config;

/**
 * <p>Config for JdbcService.  Field names of this class (with the underscore
 * prefix stripped out) are the config options.  Only the <i>url</i> option is
 * required.</p>
 *
 * <pre><code>
 * String jdbcUrl = "jdbc:///foo";
 *
 * Web.property(jdbcUrl + ".url", "jdbc:mysql://localhost/myDb");
 * Web.property(jdbcUrl + ".user", "root");
 * Web.property(jdbcUrl + ".pass", "mypassword");
 * Web.property(jdbcUrl + ".poolSize", "64");
 * Web.property(jdbcUrl + ".testQueryBefore", "SELECT 1");
 * </code></pre>
 *
 */
public class JdbcConfig
{
  private String _url;
  private String _user;
  private String _pass;

  private int _poolSize = 128;

  private String _testQueryBefore;
  private String _testQueryAfter;

  public static JdbcConfig from(Config config, String id)
    throws Exception
  {
    JdbcConfig jdbcConfig = new JdbcConfig();

    config.inject(jdbcConfig, id);

    if (jdbcConfig.url() == null) {
      throw new Exception(id + ".url is not set");
    }

    return jdbcConfig;
  }

  public JdbcConfig url(String url)
  {
    _url = url;

    return this;
  }

  public String url()
  {
    return _url;
  }

  public JdbcConfig user(String user)
  {
    _user = user;

    return this;
  }

  public String user()
  {
    return _user;
  }

  public JdbcConfig pass(String pass)
  {
    _pass = pass;

    return this;
  }

  public String pass()
  {
    return _pass;
  }

  public JdbcConfig poolSize(int poolSize)
  {
    _poolSize = poolSize;

    return this;
  }

  public int poolSize()
  {
    return _poolSize;
  }

  public JdbcConfig testQueryBefore(String query)
  {
    _testQueryBefore = query;

    return this;
  }

  public String testQueryBefore()
  {
    return _testQueryBefore;
  }

  public JdbcConfig testQueryAfter(String query)
  {
    _testQueryAfter = query;

    return this;
  }

  public String testQueryAfter()
  {
    return _testQueryAfter;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[url=" + url()
                                      + ", user=" + user()
                                      + ", poolSize=" + poolSize()
                                      + "]";
  }
}
