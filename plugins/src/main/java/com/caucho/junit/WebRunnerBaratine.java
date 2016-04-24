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
