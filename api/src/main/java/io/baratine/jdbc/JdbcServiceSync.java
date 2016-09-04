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

import java.sql.ResultSet;

/**
 * <p>Synchronous interface for JdbcService, primarily for testing.</p>
 *
 * <p><b>Note</b>: using a synchronous interface will block the caller, which
 * is generally a bad idea because it will block single-threaded services.</p>
 *
 * <pre></code>
 * {@literal @}Inject @Service("jdbc:///foo")
 * private JdbcServiceSync _service;
 * </code></pre>
 *
 * @see JdbcService
 */
public interface JdbcServiceSync extends JdbcService
{
  int execute(String sql, Object ... params);

  ResultSet query(String sql, Object ... params);

  <T> T query(SqlFunction<T> fun);

  JdbcStat stats();
}
