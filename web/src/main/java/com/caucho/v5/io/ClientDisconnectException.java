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

package com.caucho.v5.io;

import java.io.IOException;

/**
 * Exception thrown when a client unexpectedly closes a connection.
 * Generally this is a broken pipe exception, but unfortunately, java.io.*
 * doesn't have a specific BrokenPipeException.
 */
@SuppressWarnings("serial")
public class ClientDisconnectException extends IOException {
  // force instantiation for JNI
  public final static ClientDisconnectException EXN
    = new ClientDisconnectException();
  
  public ClientDisconnectException()
  {
  }
  
  public ClientDisconnectException(String msg)
  {
    super(msg);
  }
  
  public ClientDisconnectException(Exception exn)
  {
    super(exn);
  }
  
  public ClientDisconnectException(String msg, Exception exn)
  {
    super(msg, exn);
  }

  /**
   * Only create a disconnect exception if it's an IOException
   * Possible later check for broken pipe.
   */
  public static IOException create(IOException exn)
  {
    return create(exn.getMessage(), exn);
  }

  /**
   * Only create a disconnect exception if it's an IOException
   * Possible later check for broken pipe.
   */
  public static IOException create(String msg, IOException exn)
  {
    if (exn.getClass().equals(IOException.class)
        || exn.getClass().equals(java.net.SocketException.class)
        || exn.getClass().getName().equals("javax.net.ssl.SSLException")) {
      return new ClientDisconnectException(msg, exn);
    }
    else
      return exn;
  }
}
