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
import io.baratine.service.Service;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Clustered file system.
 */
public interface BfsFileSync extends BfsFile
{
  /**
   * Returns the <code>Status</code> (stat) of this file.  Use this method to
   * determine file size, check file existence, check file or directory, etc.
   *
   * @return the status of the file
   */
  Status getStatus();

  /**
   * Lists the files in this directory.
   *
   * @return the list of files
   */
  String []list();
  
  @Direct
  BfsFileSync lookup(String relativePath);

  /**
   * Opens this file for reading.
   *
   * @return an <code>InputStream</code> for this file
   */
  InputStream openRead();

  /**
   * Opens this file as a random-access file for reading
   *
   * @return a <code>BlobReader</code> for this file
   */
  BlobReader openReadBlob();

  /**
   * Opens this file for writing.
   *
   * @param options
   * @return an <code>OutputStream</code> for this file
   */
  OutputStream openWrite(WriteOption...options);
  //void openWrite(Result<OutputStream> result, WriteOption...options);

  /**
   * Deletes this file.
   */
  void remove();
  //void remove(Result<Boolean> result);
  
  /**
   * Copies to a destination file
   */
  boolean copyTo(String dstPath, WriteOption...options);
  
  /**
   * Rename to a destination file
   */
  boolean renameTo(String dstPath, WriteOption...options);

  /**
   * Watch this file for changes.
   *
   * @param watch
   */
  Cancel watch(@Service Watch watch);
}
