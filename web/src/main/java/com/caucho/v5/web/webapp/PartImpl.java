/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Alex Rojkov
 */

package com.caucho.v5.web.webapp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import io.baratine.web.Part;

public class PartImpl implements Part
{
  private byte[] _data;

  public PartImpl(byte[] data)
  {
    _data = data;
  }

  @Override
  public String contentType()
  {
    return null;
  }

  @Override
  public String header(String header)
  {
    return null;
  }

  @Override
  public String[] headers(String header)
  {
    return new String[0];
  }

  @Override
  public String[] headers()
  {
    return new String[0];
  }

  @Override
  public String name()
  {
    return null;
  }

  @Override
  public long size()
  {
    return 0;
  }

  @Override
  public InputStream data()
  {
    return new ByteArrayInputStream(_data);
  }
}
