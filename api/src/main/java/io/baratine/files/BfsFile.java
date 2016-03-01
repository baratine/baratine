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

package io.baratine.files;

import io.baratine.db.BlobReader;
import io.baratine.service.Cancel;
import io.baratine.service.Direct;
import io.baratine.service.Result;
import io.baratine.service.Service;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Clustered file system.
 */
public interface BfsFile
{
  /**
   * Returns the <code>Status</code> (stat) of this file.  Use this method to
   * determine file size, check file existence, check file or directory, etc.
   *
   * @param result handle to return the status of the file
   */
  void getStatus(Result<Status> result);

  /**
   * Lookup a file relative to this path.
   *
   * @param relativePath
   * @return a <code>ServiceRef</code> proxy for the given path
   */
  @Direct
  BfsFile lookup(String relativePath);

  /**
   * Lists the files in this directory.
   *
   * @param result handle for the return the list of files
   */
  void list(Result<String[]> result);

  /**
   * Opens this file for reading.
   *
   * @param result an InputStream for this file
   */
  void openRead(Result<InputStream> result);

  /**
   * Opens this file for reading as a read blob.
   *
   * @param result an BlobReader for this file
   */
  void openReadBlob(Result<BlobReader> result);

  /**
   * Opens this file for writing.
   *
   * @param options
   * @param result an OutputStream for this file
   */
  void openWrite(Result<OutputStream> result, WriteOption...options);
  
  /**
   * Copies the file to a destination file
   */
  void copyTo(String relPath, Result<Boolean> result, WriteOption...options);
  
  /**
   * Renames the file to a destination file
   */
  void renameTo(String relPath, Result<Boolean> result, WriteOption...options);

  /**
   * Deletes this file.
   */
  void remove(Result<Boolean> result);

  /**
   * TODO
   * @param result
   */
  void removeAll(Result<Boolean> result);

  /**
   * Watch this file for changes.
   *
   * @param watch
   */
  void watch(@Service Watch watch, Result<Cancel> result);
}
