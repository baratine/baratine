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

package com.caucho.v5.kelp.upgrade;

import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.kelp.Column.ColumnType;

/**
 * row information
 */
public class RowUpgrade
{
  private final String _name;
  private final ColumnUpgrade[] _columns;
  private final SchemeUpgrade[] _scheme;
  
  RowUpgrade(RowUpgradeBuilder builder)
  {
    _name = builder.name();
    
    _columns = builder.columns();
    _scheme = builder.scheme();
  }
  
  public String name()
  {
    return _name;
  }

  public int keyOffset()
  {
    return 0;
  }

  public int keyLength()
  {
    return 0;
  }
  
  public ColumnUpgrade []columns()
  {
    return _columns;
  }
  
  public SchemeUpgrade []scheme()
  {
    return _scheme;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + name() + "]";
  }
  
  public static class RowUpgradeBuilder
  {
    private String _name;
    private ArrayList<ColumnUpgrade> _columns = new ArrayList<>();
    private ArrayList<SchemeUpgrade> _scheme = new ArrayList<>();
    
    private int _rowLength;
    private int _keyStart;
    private int _keyEnd;
    
    public void name(String name)
    {
      Objects.requireNonNull(name);
      
      _name = name;
    }
    
    public String name()
    {
      return _name;
    }
    
    public ColumnUpgrade []columns()
    {
      ColumnUpgrade []columns = new ColumnUpgrade[_columns.size()];
      
      _columns.toArray(columns);
      
      return columns;
    }
    
    public SchemeUpgrade []scheme()
    {
      SchemeUpgrade []scheme = new SchemeUpgrade[_scheme.size()];
      
      _scheme.toArray(scheme);
      
      return scheme;
    }

    public int rowLength()
    {
      return _rowLength;
    }

    public RowUpgrade build()
    {
      return new RowUpgrade(this);
    }

    public ColumnUpgrade column(String name, 
                                ColumnType state,
                                int offset,
                                int length,
                                boolean isKey)
    {
      return new ColumnUpgrade(name, state, offset, length, isKey);
    }
    
    public void column(ColumnUpgrade column)
    {
      Objects.requireNonNull(column);
      
      _columns.add(column);
      
      _rowLength += column.length();
      
      if (! column.isKey()) {
        
      }
    }

    public RowUpgradeBuilder scheme(String name, 
                             String []fields)
    {
      _scheme.add(new SchemeUpgrade(name, fields));
      
      return this;
    }
  }
}
