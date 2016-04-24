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
import static io.baratine.web.Web.start;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.util.RandomUtil;
import com.caucho.v5.vfs.VfsOld;
import io.baratine.web.Web;
import io.baratine.web.WebServer;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

public class WebRunnerBaratine extends BaseRunner
{
  public WebRunnerBaratine(Class<?> klass) throws InitializationError
  {
    super(klass);
  }

  @Override
  public void runChild(FrameworkMethod child, RunNotifier notifier)
  {
    WebServer web = null;
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    EnvironmentClassLoader envLoader = EnvironmentClassLoader.create(oldLoader,
                                                                     "test-loader");

    try {
      thread.setContextClassLoader(envLoader);

      ConfigurationBaratine config = getConfiguration();

      if (config.testTime() != -1) {
        TestTime.setTime(config.testTime());
        RandomUtil.setTestSeed(config.testTime());
      }

      State.clear();

      Logger.getLogger("").setLevel(Level.FINER);
      Logger.getLogger("javax.management").setLevel(Level.INFO);

      String user = System.getProperty("user.name");
      String baratineRoot = "/tmp/" + user + "/qa";
      System.setProperty("baratine.root", baratineRoot);

      try {
        VfsOld.lookup(baratineRoot).removeAll();
      } catch (Exception e) {
      }

      //
      port(8080);

      for (ServiceTest serviceTest : getServices()) {
        Web.include(serviceTest.value());
      }

      web = start();
      super.runChild(child, notifier);
    } finally {
      Logger.getLogger("").setLevel(Level.INFO);

      try {
        envLoader.close();
      } catch (Throwable e) {
        e.printStackTrace();
      }

      thread.setContextClassLoader(oldLoader);
    }
  }
}
