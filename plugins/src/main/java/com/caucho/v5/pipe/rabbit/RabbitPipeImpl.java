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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import com.caucho.v5.ramp.pipe.PipeAsset;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import io.baratine.config.Config;
import io.baratine.service.OnDestroy;
import io.baratine.service.OnInit;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;
import io.baratine.vault.Id;

public class RabbitPipeImpl extends PipeAsset<RabbitMessage> implements RabbitPipe
{
  private static final Logger _logger = Logger.getLogger(RabbitPipeImpl.class.toString());

  private @Id String _id;

  @Inject
  private Config _c;

  private RabbitConfig _config;

  private Connection _conn;
  private Channel _channel;

  private String _consumerTag;

  private RabbitPipeImpl _self;

  @Override
  public String id()
  {
    return _id;
  }

  @OnInit
  public void onInit(Result<Void> result)
  {
    _self = ServiceRef.current().service(_id).as(RabbitPipeImpl.class);

    try {
      _config = RabbitConfig.from(_c, _id);

      if (_logger.isLoggable(Level.FINE)) {
        _logger.log(Level.FINE, "onInit: " + _id + ", " + _config);
      }

      connect();

      result.ok(null);
    }
    catch (Exception e) {
      _logger.log(Level.WARNING, "onInit: cannot connect, " + _id + ", " + _config, e);

      result.fail(e);
    }
  }

  private void connect()
    throws Exception
  {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setUri(_config.uri());

    try {
      _conn = factory.newConnection();
      _channel = _conn.createChannel();

      AMQP.Queue.DeclareOk responseQueue = _channel.queueDeclare(_config.queue(), _config.durable(), _config.exclusive(), _config.autoDelete(), null);

      if (! "".equals(_config.exchange())) {
        AMQP.Exchange.DeclareOk responseExchange = _channel.exchangeDeclare(_config.exchange(), _config.exchangeType(), _config.durable(), _config.autoDelete(), false, null);

        _channel.queueBind(responseQueue.getQueue(), _config.exchange(), _config.routingKey());
      }

      _logger.log(Level.INFO, "connect: " + _id + ", queue=" + responseQueue.getQueue() + ", " + _config + " . " + _self);
    }
    catch (Exception e) {
      closeChannel();
      closeConnection();

      throw e;
    }
  }

  private void reconnect()
  {
    _logger.log(Level.INFO, "reconnect: " + _id);

    try {
      closeChannel();
      closeConnection();

      connect();
    }
    catch (Exception e) {
      _logger.log(Level.WARNING, "error reconnecting: " + _config + ", error=" + e.getMessage(), e);
    }
  }

  public void onRabbitReceive(RabbitMessage msg, Result<Void> result)
  {
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
  protected void onInitReceive()
  {
    System.err.println("RabbitPipeImpl.onInitReceive0: " + _consumerTag);

    if (_consumerTag != null) {
      return;
    }

    try {
      boolean isAutoAck = false;

      _consumerTag = _channel.basicConsume(_config.routingKey(), isAutoAck, new DefaultConsumer(_channel) {
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

          _self.onRabbitReceive(msg, (Void, e) -> {
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
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void onSend(RabbitMessage value)
  {
    System.err.println("RabbitPipeImpl.onSend0: " + value + " . " + _config);

    if (_logger.isLoggable(Level.FINEST)) {
      _logger.log(Level.FINEST, "send: " + _id + ", " + _config + ", msg=" + value);
    }

    try {
      _channel.basicPublish(_config.exchange(),
                            _config.routingKey(),
                            value.mandatory(),
                            value.immediate(),
                            value.properties(),
                            value.body());
    }
    catch (Exception e) {
      _logger.log(Level.WARNING, "send error: " + _id + ", " + _config + ", error=" + e.getMessage(), e);

      reconnect();

      throw new RuntimeException(e);
    }
  }

  @OnDestroy
  public void onShutdown()
  {
    try {
      if (_consumerTag != null) {
        _channel.basicCancel(_consumerTag);
      }

      _channel.close();
      _conn.close();
    }
    catch (Exception e) {
    }
  }
}
