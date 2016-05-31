package com.caucho.v5.pipe.rabbit;

import java.net.URI;
import java.net.URISyntaxException;

import io.baratine.config.Config;

public class RabbitConfig
{
  private String _id;

  private URI _uri;

  private String _exchange;
  private String _queue;
  private String _routingKey;
  private String _exchangeType;

  private boolean _isDurable;
  private boolean _isExclusive;
  private boolean _isAutoDelete;

  public static RabbitConfig from(Config config, String id)
    throws URISyntaxException
  {
    String url = "pipe+rabbitmq:" + id;

    RabbitConfig r = new RabbitConfig();

    r._id = id;

    r.uri(config.get(url + ".uri", "amqp://127.0.0.1"));
    r.exchange(config.get(url + ".exchange", ""));
    r.queue(config.get(url + ".queue", ""));
    r.routingKey(config.get(url + ".routingKey", ""));
    r.exchangeType(config.get(url + ".exchangeType", "direct"));

    r.durable(config.get(url + ".durable", Boolean.class, false));
    r.exclusive(config.get(url + ".exclusive", Boolean.class, false));
    r.autoDelete(config.get(url + ".autoDelete", Boolean.class, false));

    if (r.exchange().length() == 0
        && r.routingKey().length() == 0
        && r.queue().length() > 0) {
      // use default queue == routingKey for unnamed exchange
      r.routingKey(r.queue());
    }

    return r;
  }

  public RabbitConfig uri(String uri)
    throws URISyntaxException
  {
    _uri = new URI(uri);

    return this;
  }

  public RabbitConfig exchange(String name)
  {
    _exchange = name;

    return this;
  }

  public RabbitConfig queue(String name)
  {
    _queue = name;

    return this;
  }

  public RabbitConfig routingKey(String name)
  {
    _routingKey = name;

    return this;
  }

  public RabbitConfig exchangeType(String type)
  {
    _exchangeType = type;

    return this;
  }

  public RabbitConfig durable(boolean isDurable)
  {
    _isDurable = isDurable;

    return this;
  }

  public RabbitConfig exclusive(boolean isExclusive)
  {
    _isExclusive = isExclusive;

    return this;
  }

  public RabbitConfig autoDelete(boolean isAutoDelete)
  {
    _isAutoDelete = isAutoDelete;

    return this;
  }

  public URI uri()
  {
    return _uri;
  }

  public String exchange()
  {
    return _exchange;
  }

  public String queue()
  {
    return _queue;
  }

  public String routingKey()
  {
    return _routingKey;
  }

  public String exchangeType()
  {
    return _exchangeType;
  }

  public boolean durable()
  {
    return _isDurable;
  }

  public boolean exclusive()
  {
    return _isExclusive;
  }

  public boolean autoDelete()
  {
    return _isAutoDelete;
  }

  @Override
  public String toString()
  {
    String uri = uri().toString();

    if (_uri.getAuthority() != null) {
      uri.replace(_uri.getAuthority(), "XXX");
    }

    return getClass().getSimpleName() + "[id=" + _id
                                      + ", uri=" + uri
                                      + ", exchange=" + exchange()
                                      + ", queue=" + queue()
                                      + ", routingKey=" + routingKey()
                                      + "]";
  }
}
