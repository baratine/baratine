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

package com.caucho.v5.http.protocol2;

import java.io.IOException;

import com.caucho.v5.amp.deliver.MessageDeliver;
import com.caucho.v5.io.WriteBuffer;


/**
 * InputStreamHttp reads a single HTTP frame.
 */
public class MessageHttp implements MessageDeliver<MessageHttp>
{
  /**
   * Deliver the message
   * 
   * @param os the physical output stream
   * @param header temp buffer for the frame header
   * @param writerHttp the writer context
   */
  public void deliver(WriteBuffer os,
                      OutHttp writerHttp)
    throws IOException
  {
    System.out.println("UNKNOWN-MESSAGE: " + this);
  }
}
