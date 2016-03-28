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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.http.protocol.ConnectionHttp;
import com.caucho.v5.http.protocol.OutResponseBase;
import com.caucho.v5.http.protocol.RequestFacadeBase;
import com.caucho.v5.http.protocol.RequestHttpBase;
import com.caucho.v5.http.protocol.RequestHttpState;
import com.caucho.v5.http.protocol.RequestUpgrade;
import com.caucho.v5.http.protocol.WebCookie;
import com.caucho.v5.http.protocol2.OutHeader;
import com.caucho.v5.http.websocket.WebSocketBaratineImpl;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.TempInputStream;
import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.network.port.ConnectionProtocol;
import com.caucho.v5.network.port.StateConnection;
import com.caucho.v5.util.Base64Util;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.RandomUtil;
import com.caucho.v5.util.Utf8Util;
import com.caucho.v5.web.CookieWeb;

import io.baratine.config.Config;
import io.baratine.inject.Injector;
import io.baratine.io.Buffer;
import io.baratine.pipe.Pipe;
import io.baratine.service.Result;
import io.baratine.service.ServiceException;
import io.baratine.service.ServiceRef;
import io.baratine.web.BodyReader;
import io.baratine.web.Form;
import io.baratine.web.HttpStatus;
import io.baratine.web.MultiMap;
import io.baratine.web.OutWeb;
import io.baratine.web.RequestWeb;
import io.baratine.web.ServiceWebSocket;


/**
 * User facade for baratine http requests.
 */
public final class RequestBaratineImpl extends RequestFacadeBase
  implements RequestBaratine
{
  private static final L10N L = new L10N(RequestBaratineImpl.class);
  private static final Logger log
    = Logger.getLogger(RequestBaratineImpl.class.getName());

  private static final String FORM_TYPE = "application/x-www-form-urlencoded";

  private ConnectionHttp _connHttp;

  //private RequestHttpBase _requestHttp;
  private RequestHttpState _requestState;

  //private InvocationBaratine _invocation;

  private RequestUpgrade _upgrade;
  private int _status = 200;
  private String _statusMessage = "ok";
  private String _contentType;

  private List<ViewRef<?>> _views;

  private ArrayList<CookieWeb> _cookieList = new ArrayList<>();

  private StateRequest _state = StateRequest.ACCEPT;

  private boolean _isBodyComplete;
  private TempBuffer _bodyHead;
  private TempBuffer _bodyTail;

  // callback for waiting for a body
  private Class<?> _bodyType;
  private Result<Object> _bodyResult;
  private String _bodyParamName;
  private RequestProxy _requestProxy;

  private RequestBaratineImpl _next;
  private RequestBaratineImpl _prev;

  private FormImpl _form;

  public RequestBaratineImpl(ConnectionHttp connHttp)
  {
    Objects.requireNonNull(connHttp);
    _connHttp = connHttp;

    //if (true) throw new UnsupportedOperationException();

    //RequestHttpBase requestHttp = null;
    //_requestHttp = requestHttp;
  }

  public void init(RequestHttpState requestState)
  {
    _requestState = requestState;
  }

  public ConnectionHttp connHttp()
  {
    return _connHttp;
  }

  public RequestHttpBase requestHttp()
  {
    return requestState().requestHttp();
  }

  public RequestHttpState requestState()
  {
    return _requestState;
  }

  public InvocationBaratine invocation()
  {
    return requestState().invocation();
  }

  @Override
  public WebApp webApp()
  {
    return invocation().webApp();
  }

  private ServicesAmp services()
  {
    return webApp().serviceManager();
  }

  @Override
  public String addressRemote()
  {
    return requestHttp().getRemoteAddr();
  }

  @Override
  public String method()
  {
    return requestHttp().getMethod();
  }

  @Override
  public String header(String key)
  {
    return requestHttp().getHeader(key);
  }


  @Override
  public String uri()
  {
    return invocation().getURI();
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
    for (WebCookie cookie : requestHttp().getCookies()) {
      if (cookie.name().equals(key)) {
        return cookie.value();
      }
    }

    return null;
  }

  @Override
  public Reader getReader()
  {
    throw new UnsupportedOperationException();
    /*
    try {
      //return requestHttp().getReader();
      throw new UnsupportedOperationException();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    */
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

  @Override
  public ServiceRefAmp session(String name)
  {
    ServicesAmp services = services();

    if (name.indexOf('/') >= 0) {
      throw new IllegalArgumentException(name);
    }

    String sessionId = cookie("JSESSIONID");

    if (sessionId == null) {
      sessionId = generateSessionId();

      cookie("JSESSIONID", sessionId);
    }
    
    System.out.println("SELP: " + services.service("session:///" + name));

    return services.service("session:///" + name + "/" + sessionId);
  }

  @Override
  public <X> X session(Class<X> type)
  {
    String address = services().address(type);

    return sessionImpl(address + "/").as(type);
  }

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

    Base64Util.encode(sb, webApp().nextId());
    Base64Util.encode(sb, RandomUtil.getRandomLong());

    return sb.toString();
  }

  //
  // injection methods
  //

  /*
  @Override
  public <T> T instance(Class<T> type, Annotation ...qualifiers)
  {
    InjectManagerAmp inject = InjectManagerAmp.current();

    return inject.instance(type, qualifiers);
  }
  */

  @Override
  public Injector inject()
  {
    WebApp webApp = invocation().webApp();

    if (webApp != null) {
      return webApp.inject();
    }
    else {
      return InjectorAmp.current();
    }
  }

  /*
  @Override
  public ServiceManager services()
  {
    return ServiceManagerAmp.current();
  }
  */

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
  @Override
  public void upgrade(ConnectionProtocol upgrade)
  {
    _requestState.upgrade(upgrade);
  }

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
  public void finishRequest()
  {
    /*
    try {
      //getRequestHttp().finishInvocation();
      //getRequestHttp().getResponse().finishRequest();

      //getRequestHttp().finishRequest();
      //getRequestHttp().getResponse().finishRequest();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    */
  }

  //
  // RequestFacade methods
  //

  @Override
  public String getMethod()
  {
    return requestHttp().getMethod();
  }

  @Override
  public String getHeader(String key)
  {
    return requestHttp().getHeader(key);
  }

  /*
  @Override
  public void read(Pipe<Buffer> reader, int prefetch)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  @Override
  public String protocol()
  {
    return requestHttp().getScheme();
  }

  @Override
  public String host()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int port()
  {
    return requestHttp().getLocalPort();
  }

  @Override
  public String query()
  {
    return invocation().getQueryString();
  }

  @Override
  public String query(String name)
  {
    return queryMap().getFirst(name);
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
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public <X> void attribute(X value)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public InetSocketAddress ipRemote()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public InetSocketAddress ipLocal()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String ip()
  {
    return connHttp().connTcp().getRemoteHost();
  }

  @Override
  public boolean secure()
  {
    return false;
  }

  @Override
  public X509Certificate[] certs()
  {
    return null;
  }

  @Override
  public <X> X body(Class<X> type, String paramName)
  {
    Objects.requireNonNull(type);

    if (! _isBodyComplete) {
      throw new IllegalStateException(L.l("body cannot be called with incomplete body"));
    }

    if (true) {
      return webApp().bodyResolver().body(this, type, paramName);
    }

    if (InputStream.class.equals(type)) {
      TempInputStream is = new TempInputStream(_bodyHead);
      _bodyHead = _bodyTail = null;

      return (X) is;
    }
    else if (String.class.equals(type)) {
      TempInputStream is = new TempInputStream(_bodyHead);
      _bodyHead = _bodyTail = null;

      try {
        return (X) Utf8Util.readString(is);
      } catch (IOException e) {
        throw new BodyException(e);
      }
    }
    else if (Reader.class.equals(type)) {
      TempInputStream is = new TempInputStream(_bodyHead);

      _bodyHead = _bodyTail = null;

      try {
        return (X) new InputStreamReader(is, "utf-8");
      } catch (IOException e) {
        throw new BodyException(e);
      }
    }
    else if (Form.class.equals(type)) {
      String contentType = header("content-type");

      if (contentType == null || ! contentType.equals(FORM_TYPE)) {
        throw new IllegalStateException(L.l("Form expects {0}", FORM_TYPE));
      }

      TempInputStream is = new TempInputStream(_bodyHead);
      _bodyHead = _bodyTail = null;

      try {
        FormImpl form = new FormImpl();

        FormBaratine.parseQueryString(form, is, "utf-8");

        return (X) form;
      } catch (Exception e) {
        throw new BodyException(e);
      }
    }
    /*
    else if (header("content-type").startsWith("application/json")) {
      TempInputStream is = new TempInputStream(_bodyHead);
      _bodyHead = _bodyTail = null;

      try {
        Reader reader = new InputStreamReader(is, "utf-8");

        JsonReader isJson = new JsonReader(reader);
        return (X) isJson.readObject(type);
      } catch (IOException e) {
        throw new BodyException(e);
      }
    }
    */

    throw new IllegalStateException(L.l("Unknown body type: " + type));
  }

  @Override
  public FormImpl getForm()
  {
    return _form;
  }

  public void setForm(FormImpl form)
  {
    _form = form;
  }

  @Override
  public <X> void body(BodyReader<X> reader, Result<X> result)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public <X> void body(Class<X> type,
                       String paramName,
                       Result<X> result)
  {
    Objects.requireNonNull(type);

    if (_isBodyComplete) {
      result.ok(body(type, paramName));
    }
    else {
      _bodyType = type;
      _bodyResult = (Result) result;
      _bodyParamName = paramName;
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
  public RequestWeb length(long length)
  {
    contentLength(length);

    return this;
  }

  @Override
  public RequestWeb type(String contentType)
  {
    header("content-type", contentType);

    return this;
  }

  @Override
  public RequestWeb encoding(String contentType)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /*
  @Override
  public void ok(Object value)
  {
    getResponse().ok(value);
  }
  */

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

  /*
  @Override
  public void fail(Throwable exn)
  {
    getResponse().fail(exn);
    System.out.println("GR: " + getResponse() + " " + exn);
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
    ok(null);
    //ok();
  }

  @Override
  public void redirect(String address)
  {
    status(HttpStatus.MOVED_TEMPORARILY);
    header("Location", encodeUrl(address));
    type("text/plain; charset=utf-8");

    write("Moved: " + encodeUrl(address));

    ok();
  }

  public String encodeUrl(String address)
  {
    return address;
  }

  @Override
  public OutWeb<Buffer> push(Pipe<Buffer> out)
  {
    throw new UnsupportedOperationException();
  }

  /*
  @Override
  public void handle(Buffer value, Throwable exn, boolean isEnd)
  {
    throw new UnsupportedOperationException();
  }
  */

  @Override
  public RequestBaratine status(HttpStatus status)
  {
    Objects.requireNonNull(status);

    requestHttp().status(status.code(), status.message());

    return this;
  }

  public RequestBaratine status(int code, String message)
  {
    if (code <= 0 || code >= 600) {
      throw new IllegalArgumentException(String.valueOf(code));
    }

    if (message == null || message.isEmpty()) {
      throw new IllegalArgumentException(message);
    }

    _status = code;
    _statusMessage = message;

    requestHttp().status(code, message);

    return this;
  }

  @Override
  public RequestBaratine header(String key, String value)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);

    requestHttp().setHeaderOut(key, value);

    return this;
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
      OutResponseBase out = requestHttp().getOut();

      Utf8Util.write(out, value);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }

    return this;
  }

  @Override
  public OutWeb<Buffer> write(Buffer buffer)
  {
    requestHttp().getOut().write(buffer);

    return this;
  }

  @Override
  public OutWeb<Buffer> write(byte[] buffer, int offset, int length)
  {
    requestHttp().getOut().write(buffer, offset, length);

    return this;
  }

  @Override
  public OutWeb<Buffer> write(String value, String enc)
  {
    try {
      requestHttp().getOut().write(value.getBytes(enc));

      return this;
    } catch (IOException e) {
      throw new IllegalStateException(getClass().getName());
    }
  }

  @Override
  public OutWeb<Buffer> write(char[] buffer, int offset, int length)
  {
    // XXX: hack
    write(new String(buffer, offset, length));

    return this;
  }

  @Override
  public OutWeb<Buffer> write(char[] buffer, int offset, int length, String enc)
  {
    return this;
  }

  @Override
  public Writer writer()
  {
    return new WriterBaratine(this);
  }

  @Override
  public Writer writer(String enc)
  {
    return new WriterBaratineEnc(this, enc);
  }

  @Override
  public OutputStream output()
  {
    throw new UnsupportedOperationException();
  }

  //@Override
  public RequestBaratineImpl flush()
  {
    try {
      OutResponseBase out = requestHttp().getOut();

      out.flush();
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }

    return this;
  }

  public void next(Buffer data)
  {
    try {
      OutResponseBase out = requestHttp().getOut();

      out.write(data);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  // XXX: temp
  @Override
  public PrintWriter getWriter()
  {
    throw new UnsupportedOperationException();
    /*
    PrintWriter writer = _writer;

    if (writer != null) {
      return writer;
    }

    // String encoding = getCharacterEncoding();
    String encoding = null; // getResponse().getEncoding();

    ResponseWriter newWriter = getResponse().getResponsePrintWriter();
    newWriter.init(getResponse().getOut());

    _writer = newWriter;

    if (encoding != null) {
      try {
        getResponse().getOut().setEncoding(encoding);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    return newWriter;
    */
  }
  //
  // response facade methods
  //

  @Override
  public int getStatus()
  {
    return _status;
  }

  @Override
  public String getStatusMessage()
  {
    return _statusMessage;
  }

  @Override
  public String getContentType()
  {
    return _contentType;
  }

  @Override
  public void setContentType(String value)
  {
    _contentType = value;
  }

  @Override
  public RequestBaratine contentLength(long length)
  {
    requestHttp().contentLengthOut(length);

    return this;
  }

  @Override
  public void ok(Object value)
  {
    if (view(value)) {
      return;
    }

    if (viewPrimitives(value)) {
      return;
    }

    log.warning(L.l("{0} does not have a matching view type", value));

    halt(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private boolean view(Object value)
  {
    List<ViewRef<?>> views = _views;

    if (views == null) {
      return false;
    }

    for (ViewRef<?> view : views) {
      if (((ViewRef) view).render(this, value)) {
        return true;
      }
    }

    return false;
  }

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

  @Override
  public final void ok()
  {
    try {
      requestHttp().getOut().close();
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

      write("Internal Server Error: " + exn + "\n");

      writeTrace(exn);
    }

    try {
      requestHttp().getOut().close();
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

  @Override
  public void writeCookies(WriteBuffer os) throws IOException
  {
    ArrayList<CookieWeb> cookieList = _cookieList;

    if (cookieList == null) {
      return;
    }

    for (CookieWeb cookie : cookieList) {
      printCookie(os, cookie);
    }
  }

  private void printCookie(WriteBuffer os, CookieWeb cookie)
    throws IOException
  {
    os.print("\r\nSet-Cookie: ");
    os.print(cookie.name());
    os.print("=");
    os.print(cookie.value());
  }

  @Override
  public void fillCookies(OutHeader out) throws IOException
  {
  }

  @Override
  public RequestWeb cookie(String key, String value)
  {
    requestHttp().cookie(key, value);
    /*
    if (_cookieList == null) {
      _cookieList = new ArrayList<>();
    }

    _cookieList.add(new WebCookie(key, value));
    */

    return this;
  }

  //
  // service methods

  //
  // implementation methods
  //

  /*
  @Override
  public ResponseBaratine getResponse()
  {
    return _response;
  }
  */

  /*
  @Override
  public void invocation(Invocation invocation)
  {
    _invocation = (InvocationBaratine) invocation;
  }
  */

  public void requestProxy(RequestProxy proxy)
  {
    _requestProxy = proxy;
  }

  RequestProxy requestProxy()
  {
    return _requestProxy;
  }

  /*
  @Override
  public StateConnection resume()
  {
    try {
      return super.resume();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);

      return StateConnection.CLOSE;
    }
  }
  */

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
  public void next(ConnectionProtocol next)
  {
    RequestBaratineImpl nextBar = (RequestBaratineImpl) next;
    _next = nextBar;

    if (nextBar != null) {
      nextBar._prev = this;
    }
  }

  public RequestBaratineImpl next()
  {
    return _next;
  }

  public RequestBaratineImpl prev()
  {
    return _prev;
  }

  @Override
  public boolean isPrevCloseWrite()
  {
    RequestBaratineImpl prev = prev();

    if (prev == null) {
      return true;
    }
    else {
      return prev._state.isCloseWrite();
    }
  }

  //@Override
  public void onCloseWrite()
  {
    StateRequest state = _state;

    _state = state.toCloseWrite();

    ConnectionHttp connHttp = connHttp();

    RequestBaratineImpl reqNext = next();

    connHttp.onCloseWrite();

    if (reqNext != null) {
      reqNext.writePending();
    }

    switch (state) {
    case CLOSE_READ:
      connHttp().connTcp().proxy().requestWake();
      break;
    }

    _next = null;
    /*
    RequestHttpBase requestHttp = _requestHttp;
    _requestHttp = null;

    if (requestHttp != null) {
      requestHttp.freeSelf();
    }
    */
  }

  private void writePending()
  {
    RequestHttpBase reqHttp = requestHttp();

    if (reqHttp != null) {
      reqHttp.writePending();
    }
  }

  @Override
  public StateConnection service()
  {
    try {
      StateConnection nextState = _state.service(this);

      //return StateConnection.CLOSE;
      return nextState;

      /*
      if (_invocation == null && getRequestHttp().parseInvocation()) {
        if (_invocation == null) {
          return NextState.CLOSE;
        }
        return _invocation.service(this, getResponse());
      }
      else
      if (_upgrade != null) {
        return _upgrade.service();
      }
      else if (_invocation != null) {
        return _invocation.service(this);
      }
      else {
        return StateConnection.CLOSE;
      }
      */
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      e.printStackTrace();

      toClose();

      return StateConnection.CLOSE_READ_A;
    }
  }

  /*
  private StateConnection accept()
  {
    try {
      _state = StateRequest.ACTIVE;

      _requestHttp = _connHttp.newRequestHttp();

      _invocation = (InvocationBaratine) _requestHttp.parseInvocation(this);

      if (_invocation == null) {
        _state = StateRequest.CLOSE;

        return StateConnection.CLOSE;
      }

      if (! _isBodyComplete) {
        // XXX: only on non-upgrade and non-101
        _requestHttp.readBodyChunk(this);
      }

      StateConnection nextState = _invocation.service(this);

      ServiceRef.flushOutboxAndExecuteLast();

      if (! _isBodyComplete) {
        return StateConnection.READ;
      }
      else if (_state == StateRequest.UPGRADE) {
        return StateConnection.READ;
      }
      else if (_requestHttp.isKeepalive()) {
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
  public void bodyChunk(TempBuffer tBuf)
  {
    if (_bodyHead == null) {
      _bodyHead = _bodyTail = tBuf;
    }
    else {
      _bodyTail.setNext(tBuf);
      _bodyTail = tBuf;
    }
  }

  @Override
  public void bodyComplete()
  {
    _isBodyComplete = true;

    //connHttp().requestComplete();

    if (_bodyResult != null) {
      _bodyResult.ok(body(_bodyType, _bodyParamName));
    }
  }

  private void toClose()
  {
  }

  @Override
  public void views(List<ViewRef<?>> views)
  {
    _views = views;
  }

  @Override
  public String toString()
  {
    InvocationBaratine invocation = invocation();

    if (invocation != null) {
      return getClass().getSimpleName() + "[" + invocation.getURI() + "]";
    }
    else {
      return getClass().getSimpleName() + "[null]";
    }
  }

  private static class WriterBaratine extends Writer
  {
    private RequestBaratineImpl _request;

    WriterBaratine(RequestBaratineImpl request)
    {
      _request = request;
    }

    @Override
    public void write(char []buffer, int offset, int length)
    {
      _request.write(buffer, offset, length);
    }

    @Override
    public void flush()
    {
      _request.flush();
    }

    public void close()
    {
    }
  }

  private static class WriterBaratineEnc extends Writer
  {
    private RequestBaratineImpl _request;
    private String _enc;

    WriterBaratineEnc(RequestBaratineImpl request, String enc)
    {
      _request = request;
      _enc = enc;
    }

    @Override
    public void write(char []buffer, int offset, int length)
    {
      _request.write(buffer, offset, length, _enc);
    }

    @Override
    public void flush()
      throws IOException
    {
      _request.flush();
    }

    public void close()
    {

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

      @Override
      public StateRequest toUpgrade() { return StateRequest.UPGRADE; }
    },

    ACTIVE {
      @Override
      public StateRequest toUpgrade() { return StateRequest.UPGRADE; }

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

      @Override
      public boolean isCloseWrite() { return true; }
    },

    CLOSE {
      @Override
      public StateRequest toCloseRead() { return this; }

      @Override
      public StateRequest toCloseWrite() { return this; }

      @Override
      public boolean isCloseWrite() { return true; }
    };

    public StateConnection service(RequestBaratineImpl requestBaratineImpl)
    {
      throw new IllegalStateException(toString());
    }

    public StateRequest toUpgrade()
    {
      throw new IllegalStateException(toString());
    }

    public StateRequest toCloseRead()
    {
      return CLOSE_READ;
    }

    public StateRequest toCloseWrite()
    {
      return CLOSE_WRITE;
    }

    public boolean isCloseWrite()
    {
      return false;
    }
  }
}
