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

package com.caucho.v5.h3.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import com.caucho.v5.h3.InH3;
import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.h3.OutH3;
import com.caucho.v5.h3.QueryBuilderH3;
import com.caucho.v5.h3.QueryH3;
import com.caucho.v5.h3.context.ContextH3Impl;
import com.caucho.v5.h3.query.QueryBuilderH3Impl;
import com.caucho.v5.h3.query.QueryH3Amp;
import com.caucho.v5.util.L10N;

/**
 * Factory for H3 input/output.
 */
public class OutFactoryH3Impl implements OutFactoryH3
{
  private static final L10N L = new L10N(OutFactoryH3Impl.class);
  
  private ContextH3Impl _context = new ContextH3Impl();
  private boolean _isGraph;
  
  OutFactoryH3Impl(OutFactoryBuilderH3Impl builder)
  {
    _isGraph = builder.isGraph();
  }
  
  /**
   * Adds a predefined schema
   */
  @Override
  public void schema(Class<?> type)
  {
    Objects.requireNonNull(type);
    
    _context.schema(type);
  }
  
  @Override
  public OutH3 out(OutputStream os)
  {
    Objects.requireNonNull(os);
    
    OutRawH3 outRaw = new OutRawH3Impl(os);
    
    if (_isGraph) {
      return new OutH3ImplGraph(_context, outRaw);
    }
    else {
      return new OutH3Impl(_context, outRaw);
    }
  }
  
  @Override
  public InH3 in(InputStream is)
  {
    Objects.requireNonNull(is);
    
    InRawH3 inRaw = new InRawH3Impl(is);
    
    return new InH3Impl(_context, inRaw);
  }

  @Override
  public QueryBuilderH3 newQuery()
  {
    return new QueryBuilderH3Impl(_context);
  }

  @Override
  public void query(InputStream is, QueryH3 query, Object []values)
  {
    Objects.requireNonNull(is);
    Objects.requireNonNull(query);
    Objects.requireNonNull(values);
    
    if (values.length != query.count()) {
      throw new IllegalArgumentException(L.l("value length {0} does not match query count {1}",
                                             values.length, query.count()));
    }
    
    QueryH3Amp queryAmp = (QueryH3Amp) query;
    
    try (InRawH3 inRaw = new InRawH3Impl(is)) {
      try (InH3Impl in = new InH3Impl(_context, inRaw)) {
        in.query(queryAmp, values);
      }
    }
  }
}
