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

package com.caucho.v5.log.impl;

import com.caucho.v5.util.L10N;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Proxy for an underlying handler, e.g. to handle different
 * logging levels.
 */
public class SubHandler extends Handler {
  private static final L10N L = new L10N(SubHandler.class);
  
  private Handler _handler;

  SubHandler(Handler handler)
  {
    _handler = handler;
  }

  /**
   * Publishes the record.
   */
  public void publish(LogRecord record)
  {
    if (! isLoggable(record))
      return;

    if (_handler != null)
      _handler.publish(record);
  }

  /**
   * Flushes the buffer.
   */
  public void flush()
  {
    if (_handler != null)
      _handler.flush();
  }

  /**
   * Closes the handler.
   */
  public void close()
  {
    if (_handler != null)
      _handler.close();

    _handler = null;
  }

  /**
   * Returns the hash code.
   */
  public int hashCode()
  {
    if (_handler == null)
      return super.hashCode();
    else
    return _handler.hashCode();
  }

  /**
   * Test for equality.
   */
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    else if (o == null)
      return false;

    if (_handler == null)
      return false;
    else if (o.equals(_handler))
      return true;

    if (! (o instanceof SubHandler))
      return false;

    SubHandler subHandler = (SubHandler) o;

    return _handler.equals(subHandler._handler);
  }

  public String toString()
  {
    return "SubHandler[" + _handler + "]";
  }
}
