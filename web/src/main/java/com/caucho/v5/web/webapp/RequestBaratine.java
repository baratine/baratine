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

package com.caucho.v5.web.webapp;

import java.io.PrintWriter;
import java.io.Reader;
import java.util.List;

import com.caucho.v5.network.port.ConnectionProtocol;

import io.baratine.web.RequestWeb;

/**
 * Baratine facade for http requests.
 */
public interface RequestBaratine extends RequestWeb
{
  String uri();

  String method();
  
  String header(String key);
  
  // XXX: temp
  Reader getReader();
  
  /**
   * Starts duplex mode.
   */
  void upgrade(ConnectionProtocol conn);

  String addressRemote();
  //RequestBaratine status(int code);
  //RequestBaratine header(String key, String value);
  
  RequestBaratine contentLength(long length);
  
  //RequestBaratine write(String string);
  

  // sets an assigned view
  default void views(List<ViewRef<?>> views)
  {
    
  }
  
  // XXX: temp
  PrintWriter getWriter();

  void requestProxy(RequestProxy proxy);

  WebApp webApp();
  
  /*
  @Override
  default void handle(Buffer value, Throwable exn, boolean isEnd)
  {
    if (isEnd) {
      ok();
    }
    else if (exn != null) {
      fail(exn);
    }
    else {
      next(value);
    }
  }
  */
}
