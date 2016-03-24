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

package com.caucho.v5.ramp.pubsub;

import io.baratine.service.ServiceException;
import io.baratine.service.ServiceRef;
import io.baratine.stream.PubSubService;
import io.baratine.stream.ResultStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.caucho.v5.util.L10N;

/**
 * pub/sub main service.
 */
public class PubSubServiceRamp<T> implements PubSubService<T>
{
  private static final L10N L = new L10N(PubSubServiceRamp.class);
  
  private final SchemePubSubRamp _scheme;

  private List<ResultStream<T>> _subscriberList = new ArrayList<>();

  private String _podName;

  private String _path;
  
  public PubSubServiceRamp(SchemePubSubRamp scheme, 
                           String podName, 
                           String path)
  {
    Objects.requireNonNull(scheme);
    
    _scheme = scheme;
    _podName = podName;
    _path = path;
  }
  
  @Override
  public void publish(T message)
  {
    int size = _subscriberList.size();

    for (int i = 0; i < size; i++) {
      ResultStream<T> subscriber = _subscriberList.get(i);
      
      if (subscriber.isCancelled()) {
        _subscriberList.remove(i);
        size--;
        i--;
        continue;
      }
      
      try {
        subscriber.accept(message);
      } catch (Throwable e) {
        subscriber.fail(e);
        _subscriberList.remove(i);
        i--;
        size--;
      }
    }
  }

  @Override
  public void consume(ResultStream<T> subscriber)
  {
    Objects.requireNonNull(subscriber);
    
    if (subscriber.isCancelled()) {
      throw new ServiceException(L.l("Subscriber is closed"));
    }
    
    _subscriberList.add(subscriber);
    
    cleanSubscribers();
  }

  @Override
  public void subscribe(ResultStream<T> subscriber)
  {
    Objects.requireNonNull(subscriber);
    
    if (subscriber.isCancelled()) {
      throw new ServiceException(L.l("Subscriber is closed"));
    }
    
    _subscriberList.add(subscriber);
    
    cleanSubscribers();
  }
  
  private void cleanSubscribers()
  {
    int size = _subscriberList.size();
    
    for (int i = size - 1; i >= 0; i--) {
      ResultStream<T> subscriber = _subscriberList.get(i);
      
      if (subscriber.isCancelled()) {
        _subscriberList.remove(i);
      }
    }
  }
}
