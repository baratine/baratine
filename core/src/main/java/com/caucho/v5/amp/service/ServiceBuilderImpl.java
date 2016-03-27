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

package com.caucho.v5.amp.service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.deliver.Deliver;
import com.caucho.v5.amp.deliver.QueueDeliver;
import com.caucho.v5.amp.deliver.QueueDeliverBuilder;
import com.caucho.v5.amp.deliver.QueueDeliverBuilderImpl;
import com.caucho.v5.amp.inbox.InboxQueue;
import com.caucho.v5.amp.inbox.QueueServiceFactoryInbox;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.journal.StubJournal;
import com.caucho.v5.amp.manager.ServicesAmpImpl;
import com.caucho.v5.amp.proxy.ProxyHandleAmp;
import com.caucho.v5.amp.session.SessionServiceManagerImpl;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.StubClass;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.StubAmpJournal;
import com.caucho.v5.amp.stub.StubClassFactoryAmp;
import com.caucho.v5.amp.stub.StubFactoryImpl;
import com.caucho.v5.amp.stub.StubGenerator;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.inject.impl.ServiceImpl;
import com.caucho.v5.util.L10N;

import io.baratine.inject.Key;
import io.baratine.service.Api;
import io.baratine.service.Journal;
import io.baratine.service.Queue;
import io.baratine.service.QueueFullHandler;
import io.baratine.service.Service;
import io.baratine.service.Startup;
import io.baratine.service.Workers;

/**
 * Service builder for services needing configuration.
 */
public class ServiceBuilderImpl<T> implements ServiceBuilderAmp, ServiceConfig
{
  private static final L10N L = new L10N(ServiceBuilderImpl.class);
  private static final Logger log
    = Logger.getLogger(ServiceBuilderImpl.class.getName());
  
  private final ServicesAmpImpl _services;

  private Object _worker;
  private Supplier<? extends T> _serviceSupplier;
  
  private String _address;
  
  private int _queueSizeMax;
  private int _queueSize;

  private long _offerTimeout;
  private QueueFullHandler _queueFullHandler;
  
  // private ServiceConfig.Builder _builderConfig;
  
  private Class<?> _api;
  private Class<?> _apiDefault;

  private boolean _isForeign;

  private Class<T> _type;
  private Class<T> _serviceClass;

  private long _journalDelay;

  private int _workers = 1;

  private boolean _isJournal;

  private boolean _isAutoStart;

  private int _journalMaxCount;

  private boolean _isPublic;

  private String _name;
  
  public ServiceBuilderImpl(ServicesAmpImpl manager)
  {
    Objects.requireNonNull(manager);
    
    _services = manager;
    
    initDefaults();
  }

  public ServiceBuilderImpl(ServicesAmpImpl manager,
                            Class<T> serviceClass)
  {
    Objects.requireNonNull(manager);
    _services = manager;
    
    initDefaults();
    
    Objects.requireNonNull(serviceClass);
    
    _type = serviceClass;
    
    validateServiceClass(serviceClass);
    
    _serviceClass = serviceClass;
    
    introspectAnnotations(serviceClass);
  }

  public ServiceBuilderImpl(ServicesAmpImpl manager,
                            Class<T> serviceClass,
                            Supplier<? extends T> supplier)
  {
    Objects.requireNonNull(manager);
    Objects.requireNonNull(serviceClass);
    Objects.requireNonNull(supplier);
    
    _services = manager;
    
    initDefaults();
    
    //validateServiceClass(serviceClass);
    
    _type = serviceClass;
    //_serviceClass = serviceClass;
    _serviceSupplier = supplier;
    
    introspectAnnotations(serviceClass);
  }
  
  private void initDefaults()
  {
    //_builderConfig = ServiceConfig.Builder.create();
    
    queueSizeMax(16 * 1024);
    queueSize(64);
    
    if (_services.journalDelay() >= 0) {
      journalDelay(_services.journalDelay(), TimeUnit.MILLISECONDS);
    }
  }
  
  private void validateServiceClass(Class<T> serviceClass)
  {
    ValidatorService validator = new ValidatorService();
    
    validator.serviceClass(serviceClass);
  }
  
  /**
   * snapshot/DTO to protect against changes. 
   */
  private ServiceBuilderImpl(ServiceBuilderImpl<T> builder)
  {
    Objects.requireNonNull(builder);
    
    _services = null;
    
    _address = builder.address();
    
    if (_address != null) {
      _name = _address;
    }
    else {
      _name = builder.name();
    }
    
    _workers = builder.workers();
    
    _api = builder.api();
    
    _queueSize = builder.queueSize();
    _queueSizeMax = builder.queueSizeMax();
    
    _offerTimeout = builder.queueTimeout();
    _queueFullHandler = builder.queueFullHandler();
    
    _isPublic = builder.isPublic();
    _isAutoStart = builder.isAutoStart();
    _isJournal = builder.isJournal();
    _journalMaxCount = builder.journalMaxCount();
    _journalDelay = builder.journalDelay();
  }
  
  private void queueSizeMax(int size)
  {
    _queueSizeMax = size;
  }

  @Override
  public int queueSizeMax()
  {
    return _queueSizeMax;
  }
  
  private void queueSize(int size)
  {
    _queueSize = size;
  }
  
  @Override
  public int queueSize()
  {
    return _queueSize;
  }
  
  @Override
  public long queueTimeout()
  {
    return 10 * 1000L;
  }

  private void journalDelay(long delay, TimeUnit unit)
  {
    _journalDelay = unit.toMillis(delay);
  }
  
  private ClassLoader classLoader()
  {
    return _services.classLoader();
  }

  public ServiceBuilderAmp service(Object worker)
  {
    Objects.requireNonNull(worker);
    
    if (worker instanceof Class<?>) {
      throw new IllegalStateException();
    }
    
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

  public ServiceBuilderAmp serviceSupplier(Supplier<T> serviceSupplier)
  {
    Objects.requireNonNull(serviceSupplier);
    
    _serviceSupplier = serviceSupplier;

    return this;
  }

  public ServiceBuilderAmp service(Key<?> key, Class<?> apiClass)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(apiClass);
    
    _serviceClass = (Class) apiClass;
    
    introspectAnnotations(apiClass);

    _serviceSupplier = (Supplier) newSupplier(key);

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
        addressAuto(serviceClass);
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
    
    Api api = serviceClass.getAnnotation(Api.class);
    
    if (api != null) {
      _apiDefault = api.value();
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
  
  @Override
  public QueueFullHandler queueFullHandler()
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
    
    if (q < 0) {
      return "";
    }
    
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
  
  @Override
  public int workers()
  {
    return _workers;
  }

  @Override
  public ServiceBuilderAmp address(String path)
  {
    _address = path;

    return this;
  }

  @Override
  public String address()
  {
    return _address;
  }
  
  @Override
  public ServiceBuilderImpl addressAuto()
  {
    return addressAuto(_serviceClass);
  }
  
  private ServiceBuilderImpl addressAuto(Class<?> serviceClass)
  {
    String address;
   
    if (api() != null) {
      address = _services.address(api());
    }
    else if (_type != null) {
      address = address(_type);
    }
    else if (serviceClass != null) {
      address = address(serviceClass);
    }
    else if (_worker != null) {
      address = address(_worker.getClass());
    }
    else {
      throw new IllegalStateException();
    }
    
    //System.out.println("AA: " + _address +  " + serviceClass + " " + _api + " " + _type);
    _address = getPath(address);

    //_path = address;
    String podName = getPod(address);
    
    if (! podName.isEmpty()
        && ! podName.equals(_services.node().podName())) {
      _isForeign = true;
    }

    return this;
  }
  
  private String address(Class<?> serviceClass)
  {
    Service service = serviceClass.getAnnotation(Service.class);
    
    if (service != null && ! service.value().isEmpty()) {
      return service.value();
    }
    
    Api apiAnn = serviceClass.getAnnotation(Api.class);
    
    if (apiAnn != null) {
      return _services.address(apiAnn.value());
    }
    
    Class<?> api = findApi(serviceClass);

    if (api != null) {
      return _services.address(api);
    }
    
    return _services.address(serviceClass); 
  }
  
  private Class<?> findApi(Class<?> serviceClass)
  {
    for (Class<?> api : serviceClass.getInterfaces()) {
      if (api.isAnnotationPresent(Service.class)) {
        return api;
      }
    }
    
    return null;
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
    Objects.requireNonNull(api);
    _api = api;

    return this;
  }

  @Override
  public ServiceBuilderAmp serviceClass(Class<?> serviceClass)
  {
    Objects.requireNonNull(serviceClass);
    
    _serviceClass = (Class) serviceClass;

    return this;
  }

  @Override
  public Class<?> api()
  {
    if (_api != null) {
      return _api;
    }
    else {
      return _apiDefault;
    }
  }
  
  //@Override
  public ServiceBuilderAmp autoStart(boolean isAutoStart)
  {
    _isAutoStart = isAutoStart;
    
    return this;
  }
  
  @Override
  public boolean isAutoStart()
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

  /**
   * Take a snapshot of the config to avoid changes.
   */
  private ServiceConfig config()
  {
    return new ServiceBuilderImpl(this);
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
    ServiceConfig config = config();
    
    StubFactoryAmp factory = new StubFactoryImpl(()->createStub(_worker), config);
    
    //ServiceRefAmp serviceRef = _manager.service(()->_worker, _address, config);
    ServiceRefAmp serviceRef = service(factory);

    if (_address != null) {
      if (_services.service(_address).isClosed()) {
        serviceRef = serviceRef.bind(_address);
      }
    }
    
    return serviceRef;
  }
  
  private StubAmp createStub(Object worker)
  {
    return createStub(worker, config());
  }
  
  private StubAmp createStub(Object bean, ServiceConfig config)
  {
    String path = null;
    
    if (bean instanceof StubAmp) {
      return (StubAmp) bean;
    }
    else {
      return stubFactory().stub(bean, config);
    }
  }
  
  private StubClassFactoryAmp stubFactory()
  {
    return _services.stubFactory();
  }
  
  private ServiceRefAmp buildWorkers()
  {
    ServiceConfig config = config();
    
    StubFactoryAmp stubFactory
      = new StubFactoryImpl(()->createStub(_serviceSupplier.get()),
                            config);
      
    ServiceRefAmp serviceRef = service(stubFactory);

    if (_address != null) {
      if (_services.service(_address).isClosed()) {
        serviceRef = serviceRef.bind(_address);
      }
    }
      
    if (config.isAutoStart()) {
      _services.addAutoStart(serviceRef);
    }
      
    return serviceRef;
  }
  
  @Override
  public long journalDelay()
  {
    return _journalDelay;
  }
  
  private ServiceRefAmp buildSession()
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(_services.classLoader());
    
      return buildSessionImpl();
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }
  
  private ServiceRefAmp buildSessionImpl()
  {
    Supplier<?> supplier = newSupplier(_serviceClass);

    String address = _address;
    
    ServiceConfig config = config();
    
    SessionServiceManagerImpl context;
    
    context = new SessionServiceManagerImpl(address,
                                            _services,
                                            _serviceClass,
                                            supplier,
                                            config);

    ServiceRefAmp serviceRef = _services.newService(context).ref();
    
    context.setServiceRef(serviceRef);
    
    if (address != null) {
      if (_services.service(_address).isClosed()) {
        // XXX:
        serviceRef.bind(address);
      }
    }

    return (ServiceRefAmp) serviceRef;
  }
  
  private ServiceRefAmp buildService()
  {
    ServiceConfig config = config();
    
    StubFactoryAmp factory = pluginFactory(_serviceClass, config);
    
    if (factory != null) {
      return service(factory);
    }
    
    Object worker = newWorker(_serviceClass);
    Objects.requireNonNull(worker,
                           L.l("unable to create worker for class {0}",
                               _serviceClass));
    
    //ActorAmp actor = _manager.createActor(worker, config);
    
    factory = new StubFactoryImpl(()->createStub(worker), config);
    
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
    if (_serviceSupplier != null) {
      return _serviceSupplier.get();
    }
    
    InjectorAmp injectManager = _services.injector();
    
    if (injectManager != null) {
      Key<?> key = Key.of(serviceClass, ServiceImpl.class);
      
      //return new SupplierBean(key, injectManager, getClassLoader());
      Object worker = injectManager.instance(key);
      
      Objects.requireNonNull(worker);
      
      return worker;
    }
    else {
      return new SupplierClass<>(serviceClass, classLoader()).get();
    }
  }
  
  @SuppressWarnings({"unchecked", "rawtypes"})
  private <T> Supplier<T> newSupplier(Class<T> serviceClass)
  {
    if (_serviceSupplier != null) {
      return (Supplier) _serviceSupplier;
    }
    
    InjectorAmp injectManager = _services.injector();
    
    if (injectManager != null) {
      Key<T> key = Key.of(serviceClass, ServiceImpl.class);
      
      return new SupplierBean<>(key, injectManager, classLoader());
    }
    else {
      return new SupplierClass<>(serviceClass, classLoader());
    }
  }
  
  private StubFactoryAmp pluginFactory(Class<?> serviceClass,
                                         ServiceConfig config)
  {
    if (serviceClass == null) {
      return null;
    }
    
    for (StubGenerator generator : _services.stubGenerators()) {
      StubFactoryAmp factory = generator.factory(serviceClass,
                                                   _services,
                                                   config);
      
      if (factory != null) {
        return factory;
      }
    }
    
    return null;
  }
  
  private <T> Supplier<T> newSupplier(Key<T> key)
  {
    InjectorAmp injector = InjectorAmp.current(classLoader());
    
    Objects.requireNonNull(injector);
    
    return new SupplierBean<>(key, injector, classLoader());
  }
  
  /**
   * Main service builder. Called from ServiceBuilder and ServiceRefBean.
   */
  /*
  ServiceRefAmp service(Supplier<?> beanFactory,
                        String address,
                        ServiceConfig config)
                        */
  private ServiceRefAmp service(StubFactoryAmp stubFactory)
  {
    validateOpen();
    
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    OutboxAmp outbox = OutboxAmp.current();
    Object oldContext = null;
    
    try {
      thread.setContextClassLoader(_services.classLoader());
      
      if (outbox != null) {
        oldContext = outbox.getAndSetContext(_services.inboxSystem());
      }
      
      //return serviceImpl(beanFactory, address, config);
      ServiceRefAmp serviceRef = serviceImpl(stubFactory);
      
      String address = stubFactory.address();
      
      if (address != null) {
        _services.bind(serviceRef, address);
      }
      
      if (stubFactory.config().isAutoStart()) {
        _services.addAutoStart(serviceRef);
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
  
  private ServiceRefAmp serviceImpl(StubFactoryAmp stubFactory)
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
    
    if (stubFactory.config().isJournal()) {
      serviceRef = serviceJournal(stubFactory);

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
      
      serviceFactory = new QueueServiceFactoryImpl(_services, stubFactory);
      
      QueueDeliverBuilderImpl<MessageAmp> queueBuilder
        = new QueueDeliverBuilderImpl<>();
      
      //queueBuilder.setOutboxFactory(OutboxAmpFactory.newFactory());
      queueBuilder.setClassLoader(_services.classLoader());
      
      ServiceConfig config = stubFactory.config();
    
      queueBuilder.sizeMax(config.queueSizeMax());
      queueBuilder.size(config.queueSize());
    
      InboxAmp inbox = new InboxQueue(_services, 
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
                    serviceRef.api().getType()));
    }
  
    return serviceRef;
  }
  
  private void validateOpen()
  {
    if (_services.isClosed()) {
      throw new IllegalStateException(L.l("{0} is closed", this));
    }
  }
  
  private ServiceRefAmp serviceJournal(StubFactoryAmp stubFactory)
  {
    ServiceConfig config = stubFactory.config();
    
    StubAmp stubMain = stubFactory.stubMain();
     
    // XXX: check on multiple names
    String journalName = stubMain.name();

    long journalDelay = config.journalDelay();
    
    if (journalDelay < 0) {
      journalDelay = _journalDelay;
    }
    
    JournalAmp journal = _services.journal(journalName, 
                                          config.journalMaxCount(),
                                          journalDelay);
    
    final StubJournal stubJournal = stubJournal(stubMain, journal);

    stubMain.journal(journal);
    
    Class<?> api = (Class<?>) stubMain.api().getType();

    //ClassStub skel = _services.stubFactory().stub(api, config());
    StubClass skel = new StubClass(_services, api, api);
    skel.introspect();
    
    // XXX: 
    StubAmp stubTop = new StubAmpJournal(skel, journal, stubMain, _name);
    
    QueueServiceFactoryInbox serviceFactory
      = new JournalServiceFactory(stubTop, stubJournal, stubMain, config);

    ServiceRefAmp serviceRef = service(serviceFactory, config);

    stubJournal.setInbox(serviceRef.inbox());

    return serviceRef;
  }

  protected StubJournal stubJournal(StubAmp stub,
                                            JournalAmp journal)
  {
    JournalAmp toPeerJournal = null;
    JournalAmp fromPeerJournal = null;

    final StubJournal stubJournal
      = new StubJournal(stub, journal, toPeerJournal, fromPeerJournal);

    stub.journal(journal);
    
    return stubJournal;
  }
  
  /**
   * Used by journal builder.
   */
  ServiceRefAmp service(QueueServiceFactoryInbox serviceFactory,
                        ServiceConfig config)
  {
    QueueDeliverBuilderImpl<MessageAmp> queueBuilder
      = new QueueDeliverBuilderImpl<>();
    
    //queueBuilder.setOutboxFactory(OutboxAmpFactory.newFactory());
    queueBuilder.setClassLoader(_services.classLoader());
    
    queueBuilder.sizeMax(config.queueSizeMax());
    queueBuilder.size(config.queueSize());
  
    InboxAmp inbox = new InboxQueue(_services, 
                                    queueBuilder,
                                    serviceFactory,
                                    config);

    return inbox.serviceRef();
  }

  private static class SupplierBean<T> implements Supplier<T>
  {
    private Key<T> _key;
    private InjectorAmp _injectManager;
    private ClassLoader _loader;
    
    SupplierBean(Key<T> key, 
                 InjectorAmp injectManager,
                 ClassLoader loader)
    {
      _key = key;
      
      _injectManager = injectManager;
      _loader = loader;
    }

    @Override
    public T get()
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
  
  private static class SupplierClass<T> implements Supplier<T>
  {
    private Class<T> _cl;
    private ClassLoader _loader;
    
    SupplierClass(Class<T> cl, ClassLoader loader)
    {
      _cl = cl;
      _loader = loader;
    }

    @Override
    public T get()
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
  
  private class QueueServiceFactoryImpl implements QueueServiceFactoryInbox
  {
    //private ServiceManagerAmp _manager;
    private StubFactoryAmp _stubFactory;

    QueueServiceFactoryImpl(ServicesAmp manager,
                            StubFactoryAmp actorFactory)
    {
      Objects.requireNonNull(manager);
      Objects.requireNonNull(actorFactory);
      
      //_manager = manager;
      _stubFactory = actorFactory;

      if (config().isJournal()) {
        throw new IllegalStateException();
      }
    }

    @Override
    public String getName()
    {
      return _stubFactory.actorName();
    }
    
    public ServiceConfig config()
    {
      return _stubFactory.config();
    }

    @Override
    public StubAmp stubMain()
    {
      return _stubFactory.stubMain();
    }

    @Override
    public QueueDeliver<MessageAmp> build(QueueDeliverBuilder<MessageAmp> queueBuilder,
                                          InboxQueue inbox)
    {
      ServiceConfig config = config();
      
      if (config.isJournal()) {
        throw new IllegalStateException();
      }
      
      Supplier<Deliver<MessageAmp>> factory
        = inbox.createDeliverFactory(_stubFactory, config);
      
      if (config.workers() > 0) {
        queueBuilder.multiworker(true);
        //queueBuilder.multiworerOffset(sdf
        return queueBuilder.build(factory, config.workers());
      }
      else {
        return queueBuilder.build(factory.get());
      }
    }
  }

  class JournalServiceFactory implements QueueServiceFactoryInbox
  {
    private StubAmp _stubTop;
    private StubAmp _stubJournal;
    private StubAmp _stubMain;
    //private ServiceConfig _config;
    
    JournalServiceFactory(StubAmp stubTop,
                          StubAmp stubJournal,
                          StubAmp stubMain,
                          ServiceConfig config)
    {
      _stubTop = stubTop;
      _stubJournal = stubJournal;
      _stubMain = stubMain;
      //_config = config;
    }
    
    public String getName()
    {
      return _stubTop.name();
    }
    
    public StubAmp stubMain()
    {
      return _stubTop;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public QueueDeliver<MessageAmp> build(QueueDeliverBuilder<MessageAmp> queueBuilder,
                                          InboxQueue inbox)
    {
      Deliver<MessageAmp> deliverJournal
        = inbox.createDeliver(_stubJournal);
      
      Deliver<MessageAmp> deliverMain
        = inbox.createDeliver(_stubMain);
      
      return queueBuilder.disruptor(deliverJournal, deliverMain);
    }
  }
}
