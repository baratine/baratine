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

package com.caucho.v5.store.io;

import java.io.IOException;

/**
 * Filesystem access for a random-access store.
 * 
 * The store is designed around a single writer thread and multiple
 * reader threads. When possible, it uses mmap.
 */
public interface StoreReadWrite
{
  final static int FILE_SIZE_INCREMENT = 8 * 1024 * 1024;
  
  /**
   * Returns the file size.
   */
  long fileSize();
  
  /**
   * Returns the alignment size for mmaps.
   */
  long getChunkSize();
  
  long getMmapCloseTimeout();
  
  void create()
    throws IOException;

  void init()
    throws IOException;
  
  /**
   * Opens the underlying file to the database.
   */
  InStore openRead(long offset, int size);

  /**
   * Opens the underlying file to the database.
   */
  OutStore openWrite(long offset, int size);
  
  // used in QA
  void fsync();
  
  void close();

}
