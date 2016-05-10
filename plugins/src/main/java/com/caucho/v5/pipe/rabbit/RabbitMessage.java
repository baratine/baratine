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

import java.io.UnsupportedEncodingException;

import com.rabbitmq.client.AMQP;

public class RabbitMessage
{
  private byte[] _body;
  private boolean _isMandatory;
  private boolean _isImmediate;

  private AMQP.BasicProperties _props;

  private boolean _isRedeliver;

  public static RabbitMessage newMessage()
  {
    return new RabbitMessage();
  }

  public String bodyString()
  {
    try {
      return new String(_body, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  public RabbitMessage bodyString(String str)
  {
    try {
      _body = str.getBytes("UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }

    return this;
  }

  public byte[] body()
  {
    return _body;
  }

  public RabbitMessage body(byte[] bytes)
  {
    _body = bytes;

    return this;
  }

  public boolean mandatory()
  {
    return _isMandatory;
  }

  public RabbitMessage mandatory(boolean isMandatory)
  {
    _isMandatory = isMandatory;

    return this;
  }

  public boolean immediate()
  {
    return _isImmediate;
  }

  public RabbitMessage immediate(boolean isImmediate)
  {
    _isImmediate = isImmediate;

    return this;
  }

  public AMQP.BasicProperties properties()
  {
    return _props;
  }

  public RabbitMessage properties(AMQP.BasicProperties props)
  {
    _props = props;

    return this;
  }

  public boolean redeliver()
  {
    return _isRedeliver;
  }

  protected RabbitMessage redeliver(boolean isRedeliver)
  {
    _isRedeliver = isRedeliver;

    return this;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[body=" + bodyString() + "]";
  }
}
