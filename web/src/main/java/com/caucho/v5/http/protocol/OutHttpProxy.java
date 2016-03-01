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

package com.caucho.v5.http.protocol;

import com.caucho.v5.io.TempBuffer;

/**
 * Proxy writer interface to the http response.
 */
public interface OutHttpProxy
{
  /**
   * Writes the first buffer for a http response. The first buffer
   * will trigger the headers to be written.
   * 
   * If the request is not the final request, free the buffer.
   * 
   * @param buffer the temp buffer with data
   * @param length the length of the data in the buffer
   * @param isEnd true for the final result
   */
  void writeFirst(OutHttp outHttp, TempBuffer head, long length, boolean isEnd);
  
  /**
   * Writes a following buffer for a http response.
   * 
   * If the request is not the final request, free the buffer.
   * 
   * @param buffer the temp buffer with data
   * @param isEnd true for the final result
   */
  void writeNext(OutHttp outHttp, TempBuffer head, boolean isEnd);
  
  /**
   * Disconnect the connection
   */
  void disconnect(OutHttp outHttp);
}
