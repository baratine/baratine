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
 *
 * @param <T> type of the encapsulated value
 */
public interface Message<T>
{
  /**
   * Method value returns encapsulated message value.
   *
   * @return encapsulated value
   */
  T value();

  /**
   * Method headers returns optional header passed with the message
   *
   * @return map of headers
   */
  Map<String,Object> headers();

  /**
   * Method header returns value of a header matching a key
   *
   * @param key header key
   * @return header value
   */
  Object header(String key);

  /**
   * Create an instance of a MessageBuilder using passed value as an encapsulated
   * Message value.
   *
   * @param value value to encapsulate in a message
   * @param <X> type of the value
   * @return an instance of MessageBuilder
   */
  static <X> MessageBuilder<X> newMessage(X value)
  {
    return new MessageImpl<>(value);
  }

  /**
   * MessageBuilder interface allows setting properties for a Message, such as
   * headers.
   *
   * @param <T> type of a value encapsulated in a Message
   */
  interface MessageBuilder<T> extends Message<T>
  {
    MessageBuilder<T> header(String key, Object value);
  }
}
