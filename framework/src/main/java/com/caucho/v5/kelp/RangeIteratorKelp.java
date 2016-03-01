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

package com.caucho.v5.kelp;

import io.baratine.service.Result;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;


/**
 * iterator across the btree.
 */
class RangeIteratorKelp implements Iterator<RowCursor>
{
  private final TableKelp _table;
  
  private final RowCursor _min;
  private final RowCursor _max;
  private final Predicate<RowCursor> _predicate;
  
  private final RowCursor _indexCursor;
  private final RowCursor _cursor;
    
  private PageLeaf _leaf;
    
  private boolean _isValid;
  private boolean _isIncrementRequired;
  
  private boolean _isData = true;
    
  RangeIteratorKelp(TableKelp table,
                    RowCursor min,
                    RowCursor max,
                    Predicate<RowCursor> predicate)
  {
    Objects.requireNonNull(predicate);
    
    _table = table;
    
    _min = min;
    _max = max;
    
    _predicate = predicate;
    
    _indexCursor = _table.cursor();
    _cursor = _table.cursor();
      
    _indexCursor.setKey(_min);

    _leaf = _table.getTableService().getLeafByCursor(_indexCursor);
  }
  
  void setData(boolean isData)
  {
    _isData = isData;
  }
    
  @Override
  public boolean hasNext()
  {
    while (true) {
      if (_leaf == null) {
        return false;
      }
      else if (_isValid) {
        return true;
      }

      if (_isIncrementRequired) {
        _isIncrementRequired = false;

        _indexCursor.incrementKey();

        if (_indexCursor.compareTo(_max) > 0) {
          _leaf = null;

          return false;
        }
      }

      _cursor.setKey(_max);

      _leaf = _leaf.first(_table, _indexCursor, _cursor);

      if (_leaf == null) {
        return false;
      }

      _isIncrementRequired = true;
      _indexCursor.setKey(_cursor);

      if ((! _isData || _cursor.isData()) && _predicate.test(_cursor)) {
        _isValid = true;

        return true;
      }
    }
  }

  @Override
  public RowCursor next()
  {
    if (! hasNext()) {
      return null;
    }
    
    _isIncrementRequired = true;
    _isValid = false;
    return _cursor;
  }

  @Override
  public void remove()
  {
    if (_isValid) {
      BackupKelp backup = null;
      Result<Boolean> result = null;
      
      _table.remove(_cursor, backup, result);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _predicate + "]";
  }
  
  static class PredicateTrue implements Predicate<RowCursor> {
    static final PredicateTrue TRUE = new PredicateTrue();

    @Override
    public final boolean test(RowCursor cursor)
    {
      return true;
    }
  }
}
