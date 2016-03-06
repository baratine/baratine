/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.network.port;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.caucho.v5.io.SocketBar;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.LruCache;

/**
 * Throttles connections
 */
public class ThrottleSocketImpl extends ThrottleSocket
{
  private static final Logger log = Logger.getLogger(ThrottleSocket.class.getName());
  private static final L10N L = new L10N(ThrottleSocketImpl.class);
  
  private final LruCache<String,AtomicInteger> _ipMap
    = new LruCache<String,AtomicInteger>(8192);
  
  private int _maxConcurrentRequests = -1;
  private String _lastRemote = "";

  public ThrottleSocketImpl()
  {
  }

  @Override
  public int getMaxConcurrentRequests()
  {
    return _maxConcurrentRequests;
  }

  public void setMaxConcurrentRequests(int maxConcurrentRequests)
  {
    _maxConcurrentRequests = maxConcurrentRequests;
  }

  public int getRequestCount()
  {
    return -1;
  }

  @Override
  public boolean accept(SocketBar socket)
  {
    int max = _maxConcurrentRequests;

    if (max < 0)
      return true;
    
    String ip = socket.getRemoteHost();

    AtomicInteger client = _ipMap.get(ip);

    if (client == null) {
      client = _ipMap.putIfNew(ip, new AtomicInteger());
    }

    // network/0240
    if (client.getAndIncrement() < max) {
      return true;
    }
    else {
      client.getAndDecrement();
      
      String remote = String.valueOf(socket.addressRemote());

      if (! remote.equals(_lastRemote)) {
        log.warning(L.l("Throttle: IP '{0}' attempted too many ({1}) connections.",
                        socket.addressRemote(), max));
      }
      
      _lastRemote = remote;
      
      return false;
    }
  }

  @Override
  public void close(SocketBar socket)
  {
    int max = _maxConcurrentRequests;

    if (max < 0)
      return;
    
    String ip = socket.getRemoteHost();

    AtomicInteger client = _ipMap.get(ip);

    if (client != null) {
      client.decrementAndGet();
    }
  }
}
