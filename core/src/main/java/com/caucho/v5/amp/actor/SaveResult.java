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

package com.caucho.v5.amp.actor;

import io.baratine.service.Result;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Counter/marker for handling multiple saves, e.g. with multiple resources.
 */
public class SaveResult
{
  private static final Logger log
    = Logger.getLogger(SaveResult.class.getName());
  
  private final Result<Boolean> _result;
  
  private int _beanCount;
  private int _saveCount;
  
  public SaveResult(Result<Boolean> result)
  {
    Objects.requireNonNull(result);
    
    _result = result;
    _beanCount = 1;
  }
  
  public Result<Boolean> addBean()
  {
    _beanCount++;
    
    return new ResultSave();
  }
  
  public void completeBean()
  {
    _saveCount++;
    
    if (_beanCount <= _saveCount) {
      _result.ok(true);
    }
  }
  
  private class ResultSave implements Result<Boolean>
  {
    private boolean _isDone;

    @Override
    public void handle(Boolean value, Throwable exn)
    {
      if (! _isDone) {
        _isDone = true;
        
        completeBean();
      }
      
      if (exn != null && log.isLoggable(Level.FINER)) {
        log.log(Level.FINER, exn.toString(), exn);
      }
    }
  }
}
