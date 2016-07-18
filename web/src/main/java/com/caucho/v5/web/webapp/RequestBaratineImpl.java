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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.http.protocol.ConnectionHttp;
import com.caucho.v5.http.protocol.RequestHttpBase;
import com.caucho.v5.http.protocol.RequestHttpWeb;
import com.caucho.v5.http.websocket.WebSocketBaratineImpl;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.io.OutputStreamWithBuffer;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.TempInputStream;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.network.port.ConnectionProtocol;
import com.caucho.v5.network.port.ConnectionTcp;
import com.caucho.v5.network.port.StateConnection;
import com.caucho.v5.util.Base64Util;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.RandomUtil;
import com.caucho.v5.web.CookieWeb;

import io.baratine.config.Config;
import io.baratine.inject.Injector;
import io.baratine.io.Buffer;
import io.baratine.io.Buffers;
import io.baratine.pipe.Credits;
import io.baratine.service.Result;
import io.baratine.service.ServiceException;
import io.baratine.service.ServiceRef;
import io.baratine.web.HttpStatus;
import io.baratine.web.MultiMap;
import io.baratine.web.RequestWeb;
import io.baratine.web.ServiceWebSocket;
import io.baratine.web.ViewResolver;


/**
 * User facade for baratine http requests.
 */
public final class RequestBaratineImpl extends RequestHttpWeb 
  implements RequestBaratine, ConnectionProtocol
{
  private static final L10N L = new L10N(RequestBaratineImpl.class);
  private static final Logger log
    = Logger.getLogger(RequestBaratineImpl.class.getName());

  //private List<ViewRef<?>> _views;
  
  //private ViewResolver<Object> _viewResolver;
  
  private RouteBaratine _route;

  private ArrayList<CookieWeb> _cookieList;

  private StateRequest _state = StateRequest.ACCEPT;

  private boolean _isBodyComplete;
  private TempBuffer _bodyHead;
  private TempBuffer _bodyTail;

  // callback for waiting for a body
  private Class<?> _bodyType;
  private Result<Object> _bodyResult;
  private Object _bodyValue;
  private HashMap<String, Object> _attributeMap;
  private RequestOutputStream _out;

  public RequestBaratineImpl(ConnectionHttp connHttp,
                             RequestHttpBase request)
  {
    super(connHttp, request);
    
    request.init(this);
  }
  
  @Override
  public void route(RouteBaratine route)
  {
    Objects.requireNonNull(route);
    
    _route = route;
  }
  
  private RouteBaratine route()
  {
    return _route;
  }

  @Override
  public WebApp webApp()
  {
    return invocation().webApp();
  }

  @Override
  public ServicesAmp services()
  {
    return webApp().services();
  }

  @Override
  public Buffers buffers()
  {
    return webApp().buffers();
  }

  @Override
  public String method()
  {
    return requestHttp().method();
  }

  @Override
  public String header(String key)
  {
    return requestHttp().header(key);
  }


  @Override
  public String uri()
  {
    return invocation().uri();
  }

  @Override
  public String path()
  {
    return invocation().path();
  }

  @Override
  public String pathInfo()
  {
    return invocation().pathInfo();
  }

  @Override
  public String cookie(String key)
  {
    for (CookieWeb cookie : requestHttp().cookies()) {
      if (cookie.name().equals(key)) {
        return cookie.value();
      }
    }

    return null;
  }

  @Override
  public ServiceRefAmp service(String address)
  {
    return services().service(address);
  }

  @Override
  public <X> X service(Class<X> type)
  {
    return services().service(type);
  }

  @Override
  public <X> X service(Class<X> type, String id)
  {
    return services().service(type, id);
  }
  
  //
  // session methods
  //
  
  /**
   * Find the session by its service name.
   * 
   * If the session doesn't exist, create it.
   */
  @Override
  public ServiceRefAmp session(String name)
  {
    String address = "session:///" + name + "/";
    
    return sessionImpl(address);
  }
  
  /**
   * Find the session by its service type.
   * 
   * If the session doesn't exist, create it.
   */
  @Override
  public <X> X session(Class<X> type)
  {
    String address = services().address(type);
    
    if (address.startsWith("/")) {
      address = "session://" + address;
    }

    return sessionImpl(address + "/").as(type);
  }
  
  /**
   * Find or create a session cookie as the session id and 
   * find or create a session with the generated address.
   */
  private ServiceRefAmp sessionImpl(String address)
  {
    if (! address.startsWith("session:") || ! address.endsWith("/")) {
      throw new IllegalArgumentException(address);
    }

    String sessionId = cookie("JSESSIONID");

    if (sessionId == null) {
      sessionId = generateSessionId();

      cookie("JSESSIONID", sessionId);
    }

    return services().service(address + sessionId);
  }

  private String generateSessionId()
  {
    StringBuilder sb = new StringBuilder();

    Base64Util.encodeUrl(sb, webApp().nextId());
    Base64Util.encodeUrl(sb, RandomUtil.getRandomLong());
    sb.append('.');
    sb.append(Base64Util.encodeUrl(webApp().node()));

    return sb.toString();
  }

  //
  // injection methods
  //

  @Override
  public Injector injector()
  {
    WebApp webApp = invocation().webApp();

    if (webApp != null) {
      return webApp.inject();
    }
    else {
      return InjectorAmp.current();
    }
  }

  //
  // config methods
  //

  @Override
  public Config config()
  {
    WebApp webApp = webApp();

    if (webApp != null) {
      return webApp.config();
    }
    else {
      throw new IllegalStateException();
    }
  }

  /**
   * Starts an upgrade of the HTTP request to a protocol on raw TCP.
   */
  /*
  @Override
  public void upgrade(ConnectionProtocol upgrade)
  {
    _requestState.upgrade(upgrade);
  }
  */

  /**
   * Starts an upgrade of the HTTP request to a protocol on raw TCP.
   */
  @Override
  public void upgrade(Object protocol)
  {
    Objects.requireNonNull(protocol);

    if (protocol instanceof ServiceWebSocket) {
      ServiceWebSocket<?,?> webSocket = (ServiceWebSocket<?,?>) protocol;

      upgradeWebSocket(webSocket);
    }
    else {
      throw new IllegalArgumentException(protocol.toString());
    }
  }

  /**
   * Service a request.
   *
   * @param service the http request facade
   */
  private <T,S> void upgradeWebSocket(ServiceWebSocket<T,S> service)
  {
    /*
    try {
      _service.open((WebRequest) request);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    */
    TypeRef type = TypeRef.of(service.getClass()).to(ServiceWebSocket.class).param(0);

    ServiceRef selfRef = ServiceRef.current();

    service = selfRef.pin(service).as(ServiceWebSocket.class);

    Class<T> rawClass = (Class) type.rawClass();

    WebSocketBaratineImpl<T,S> ws
      = new WebSocketBaratineImpl<>(webApp().wsManager(),
                                    service, rawClass);

    try {
      if (! ws.handshake(this)) {
        throw new ServiceException("WebSocket handshake failed for " + this);
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
      e.printStackTrace();

      fail(e);
    }
    //request.flush();
  }

  @Override
  public String scheme()
  {
    return requestHttp().scheme();
  }

  @Override
  public String host()
  {
    return invocation().host();
  }

  @Override
  public String query()
  {
    return invocation().queryString();
  }

  @Override
  public String query(String name)
  {
    return queryMap().first(name);
  }

  @Override
  public MultiMap<String,String> queryMap()
  {

  return invocation().queryMap();
  }

  @Override
  public String version()
  {
    return requestHttp().getProtocol();
  }

  @Override
  public String path(String key)
  {
    return invocation().pathMap().get(key);
  }

  @Override
  public Map<String,String> pathMap()
  {
    return invocation().pathMap();
  }

  @Override
  public <X> X attribute(Class<X> key)
  {
    String name = key.getSimpleName();
    
    return (X) _attributeMap.get(name);
  }

  @Override
  public <X> void attribute(X value)
  {
    if (_attributeMap == null) {
      _attributeMap = new HashMap<>();
    }
    
    String name = value.getClass().getSimpleName();
    
    _attributeMap.put(name, value);
  }

  @Override
  public InetSocketAddress ipRemote()
  {
    return connHttp().connTcp().ipRemote();
  }

  @Override
  public String ip()
  {
    return connHttp().connTcp().addressRemote();
  }

  @Override
  public int port()
  {
    return connHttp().connTcp().portLocal();
  }

  @Override
  public InetSocketAddress ipLocal()
  {
    return connHttp().connTcp().ipLocal();
  }

  @Override
  public SecureWeb secure()
  {
    if (connHttp().connTcp().isSecure()) {
      return new SecureWebImpl(connHttp().connTcp());
    }
    else {
      return null;
    }
  }

  //@Override
  private <X> X body(Class<X> type)
  {
    Objects.requireNonNull(type);
    
    if (_bodyValue == null) {
      if (! _isBodyComplete) {
        throw new IllegalStateException(L.l("body cannot be called with incomplete body"));
      }

      _bodyValue = webApp().bodyResolver().body(this, type);
    }
    
    return (X) _bodyValue;
  }

  @Override
  public <X> void body(Class<X> type,
                       Result<X> result)
  {
    Objects.requireNonNull(type);

    if (_isBodyComplete) {
      result.ok(body(type));
    }
    else {
      _bodyType = type;
      _bodyResult = (Result) result;
    }
  }

  @Override
  public InputStream inputStream()
  {
    if (! _isBodyComplete) {
      throw new IllegalStateException(L.l("inputStream cannot be called with incomplete body"));
    }

    TempInputStream is = new TempInputStream(_bodyHead);
    _bodyHead = _bodyTail = null;

    return is;
  }

  @Override
  public RequestBaratine status(HttpStatus status)
  {
    Objects.requireNonNull(status);

    requestHttp().status(status.code(), status.message());

    return this;
  }

  @Override
  public RequestWeb length(long length)
  {
    RequestOutputStream out = _out;
    
    if (out != null) {
      out.length(length);
    }
    else {
      requestHttp().contentLengthOut(length);
    }

    return this;
  }

  @Override
  public RequestWeb type(String contentType)
  {
    RequestOutputStream out = _out;
    
    if (out != null) {
      out.type(contentType);
    }
    else {
      requestHttp().headerOutContentType(contentType);
    }

    return this;
  }

  @Override
  public RequestWeb header(String key, String value)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);

    RequestOutputStream out = _out;
    
    if (out != null) {
      out.header(key, value);
    }
    else {
      requestHttp().headerOut(key, value);
    }

    return this;
  }

  @Override
  public RequestWeb encoding(String encoding)
  {
    requestHttp().headerOutContentEncoding(encoding);
    
    return this;
  }

  /*
  @Override
  public void ok(Object result, Throwable exn)
  {
    if (exn != null) {
      fail(exn);
    }
    else {
      ok(result);
    }
  }
  */

  @Override
  public void halt()
  {
    ok();
  }

  @Override
  public void halt(HttpStatus status)
  {
    status(status);
    
    //type("text/plain; charset=utf-8");
    //ok(null);
    ok();
  }

  @Override
  public void redirect(String address)
  {
    status(HttpStatus.MOVED_TEMPORARILY);
    header("location", encodeUrl(address));
    type("text/plain; charset=utf-8");

    write("Moved: " + encodeUrl(address));

    ok();
  }

  public String encodeUrl(String address)
  {
    return address;
  }

  @Override
  public RequestWeb push(OutFilterWeb filter)
  {
    _out = new RequestOutFilter(new RequestBaratineNext(this), filter);
    
    return this;
  }

  @Override
  public Credits credits()
  {
    throw new UnsupportedOperationException();
  }

  //@Override
  public String headerOut(String key)
  {
    return requestHttp().headerOut(key);
  }

  //
  // write methods
  //

  @Override
  public RequestBaratine write(String value)
  {
    try {
      writer().write(value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return this;
  }

  @Override
  public RequestWeb write(char[] buffer, int offset, int length)
  {
    try {
      writer().write(buffer, offset, length);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return this;
  }

  @Override
  public RequestWeb write(Buffer buffer)
  {
    requestHttp().out().write(buffer);

    return this;
  }
  
  private OutputStreamWithBuffer out()
  {
    OutputStreamWithBuffer out = _out;
    
    if (out != null) {
      return out;
    }
    else {
      return requestHttp().out();
    }
  }

  @Override
  public RequestWeb write(byte[] buffer, int offset, int length)
  {
    try {
      out().write(buffer, offset, length);

      return this;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Writer writer()
  {
    return requestHttp().writer(out());
  }

  @Override
  public OutputStream output()
  {
    return out();
  }

  @Override
  public RequestBaratineImpl flush()
  {
    try {
      OutputStream out = out();

      out.flush();
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }

    return this;
  }

  /*
  public void next(Buffer data)
  {
    try {
      OutResponseBase out = requestHttp().out();

      out.write(data);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }
  */

  @Override
  public void ok(Object value)
  {
    ViewResolver<Object> resolver = viewResolver();
    
    
    if (resolver != null && resolver.render(this, value)) {
      return;
    }

    log.warning(L.l("{0} does not have a matching view type", value));

    halt(HttpStatus.INTERNAL_SERVER_ERROR);
  }
  
  private ViewResolver<Object> viewResolver()
  {
    return route().viewResolver();
  }

  /*
  private boolean viewPrimitives(Object value)
  {
    if (value instanceof String
        || value instanceof Character
        || value instanceof Boolean
        || value instanceof Number) {
      if (headerOut("content-type") == null) {
        type("text/plain; charset=utf-8");
      }

      write(value.toString());
      ok();

      return true;
    }
    else if (value == null) {
      ok();

      return true;
    }
    else {
      return false;
    }
  }
  */

  @Override
  public final void ok()
  {
    try {
      requestHttp().writerClose();

      out().close();
    } catch (IOException e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  @Override
  public void fail(Throwable exn)
  {
    log.log(Level.FINE, exn.toString(), exn);

    if (exn instanceof FileNotFoundException) {
      status(HttpStatus.NOT_FOUND);
      type("text/plain; charset=utf-8");

      write("File Not Found\n");
    }
    else {
      status(HttpStatus.INTERNAL_SERVER_ERROR);
      type("text/plain; charset=utf-8");

      exn.printStackTrace();
      write("Internal Server Error: " + exn + "\n");

      writeTrace(exn);
    }

    try {
      requestHttp().out().close();
    } catch (IOException e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  private void writeTrace(Throwable exn)
  {
    if (CurrentTime.isTest() && ! log.isLoggable(Level.FINE)) {
      return;
    }

    while (exn != null) {
      for (StackTraceElement stack : exn.getStackTrace()) {
        write("\n  ");
        write(String.valueOf(stack));
      }

      exn = exn.getCause();
      if (exn != null) {
        write("\n\nCaused by: " + exn);
      }
    }
  }

  //
  // http response
  //

  public void writeCookies(WriteStream os) throws IOException
  {
    ArrayList<CookieWeb> cookieList = _cookieList;

    if (cookieList == null) {
      return;
    }
    
    int size = cookieList.size();

    for (int i = 0; i < size; i++) {
      printCookie(os, cookieList.get(i));
    }
  }

  private void printCookie(WriteStream os, CookieWeb cookie)
    throws IOException
  {
    os.print("\r\nset-cookie: ");
    os.print(cookie.name());
    os.print("=");
    os.print(cookie.value());
    
    if (cookie.httpOnly()) {
      os.print("; HttpOnly");
    }
    
    if (cookie.secure()) {
      os.print("; Secure");
    }
  }

  /*
  @Override
  public void fillCookies(OutHeader out) throws IOException
  {
  }
  */

  @Override
  public CookieBuilder cookie(String key, String value)
  {
    CookieBuilderImpl cookieBuilder = new CookieBuilderImpl(key, value);
    
    requestHttp().cookie(cookieBuilder);
    
    if (secure() != null) {
      cookieBuilder.secure(true);
    }

    cookieBuilder.httpOnly(true);
    cookieBuilder.path("/");

    return cookieBuilder;
  }

  //
  // service methods

  //
  // implementation methods
  //

  /*
  @Override
  public void requestProxy(RequestProxy proxy)
  {
    _requestProxy = proxy;
  }
  */

  RequestProxy requestProxy()
  {
    return route().requestProxy();
  }

  @Override
  public StateConnection onCloseRead()
  {
    _state = _state.toCloseRead();

    switch (_state) {
    case CLOSE_READ:
      return StateConnection.CLOSE_READ_S;

    case CLOSE:
      return StateConnection.CLOSE;

    default:
      return StateConnection.CLOSE;
    }
  }

  //@Override
  public void onCloseWrite()
  {
    StateRequest state = _state;

    _state = state.toCloseWrite();

    ConnectionHttp connHttp = connHttp();

    //RequestBaratineImpl reqNext = null;//next();

    connHttp.onWriteEnd();

    /*
    if (reqNext != null) {
      reqNext.writePending();
    }
    */

    switch (state) {
    case CLOSE_READ:
      connHttp().connTcp().proxy().requestWake();
      break;
    }

    //_next = null;
    /*
    RequestHttpBase requestHttp = _requestHttp;
    _requestHttp = null;

    if (requestHttp != null) {
      requestHttp.freeSelf();
    }
    */
  }

  /*
  private void writePending()
  {
    RequestHttpBase reqHttp = requestHttp();

    if (reqHttp != null) {
      reqHttp.writePending();
    }
  }
  */

  /*
  @Override
  public StateConnection service()
  {
    try {
      StateConnection nextState = _state.service(this);

      //return StateConnection.CLOSE;
      return nextState;
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      e.printStackTrace();

      toClose();

      return StateConnection.CLOSE_READ_A;
    }
  }
  */

  private StateConnection readBody()
  {
    try {
      //_requestHttp.readBodyChunk(this);

      /*
      if (! _isBodyComplete) {
        // XXX: only on non-upgrade and non-101
        _requestHttp.readBodyChunk(this);
      }
      */

      if (! _isBodyComplete) {
        return StateConnection.READ;
      }

      requestProxy().bodyComplete(this);

      ServiceRef.flushOutboxAndExecuteLast();

      if (requestHttp().isKeepalive()) {
        return StateConnection.READ;
      }
      else {
        return StateConnection.CLOSE_READ_A;
      }
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);

      e.printStackTrace();

      toClose();

      return StateConnection.CLOSE;
    }
  }

  //
  // body callback
  //

  @Override
  public void onBodyChunk(TempBuffer tBuf)
  {
    if (_bodyHead == null) {
      _bodyHead = _bodyTail = tBuf;
    }
    else {
      _bodyTail.next(tBuf);
      _bodyTail = tBuf;
    }
  }

  //@Override
  public void bodyComplete()
  {
    _isBodyComplete = true;

    //connHttp().requestComplete();

    if (_bodyResult != null) {
      _bodyResult.ok(body(_bodyType));
    }
  }

  private void toClose()
  {
  }

  /*
  @Override
  public void views(List<ViewRef<?>> views)
  {
    _views = views;
  }
  */

  @Override
  public String toString()
  {
    InvocationBaratine invocation = invocation();

    if (invocation != null) {
      return getClass().getSimpleName() + "[" + invocation.uri() + "]";
    }
    else {
      return getClass().getSimpleName() + "[null]";
    }
  }
  
  private static class SecureWebImpl implements SecureWeb
  {
    private ConnectionTcp _connTcp;
    
    SecureWebImpl(ConnectionTcp connTcp)
    {
      _connTcp = connTcp;
    }

    @Override
    public String protocol()
    {
      return _connTcp.secureProtocol();
    }

    @Override
    public String cipherSuite()
    {
      return _connTcp.cipherSuite();
    }
    
    @Override
    public String toString()
    {
      return (getClass().getSimpleName()
             + "[" + protocol() + "," + cipherSuite() + "]");
    }
  }
  
  private class CookieBuilderImpl implements CookieBuilder, CookieWeb
  {
    private final String _key;
    private final String _value;
    
    private boolean _httpOnly;
    private boolean _secure;
    
    private String _path;
    private String _domain;
    
    CookieBuilderImpl(String key, String value)
    {
      Objects.requireNonNull(key);
      Objects.requireNonNull(value);
      
      _key = key;
      _value = value;
    }

    @Override
    public String name()
    {
      return _key;
    }

    @Override
    public String value()
    {
      return _value;
    }

    @Override
    public String domain()
    {
      return _domain;
      
    }
    @Override
    public CookieBuilderImpl domain(String domain)
    {
      _domain = domain;
      
      return this;
    }
    
    @Override
    public CookieBuilder httpOnly(boolean isHttpOnly)
    {
      _httpOnly = isHttpOnly;
      
      return this;
    }

    @Override
    public boolean httpOnly()
    {
      return _httpOnly;
    }

    @Override
    public String path()
    {
      return _path;
      
    }
    @Override
    public CookieBuilderImpl path(String path)
    {
      _path = path;
      
      return this;
    }
    
    @Override
    public CookieBuilder secure(boolean isSecure)
    {
      _secure = isSecure;
      
      return this;
    }

    @Override
    public boolean secure()
    {
      return _secure;
    }
  }

  private enum StateRequest
  {
    ACCEPT {
      @Override
      public StateConnection service(RequestBaratineImpl request)
      {
        //return request.accept();
        return null;
      }

      /*
      @Override
      public StateRequest toUpgrade() { return StateRequest.UPGRADE; }
      */
    },

    ACTIVE {
      /*
      @Override
      public StateRequest toUpgrade() { return StateRequest.UPGRADE; }
      */

      @Override
      public StateConnection service(RequestBaratineImpl request)
      {
        return request.readBody();
      }

    },

    UPGRADE,

    CLOSE_READ {
      @Override
      public StateRequest toCloseWrite() { return CLOSE; }
    },

    CLOSE_WRITE {
      @Override
      public StateRequest toCloseRead() { return CLOSE; }

      //@Override
      //public boolean isCloseWrite() { return true; }
    },

    CLOSE {
      @Override
      public StateRequest toCloseRead() { return this; }

      @Override
      public StateRequest toCloseWrite() { return this; }

      //@Override
      //public boolean isCloseWrite() { return true; }
    };

    public StateConnection service(RequestBaratineImpl requestBaratineImpl)
    {
      throw new IllegalStateException(toString());
    }

    /*
    public StateRequest toUpgrade()
    {
      throw new IllegalStateException(toString());
    }
    */

    public StateRequest toCloseRead()
    {
      return CLOSE_READ;
    }

    public StateRequest toCloseWrite()
    {
      return CLOSE_WRITE;
    }

    /*
    public boolean isCloseWrite()
    {
      return false;
    }
    */
  }
}
