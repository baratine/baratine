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

package io.baratine.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * Interface Part represents part in multipart/form-data request.
 *
 * e.g.
 *
 * <blockquote><pre>
 *   &#64;Post
 *   public void (&#64;Body Part[] parts, Result&lt;String&gt; result)
 *   {
 *     result.ok("received " + parts.length + " parts");
 *   }
 * </pre></blockquote>
 */
public interface Part
{
  /**
   * Content-Type header of the part
   * @return content type of the part
   */
  String contentType();

  /**
   * Returns header value for specified header name
   *
   * @param name
   * @return header string value
   */
  String header(String name);

  /**
   * All header values matching header name
   * @param name
   * @return {@code Collection} of header values
   */
  Collection<String> headers(String name);

  /**
   * All header names
   * @return {@code Collection} of header names
   */
  Collection<String> headerNames();

  /**
   * Name of the part
   * @return name
   */
  String name();

  /**
   * Client submitted file name of the part
   *
   * @return file name
   */
  String getFileName();

  /**
   * Size of the part
   * @return size of the part
   */
  long size();

  /**
   * {@code InputStream} with part's data
   *
   * @return {@code InputStream} containing part's data.
   * @throws IOException
   */
  InputStream data() throws IOException;
}
