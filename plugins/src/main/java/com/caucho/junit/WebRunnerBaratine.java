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
 * @author Alex Rojkov
 */

package com.caucho.junit;

import static io.baratine.web.Web.port;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.inject.AnnotationLiteral;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.RandomUtil;
import com.caucho.v5.vfs.VfsOld;
import com.caucho.v5.websocket.WebSocketClient;
import io.baratine.web.Path;
import io.baratine.web.ServiceWebSocket;
import io.baratine.web.Web;
import io.baratine.web.WebServer;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

/**
 * Class {@code WebRunnerBaratine} is a JUnit Runner that will deployed services
 * into a Baratine Web Container and expose them via http endpoints.
 *
 * @see RunnerBaratine
 */
public class WebRunnerBaratine extends BaseRunner<InjectionTestPoint>
{
  private final static Logger log
    = Logger.getLogger(BaseRunner.class.getName());

  private final static L10N L = new L10N(WebRunnerBaratine.class);

  private WebServer _web;
  private ClassLoader _oldLoader;
  private EnvironmentClassLoader _envLoader;

  public WebRunnerBaratine(Class<?> klass) throws InitializationError
  {
    super(klass);
  }

  private Http getHttpConfiguration()
  {
    Http http = this.getTestClass().getAnnotation(Http.class);

    if (http == null)
      http = HttpDefault.INSTANCE;

    return http;
  }

  private String httpHost()
  {
    return getHttpConfiguration().host();
  }

  protected int httpPort()
  {
    return getHttpConfiguration().port();
  }

  private String httpUrl()
  {
    return "http://" + httpHost() + ':' + httpPort();
  }

  private String wsUrl()
  {
    return "ws://" + httpHost() + ':' + httpPort();
  }

  @Override
  public InjectionTestPoint createInjectionPoint(Class type,
                                                 Annotation[] annotations,
                                                 MethodHandle setter)
  {
    return new InjectionTestPoint(type, annotations, setter);
  }

  @Override
  protected void initTestInstance() throws Throwable
  {
    bindFields(_test);
  }

  protected void bindFields(Object test)
    throws Throwable
  {
    for (int i = 0; i < getInjectionTestPoints().size(); i++) {
      InjectionTestPoint ip
        = getInjectionTestPoints().get(i);

      Object obj = resolve(ip);
      ip.inject(test, obj);
    }
  }

  @Override
  protected Object resolve(InjectionTestPoint ip)
  {
    Class type = ip.getType();
    Annotation[] annotations = ip.getAnnotations();
    Object result;

    try {
      if (HttpClient.class.equals(type)) {
        result = new HttpClient(httpPort());
      }
      else if (ServiceWebSocket.class.isAssignableFrom(type)) {
        Path path = null;
        for (Annotation ann : annotations) {
          if (Path.class.isAssignableFrom(ann.annotationType())) {
            path = (Path) ann;
            break;
          }
        }

        Objects.requireNonNull(path);

        result = type.newInstance();

        String urlPath = path.value();

        if (!urlPath.startsWith("/"))
          throw new IllegalStateException(L.l(
            "path value in annotation {0} must start with a '/'",
            path));

        final String wsUrl = wsUrl() + urlPath;

        WebSocketClient.open(wsUrl, (ServiceWebSocket<Object,Object>) result);
      }
      else if (WebRunnerBaratine.class.equals(type)) {
        result = this;
      }
      else {
        throw new IllegalArgumentException(L.l("type {0} is not supported",
                                               type));
      }

      return result;
    } catch (InstantiationException | IllegalAccessException | IOException e) {
      String message = L.l(
        "can't resolve object of type {0} with annotations {1} due to {2}",
        type,
        Arrays.asList(annotations),
        e.getMessage());

      log.log(Level.SEVERE, message, e);

      throw new RuntimeException(message, e);
    }
  }

  @Override
  public void stop()
  {
    _web.close();
  }

  @Override
  public void start()
  {
    start(false);
  }

  public void start(boolean isClean)
  {
    Thread thread = Thread.currentThread();

    if (isClean) {
      _oldLoader = thread.getContextClassLoader();

      _envLoader = EnvironmentClassLoader.create(_oldLoader,
                                                 "test-loader");

      thread.setContextClassLoader(_envLoader);

      ConfigurationBaratine config = getConfiguration();

      if (config.testTime() != -1) {
        TestTime.setTime(config.testTime());
        RandomUtil.setTestSeed(config.testTime());
      }

      Logger.getLogger("").setLevel(Level.FINER);
      Logger.getLogger("javax.management").setLevel(Level.INFO);

      String baratineRoot = getWorkDir();
      System.setProperty("baratine.root", baratineRoot);

      try {
        if (isClean)
          VfsOld.lookup(baratineRoot).removeAll();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    State.clear();

    port(httpPort());

    for (ServiceTest serviceTest : getServices()) {
      Web.include(super.resove(serviceTest.value()));
    }

    networkSetup();

    Web.scanAutoConf();

    _web = Web.start();
  }

  @Override
  public void runChild(FrameworkMethod child, RunNotifier notifier)
  {
    try {
      start(true);

      super.runChild(child, notifier);
    } finally {
      Logger.getLogger("").setLevel(Level.INFO);

      try {
        _envLoader.close();
      } catch (Throwable e) {
        e.printStackTrace();
      }

      Thread.currentThread().setContextClassLoader(_oldLoader);
    }
  }

  protected void networkSetup()
  {
  }

  static class HttpDefault extends AnnotationLiteral<Http> implements Http
  {
    private final static Http INSTANCE = new HttpDefault();

    private HttpDefault()
    {
    }

    @Override
    public String host()
    {
      return "localhost";
    }

    @Override
    public int port()
    {
      return 8080;
    }

    @Override
    public boolean secure()
    {
      return false;
    }
  }
}
