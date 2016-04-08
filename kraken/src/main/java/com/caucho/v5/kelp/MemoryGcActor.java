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

import io.baratine.service.Service;

import com.caucho.v5.util.L10N;

/**
 * Actor for doing a segment garbage collection
 */
@Service
public class MemoryGcActor
{
  private static final L10N L = new L10N(MemoryGcActor.class);
  
  private TableKelp _table;
  private PageServiceSync _pageService;

  private boolean _isClosed;
  
  MemoryGcActor(TableKelp table)
  {
    _table = table;
    _pageService = table.getTableService();
  }
  
  public MemoryGcActor()
  {
  }

  public void collectPageMemory()
  {
    _pageService.collect();
  }
}
