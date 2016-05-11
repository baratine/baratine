/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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
 */

package io.baratine.db;

import java.io.InputStream;

/**
 * Experimental async database.
 */
public interface Cursor
{
  long getVersion();
  
  long getUpdateTime();
  
  long getTimeout();
  
  int getInt(int index);
  
  long getLong(int index);
  
  double getDouble(int index);
  
  String getString(int index);
  
  Object getObject(int index);

  InputStream getInputStream(int i);

  byte[] getBytes(int i);

  BlobReader getBlobReader(int i);

  int getColumnCount();
}
