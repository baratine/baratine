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

package io.baratine.pipe;

import java.util.Map;

/**
 * General message type for pipes.
 * 
 * This Message API is designed to improve interoperability by providing a 
 * useful default API. While pipes can use any message type, general messages 
 * drivers like an AMQP broker or a mail sender need to choose one of 
 * their own. 
 * 
 * Applications may be better served by using application-specific messages.
 */
public interface Message<T>
{
  T value();
  
  Map<String,Object> headers();
  
  Object header(String key);
  
  static <X> MessageBuilder<X> newMessage(X value)
  {
    return new MessageImpl<>(value);
  }
  
  public interface MessageBuilder<T> extends Message<T>
  {
    MessageBuilder<T> header(String key, Object value);
  }
}
