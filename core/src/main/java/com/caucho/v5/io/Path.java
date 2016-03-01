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
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.io;

import java.io.IOException;


/**
 * A virtual filesystem path, essentially represented by a URL.
 * Its API resembles a combination of  the JDK File object and the URL object.
 *
 * <p>Paths are, in general, given with the canonical file separator of
 * forward slash, '/'.  The filesystems will take care of any necessary
 * translation.
 *
 * <p>Currently available filesystems:
 * <dl>
 * <dt>file:/path/to/file<dd>Java file
 * <dt>http://host:port/path/name?query<dd>HTTP request
 * <dt>tcp://host:port<dd>Raw TCP connection
 * <dt>mailto:user@host?subject=foo&cc=user2<dd>Mail to a user.
 * <dt>log:/group/subgroup/item<dd>Logging based on the configuration file.
 * <dt>stdout:<dd>System.out
 * <dt>stderr:<dd>System.err
 * <dt>null:<dd>The equivalent of /dev/null
 * </dl>
 */
public interface Path extends Source, Comparable<Path>
{
  boolean exists();

  String getNativePath();
  boolean remove() throws IOException;

  Path lookup(String string);

  String getURL();

  String getPath();
}
