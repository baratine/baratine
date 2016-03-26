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

package com.caucho.v5.amp.remote;

import java.util.Objects;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.service.ActorFactoryAmp;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Builder for a factory for link serviceRefs.
 */
public class ServiceRefLinkFactoryBuilder
{
  private final ServiceManagerAmp _manager;
  private final OutAmpFactory _outFactory;
  
  private String _name;
  private ChannelClientFactory _channelFactory;
  private ServiceRefAmp _queryMapRef;
  private ServiceConfig _config;
  
  private String _scheme = "bartender:";
  
  public ServiceRefLinkFactoryBuilder(ServiceManagerAmp rampManager,
                                      OutAmpFactory outFactory)
  {
    Objects.requireNonNull(rampManager);
    Objects.requireNonNull(outFactory);
    
    _manager = rampManager;
    _outFactory = outFactory;
  }
  
  public ServiceRefLinkFactoryBuilder name(String name)
  {
    _name = name;
    
    return this;
  }
  
  public ServiceRefLinkFactoryBuilder queryMapRef(ServiceRefAmp queryMapRef)
  {
    _queryMapRef = queryMapRef;
    
    return this;
  }
  
  public ServiceRefLinkFactoryBuilder config(ServiceConfig config)
  {
    _config = config;
    
    return this;
  }
  
  public ServiceRefLinkFactoryBuilder channelFactory(ChannelClientFactory factory)
  {
    _channelFactory = factory;
    
    return this;
  }
  
  public ServiceRefLinkFactoryBuilder scheme(String scheme)
  {
    _scheme = scheme;
    
    return this;
  }
  
  public ServiceRefLinkFactory build()
  {
    ServiceConfig config = _config;
    Objects.requireNonNull(config);
    /*
    if (config == null) {
      ServiceConfig.Builder builder = ServiceConfig.Builder.create();
      
      config = builder.build();
    }
    */
    
    ChannelClientFactory channelFactory = _channelFactory;
    
    if (channelFactory == null) {
      channelFactory = new ChannelClientFactory.Base();
    }
    
    ActorFactoryAmp actorFactory = new ActorFactoryLink(_manager,
                                                        _name,
                                                        _queryMapRef,
                                                        _outFactory,
                                                        channelFactory,
                                                        config);
    
    //ServiceRefAmp service = _manager.service(actorFactory);
    
    ServiceRefAmp service = _manager.newService(actorFactory).ref();
  
    StubAmpOut actor = (StubAmpOut) service.stub();
    actor.init(_manager);

    ServiceRefLinkFactory channelService
      = new ServiceRefLinkFactory(_manager, service, actor, _queryMapRef, _scheme);
    
    return channelService;
  }
  
  static class ActorFactoryLink implements ActorFactoryAmp {
    private final ServiceManagerAmp _manager;
    private final OutAmpFactory _outFactory;
    private final ChannelClientFactory _channelFactory;
    private final String _addressRemote;
    private final ServiceRefAmp _callerRef;
    private final ServiceConfig _config;
    
    public ActorFactoryLink(ServiceManagerAmp manager,
                            String address,
                            ServiceRefAmp callerSelf,
                            OutAmpFactory connFactory,
                            ChannelClientFactory channelFactory,
                            ServiceConfig config)
    {
      Objects.requireNonNull(manager);
      Objects.requireNonNull(connFactory);
      Objects.requireNonNull(channelFactory);
      Objects.requireNonNull(config);
      
      _manager = manager;
      _addressRemote = address;
      _callerRef = callerSelf;

      _outFactory = connFactory;
      _channelFactory = channelFactory;
      
      _config = config;
    }
    
    @Override
    public String actorName()
    {
      return _addressRemote;
    }
    
    @Override
    public ServiceConfig config()
    {
      return _config;
    }

    @Override
    public StubAmp get()
    {
      OutAmpManager outManager;
      outManager = new OutAmpManagerClient(_outFactory);
      
      ChannelClient channel;
      
      channel = _channelFactory.createChannelClient(_manager, outManager, _addressRemote);
      
      String addressRemote = _addressRemote;
      
      return new StubAmpOutClient(_manager, outManager, addressRemote, _callerRef, channel);
    }
    
    @Override
    public StubAmp stubMain()
    {
      return get();
    }
  }
}
