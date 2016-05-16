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
 * @author Nam Nguyen
 */

package com.caucho.v5.pipe.rabbit;

import java.net.URI;
import java.net.URISyntaxException;

public interface RabbitPipe
{
  public static String CONFIG_EXCHANGE = "pipe_exchange";
  public static String CONFIG_ROUTING_KEY = "pipe_routing_key";

  public static String CONFIG_DURABLE = "pipe_durable";
  public static String CONFIG_EXCLUSIVE = "pipe_exclusive";
  public static String CONFIG_AUTO_DELETE = "pipe_auto_delete";

  public static URI createURI(URI uri, String exchange, String queue,
                              boolean isDurable, boolean isExclusive,
                              boolean isAutoDelete)
  {
    String str = uri.toString();

    StringBuilder sb = new StringBuilder();

    String query = uri.getQuery();

    if (query != null) {
      sb.append(query);
      sb.append('&');
    }

    sb.append(CONFIG_EXCHANGE);
    sb.append('=');
    sb.append(exchange);

    sb.append('&');
    sb.append(CONFIG_ROUTING_KEY);
    sb.append('=');
    sb.append(queue);

    if (isDurable) {
      sb.append('&');
      sb.append(CONFIG_DURABLE);
      sb.append('=');
      sb.append(isDurable);
    }

    if (isExclusive) {
      sb.append('&');
      sb.append(CONFIG_EXCLUSIVE);
      sb.append('=');
      sb.append(isExclusive);
    }

    if (isAutoDelete) {
      sb.append('&');
      sb.append(CONFIG_AUTO_DELETE);
      sb.append('=');
      sb.append(isAutoDelete);
    }

    if (query != null) {
      int i = str.indexOf('?');

      str = str.substring(0, i) + '?' + query;
    }
    else {
      str = str + '?' + query;
    }

    try {
      return new URI(str);
    }
    catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
