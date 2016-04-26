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
 */

package com.caucho.v5.kraken.table;

import java.util.ArrayList;
import java.util.List;

import io.baratine.db.Cursor;
import io.baratine.service.Result;

public class DatabaseKrakenImpl implements DatabaseKraken
{
  private KrakenImpl _kraken;

  DatabaseKrakenImpl(KrakenImpl kraken)
  {
    _kraken = kraken;
  }

  @Override
  public void execute(Result<Object> result, String sql, Object... params)
  {
    _kraken.exec(sql, params, result);
  }

  public void query(Result<ResultSetKraken> result,
                    String sql,
                    Object... params)
  {
    _kraken.findAll(sql, params, result.of(it -> {
      List<List<Object>> data = new ArrayList<>();

      for (Cursor cursor : it) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < cursor.getColumnCount(); i++)
          list.add(cursor.getObject(i + 1));

        data.add(list);
      }

      return new ResultSetKrakenImpl(data);
    }));
  }
}
