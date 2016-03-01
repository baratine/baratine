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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.vfs;

import java.io.IOException;
import java.util.Map;

import com.caucho.v5.io.StreamImpl;

public class ConstPath extends PathImpl
{
  private String _scheme = "const";
  private String _path = "";
  
  StreamImpl _stream;

  public ConstPath(PathImpl root, StreamImpl stream)
  {
    super(root);
    
    _stream = stream;
  }

  @Override
  public PathImpl schemeWalk(String userPath, Map<String,Object> attributes,
                         String path, int offset)
  {
    return this;
  }

  public String getURL()
  {
    return getScheme() + ":" + getPath();
  }

  public String getScheme()
  {
    return _scheme;
  }

  public void setScheme(String scheme)
  {
    _scheme = scheme;
  }

  public String getPath()
  {
    return _path;
  }

  public void setPath(String path)
  {
    _path = path;
  }

  public boolean exists()
  {
    return true;
  }

  public StreamImpl openReadImpl() throws IOException
  {
    return _stream;
  }

  public StreamImpl openWriteImpl() throws IOException
  {
    return _stream;
  }

  public StreamImpl openReadWriteImpl() throws IOException
  {
    return _stream;
  }

  public StreamImpl openAppendImpl() throws IOException
  {
    return _stream;
  }
}

