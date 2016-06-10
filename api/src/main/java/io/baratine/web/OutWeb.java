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

package io.baratine.web;

import java.io.OutputStream;
import java.io.Writer;

import io.baratine.io.Buffer;
import io.baratine.pipe.Credits;

public interface OutWeb
{
  OutWeb write(Buffer buffer);
  OutWeb write(byte []buffer, int offset, int length);
  
  OutWeb write(String value);
  OutWeb write(char []buffer, int offset, int length);
  
  OutWeb flush();
  
  Writer writer();
  
  OutputStream output();

  Credits credits();
  OutWeb push(OutFilterWeb outFilter);
  
  void halt();
  void halt(HttpStatus status);
  
  void fail(Throwable exn);
  
  public interface OutFilterWeb
  {
    default void header(RequestWeb request, String key, String value)
    {
      request.header(key, value);
    }
    
    default void type(RequestWeb request, String type)
    {
      request.type(type);
    }
    
    default void length(RequestWeb request, long length)
    {
      request.length(length);
    }
    
    void write(RequestWeb out, Buffer buffer);
    void ok(OutWeb out);
    
    default Credits credits(OutWeb out)
    {
      return out.credits();
    }
  }
}
