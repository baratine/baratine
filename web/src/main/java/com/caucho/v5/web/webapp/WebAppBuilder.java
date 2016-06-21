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

package com.caucho.v5.web.webapp;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Provider;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.manager.InjectAutoBindService;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.amp.vault.StubGeneratorVault;
import com.caucho.v5.beans.ValidatorProviderDefault;
import com.caucho.v5.config.Configs;
import com.caucho.v5.config.inject.BaratineProducer;
import com.caucho.v5.http.dispatch.InvocationRouter;
import com.caucho.v5.http.websocket.WebSocketManager;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.inject.InjectorAmp.InjectBuilderAmp;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.util.L10N;
import com.caucho.v5.web.builder.IncludeWebAmp;
import com.caucho.v5.web.builder.WebBuilderAmp;
import com.caucho.v5.web.file.StaticFileWeb;
import com.caucho.v5.web.webapp.FilterFactory.BeanFactoryAnn;
import com.caucho.v5.web.webapp.FilterFactory.BeanFactoryClass;

import io.baratine.config.Config;
import io.baratine.config.Config.ConfigBuilder;
import io.baratine.convert.Convert;
import io.baratine.inject.Binding;
import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Injector;
import io.baratine.inject.Injector.BindingBuilder;
import io.baratine.inject.Injector.InjectAutoBind;
import io.baratine.inject.Injector.InjectorBuilder;
import io.baratine.inject.Key;
import io.baratine.io.Buffers;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import io.baratine.service.Services;
import io.baratine.service.Session;
import io.baratine.vault.Vault;
import io.baratine.web.HttpMethod;
import io.baratine.web.IfContentType;
import io.baratine.web.IncludeWeb;
import io.baratine.web.InstanceBuilder;
import io.baratine.web.OutBuilder;
import io.baratine.web.RequestWeb;
import io.baratine.web.RouteBuilder;
import io.baratine.web.ServiceWeb;
import io.baratine.web.ServiceWebSocket;
import io.baratine.web.ViewRender;
import io.baratine.web.ViewResolver;
import io.baratine.web.WebBuilder;
import io.baratine.web.WebSocket;
import io.baratine.web.WebSocketBuilder;
import io.baratine.web.WebSocketClose;

/**
 * Baratine's web-app instance builder
 */
public class WebAppBuilder
  implements WebBuilderAmp
{
  private static final L10N L = new L10N(WebAppBuilder.class);
  private static final Logger log
    = Logger.getLogger(WebAppBuilder.class.getName());

  private static final Predicate<RequestWeb> TRUE = x->true;

  private static final Map<HttpMethod,Predicate<RequestWeb>> _methodMap;

  private final HttpBaratine _http;
  private EnvironmentClassLoader _classLoader;

  private ArrayList<RouteWebApp> _routes = new ArrayList<>();
  private ArrayList<ViewRef<?>> _views = new ArrayList<>();
  
  private ArrayList<FilterFactory<ServiceWeb>> _filtersBeforeWebApp
    = new ArrayList<>();
  private ArrayList<FilterFactory<ServiceWeb>> _filtersAfterWebApp
    = new ArrayList<>();

  private Throwable _configException;

  //private ServiceManagerBuilder _serviceBuilder;

  private InjectBuilderAmp _injectBuilder;

  private WebAppFactory _factory;

  private ServiceManagerBuilderAmp _serviceBuilder;
  private ConfigBuilder _configBuilder;
  private WebAppAutoBind _autoBind;
  private WebApp _webApp;
  private WebSocketManager _wsManager;


  /**
   * Creates the host with its environment loader.
   */
  public WebAppBuilder(WebAppFactory factory)
  {
    Objects.requireNonNull(factory);

    _factory = factory;

    _http = factory.http();

    _classLoader = EnvironmentClassLoader.create(_http.classLoader(),
                                                 factory.id());

    view(new ViewPrimitive(), String.class, -1000);
    view(new ViewPrimitive(), Number.class, -1000);
    view(new ViewPrimitive(), Void.class, -1000);
    view(new ViewPrimitive(), Boolean.class, -1000);
    view(new ViewPrimitive(), Character.class, -1000);

    if (factory.config().get("server.gzip", Boolean.class, false)) {
      before(new FilterBeforeGzipFactory());
    }
  }

  public void before(FilterFactory<ServiceWeb> filter)
  {
    Objects.requireNonNull(filter);

    _filtersBeforeWebApp.add(filter);
  }

  protected void init()
  {
    Thread thread = Thread.currentThread();
    ClassLoader loader = thread.getContextClassLoader();

    OutboxAmp outbox = OutboxAmp.current();
    Object oldContext = null;

    if (outbox != null) {
      oldContext = outbox.context();
    }

    try {
      thread.setContextClassLoader(classLoader());

      initSelf();

      new WebApp(this);
    } catch (Throwable e) {
      e.printStackTrace();
      log.log(Level.WARNING, e.toString(), e);

      configException(e);

      _webApp = new WebAppBaratineError(this);
    } finally {
      thread.setContextClassLoader(loader);

      if (outbox != null) {
        outbox.getAndSetContext(oldContext);
      }
    }
  }

  protected void initSelf()
  {
    OutboxAmp outbox = OutboxAmp.current();

    _configBuilder = Configs.config();
    _configBuilder.add(_factory.config());

    _injectBuilder = InjectorAmp.manager(classLoader());

    _injectBuilder.include(BaratineProducer.class);

    _serviceBuilder = ServicesAmp.newManager();
    _serviceBuilder.name("webapp");
    _serviceBuilder.autoServices(true);
    _serviceBuilder.injector(_injectBuilder);
    //_serviceBuilder.setJournalFactory(new JournalFactoryImpl());
    addFactories(_serviceBuilder);
    addStubVault(_serviceBuilder);
    _serviceBuilder.contextManager(true);

    ServicesAmp services = _serviceBuilder.get();
    Amp.contextManager(services);

    _injectBuilder.autoBind(new InjectAutoBindService(services));

    if (outbox != null) {
      InboxAmp inbox = services.inboxSystem();
      // XXX: should set the inbox
      outbox.getAndSetContext(inbox);
      //System.out.println("OUTBOX-a: " + inbox + " " + serviceManager);
    }

    _wsManager = webSocketManager();
  }

  protected void addFactories(ServiceManagerBuilderAmp builder)
  {
  }

  protected void addStubVault(ServiceManagerBuilderAmp builder)
  {
    try {
      StubGeneratorVault gen = new StubGeneratorVault();

      builder.stubGenerator(gen);
    } catch (Exception e) {
      log.finer(e.toString());
    }
  }

  public WebSocketManager webSocketManager()
  {
    return _wsManager;
  }

  // @Override
  private void build()
  {
    Thread thread = Thread.currentThread();
    ClassLoader loader = thread.getContextClassLoader();
    OutboxAmp outbox = OutboxAmp.current();
    Object context = outbox.getAndSetContext(null);

    try {
      thread.setContextClassLoader(classLoader());

    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);

      configException(e);

      _webApp = new WebAppBaratineError(this);
    } finally {
      thread.setContextClassLoader(loader);
      outbox.getAndSetContext(context);
    }
  }

  void build(WebApp webApp)
  {
    Objects.requireNonNull(webApp);

    _webApp = webApp;

    _autoBind = new WebAppAutoBind(webApp);
    _injectBuilder.autoBind(_autoBind);
    _injectBuilder.include(ValidatorProviderDefault.class);

    _injectBuilder.provider(()->webApp.config()).to(Config.class);
    _injectBuilder.provider(()->webApp.inject()).to(Injector.class);
    _injectBuilder.provider(()->webApp.services()).to(Services.class);

    generateFromFactory();

    // defaults

    get("/**").to(StaticFileWeb.class);

    _injectBuilder.get();
    _serviceBuilder.start();
  }

  //@Override
  public String id()
  {
    return _factory.id();
  }

  public String path()
  {
    return _factory.path();
  }

  public EnvironmentClassLoader classLoader()
  {
    return _classLoader;
  }

  /*
  public WebAppBaratineHttp getWebAppHttp()
  {
    return _webAppHttp;
  }
  */

  //
  // deployment
  //

  Config config()
  {
    return _configBuilder.get();
  }

  @Override
  public InjectBuilderAmp injectBuilder()
  {
    return _injectBuilder;
  }

  @Override
  public ServicesAmp services()
  {
    return serviceBuilder().raw();
  }

  ServiceManagerBuilderAmp serviceBuilder()
  {
    ServiceManagerBuilderAmp builder = _serviceBuilder;

    return builder;
  }

  public Buffers buffers()
  {
    return Buffers.factory();
  }

  private void configException(Throwable e)
  {
    if (_configException == null) {
      log.log(Level.FINER, e.toString(), e);

      _configException = e;
    }
    else {
      log.log(Level.FINER, e.toString(), e);
    }
  }

  //@Override
  public Throwable configException()
  {
    return _configException;
  }

  private void generateFromFactory()
  {
    for (IncludeWebAmp include : _factory.includes()) {
      include.build(this);
    }
  }

  /**
   * Builds the web-app's router
   */
  public InvocationRouter<InvocationBaratine> buildRouter(WebApp webApp)
  {
    // find views

    InjectorAmp inject = webApp.inject();

    buildViews(inject);

    ArrayList<RouteMap> mapList = new ArrayList<>();

    ServicesAmp manager = webApp.services();

    ServiceRefAmp serviceRef = manager.newService(new RouteService()).ref();

    while (_routes.size() > 0) {
      ArrayList<RouteWebApp> routes = new ArrayList<>(_routes);
      _routes.clear();

      for (RouteWebApp route : routes) {
        mapList.addAll(route.toMap(inject, serviceRef));
      }
    }

    /*
    for (RouteConfig config : _routeList) {
      RouteBaratine route = config.buildRoute();

      mapList.add(new RouteMap("", route));
    }
    */

    RouteMap []routeArray = new RouteMap[mapList.size()];

    mapList.toArray(routeArray);

    return new InvocationRouterWebApp(webApp, routeArray);
  }

  private void buildViews(InjectorAmp inject)
  {
    for (Binding<ViewRender> binding : inject.bindings(ViewRender.class)) {
      try {
        ViewRender<?> view = (ViewRender<?>) binding.provider().get();

        Key<ViewRender<?>> key = (Key) binding.key();

        TypeRef typeRef = TypeRef.of(key.type());
        TypeRef renderRef = typeRef.to(ViewRender.class).param(0);

        Class<?> type = renderRef != null ? renderRef.rawClass() : Object.class;

        _views.add(new ViewRefRender(view, type, binding.priority()));
      } catch (Exception e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }

    for (Binding<ViewResolver> binding : inject.bindings(ViewResolver.class)) {
      try {
        ViewResolver<?> view = (ViewResolver<?>) binding.provider().get();

        Key<ViewResolver<?>> key = (Key) binding.key();

        TypeRef typeRef = TypeRef.of(key.type());
        TypeRef resolverRef = typeRef.to(ViewResolver.class).param(0);

        Class<?> type = resolverRef != null ? resolverRef.rawClass() : Object.class;

        _views.add(new ViewRefResolver(view, type, binding.priority()));
      } catch (Exception e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }
  }

  /**
   * Dummy to own the service.
   */
  private class RouteService
  {
  }

  @Override
  public WebBuilder include(Class<?> type)
  {
    System.out.println("ROUTER: " + type);
    IncludeWeb gen = (IncludeWeb) injector().instance(type);

    System.out.println("GEN: " + gen);

    return this;
  }

  //
  // inject
  //

  @Override
  public <T> BindingBuilder<T> bean(Class<T> type)
  {
    return _injectBuilder.bean(type);
  }

  @Override
  public <T> BindingBuilder<T> bean(T bean)
  {
    return _injectBuilder.bean(bean);
  }

  @Override
  public <U> WebBuilderAmp bean(Key<U> keyParent, Method method)
  {
    // XXX: should be key instead of supplier?

    _injectBuilder.include(keyParent, method);

    return this;
  }

  @Override
  public <T> BindingBuilder<T> beanProvider(Provider<T> provider)
  {
    return _injectBuilder.provider(provider);
  }

  /*
  @Override
  public <T,X> BindingBuilder<T> beanFunction(Function<X,T> function)
  {
    return _injectBuilder.function(function);
  }
  */

  /*
  @Override
  public <T,U> BindingBuilder<T> provider(Key<U> parent, Method m)
  {
    return _injectBuilder.provider(parent, m);
  }
  */

  /*
  @Override
  public <T> BindingBuilder<T> bean(T bean)
  {
    return _injectBuilder.bean(bean);
  }
  */

  @Override
  public <S,T> Convert<S,T> converter(Class<S> source, Class<T> target)
  {
    return _injectBuilder.get().converter().converter(source, target);
  }

  @Override
  public <T> ServiceRef.ServiceBuilder service(Class<T> type)
  {
    if (Vault.class.isAssignableFrom(type)) {
      addAssetConverter(type);
    }

    ServiceRef.ServiceBuilder builder;

    /*
    if (_webApp != null && _webApp.serviceManager() != null) {
      builder = _webApp.serviceManager().newService(type).addressAuto();
    }
    else {
      builder = _serviceBuilder.service(type);
    }
    */
    builder = _serviceBuilder.service(type);

    return builder;
  }

  @Override
  public ServiceRef.ServiceBuilder service(Key<?> key, Class<?> api)
  {
    ServiceRef.ServiceBuilder builder = _serviceBuilder.service(key, api);

    if (Vault.class.isAssignableFrom(api)) {
      addAssetConverter(api);
    }

    return builder;
  }

  private void addAssetConverter(Class<?> api)
  {
    TypeRef resourceRef =  TypeRef.of(api).to(Vault.class);
    Class<?> idType = resourceRef.param(0).rawClass();
    Class<?> itemType = resourceRef.param(1).rawClass();

    Service service = api.getAnnotation(Service.class);

    String address = "";

    if (service != null) {
      address = service.value();
    }

    if (address.isEmpty()) {
      address = "/" + itemType.getSimpleName();
    }

    TypeRef convertRef = TypeRef.of(Convert.class, String.class, itemType);

    Convert<String,?> convert
       = new ConvertAsset(address, itemType);

    bean(convert).to(Key.of(convertRef.type()));
  }

  @Override
  public <X> ServiceRef.ServiceBuilder service(Class<X> type,
                                               Supplier<? extends X> supplier)
  {
    ServiceRef.ServiceBuilder builder = _serviceBuilder.service(type, supplier);

    return builder;
  }

  @Override
  public RouteBuilder route(HttpMethod method, String path)
  {
    RoutePath route = new RoutePath(method, path);

    _routes.add(route);

    return route;
  }

  @Override
  public WebSocketBuilder websocket(String path)
  {
    WebSocketPath route = new WebSocketPath(path);

    _routes.add(route);

    return route;
  }

  //
  // views
  //

  @Override
  public <T> WebBuilder view(ViewRender<T> view)
  {
    _views.add(new ViewRefRender<>(view));

    return this;
  }

  @Override
  public <T> WebBuilder view(Class<? extends ViewRender<T>> viewType)
  {
    ViewRender<?> view = injector().instance(viewType);

    return view(view, Key.of(viewType));
  }

  private <T> WebBuilder view(ViewRender<T> view, Key key)
  {
    _views.add(new ViewRefRender(view, (Class) key.type(), 0));

    return this;
  }

  private <T> WebBuilder view(ViewRender<T> view, Class<T> type, int priority)
  {
    _views.add(new ViewRefRender<>(view, type, priority));

    return this;
  }

  protected <T> WebBuilder view(ViewResolver<? super T> view, Class<T> type, int priority)
  {
    _views.add(new ViewRefResolver<>(view, type, priority));

    return this;
  }

  List<ViewRef<?>> views()
  {
    return _views;
  }

  BodyResolver bodyResolver()
  {
    return new BodyResolverBase();
  }

  @Override
  public InjectorAmp injector()
  {
    return _injectBuilder.get();
  }

  @Override
  public InjectorBuilder autoBind(InjectAutoBind autoBind)
  {
    throw new UnsupportedOperationException();
  }

  // @Override
  public WebApp get()
  {
    Thread thread = Thread.currentThread();
    ClassLoader loader = thread.getContextClassLoader();
    OutboxAmp outbox = OutboxAmp.current();
    Object context = outbox.getAndSetContext(null);

    try {
      thread.setContextClassLoader(classLoader());

      if (configException() == null) {
        return _webApp.start();
      }
      else {
        return new WebAppBaratineError(this).start();
      }

    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);

      configException(e);

      return new WebAppBaratineError(this);
    } finally {
      thread.setContextClassLoader(loader);
      outbox.getAndSetContext(context);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + id() + "]";
  }

  private class WebAppAutoBind implements InjectAutoBind
  {
    private WebApp _webApp;

    WebAppAutoBind(WebApp webApp)
    {
      _webApp = webApp;
    }

    @Override
    public <T> Provider<T> provider(Injector manager, Key<T> key)
    {
      Class<?> rawClass = key.rawClass();

      return null;
    }

  }

  /**
   * RouteWebApp is a program for routes in the web-app.
   *
   */
  static interface RouteWebApp
  {
    List<RouteMap> toMap(InjectorAmp inject, ServiceRefAmp serviceRef);
  }

  /**
   * RoutePath has a path pattern for a route.
   */
  class RoutePath implements RouteBuilderAmp, RouteWebApp, OutBuilder
  {
    private HttpMethod _method;
    private String _path;
    private ServiceWeb _service;
    
    private ArrayList<Predicate<RequestWeb>> _predicateList = new ArrayList<>();
    
    private ArrayList<FilterFactory<ServiceWeb>> _filtersBefore
      = new ArrayList<>();
    private ArrayList<FilterFactory<ServiceWeb>> _filtersAfter
      = new ArrayList<>();
    private Class<? extends ServiceWeb> _serviceClass;

    private ViewRef<?> _viewRef;

    RoutePath(HttpMethod method, String path)
    {
      _method = method;
      _path = path;

      _filtersBefore.addAll(_filtersBeforeWebApp);
    }

    @Override
    public WebBuilderAmp webBuilder()
    {
      return WebAppBuilder.this;
    }

    @Override
    public String path()
    {
      return _path;
    }

    @Override
    public HttpMethod method()
    {
      return _method;
    }

    @Override
    public RoutePath ifAnnotation(Method method)
    {
      IfContentType contentTypeAnn = method.getAnnotation(IfContentType.class);
      
      if (contentTypeAnn != null) {
        for (String contentType : contentTypeAnn.value()) {
          _predicateList.add(req->contentType.equals(req.header("content-type")));
        }
      }
      
      return this;
    }

    @Override
    public RoutePath after(Class<? extends ServiceWeb> filterClass)
    {
      Objects.requireNonNull(filterClass);

      _filtersAfter.add(new BeanFactoryClass<>(filterClass));

      return this;
    }

    @Override
    public <X extends Annotation>
    RoutePath after(X ann, InjectionPoint<?> ip)
    {
      Objects.requireNonNull(ann);
      Objects.requireNonNull(ip);

      _filtersAfter.add(new BeanFactoryAnn<>(ServiceWeb.class, ann, ip));

      return this;
    }

    @Override
    public RoutePath before(Class<? extends ServiceWeb> filterClass)
    {
      Objects.requireNonNull(filterClass);

      _filtersBefore.add(new BeanFactoryClass<>(filterClass));

      return this;
    }

    @Override
    public <X extends Annotation>
    RoutePath before(X ann, InjectionPoint<?> ip)
    {
      Objects.requireNonNull(ann);
      Objects.requireNonNull(ip);

      _filtersBefore.add(new BeanFactoryAnn<>(ServiceWeb.class, ann, ip));

      return this;
    }

    @Override
    public OutBuilder to(ServiceWeb service)
    {
      Objects.requireNonNull(service);

      _service = service;

      return this;
    }

    @Override
    public OutBuilder to(Class<? extends ServiceWeb> serviceClass)
    {
      Objects.requireNonNull(serviceClass);

      _serviceClass = serviceClass;

      return this;
    }

    @Override
    public <T> OutBuilder view(ViewRender<T> view)
    {
      Objects.requireNonNull(view);

      _viewRef = new ViewRefRender<>(view);

      return this;
    }

    @Override
    public List<RouteMap> toMap(InjectorAmp injector,
                                ServiceRefAmp serviceRef)
    {
      ArrayList<ViewRef<?>> views = new ArrayList<>();

      if (_viewRef != null) {
        views.add(_viewRef);
      }

      views.addAll(views());

      ArrayList<ServiceWeb> filtersBefore = new ArrayList<>();

      for (FilterFactory<ServiceWeb> filterFactory : _filtersBefore) {
        ServiceWeb filter = filterFactory.apply(this);

        if (filter != null) {
          filtersBefore.add(filter);
        }
        else {
          log.warning(L.l("{0} is an unknown filter", filterFactory));
        }
      }

      ArrayList<ServiceWeb> filtersAfter = new ArrayList<>();

      for (FilterFactory<ServiceWeb> filterFactory : _filtersAfter) {
        ServiceWeb filter = filterFactory.apply(this);

        if (filter != null) {
          filtersAfter.add(filter);
        }
        else {
          log.warning(L.l("{0} is an unknown filter", filterFactory));
        }
      }

      if (_viewRef != null) {
        views.add(_viewRef);
      }

      views.addAll(views());

      ViewMap viewMap = new ViewMap();

      for (ViewRef<?> viewRef : views) {
        viewMap.add(viewRef);
      }

      ServiceWeb service;

      if (_service != null) {
        service = _service;
      }
      else if (_serviceClass != null) {
        service = injector.instance(_serviceClass);
      }
      else {
        throw new IllegalStateException();
      }

      RouteApply routeApply;

      HttpMethod method = _method;

      if (method == null) {
        method = HttpMethod.UNKNOWN;
      }

      Predicate<RequestWeb> test = buildPredicate(method);

      routeApply = new RouteApply(service, filtersBefore, filtersAfter,
                                  serviceRef, test, viewMap);

      List<RouteMap> list = new ArrayList<>();
      list.add(new RouteMap(_path, routeApply));

      /*
      CrossOrigin crossOrigin = service.getCrossOrigin();

      if (crossOrigin != null) {
        list.add(crossOriginRouteMap(crossOrigin));
      }
      */

      return list;
    }
    
    private Predicate<RequestWeb> buildPredicate(HttpMethod method)
    {
      Predicate<RequestWeb> predicate = _methodMap.get(method);
      
      if (_predicateList.size() > 0) {
        return new PredicateList(predicate, _predicateList);
      }
      else {
        return predicate;
      }
    }

    /*
    private RouteMap crossOriginRouteMap(CrossOrigin crossOrigin)
    {
      Predicate<RequestWeb> options = _methodMap.get(HttpMethod.OPTIONS);

      RouteCrossOrigin corsRoute
        = new RouteCrossOrigin(options, _method, crossOrigin);

      return new RouteMap(_path, corsRoute);
    }
  */
  }
  
  static class PredicateList implements Predicate<RequestWeb>
  {
    private final Predicate<RequestWeb> []_predicateList;
    
    PredicateList(Predicate<RequestWeb> predicate,
                  ArrayList<Predicate<RequestWeb>> predicateList)
    {
      _predicateList = new Predicate[predicateList.size() + 1];
      
      _predicateList[0] = predicate;
      
      for (int i = 0; i < predicateList.size(); i++) {
        _predicateList[i + 1] = predicateList.get(i);
      }
    }

    @Override
    public boolean test(RequestWeb req)
    {
      for (Predicate<RequestWeb> predicate : _predicateList) {
        if (! predicate.test(req)) {
          return false;
        }
      }

      return true;
    }
  }

  /**
   * WebSocketPath is a route to a websocket service.
   */
  class WebSocketPath implements WebSocketBuilder, RouteWebApp
  {
    private String _path;

    private Supplier<? extends ServiceWebSocket<?,?>> _serviceFactory;
    private Class<? extends ServiceWebSocket<?,?>> _serviceType;

    WebSocketPath(String path)
    {
      _path = path;
    }

    @Override
    public <T,S> void to(ServiceWebSocket<T,S> service)
    {
      _serviceFactory = ()->service;
    }

    @Override
    public <T,S> InstanceBuilder<ServiceWebSocket<T,S>>
    to(Class<? extends ServiceWebSocket<T,S>> type)
    {
      _serviceType = type;

      return null;
    }

    @Override
    public <T,S> void to(Supplier<? extends ServiceWebSocket<T,S>> supplier)
    {
      _serviceFactory = supplier;
    }

    @Override
    public List<RouteMap> toMap(InjectorAmp inject,
                                ServiceRefAmp serviceRef)
    {
      Function<RequestWeb,ServiceWebSocket<?,?>> fun = null;
      Supplier<? extends ServiceWebSocket<?,?>> supplier = _serviceFactory;

      ServiceWebSocket<?,?> service = null;

      Class<?> type = null;

      if (supplier != null) {
        service = supplier.get();
        Objects.requireNonNull(service);
      }
      else if (_serviceType == null) {
        throw new IllegalStateException();
      }
      else {
        Service serviceAnn = _serviceType.getAnnotation(Service.class);
        Session sessionAnn = _serviceType.getAnnotation(Session.class);

        type = itemType(_serviceType);

        if (serviceAnn == null) {
          service = inject.instance(_serviceType);
        }
        else {
          ServiceRef ref = service(_serviceType).addressAuto().ref();

          if (serviceAnn.value().startsWith("session:") || sessionAnn != null) {
            fun = req->req.session(_serviceType);
          }
          else {
            fun = req->req.service(_serviceType);
          }
        }
      }

      if (fun == null) {
        Objects.requireNonNull(service);

        type = itemType(service.getClass());

        ServiceWebSocket<?,?> serviceWs;

        /*
        serviceWs = serviceRef.pin(new WebSocketWrapper<>(service))
                             .as(ServiceWebSocket.class);
                             */
        serviceWs = serviceRef.pin(service).as(ServiceWebSocket.class);

        fun = req->serviceWs;
      }

      WebSocketApply routeApply = new WebSocketApply(fun, type);

      List<RouteMap> list = new ArrayList<>();
      list.add(new RouteMap(_path, routeApply));

      return list;
    }

    private Class<?> itemType(Class<?> serviceClass)
    {
      TypeRef typeRef = TypeRef.of(serviceClass);
      TypeRef typeRefService = typeRef.to(ServiceWebSocket.class);
      TypeRef typeService = typeRefService.param(0);

      Class<?> type;
      if (typeService != null) {
        type = typeService.rawClass();
      }
      else {
        type = String.class;
      }

      return type;
    }
  }

  private static class MethodPredicate implements Predicate<RequestWeb> {
    private HttpMethod _method;

    MethodPredicate(HttpMethod method)
    {
      Objects.requireNonNull(method);

      _method = method;
    }

    public boolean test(RequestWeb request)
    {
      return _method.name().equals(request.method());
    }
  }

  private static class MethodGet implements Predicate<RequestWeb> {
    @Override
    public boolean test(RequestWeb request)
    {
      return "GET".equals(request.method()) || "HEAD".equals(request.method());
    }
  }

  static class ConvertAsset<T> implements Convert<String,T>
  {
    private Services _manager;
    private String _address;
    private Class<T> _itemType;

    ConvertAsset(String address, Class<T> itemType)
    {
      if (! address.endsWith("/")) {
        address = address + "/";
      }

      _address = address;
      _itemType = itemType;
    }

    @Override
    public T convert(String key)
    {
      return manager().service(_address + key).as(_itemType);
    }

    private Services manager()
    {
      if (_manager == null) {
        _manager = Services.current();
      }

      return _manager;
    }
  }

  static final class WebSocketWrapper<T,S>
    implements ServiceWebSocket<T,S>
  {
    private ServiceWebSocket<T,S> _service;

    /*
    WebSocketWrapper(ServiceWebSocket<T,S> service)
    {
      _service = service;
    }
    */

    @Override
    public void open(WebSocket<S> webSocket)
    {
      try {
        // XXX: convert to async
        _service.open(webSocket);
      } catch (Throwable e) {
        e.printStackTrace();
        System.out.println("FAIL: " + e + " " + webSocket);
        webSocket.fail(e);
      }
    }

    @Override
    public void next(T value, WebSocket<S> webSocket)
      throws IOException
    {
      _service.next(value, webSocket);
    }

    @Override
    public void ping(String value, WebSocket<S> webSocket)
      throws IOException
    {
      _service.ping(value, webSocket);
    }

    @Override
    public void pong(String value, WebSocket<S> webSocket)
      throws IOException
    {
      _service.pong(value, webSocket);
    }

    @Override
    public void close(WebSocketClose code, String msg,
                      WebSocket<S> webSocket)
      throws IOException
    {
      _service.close(code, msg, webSocket);
    }
  }

  static {
    _methodMap = new HashMap<>();

    for (HttpMethod method : HttpMethod.values()) {
      _methodMap.put(method, new MethodPredicate(method));
    }

    _methodMap.put(HttpMethod.GET, new MethodGet());
    _methodMap.put(HttpMethod.UNKNOWN, TRUE);
  }
}
