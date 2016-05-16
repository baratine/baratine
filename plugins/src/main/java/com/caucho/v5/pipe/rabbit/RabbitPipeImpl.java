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

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.ramp.pipe.PipeAsset;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import io.baratine.service.OnInit;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;
import io.baratine.vault.Id;

public class RabbitPipeImpl extends PipeAsset<RabbitMessage> implements RabbitPipe
{
  private static final Logger _logger = Logger.getLogger(RabbitPipeImpl.class.toString());

  private @Id String _id;

  private URI _uri;
  private String _exchange;
  private String _routingKey;

  private boolean _isDurable;
  private boolean _isExclusive;
  private boolean _isAutoDelete;

  private Connection _conn;
  private Channel _channel;

  @Override
  public String id()
  {
    return _id;
  }

  @OnInit
  public void onInit(Result<Void> result)
  {
    String url = _id;

    while (url.startsWith("/")) {
      url = url.substring(1);
    }

    try {
      _uri = new URI(url);

      parseOptions(_uri.getQuery());

      if (_logger.isLoggable(Level.FINE)) {
        _logger.log(Level.FINE, "onInit: uri=" + toDebugString(_uri)
                                               + ", exchange=" + _exchange
                                               + ", routingKey=" + _routingKey);
      }

      connect();

      result.ok(null);
    }
    catch (Exception e) {
      _logger.log(Level.WARNING, "onInit: cannot connect, uri=" + toDebugString(_uri), e);

      result.fail(e);
    }
  }

  private void parseOptions(String query)
  {
    String exchange = "";
    String routingKey = "";

    boolean isDurable = false;
    boolean isExclusive = false;
    boolean isAutoDelete = false;

    if (query != null) {
      String[] tokens = query.split("&");

      for (String token : tokens) {
        String name;
        String value;

        int i = token.indexOf("=");

        if (i >= 0) {
          name = token.substring(0, i);
          value = token.substring(i + 1);
        }
        else {
          name = token;
          value = "";
        }

        if (RabbitPipe.CONFIG_EXCHANGE.equals(name)) {
          exchange = value;
        }
        else if (RabbitPipe.CONFIG_ROUTING_KEY.equals(name)) {
          routingKey = value;
        }
        else if (RabbitPipe.CONFIG_DURABLE.equals(name)) {
          isDurable = "true".equals(value);
        }
        else if (RabbitPipe.CONFIG_EXCLUSIVE.equals(name)) {
          isExclusive = "true".equals(value);
        }
        else if (RabbitPipe.CONFIG_AUTO_DELETE.equals(name)) {
          isAutoDelete = "true".equals(value);
        }
      }
    }

    _exchange = exchange;
    _routingKey = routingKey;

    _isDurable = isDurable;
    _isExclusive = isExclusive;
    _isAutoDelete = isAutoDelete;
  }

  private void connect()
    throws Exception
  {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setUri(_uri);

    RabbitPipeImpl self = ServiceRef.current().pin(this).as(RabbitPipeImpl.class);

    try {
      _conn = factory.newConnection();
      _channel = _conn.createChannel();

      _channel.queueDeclare(_routingKey, _isDurable, _isExclusive, _isAutoDelete, null);

      boolean isAutoAck = false;

      _channel.basicConsume(_routingKey, isAutoAck, new DefaultConsumer(_channel) {
        @Override
        public void handleDelivery(String consumerTag,
                                   Envelope envelope,
                                   AMQP.BasicProperties properties,
                                   byte[] body)
          throws IOException
        {
          RabbitMessage msg = RabbitMessage.newMessage().body(body)
                                                        .properties(properties)
                                                        .redeliver(envelope.isRedeliver());

          long deliveryTag = envelope.getDeliveryTag();

          self.onRabbitReceive(msg, (Void, e) -> {
            if (e != null) {
              _channel.basicReject(deliveryTag, false);
            }
            else {
              _channel.basicAck(deliveryTag, false);
            }
          });
        }
      });
    }
    catch (Exception e) {
      closeChannel();
      closeConnection();

      throw e;
    }
  }

  private void reconnect()
  {
    try {
      closeChannel();
      closeConnection();

      connect();
    }
    catch (Exception e) {
      _logger.log(Level.WARNING, "error reconnecting: uri=" + toDebugString(_uri) + ", error=" + e.getMessage(), e);
    }
  }

  public void onRabbitReceive(RabbitMessage msg, Result<Void> result)
  {
    System.err.println("RabbitPipeImpl.onRabbitReceive0: " + msg);

    sendDriver(msg);

    result.ok(null);
  }

  private void closeChannel()
  {
    if (_channel != null) {
      try {
        Channel channel = _channel;
        _channel = null;

        channel.close();
      }
      catch (Exception e) {
      }
    }
  }

  private void closeConnection()
  {
    if (_conn != null) {
      try {
        Connection conn = _conn;
        _conn = null;

        conn.close();
      }
      catch (Exception e) {
      }
    }
  }

  @Override
  protected void onInitSend()
  {
  }

  @Override
  protected void onSend(RabbitMessage value)
  {
    System.err.println("RabbitPipeImpl.onSend0: " + value + " . " + _logger.getLevel() + " . " + _logger.isLoggable(Level.FINEST));

    if (_logger.isLoggable(Level.FINEST)) {
      _logger.log(Level.FINEST, "send: uri=" + toDebugString(_uri) + ", msg=" + value);
    }

    System.err.println("RabbitPipeImpl.onSend1");

    try {
      _channel.basicPublish(_exchange,
                            _routingKey,
                            value.mandatory(),
                            value.immediate(),
                            value.properties(),
                            value.body());

      System.err.println("RabbitPipeImpl.onSend2");
    }
    catch (IOException e) {
      System.err.println("RabbitPipeImpl.onSend3");

      e.printStackTrace();
    }
    catch (Exception e) {
      System.err.println("RabbitPipeImpl.onSend4");
      e.printStackTrace();

      _logger.log(Level.WARNING, "send error: uri=" + toDebugString(_uri) + ", error=" + e.getMessage(), e);

      reconnect();

      throw new RuntimeException(e);
    }
  }

  private static String toDebugString(URI uri)
  {
    String str = uri.toString();

    String authority = uri.getAuthority();

    if (authority != null) {
      str = str.replace("authority", "XXX");
    }

    return str;
  }
}
