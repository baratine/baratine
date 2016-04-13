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

package com.caucho.v5.web.builder;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.inject.Qualifier;
import javax.inject.Qualifier;

import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.L10N;

import io.baratine.convert.Convert;
import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Key;
import io.baratine.service.Api;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef.ServiceBuilder;
import io.baratine.service.Workers;
import io.baratine.vault.Vault;
import io.baratine.web.Body;
import io.baratine.web.Cookie;
import io.baratine.web.Delete;
import io.baratine.web.FilterAfter;
import io.baratine.web.FilterBefore;
import io.baratine.web.Form;
import io.baratine.web.Get;
import io.baratine.web.Header;
import io.baratine.web.HttpMethod;
import io.baratine.web.IncludeWeb;
import io.baratine.web.Options;
import io.baratine.web.Patch;
import io.baratine.web.Path;
import io.baratine.web.Post;
import io.baratine.web.Put;
import io.baratine.web.Query;
import io.baratine.web.RequestWeb;
import io.baratine.web.Route;
import io.baratine.web.RouteBuilder;
import io.baratine.web.ServiceWeb;
import io.baratine.web.ServiceWebSocket;
import io.baratine.web.Trace;
import io.baratine.web.WebBuilder;
import io.baratine.web.WebSocketPath;

class IncludeWebClass implements IncludeWebAmp
{
  private static final L10N L = new L10N(IncludeWebClass.class);
  private static final Logger log
    = Logger.getLogger(IncludeWebClass.class.getName());
  
  private static WebScan[] _webScan;
  private Class<?> _type;
  
  IncludeWebClass(Class<?> type)
  {
    Objects.requireNonNull(type);
    
    _type = type;
  }

  @Override
  public void build(WebBuilderAmp builder)
  {
    Function<RequestWeb,Object> beanFactory;
    Supplier<Object> beanSupplier;
    
    if (IncludeWeb.class.isAssignableFrom(_type)) {
      Object genObject = builder.injector().instance(_type);

      IncludeWeb gen = (IncludeWeb) genObject; 
    
      gen.build(builder);
      
      beanSupplier = ()->genObject;
      beanFactory = req->genObject;
    }
    else if (ServiceWebSocket.class.isAssignableFrom(_type)) {
      Path pathAnn = _type.getAnnotation(Path.class);
      
      String path = "";
      if (pathAnn != null) {
        path = pathAnn.value();
      }
      
      if (path.isEmpty()) {
        path = "/" + _type.getSimpleName();
      }
      
      //beanSupplier = new SingletonBean(builder, _type);
      
      builder.websocket(path)
             .to((Class) _type);

      log.config("@WebSocket" + " " + path + " to " + _type.getDeclaringClass().getSimpleName());
      
      return;
    }
    else if (_type.isAnnotationPresent(Service.class)) {
      Service service = _type.getAnnotation(Service.class);
      String address = service.value();
      
      if (address.startsWith("session:")) {
        builder.service(_type);
        beanSupplier = null;
        beanFactory = req->req.session(_type);
      }
      else {
        beanSupplier = new SupplierService(builder, _type);
        beanFactory = req->beanSupplier.get();
      }
      
      if (Vault.class.isAssignableFrom(_type)) {
        TypeRef entityRef = TypeRef.of(_type).to(Vault.class).param("T");
        
        Class<?> entityType = entityRef.rawClass();
        String name = "";
        
        Path pathAnn = entityType.getAnnotation(Path.class);
        
        if (pathAnn != null) {
          name = pathAnn.value();
        }
        
        if (name.isEmpty()) {
          name = entityType.getSimpleName();
        }
        else if (name.startsWith("/")) {
          name = name.substring(1);
        }
        
        String path = "/" + name + "/{rid1}/";
        
        if (address.isEmpty()) {
          address = "/" + entityType.getSimpleName();
        }
        
        //builder.service(_type).addressAuto();
        
        Function<RequestWeb,Object> beanFactoryChild;
        Supplier<Object> beanSupplierChild = null;
        String addressChild = address;
        
        beanFactoryChild = req->req.service(addressChild + "/" + req.path("rid1"))
                                   .as(entityType);
        
        introspect(builder, path, beanSupplierChild, beanFactoryChild, entityType);
      }
    }
    else {
      beanSupplier = new SingletonBean(builder, _type);
      beanFactory = req->beanSupplier.get();
    }
    
    String path = "";
    
    Path pathAnn = _type.getAnnotation(Path.class);
    
    if (pathAnn != null) {
      path = pathAnn.value();
    }

    introspect(builder, path, beanSupplier, beanFactory);
  }
  
  private void introspect(WebBuilderAmp builder,
                          String path,
                          Supplier<Object> beanSupplier,
                          Function<RequestWeb,Object> beanFactory)
  {
    introspect(builder, path, beanSupplier, beanFactory, _type);
  }
  
  private void introspect(WebBuilderAmp builder,
                          String path,
                          Supplier<Object> beanSupplier,
                          Function<RequestWeb,Object> beanFactory,
                          Class<?> type)
    {
    // XXX: hacks
    //Object bean = builder.inject().instance(_type);
    
    Method []methods = type.getMethods();
    Arrays.sort(methods, IncludeWebClass::compareRoute);

    for (Method method : methods) {
      if (introspectRoute(builder, path, beanFactory, method)) {
      }
      else if (method.isAnnotationPresent(Service.class)) {
        introspectService(builder, method, beanSupplier);
      }
      else if (isProduces(method)) {
        builder.bean(Key.of(_type), method);
        
        // builder.provider(()->newInstance(beanSupplier,m)).to(Key.of(m));
      }
    }
  }
  
  private <T> void introspectService(WebBuilder builder,
                                     Method method,
                                     Supplier<Object> beanFactory)
  {
    Service service = method.getAnnotation(Service.class);
    Api api = method.getAnnotation(Api.class);
    
    String address = service.value();

    /*
    if (address.isEmpty()) {
      address = "/" + apiClass.getSimpleName();
    }
    */
    
    Class<T> type = (Class<T>) method.getReturnType();
    Supplier<T> supplier = ()->(T) newInstance(beanFactory,method);
    
    ServiceBuilder serviceBuilder = builder.service(type, supplier);
    
    if (api != null) {
      serviceBuilder.api(api.value());
    }
    
    if (! address.isEmpty()) {
      serviceBuilder.address(address);
    }
    
    Workers workers = method.getAnnotation(Workers.class);
    
    if (workers != null) {
      serviceBuilder.workers(workers.value());
    }
  }
  
  private boolean introspectRoute(WebBuilder builder,
                                  String path,
                                  Function<RequestWeb,Object> bean,
                                  Method method)
  {
    for (WebScan webScan : _webScan) {
      if (webScan.scan(builder, path, bean, method)) {
        return true;
      }
    }
    
    return false;
  }
  
  private static int compareRoute(Method a, Method b)
  {
    Route aRoute = a.getAnnotation(Route.class);
    Route bRoute = b.getAnnotation(Route.class);
    
    if (aRoute != null && bRoute == null) {
      return 1;
    }
    else if (aRoute == null && bRoute != null) {
      return -1;
    }
    else {
      return a.getName().compareTo(b.getName());
    }
  }
  
  private boolean isProduces(Method m)
  {
    Annotation []anns = m.getAnnotations();
    
    if (anns == null) {
      return false;
    }
    
    for (Annotation ann : anns) {
      if (ann.annotationType().isAnnotationPresent(Qualifier.class)) {
        return true;
      }
    }
    
    return false;
  }
  
  private Object newInstance(Supplier<Object> supplier, Method m)
  {
    try {
      Object parent = supplier.get();
      
      Object bean = m.invoke(parent);

      return bean;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  /*
  private void addWebRoute(WebBuilder builder,
                           Object bean,
                           HttpMethod httpMethod,
                           Method m)
  {
    String path = "/" + m.getName();
    
    builder.route(httpMethod, path).to(new WebServiceMethod(bean, m));
  }
  */
  
  /**
   * Builds a HTTP service from a method. 
   * 
   * <pre><code>
   * &#64;Get
   * void myMethod(&64;Query("id") id, Result&lt;String&gt; result);
   * </code></pre>
   * 
   * Method parameters are filled from the request.
   *  
   * @param builder called to create the service
   * @param beanFactory factory for the bean instance
   * @param method method for the service
   */
  private static ServiceWeb 
  buildWebService(WebBuilder builder,
                  Function<RequestWeb,Object> beanFactory,
                  Method method)
  {
    Parameter []params = method.getParameters();
    
    WebParam []webParam = new WebParam[params.length];
    
    for (int i = 0; i < params.length; i++) {
      webParam[i] = buildParam(builder, params[i]);
    }
    
    return new WebServiceMethod(beanFactory, method, webParam);
  }
  
  private static WebParam buildParam(WebBuilder builder, Parameter param)
  {
    if (RequestWeb.class.equals(param.getType())) {
      return new WebParamRequest();
    }
    else if (Result.class.equals(param.getType())) {
      return new WebParamResult();
    }
    else if (param.isAnnotationPresent(Query.class)) {
      Query query = param.getAnnotation(Query.class);
      
      Convert<String,?> convert
         = builder.converter(String.class, param.getType());
      
      return new WebParamQuery(query.value(), convert);
    }
    else if (param.isAnnotationPresent(Path.class)) {
      Path path = param.getAnnotation(Path.class);
      
      Convert<String,?> convert
         = builder.converter(String.class, param.getType());
      
      return new WebParamPath(path.value(), convert);
    }
    else if (param.isAnnotationPresent(Header.class)) {
      Header header = param.getAnnotation(Header.class);
      
      Convert<String,?> convert
         = builder.converter(String.class, param.getType());
      
      return new WebParamHeader(header.value(), convert);
    }
    else if (param.isAnnotationPresent(Cookie.class)) {
      Cookie cookie = param.getAnnotation(Cookie.class);
      
      Convert<String,?> convert
         = builder.converter(String.class, param.getType());
      
      return new WebParamCookie(cookie.value(), convert);
    }
    else if (param.isAnnotationPresent(Body.class)) {
      Body body = param.getAnnotation(Body.class);
      
      if (body.value().isEmpty()) {
        return new WebParamBody<>(param.getType());
      }
      else {
        Convert<String,?> convert
           = builder.converter(String.class, param.getType());
        
        return new WebParamBodyParam<>(body.value(), convert);
        
      }
      
    }
    else if (param.getType().isAnnotationPresent(Service.class)) {
      Service service = param.getType().getAnnotation(Service.class);
      String address = service.value();
      
      if (address.startsWith("session:")) {
        return new WebParamSession(param.getType());
      }
    }

    throw new UnsupportedOperationException(L.l("Unknown param type: " + param));
  }
  
  private interface WebParam
  {
    default boolean isAsync()
    {
      return false;
    }
    
    Object eval(RequestWeb request);
    
    default void evalAsync(RequestWeb request, Result<Object> result)
    {
      throw new IllegalStateException();
    }
  }
  
  private static class WebParamResult implements WebParam
  {
    @Override
    public Object eval(RequestWeb request)
    {
      return request.of();
    }
  }
  
  private static class WebParamRequest implements WebParam
  {
    @Override
    public Object eval(RequestWeb request)
    {
      return request;
    }
  }
  
  private static class WebParamQuery implements WebParam
  {
    private final String _id;
    private final Convert<String,?> _convert;
    
    WebParamQuery(String id,
                  Convert<String,?> convert)
    {
      _id = id;
      _convert = convert;
    }
    
    @Override
    public Object eval(RequestWeb request)
    {
      return _convert.convert(request.query(_id));
    }
  }
  
  private static final class WebParamPath implements WebParam
  {
    private final String _id;
    private final Convert<String,?> _convert;
    
    WebParamPath(String id, Convert<String,?> convert)
    {
      _id = id;
      _convert = convert;
    }
    
    @Override
    public Object eval(RequestWeb request)
    {
      return _convert.convert(request.path(_id));
    }
  }

  /**
   * @Header
   */
  private static class WebParamHeader implements WebParam
  {
    private final String _id;
    private final Convert<String,?> _convert;
    
    WebParamHeader(String id, Convert<String,?> convert)
    {
      _id = id;
      _convert = convert;
    }
    
    @Override
    public Object eval(RequestWeb request)
    {
      return _convert.convert(request.header(_id));
    }
  }

  /**
   * @Cookie
   */
  private static class WebParamCookie implements WebParam
  {
    private final String _id;
    private final Convert<String,?> _convert;
    
    WebParamCookie(String id, Convert<String,?> convert)
    {
      _id = id;
      _convert = convert;
    }
    
    @Override
    public Object eval(RequestWeb request)
    {
      return _convert.convert(request.cookie(_id));
    }
  }

  private static class WebParamBody<T> implements WebParam
  {
    private final Class<T> _type;
    
    WebParamBody(Class<T> type)
    {
      _type = type;
    }
    
    @Override
    public boolean isAsync()
    {
      return true;
    }
    
    
    @Override
    public Object eval(RequestWeb request)
    {
      throw new IllegalStateException();
    }
    
    @Override
    public void evalAsync(RequestWeb request, Result<Object> result)
    {
      request.body(_type, (Result<T>) result);
    }
  }

  /**
   * @Body
   */
  private static class WebParamBodyParam<T> implements WebParam
  {
    private final String _paramName;
    private final Convert<String,?> _convert;
    
    WebParamBodyParam(String paramName, Convert<String,?> convert)
    {
      _paramName = paramName;
      _convert = convert;
    }
    
    @Override
    public boolean isAsync()
    {
      return true;
    }
    
    
    @Override
    public Object eval(RequestWeb request)
    {
      throw new IllegalStateException();
    }
    
    @Override
    public void evalAsync(RequestWeb request, Result<Object> result)
    {
      // XXX: multimap?
      request.body(Form.class, result.of((map, r)->{
        _convert.convert(map.getFirst(_paramName), (Result) r);
      }));
    }
  }

  private static class WebParamSession implements WebParam
  {
    private final Class<?> _type;
    
    WebParamSession(Class<?> type)
    {
      _type = type;
    }
    
    @Override
    public Object eval(RequestWeb request)
    {
      Object value = request.session(_type);
      
      return value;
    }
  }
  
  /**
   * Scans for HTTP methods on a class.
   */
  private interface WebScan
  {
    boolean scan(WebBuilder builder, 
                 String pathPrefix,
                 Function<RequestWeb,Object> beanFactory,
                 Method m);
  }
  
  abstract private static class WebScanHttp implements WebScan
  {
    protected void addWebRoute(WebBuilder builder,
                               Function<RequestWeb,Object> beanFactory,
                               HttpMethod httpMethod,
                               String prefix,
                               String pathTail,
                               Method method)
    {
      String path = buildPath(prefix, pathTail, method);
      
      RouteBuilder routeBuilder = builder.route(httpMethod, path);
      
      filterBefore(routeBuilder, method);
      
      routeBuilder.to(buildWebService(builder, beanFactory, method));
      
      filterAfter(routeBuilder, method);

      log.config("@" + httpMethod + " " + path + " to " + method.getDeclaringClass().getSimpleName() + "." + method.getName());
    }
    
    protected String buildPath(String prefix,
                               String pathTail,
                               Method method)
    {
      if (pathTail.isEmpty()) {
        pathTail = method.getName();
      }
      
      String path = prefix;
      
      if (pathTail.equals("/")) {
        if (path.endsWith("/")) {
          path = path.substring(0, path.length() - 1);
        }
        
        if (path.equals("")) {
          path = "/";
        }
      }
      else if (path.endsWith("/")) {
        if (pathTail.startsWith("/")) {
          pathTail = pathTail.substring(1);
        }
        
        path = path + pathTail;
      }
      else if (path.isEmpty()) {
        if (pathTail.startsWith("/")) {
          pathTail = pathTail.substring(1);
        }
        
        path = "/" + pathTail;
      }
      else {
        path = path + pathTail;
      }
      
      return path;
    }
    
    /**
     * before
     */
    protected void filterBefore(RouteBuilder builder, Method method)
    {
      Class<?> type = method.getDeclaringClass();
      
      for (FilterBefore before : type.getAnnotationsByType(FilterBefore.class)) {
        builder.before(before.value());
      }
      
      for (Annotation ann : type.getAnnotations()) {
        filterBefore(builder, method, ann);
      }
      
      for (FilterBefore before : method.getAnnotationsByType(FilterBefore.class)) {
        builder.before(before.value());
      }
    
      for (Annotation ann : method.getAnnotations()) {
        filterBefore(builder, method, ann);
      }
    }
    
    /**
     * before
     */
    private void filterBefore(RouteBuilder builder,
                              Method method,
                              Annotation ann)
    {
      Class<?> annType = ann.annotationType();
        
      for (FilterBefore before : annType.getAnnotationsByType(FilterBefore.class)) {
        Class<? extends ServiceWeb> typeBefore = before.value();

        if (ServiceWeb.class.equals(typeBefore)) {
          builder.before(ann, InjectionPoint.of(method));
        }
        else {
          builder.before(typeBefore);
        }
      }
    }
  
    protected void filterAfter(RouteBuilder builder, Method method)
    {
      Class<?> type = method.getDeclaringClass();
      
      for (FilterAfter after : type.getAnnotationsByType(FilterAfter.class)) {
        builder.after(after.value());
      }
    
      for (Annotation ann : type.getAnnotations()) {
        filterAfter(builder, method, ann);
      }
    
      for (FilterAfter after : method.getAnnotationsByType(FilterAfter.class)) {
        builder.after(after.value());
      }
  
      for (Annotation ann : method.getAnnotations()) {
        filterAfter(builder, method, ann);
      }
    }

    private void filterAfter(RouteBuilder builder,
                             Method method,
                             Annotation ann)
    {
      Class<?> annType = ann.annotationType();
      
      for (FilterAfter after: annType.getAnnotationsByType(FilterAfter.class)) {
        Class<? extends ServiceWeb> typeAfter = after.value();

        if (ServiceWeb.class.equals(typeAfter)) {
          builder.after(ann, InjectionPoint.of(method));
        }
        else {
          builder.after(typeAfter);
        }
      }
    }
  }
  
  private static class WebScanGet extends WebScanHttp
  {
    @Override
    public boolean scan(WebBuilder builder, 
                        String pathPrefix,
                        Function<RequestWeb,Object> beanFactory, 
                        Method method)
    {
      Get get = method.getAnnotation(Get.class);
      
      if (get == null) {
        return false;
      }
      
      addWebRoute(builder, beanFactory, HttpMethod.GET, 
                  pathPrefix, get.value(), 
                  method);
      
      return true;
    }
  }
  
  private static class WebScanDelete extends WebScanHttp
  {
    @Override
    public boolean scan(WebBuilder builder,
                        String pathPrefix,
                        Function<RequestWeb,Object> beanFactory,
                        Method method)
    {
      Delete delete = method.getAnnotation(Delete.class);
      
      if (delete == null) {
        return false;
      }
      
      addWebRoute(builder, beanFactory, HttpMethod.DELETE, 
                  pathPrefix, delete.value(), 
                  method);
      
      return true;
    }
  }
  
  private static class WebScanOptions extends WebScanHttp
  {
    @Override
    public boolean scan(WebBuilder builder,
                        String pathPrefix,
                        Function<RequestWeb,Object> beanFactory, 
                        Method method)
    {
      Options annMethod = method.getAnnotation(Options.class);
      
      if (annMethod == null) {
        return false;
      }
      
      addWebRoute(builder, beanFactory, HttpMethod.OPTIONS, 
                  pathPrefix, annMethod.value(), method);
      
      return true;
    }
  }
  
  /**
   * PATCH
   */
  private static class WebScanPatch extends WebScanHttp
  {
    @Override
    public boolean scan(WebBuilder builder,
                        String pathPrefix,
                        Function<RequestWeb,Object> beanFactory,
                        Method method)
    {
      Patch annMethod = method.getAnnotation(Patch.class);
      
      if (annMethod == null) {
        return false;
      }
      
      addWebRoute(builder, beanFactory, HttpMethod.PATCH, 
                  pathPrefix, annMethod.value(), 
                  method);
      
      return true;
    }
  }
  
  /**
   * POST
   */
  private static class WebScanPost extends WebScanHttp
  {
    @Override
    public boolean scan(WebBuilder builder,
                        String pathPrefix,
                        Function<RequestWeb,Object> beanFactory,
                        Method method)
    {
      Post annMethod = method.getAnnotation(Post.class);
      
      if (annMethod == null) {
        return false;
      }
      
      addWebRoute(builder, beanFactory, HttpMethod.POST, 
                  pathPrefix, annMethod.value(), 
                  method);
      
      return true;
    }
  }

  /**
   * PUT
   */
  private static class WebScanPut extends WebScanHttp
  {
    @Override
    public boolean scan(WebBuilder builder,
                        String pathPrefix,
                        Function<RequestWeb,Object> beanFactory,
                        Method method)
    {
      Put annMethod = method.getAnnotation(Put.class);
      
      if (annMethod == null) {
        return false;
      }
      
      addWebRoute(builder, beanFactory, HttpMethod.PUT, 
                  pathPrefix, annMethod.value(), 
                  method);
      
      return true;
    }
  }

  /**
   * TRACE
   */
  private static class WebScanTrace extends WebScanHttp
  {
    @Override
    public boolean scan(WebBuilder builder,
                        String pathPrefix,
                        Function<RequestWeb,Object> beanFactory, 
                        Method method)
    {
      Trace annMethod = method.getAnnotation(Trace.class);
      
      if (annMethod == null) {
        return false;
      }
      
      addWebRoute(builder, beanFactory, HttpMethod.TRACE, 
                  pathPrefix, annMethod.value(), 
                  method);
      
      return true;
    }
  }

  /**
   * @Route
   */
  private static class WebScanRoute extends WebScanHttp
  {
    @Override
    public boolean scan(WebBuilder builder,
                        String pathPrefix,
                        Function<RequestWeb,Object> beanFactory,
                        Method method)
    {
      Route annMethod = method.getAnnotation(Route.class);
      
      if (annMethod == null) {
        return false;
      }
      
      addWebRoute(builder, beanFactory, HttpMethod.UNKNOWN, 
                  pathPrefix, annMethod.value(), 
                  method);
      
      return true;
    }
  }

  /**
   * WebSocket
   *
   */
  private static class WebScanWebSocket extends WebScanHttp
  {
    @Override
    public boolean scan(WebBuilder builder, 
                        String pathPrefix,
                        Function<RequestWeb,Object> beanFactory, 
                        Method method)
    {
      WebSocketPath wsPath = method.getAnnotation(WebSocketPath.class);
      
      if (wsPath == null) {
        return false;
      }
      
      addWebRoute(builder, beanFactory, HttpMethod.GET, 
                  pathPrefix, wsPath.value(), 
                  method);
      
      return true;
    }
  }
  
  /**
   * Web service by calling a method on a bean.
   */
  private static class WebServiceMethod implements ServiceWeb
  {
    private Function<RequestWeb,Object> _beanFactory;
    private Method _m;
    private WebParam []_params;
    
    WebServiceMethod(Function<RequestWeb,Object> beanFactory,
                     Method m,
                     WebParam []params)
    {
      _beanFactory = beanFactory;
      _m = m;
      _params = params;
    }

    @Override
    public void service(RequestWeb request)
    {
      try {
        Object []args = new Object[_params.length];
        
        for (int i = 0; i < args.length; i++) {
          WebParam param = _params[i];
          
          if (param.isAsync()) {
            param.evalAsync(request, new AsyncParamResult(this, request, i, args));
            return;
          }
          
          args[i] = param.eval(request);
        }
        
        Object bean = _beanFactory.apply(request);
        Objects.requireNonNull(bean);
        
        _m.invoke(bean, args);
      } catch (InvocationTargetException e) {
        request.fail(e.getCause());
      } catch (IllegalArgumentException e) {
        request.fail(new IllegalArgumentException("" + _m + " " + e.getMessage(),
                                                  e));
      } catch (Exception e) {
        request.fail(e);
      }
    }
  }
  
  /**
   * Callback to manage async parameters.
   */
  private static class AsyncParamResult implements Result<Object>
  {
    private WebServiceMethod _method;
    private RequestWeb _request;
    private int _i;
    private Object []_args;
    
    AsyncParamResult(WebServiceMethod method,
               RequestWeb request, 
               int i, 
               Object []args)
    {
      _method = method;
      _request = request;
      _i = i;
      _args = args;
    }

    @Override
    public void handle(Object value, Throwable fail) throws Exception
    {
      if (fail != null) {
        _request.fail(fail);
        return;
      }
      else {
        ok(value);
      }
    }

    @Override
    public void ok(Object value)
    {
      _args[_i++] = value;

      try {
        for (; _i < _args.length; _i++) {
          WebParam param = _method._params[_i];
        
          if (param.isAsync()) {
            param.evalAsync(_request, this);
            return;
          }
        
          _args[_i++] = param.eval(_request);
        }
        
        Object bean = _method._beanFactory.apply(_request);
        
        _method._m.invoke(bean, _args);
      } catch (InvocationTargetException e) {
        _request.fail(e.getCause());
      } catch (Exception e) {
        _request.fail(e);
      }
    }
    
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _type.getSimpleName() + "]";
  }

  private static class SingletonBean implements Supplier<Object>
  {
    private WebBuilder _builder;
    private Class<?> _type;
    private Object _bean;
    
    SingletonBean(WebBuilder builder, Class<?> type)
    {
      _builder = builder;
      _type = type;
    }
    
    public Object get()
    {
      if (_bean == null) {
        _bean = _builder.injector().instance(_type);
      }
      
      return _bean;
    }
  }
  
  private static class SupplierService implements Supplier<Object>
  {
    private WebBuilder _builder;
    private Class<?> _type;
    private Object _bean;
    private ServiceBuilder _serviceBuilder;
    
    SupplierService(WebBuilder builder, Class<?> type)
    {
      Objects.requireNonNull(builder);
      Objects.requireNonNull(type);
      
      _builder = builder;
      _type = type;
      
      _serviceBuilder = _builder.service(_type);
    }

    @Override
    public Object get()
    {
      if (_bean == null) {
        _bean = _serviceBuilder.as(_type);
      }
      
      return _bean;
    }
  }
  
  static {
    ArrayList<WebScan> webScanList = new ArrayList<>();
    
    webScanList.add(new WebScanDelete());
    webScanList.add(new WebScanGet());
    webScanList.add(new WebScanOptions());
    webScanList.add(new WebScanPatch());
    webScanList.add(new WebScanPost());
    webScanList.add(new WebScanPut());
    webScanList.add(new WebScanTrace());
    webScanList.add(new WebScanRoute());
    webScanList.add(new WebScanWebSocket());
    
    _webScan = new WebScan[webScanList.size()];
    webScanList.toArray(_webScan);
  }
}
