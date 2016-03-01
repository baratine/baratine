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

package com.caucho.v5.bartender.files;

import io.baratine.db.BlobReader;

/**
 * Direct blob reader
 */
public class BlobReaderFile implements BlobReader
{
  private final BlobReader _blobReaderDb;
  private FileEntry _fileEntry;
  private long _writeSequence;
  
  BlobReaderFile(BlobReader blobReaderDb,
                 FileEntry entry)
  {

    _blobReaderDb = blobReaderDb;
    _fileEntry = entry;

    if (entry != null) {
      _writeSequence = entry.getWriteCount();
    }
  }

  @Override
  public long getLength()
  {
    return _blobReaderDb.getLength();
  }

  @Override
  public int read(long pos, byte[] buffer, int offset, int length)
  {
    return _blobReaderDb.read(pos, buffer, offset, length);
  }

  @Override
  public boolean isValid()
  {
    FileEntry entry = _fileEntry;
    
    return entry != null && _writeSequence == entry.getWriteCount();
  }

  @Override
  public void close()
  {
    _blobReaderDb.close();
  }
}
