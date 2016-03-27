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
import io.baratine.service.Service;

import java.util.function.Predicate;

import com.caucho.v5.amp.Direct;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.kelp.PageServiceSync.PutType;
import com.caucho.v5.kelp.query.EnvKelp;

/**
 * Write API to the write actor.
 */
public interface PageService
{
  @Direct
  void getDirect(RowCursor row, Result<Boolean> cont);
  
  void getSafe(RowCursor row, Result<Boolean> cont);
  
  void getStream(RowCursor cursor, Result<GetStreamResult> result);

  void put(RowCursor cursor, PutType type, Result<Boolean> cont);

  void putWithBackup(RowCursor cursor, 
                     PutType type,
                     @Service BackupKelp backupCb, 
                     Result<? super Boolean> cont);

  // void putStream(InputStream is, Result<Boolean> result);
  
  int writeBlob(int nextPid, 
                StreamSource ss,
                int offset,
                int length);

  void update(RowCursor minCursor, 
              RowCursor maxCursor, 
              EnvKelp envKelp,
              UpdateKelp update, 
              @Service BackupKelp backup, 
              Result<Integer> result);

  void replace(RowCursor cursor,
               EnvKelp envKelp,
               UpdateKelp update, 
               @Service BackupKelp backup, 
               Result<Integer> result);
  
  void remove(RowCursor cursor,
              @Service BackupKelp backup,
              Result<Boolean> cont);
  
  void removeRange(RowCursor min, 
                   RowCursor max, 
                   Predicate<RowCursor> predicate,
                   @Service BackupKelp backup,
                   Result<Boolean> cont);
  
  //
  // checkpoint
  //
  
  void checkpoint(Result<Boolean> cont);
  
  //
  // lifecycle
  //
  
  void start(Result<TableKelp> result);

  void flush(Result<Object> result);
  
  //
  // close
  //

  void close(Result<Boolean> result);
}
