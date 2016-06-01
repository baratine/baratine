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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.caucho.v5.io.StreamSource;
import io.baratine.web.Part;

public class PartImpl implements Part
{
  private StreamSource _streamSource;
  private String _name;
  private String _fileName;
  private String _contentType;
  private long _size;
  private Map<String,List<String>> _headers;

  public PartImpl()
  {
  }

  void setData(StreamSource ss)
  {
    if (_streamSource != null)
      throw new IllegalStateException();

    _streamSource = ss;
  }

  void setName(String name)
  {
    _name = name;
  }

  void setFileName(String fileName)
  {
    _fileName = fileName;
  }

  void setContentType(String contentType)
  {
    _contentType = contentType;
  }

  void setSize(long size)
  {
    _size = size;
  }

  void setHeaders(Map<String,List<String>> headers)
  {
    _headers = headers;
  }

  @Override
  public String contentType()
  {
    return _contentType;
  }

  @Override
  public String header(String name)
  {
    List<String> headers = _headers.get(name);

    String header = null;

    if (headers != null || headers.size() > 0)
      header = headers.get(0);

    return header;
  }

  @Override
  public Collection<String> headers(String name)
  {
    return _headers.get(name);
  }

  @Override
  public Collection<String> headerNames()
  {
    return _headers.keySet();
  }

  @Override
  public String name()
  {
    return _name;
  }

  @Override
  public String getFileName()
  {
    return _fileName;
  }

  @Override
  public long size()
  {
    return _size;
  }

  @Override
  public InputStream data() throws IOException
  {
    if (_streamSource != null) {
      return getInputStreamFromStreamSource();
    }
    else {
      throw new IllegalStateException();
    }
  }

  private InputStream getInputStreamFromStreamSource() throws IOException
  {
    return _streamSource.openInputStream();
  }
}
