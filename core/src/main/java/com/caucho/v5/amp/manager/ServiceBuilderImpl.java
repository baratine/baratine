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

package com.caucho.v5.amp.manager;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.ActorFactoryImpl;
import com.caucho.v5.amp.actor.ActorFactoryWorkers;
import com.caucho.v5.amp.inbox.InboxQueue;
import com.caucho.v5.amp.inbox.OutboxAmpFactory;
import com.caucho.v5.amp.inbox.QueueServiceFactoryInbox;
import com.caucho.v5.amp.journal.ActorJournal;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.proxy.ProxyHandleAmp;
import com.caucho.v5.amp.queue.QueueServiceBuilderImpl;
import com.caucho.v5.amp.session.SessionServiceManagerImpl;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.ActorFactoryAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ServiceBuilderAmp;
import com.caucho.v5.inject.InjectManagerAmp;
import com.caucho.v5.inject.impl.ServiceImpl;
import com.caucho.v5.util.L10N;

import io.baratine.inject.Key;
import io.baratine.service.Journal;
import io.baratine.service.Queue;
import io.baratine.service.QueueFullHandler;
import io.baratine.service.Service;
import io.baratine.service.Startup;
import io.baratine.service.Workers;

/**
 * Service builder for services needing configuration.
 */
public class ServiceBuilderImpl implements ServiceBuilderAmp
{
  private static final L10N L = new L10N(ServiceBuilderImpl.class);
  private static final Logger log
    = Logger.getLogger(ServiceBuilderImpl.class.getName());
  
  private final AmpManager _manager;

  private Object _worker;
  private Supplier<?> _serviceSupplier;
  
  private String _address;
  
  private int _queueSizeMax;
  private int _queueSize;

  private long _offerTimeout;
  private QueueFullHandler _queueFullHandler;
  
  // private ServiceConfig.Builder _builderConfig;
  
  private Class<?> _api;

  private boolean _isForeign;

  private Class<?> _serviceClass;

  private long _journalDelay;

  private int _workers = 1;

  private boolean _isJournal;

  private boolean _isAutoStart;

  private int _journalMaxCount;

  private boolean _isPublic;

  private String _name;
  
  ServiceBuilderImpl(AmpManager manager)
  {
    Objects.requireNonNull(manager);
    
    _manager = manager;
    //_builderConfig = ServiceConfig.Builder.create();
    
    queueSizeMax(16 * 1024);
    queueSize(64);
    
    if (manager.getJournalDelay() >= 0) {
      journalDelay(manager.getJournalDelay(), TimeUnit.MILLISECONDS);
    }
  }
  
  private void queueSizeMax(int size)
  {
    _queueSizeMax = size;
  }

  public int queueSizeMax()
  {
    return _queueSizeMax;
  }
  
  private void queueSize(int size)
  {
    _queueSize = size;
  }
  
  public int queueSize()
  {
    return _queueSize;
  }
  
  public long queueTimeout()
  {
    return 10 * 1000L;
  }

  private void journalDelay(long delay, TimeUnit unit)
  {
    _journalDelay = unit.toMillis(delay);
  }
  
  private ClassLoader getClassLoader()
  {
    return _manager.classLoader();
  }

  public ServiceBuilderAmp service(Object worker)
  {
    Objects.requireNonNull(worker);
    
    if (_worker != null || _serviceSupplier != null) {
      throw new IllegalStateException();
    }
    
    if (worker instanceof ProxyHandleAmp) {
      throw new IllegalArgumentException(String.valueOf(worker));
    }
    
    _worker = worker;
    
    introspectAnnotations(worker.getClass());

    return this;
  }

  public ServiceBuilderAmp service(Supplier<?> serviceSupplier)
  {
    Objects.requireNonNull(serviceSupplier);
    
    _serviceSupplier = serviceSupplier;

    return this;
  }

  public ServiceBuilderAmp service(Class<?> serviceClass)
  {
    Objects.requireNonNull(serviceClass);
    
    _serviceClass = serviceClass;
    //SessionService channel = serviceClass.getAnnotation(SessionService.class);
    
    introspectAnnotations(serviceClass);
    /*
    Service service = serviceClass.getAnnotation(Service.class);
    
    boolean isSession = false;
    
    if (service != null) {
      if (_address == null && ! service.value().isEmpty()) {
        String address = service.value();
        
        _address = getPath(address);
        //_path = address;
        
        String podName = getPod(address);
        
        if (! podName.isEmpty()
            && ! podName.equals(_manager.node().podName())) {
          _isForeign = true;
        }
      }
    }

    _serviceSupplier = newSupplier(serviceClass);
    */

    return this;
  }

  public ServiceBuilderAmp service(Key<?> key, Class<?> apiClass)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(apiClass);
    
    _serviceClass = apiClass;
    //SessionService channel = serviceClass.getAnnotation(SessionService.class);
    
    introspectAnnotations(apiClass);

    /*
    Service service = apiClass.getAnnotation(Service.class);
    
    boolean isSession = false;
    
    if (service != null) {
      if (_address == null && service.value().length() > 0) {
        String address = service.value();
        
        _address = getPath(address);
        //_path = address;
        
        String podName = getPod(address);
        
        if (! podName.isEmpty()
            && ! podName.equals(_manager.node().podName())) {
          _isForeign = true;
        }
      }
    }
    */

    _serviceSupplier = newSupplier(key);

    return this;
  }
  
  private void offerTimeout(long offerTimeout, TimeUnit unit)
  {
    _offerTimeout = unit.toMillis(offerTimeout);
  }
  
  public long offerTimeout()
  {
    return _offerTimeout;
  }
  
  private void introspectAnnotations(Class<?> serviceClass)
  {
    Objects.requireNonNull(serviceClass);
    
    Service service = serviceClass.getAnnotation(Service.class);
    
    //boolean isSession = false;
    
    if (service != null) {
      if (_address == null && service.value().length() > 0) {
        addressAuto();
      }
    }
    
    Queue queue = serviceClass.getAnnotation(Queue.class);
    
    if (queue != null) {
      if (queue.capacity() > 0) {
        queueSizeMax(queue.capacity());
      }
      
      if (queue.initial() > 0) {
        queueSize(queue.initial());
      }
      
      if (queue.offerTimeout() > 0) {
        offerTimeout(queue.offerTimeout(), TimeUnit.MILLISECONDS);
      }
      
      if (queue.queueFullHandler() != null
          && queue.queueFullHandler() != QueueFullHandler.class) {
        Class<? extends QueueFullHandler> handlerClass = queue.queueFullHandler();
        
        try {
          QueueFullHandler handler = handlerClass.newInstance();
        
          queueFullHandler(handler);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    
    Workers workers = serviceClass.getAnnotation(Workers.class);
    
    if (workers != null) {
      workers(workers.value());
    }
    
    Startup startup = serviceClass.getAnnotation(Startup.class);
    
    if (startup != null) {
      autoStart(true);
    }
    
    Journal journal = serviceClass.getAnnotation(Journal.class);
    
    if (journal != null) {
      journal(true);
      autoStart(true);
      
      if (journal.delay() >= 0) {
        journalDelay(journal.delay(), TimeUnit.MILLISECONDS);
      }
    }
  }
  
  private void queueFullHandler(QueueFullHandler handler)
  {
    Objects.requireNonNull(handler);
    
    _queueFullHandler = handler;
  }
  
  QueueFullHandler queueFullHandler()
  {
    return _queueFullHandler;
  }

  private String getPod(String path)
  {
    int p = path.indexOf("://");
    
    if (p < 0) {
      return "";
    }
    
    int q = path.indexOf('/', p + 3);
    
    return path.substring(p + 3, q);
  }
  
  private String getPath(String path)
  {
    if (path.startsWith("pod://")
        || path.startsWith("public://")
        || path.startsWith("session://")) {
      int p = path.indexOf("://");
      int q = path.indexOf('/', p + 3);
      
      if (q < 0) {
        throw new IllegalStateException(path);
      }
      
      if (path.startsWith("public://")) {
        return "public://" + path.substring(q);
      }
      else if (path.startsWith("session://")) {
        return "session://" + path.substring(q);
      }
      else {
        return "local://" + path.substring(q);
      }
    }
    else {
      return path;
    }
  }

  @Override
  public ServiceBuilderAmp workers(int workers)
  {
    if (workers < 1) {
      throw new IllegalArgumentException();
    }
    
    _workers = workers;

    return this;
  }
  
  int workers()
  {
    return _workers;
  }

  @Override
  public ServiceBuilderAmp address(String path)
  {
    _address = path;

    return this;
  }

  //@Override
  public String address()
  {
    return _address;
  }
  
  @Override
  public ServiceBuilderImpl addressAuto()
  {
    String address;
    
    if (_api != null) {
      address = _manager.address(_api);
    }
    else if (_serviceClass != null) {
      address = _manager.address(_serviceClass);
    }
    else {
      throw new IllegalStateException();
    }
    
    _address = getPath(address);

    //_path = address;
    String podName = getPod(address);
    
    if (! podName.isEmpty()
        && ! podName.equals(_manager.node().podName())) {
      _isForeign = true;
    }

    return this;
  }

  @Override
  public ServiceBuilderAmp name(String name)
  {
    _name = name;

    return this;
  }

  public String name()
  {
    return _name;
  }

  @Override
  public ServiceBuilderAmp setPublic(boolean isPublic)
  {
    _isPublic = isPublic;

    return this;
  }
  
  public boolean isPublic()
  {
    return _isPublic;
  }

  /*
  @Override
  public ServiceBuilderAmp resource(Class<?> resourceClass)
  {
    throw new IllegalStateException();
  }
  */

  @Override
  public ServiceBuilderAmp channel(Class<?> channelClass)
  {
    //_sessionClass = channelClass;

    return this;
  }

  @Override
  public ServiceBuilderAmp api(Class<?> api)
  {
    _api = api;

    return this;
  }

  public Class<?> getApi()
  {
    return _api;
  }
  
  //@Override
  public ServiceBuilderAmp autoStart(boolean isAutoStart)
  {
    _isAutoStart = isAutoStart;
    
    return this;
  }
  
  public boolean autoStart()
  {
    return _isAutoStart;
  }
  
  @Override
  public ServiceBuilderAmp journal(boolean isJournal)
  {
    _isJournal = isJournal;
    
    return this;
  }
  
  public boolean isJournal()
  {
    return _isJournal;
  }
  
  @Override
  public ServiceBuilderAmp journalMaxCount(int count)
  {
    _journalMaxCount = count;
    //_builderConfig.journalMaxCount(count);
    
    return this;
  }
  
  public int journalMaxCount()
  {
    return _journalMaxCount;
  }
  
  @Override
  public ServiceBuilderAmp journalTimeout(long timeout, TimeUnit unit)
  {
    //_builderConfig.journalDelay(timeout, unit);
    _journalDelay = unit.toMillis(timeout);
    
    return this;
  }

  @Override
  public ServiceRefAmp ref()
  {
    if (_isForeign) {
      return null;
    }

    if (_address != null && _address.startsWith("session://")) {
      return buildSession();
    }
    
    /*
    if (_serviceSupplier != null) {
      return buildWorkers();
    }
    */
    if (workers() > 1) {
      return buildWorkers();
    }
    
    if (_worker != null) {
      return buildWorker();
    }
    else {
      //throw new IllegalStateException(L.l("build() requires a worker or resource."));
      return buildService();
    }
  }
  
  private ServiceRefAmp buildWorker()
  {
    ServiceConfig config = new ServiceConfigImpl(this);
    
    ActorAmp actor = _manager.createActor(_worker, config);
    
    ActorFactoryAmp factory = new ActorFactoryImpl(actor, config);
    
    //ServiceRefAmp serviceRef = _manager.service(()->_worker, _address, config);
    ServiceRefAmp serviceRef = service(factory);

    if (_address != null) {
      if (_manager.service(_address).isClosed()) {
        serviceRef = serviceRef.bind(_address);
      }
    }
    
    return serviceRef;
  }
  
  private ServiceRefAmp buildWorkers()
  {
    ServiceConfig config = new ServiceConfigImpl(this);
    
    //ActorAmp actor = _manager.getProxyFactory().createSkeleton(mainWorker, _path, _path, null, config);
    ActorAmp actor;
    SupplierActor factory;
    
    if (_serviceClass != null || true) {
      String path = address();
      
      /*
      ActorAmp actorAmp
        = _manager.createActor(_serviceSupplier.get(), path, config);
        */
      
      ActorFactoryAmp actorFactory = new ActorFactoryWorkers(_manager,
                                                             _serviceSupplier,
                                                             config);
      
      ServiceRefAmp serviceRef = service(actorFactory);
      /*
        = new ServiceRefBean(_manager, _address, _serviceClass, _serviceSupplier, config);
      */
      if (_address != null) {
        if (_manager.service(_address).isClosed()) {
          serviceRef = serviceRef.bind(_address);
        }
      }
      
      if (config.isAutoStart()) {
        _manager.addAutoStart(serviceRef);
      }
      
      return serviceRef;
    }
    else {
      if (true) throw new UnsupportedOperationException();
      
      ServiceRefAmp serviceRef = null;//_manager.service(_serviceSupplier, _address, config);

      if (_address != null) {
        serviceRef = serviceRef.bind(_address);
      }
    
      return serviceRef;
    }
  }
  
  private void configJournal(Journal journal)
  {
    if (journal != null) {
      journal(true);
      
      /*
      if (journal.count() >= 0 && _builderConfig.getJournalMaxCount() < 0) {
        _builderConfig.journalMaxCount(journal.count());
      }
      */
      
      if (journal.delay() >= 0 && journalDelay() < 0) {
        journalDelay(journal.delay(), TimeUnit.MILLISECONDS);
      }
    }
  }
  
  long journalDelay()
  {
    return _journalDelay;
  }
  
  private ServiceRefAmp buildSession()
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(_manager.classLoader());
    
      return buildSessionImpl();
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }
  
  private ServiceRefAmp buildSessionImpl()
  {
    Supplier<Object> supplier = newSupplier(_serviceClass);
    /*
    if (true) throw new UnsupportedOperationException();
    
    String path = _address;

    if (path == null) {
      return _manager.newService(supplier).ref();
    }
    */
    
    //boolean isJournal = _sessionClass.isAnnotationPresent(Journal.class);
    
    // XXX: needs to be refactored.
    //ContextChannelFactory factory = new ContextChannelFactory(_manager);
    //ContextSession context = factory.create(path, _sessionClass, isJournal);

    //SkeletonClassSession skeleton = new SkeletonClassSession(_manager, _sessionClass);

    //context.setSkeleton(skeleton);

    String address = _address;
    
    ServiceConfig config = new ServiceConfigImpl(this);
    
    SessionServiceManagerImpl context;
    
    context = new SessionServiceManagerImpl(address,
                                            _manager,
                                            _serviceClass,
                                            supplier,
                                            config);
    
    //ActorAmp actor = _manager.createActor(context, config);
    
    //ActorFactoryImpl factory = new ActorFactoryImpl(actor, config);

    //ActorAmp actor = _manager.createActorSession(bean, key, context);
    //System.out.println("SESS: " + actor);

    ServiceRefAmp serviceRef = _manager.newService(context).ref();
    
    context.setServiceRef(serviceRef);
    
    if (address != null) {
      if (_manager.service(_address).isClosed()) {
        // XXX:
        serviceRef.bind(address);
      }
    }

    return (ServiceRefAmp) serviceRef;
  }
  
  private ServiceRefAmp buildService()
  {
    ServiceConfig config = new ServiceConfigImpl(this);
    
    ActorFactoryAmp factory = pluginFactory(_serviceClass, config);
    
    if (factory != null) {
      return service(factory);
    }
    
    Object worker = newWorker(_serviceClass);
    Objects.requireNonNull(worker,
                           L.l("unable to create worker for class {0}",
                               _serviceClass));
    
    ActorAmp actor = _manager.createActor(worker, config);
    
    factory = new ActorFactoryImpl(actor, config);
    
    //ServiceRefAmp serviceRef = _manager.service(()->_worker, _address, config);
    ServiceRefAmp serviceRef = service(factory);

    /*
    if (_address != null) {
      if (_manager.service(_address).isClosed()) {
        serviceRef = serviceRef.bind(_address);
      }
    }
    */
    
    return serviceRef;
  }
  
  private Object newWorker(Class<?> serviceClass)
  {
    /*
    Supplier<Object> supplier = null;//pluginSupplier(serviceClass);
    
    if (supplier != null) {
      return supplier;
    }
    */
    
    if (_serviceSupplier != null) {
      return _serviceSupplier.get();
    }
    
    InjectManagerAmp injectManager = _manager.inject();
    
    if (injectManager != null) {
      Key key = Key.of(serviceClass, ServiceImpl.class);
      
      //return new SupplierBean(key, injectManager, getClassLoader());
      Object worker = injectManager.instance(key);
      Objects.requireNonNull(worker);
      
      return worker;
    }
    else {
      return new SupplierClass(serviceClass, getClassLoader()).get();
    }
  }
  
  private Supplier<Object> newSupplier(Class<?> serviceClass)
  {
    if (_serviceSupplier != null) {
      return (Supplier) _serviceSupplier;
    }
    /*
    Supplier<Object> supplier = null;//pluginSupplier(serviceClass);
    
    if (supplier != null) {
      return supplier;
    }
    */
    
    InjectManagerAmp injectManager = _manager.inject();
    
    if (injectManager != null) {
      Key key = Key.of(serviceClass, ServiceImpl.class);
      
      return new SupplierBean(key, injectManager, getClassLoader());
      /*
      Object worker = injectManager.instance(key);
      Objects.requireNonNull(worker);
      
      return worker;
      */
    }
    else {
      return new SupplierClass(serviceClass, getClassLoader());
    }
  }
  
  private ActorFactoryAmp pluginFactory(Class<?> serviceClass,
                                         ServiceConfig config)
  {
    if (serviceClass == null) {
      return null;
    }
    
    for (ActorGenerator generator : _manager.actorFactories()) {
      ActorFactoryAmp factory = generator.factory(serviceClass,
                                                   _manager,
                                                   config);
      
      if (factory != null) {
        return factory;
      }
    }
    
    return null;
  }
  
  private <T> Supplier<T> newSupplier(Key<T> key)
  {
    InjectManagerAmp injectManager = InjectManagerAmp.current(getClassLoader());
    
    Objects.requireNonNull(injectManager);
    
    return (Supplier) new SupplierBean(key, injectManager, getClassLoader());
  }
  
  /**
   * Main service builder. Called from ServiceBuilder and ServiceRefBean.
   */
  /*
  ServiceRefAmp service(Supplier<?> beanFactory,
                        String address,
                        ServiceConfig config)
                        */
  private ServiceRefAmp service(ActorFactoryAmp actorFactory)
  {
    validateOpen();
    
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    OutboxAmp outbox = OutboxAmp.current();
    Object oldContext = null;
    
    try {
      thread.setContextClassLoader(_manager.classLoader());
      
      if (outbox != null) {
        oldContext = outbox.getAndSetContext(_manager.inboxSystem());
      }
      
      //return serviceImpl(beanFactory, address, config);
      ServiceRefAmp serviceRef = serviceImpl(actorFactory);
      
      String address = actorFactory.address();
      
      if (address != null) {
        _manager.bind(serviceRef, address);
      }
      
      if (actorFactory.config().isAutoStart()) {
        _manager.addAutoStart(serviceRef);
      }
      
      return serviceRef;
    } finally {
      thread.setContextClassLoader(oldLoader);
      
      if (outbox != null) {
        outbox.getAndSetContext(oldContext);
      }
    }
  }    

  /*
  private ServiceRefAmp serviceImpl(Supplier<?> supplier,
                                    String address,
                                    ServiceConfig config)
                                    */
  
  private ServiceRefAmp serviceImpl(ActorFactoryAmp actorFactory)
  {
    //Object bean = supplier.get();
    
    //ActorAmp mainActor = createActor(address, bean, config);
    
    ServiceRefAmp serviceRef;
    
    /*
    if (isDebug()) {
      mainActor = new ActorAmpTrace(mainActor);
    }
    */
    
    //String name = mainActor.getName();
    
    if (actorFactory.config().isJournal()) {
      serviceRef = serviceJournal(actorFactory);

      /*
      // baratine/10e6
      addAutoStart(serviceRef);
      //      serviceRef.start();
       */
    }
    else {
      /*
      Supplier<ActorAmp> supplierActor = new SupplierActor(this, supplier, mainActor, config);
      */
      
      QueueServiceFactoryImpl serviceFactory;
      
      serviceFactory = new QueueServiceFactoryImpl(_manager, actorFactory);
      
      QueueServiceBuilderImpl<MessageAmp> queueBuilder
        = new QueueServiceBuilderImpl<>();
      
      //queueBuilder.setOutboxFactory(OutboxAmpFactory.newFactory());
      queueBuilder.setClassLoader(_manager.classLoader());
      
      ServiceConfig config = actorFactory.config();
    
      queueBuilder.capacity(config.getQueueCapacity());
      queueBuilder.initial(config.getQueueInitialSize());
    
      InboxAmp inbox = new InboxQueue(_manager, 
                                      queueBuilder,
                                      serviceFactory,
                                      config);
      /*
      InboxAmp inbox = inboxFactory.create(this, 
                                           serviceFactory);
                                           */
      
      //InboxAmp inbox = new InboxQueue(this, actorFactory);
  
      serviceRef = inbox.serviceRef();

      /*
       if (config.isAutoStart()) {
        //addAutoStart(serviceRef);
      }
      */
    }
    
    if (log.isLoggable(Level.FINEST)) {
      log.finest(L.l("Created service '{0}' ({1})",
                    serviceRef.address(),
                    serviceRef.apiClass().getName()));
    }
  
    return serviceRef;
  }
  
  private void validateOpen()
  {
    if (_manager.isClosed()) {
      throw new IllegalStateException(L.l("{0} is closed", this));
    }
  }
  
  private ServiceRefAmp serviceJournal(ActorFactoryAmp actorFactory)
  {
    ServiceConfig config = actorFactory.config();
    
    ActorAmp actor = actorFactory.mainActor();
     
    // XXX: check on multiple names
    String journalName = actor.getName();

    long journalDelay = config.getJournalDelay();
    
    if (journalDelay < 0) {
      journalDelay = _journalDelay;
    }
    
    JournalAmp journal = _manager.journal(journalName, 
                                          config.getJournalMaxCount(),
                                          journalDelay);
    
    final ActorJournal journalActor = createJournalActor(actor, journal);

    actor.setJournal(journal);

    DisruptorBuilderTop topBuilder = disruptor(actor.getApiClass(), journal);
    topBuilder.name(actor.getName());
    
    topBuilder.actorMain(actor);
    
    DisruptorBuilderAmp builder = topBuilder;
    
    builder = builder.next(()->journalActor, config);
    
    builder.next(()->actor, config);

    ServiceRefAmp serviceRef = (ServiceRefAmp) topBuilder.build(config);

    journalActor.setInbox(serviceRef.inbox());

    return serviceRef;
  }

  private <T> DisruptorBuilderTop<T> disruptor(Class<T> api,
                                               JournalAmp journal)
  {
    return new DisruptorBuilderTop<T>(this, _manager, api, journal);
  }

  protected ActorJournal createJournalActor(ActorAmp actor,
                                            JournalAmp journal)
  {
    JournalAmp toPeerJournal = null;
    JournalAmp fromPeerJournal = null;

    final ActorJournal journalActor
      = new ActorJournal(actor, journal, toPeerJournal, fromPeerJournal);

    actor.setJournal(journal);
    
    return journalActor;
  }

  /*
  protected ActorJournal createJournalActorPeer(ActorAmp actor,
                                            JournalAmp journal)
  {
    
    // PodBartender pod = BartenderSystem.getCurrentPod();
    NodePodAmp shard = BartenderSystem.getCurrentShard();
    
    String peerServerName = null;
    int peerIndex = -1;
    int selfIndex = -1;

    if (shard != null) {
      int serverCount = shard.getServerCount();

      for (int i = 0; i < serverCount; i++) {
        ServerBartender server = shard.getServer(i);
        
        if (server != null 
            && server != null 
            && server.isSelf()) {
          selfIndex = i;
          
          ServerBartender serverPeer = null;
          
          if (i == 0) {
            peerIndex = 1;
            serverPeer = shard.getServer(peerIndex);
          }
          else if (i == 1) {
            peerIndex = 0;
            serverPeer = shard.getServer(peerIndex);
          }
            
          if (serverPeer != null) {
            peerServerName = serverPeer.getId();
          }
        }
      }
    }
      JournalAmp journal = _journalFactory.open(journalName, 
                                                config.getJournalMaxCount(),
                                                journalDelay);
      
      JournalAmp toPeerJournal = null;
      JournalAmp fromPeerJournal = null;
      
      if (peerServerName != null && peerIndex >= 0) {
        toPeerJournal = _journalFactory.openPeer(peerIndex + ":" + journalName,
                                                 peerServerName);
      }
      
      if (selfIndex >= 0) {
        fromPeerJournal = _journalFactory.open(selfIndex + ":" + journalName, -1, -1);
      }

      final ActorJournal journalActor
        = new ActorJournal(actor, journal, toPeerJournal, fromPeerJournal);

      actor.setJournal(journal);
      
      return journalActor;
  }
  */
  
  /**
   * Used by disruptor builder.
   */
  // @Override
  ServiceRefAmp service(QueueServiceFactoryInbox serviceFactory,
                        ServiceConfig config)
  {
    QueueServiceBuilderImpl<MessageAmp> queueBuilder
      = new QueueServiceBuilderImpl<>();
    
    //queueBuilder.setOutboxFactory(OutboxAmpFactory.newFactory());
    queueBuilder.setClassLoader(_manager.classLoader());
    
    queueBuilder.capacity(config.getQueueCapacity());
    queueBuilder.initial(config.getQueueInitialSize());
  
    InboxAmp inbox = new InboxQueue(_manager, 
                                    queueBuilder,
                                    serviceFactory,
                                    config);
    /*
    InboxAmp inbox = inboxFactory.create(this, 
                                         serviceFactory);
                                         */
    
    //InboxAmp inbox = new InboxQueue(this, actorFactory);

    return inbox.serviceRef();
  }

  private static class SupplierBean implements Supplier<Object>
  {
    private Key<?> _key;
    private InjectManagerAmp _injectManager;
    private ClassLoader _loader;
    
    SupplierBean(Key<?> key, 
                 InjectManagerAmp injectManager,
                 ClassLoader loader)
    {
      _key = key;
      
      _injectManager = injectManager;
      _loader = loader;
    }

    @Override
    public Object get()
    {
      Thread thread = Thread.currentThread();
      ClassLoader oldLoader = thread.getContextClassLoader();
      
      try {
        thread.setContextClassLoader(_loader);
        
        return _injectManager.instance(_key);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        thread.setContextClassLoader(oldLoader);
      }
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _key + "]";
    }
  }
  
  private static class SupplierClass implements Supplier<Object>
  {
    private Class<?> _cl;
    private ClassLoader _loader;
    
    SupplierClass(Class<?> cl, ClassLoader loader)
    {
      _cl = cl;
      _loader = loader;
    }

    @Override
    public Object get()
    {
      Thread thread = Thread.currentThread();
      ClassLoader oldLoader = thread.getContextClassLoader();
      
      try {
        thread.setContextClassLoader(_loader);
        
        return _cl.newInstance();
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        thread.setContextClassLoader(oldLoader);
      }
    }
  }
}
