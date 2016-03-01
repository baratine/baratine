package com.caucho.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Mojo 'run' executes Baratine .bar file. Typically, the plugin is
 * invoked using the following sequence of goals.
 * <p>
 * <code>mvn clean package baratine:run</code>
 * <p>
 * The plugin can run Baratine in an embedded mode or external mode. In embedded
 * mode Baratine is executed within the JVM executing the pom.xml.
 * <p>
 * External mode executes Baratine in a separate JVM.
 * <p>
 * In both cases Baratine is run in an interactive mode and is capable of accepting
 * and executing standard Baratine commands, e.g. jamp-query, deploy, etc.
 * <p>
 * If a script is supplied in configuration Baratine will execute it.
 * <p>
 * A script can automatically close Baratine and proceed with maven goals by
 * executing command 'exit'
 * <p>
 * If your pom.xml doesn't specify script or in the case when the script doesn't
 * finish with exit command a manually entered <code>exit</code> command will
 * stop Baratine and allow maven to proceed to executing the next goal.
 */
@Mojo(name = "run", defaultPhase = LifecyclePhase.PACKAGE,
      requiresProject = true,
      threadSafe = true, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class RunMojo extends BaratineExecutableMojo
{
  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  /**
   * Script to execute.
   * e.g.
   * <code>
   * <p>
   * sleep 1
   * jamp-query /test-service method parameter
   * exit
   * <p>
   * </code>
   */
  @Parameter
  private String script;

  /**
   * When set to true allows skipping of the baratine:run
   */
  @Parameter(property = "baratine.run.skip")
  private boolean skip = false;

  /**
   * Instructs Baratine to execute in verbose mode.
   */
  @Parameter(property = "baratine.run.verbose")
  private boolean verbose = false;

  /**
   * Instructs Baratine to execute in a spawned JVM.
   */
  @Parameter(property = "baratine.run.external")
  private boolean external = false;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException
  {
    if (skip)
      return;

    if (external) {
      executeExternal();
    }
    else {
      try {
        executeInternal();
      } catch (Exception e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }
  }

  private void executeInternal()
    throws MojoFailureException, MojoExecutionException, IOException,
    ScriptException, InterruptedException
  {
    ScriptEngineHandle handle = getScriptEngine();

    if (handle != null)
      executeInternal(handle);
    else {
      getLog().warn(
        "can't obtain Baratine ScriptEngine falling back to executing Baratine in a separate process");
      executeExternal();
    }
  }

  private ScriptEngineHandle getScriptEngine()
  {
    String baratine = getBaratine();
    String baratineApi = getBaratineApi();

    getLog().info("using baratine.jar: " + baratine);
    getLog().info("using baratine-api.jar: " + baratineApi);

    List<URL> urls = new ArrayList<>();

    ScriptEngineHandle handle = null;
    try {
      addUrl(urls, baratine);
      if (baratineApi != null)
        addUrl(urls, baratineApi);

      URLClassLoader cl
        = new URLClassLoader(urls.toArray(new URL[urls.size()]));

      ScriptEngine script
        = new ScriptEngineManager(cl).getEngineByName("baratine");

      handle = new ScriptEngineHandle(script, cl);
    } catch (Exception e) {
      getLog().debug(e.getMessage(), e);
    }

    return handle;
  }

  @SuppressWarnings("deprecation")
  private void addUrl(List<URL> urls, String file) throws MalformedURLException
  {
    urls.add(new File(file).toURL());
  }

  private void executeInternal(ScriptEngineHandle handle)
    throws InterruptedException, ScriptException, MojoExecutionException,
    IOException
  {
    Thread currentThread = Thread.currentThread();

    ClassLoader oldClassloader = currentThread.getContextClassLoader();

    try {
      currentThread.setContextClassLoader(handle.getClassLoader());
      executeInternalImpl(handle.getScriptEngine());
    } finally {
      currentThread.setContextClassLoader(oldClassloader);
    }
  }

  private void executeInternalImpl(ScriptEngine script)
    throws IOException, ScriptException, MojoExecutionException,
    InterruptedException
  {
    cleanWorkDir();

    Object obj = script.eval(getStartCmd());
    System.out.println(obj);
    Set artifacts = project.getDependencyArtifacts();

    for (Object a : artifacts) {
      Artifact artifact = (Artifact) a;
      if (!"bar".equals(artifact.getType()))
        continue;

      String file = getDeployableBar(artifact);

      obj = script.eval(getDeployCmd(file));
      System.out.print(obj);

      Thread.sleep(this.deployInterval * 1000);
    }

    obj = script.eval(getDeployCmd(getBarLocation()));
    System.out.println(obj);

    Thread.sleep(this.deployInterval * 1000);

    boolean isScriptExit = false;

    if (this.script != null) {
      byte[] buf = this.script.getBytes(StandardCharsets.UTF_8);

      int i = 0;

      for (; i < buf.length && buf[i] == ' '; i++) ;

      int start = i;

      getLog().info("running Baratine Script");

      for (; i < buf.length; i++) {
        if (buf[i] == '\n' || i == buf.length - 1) {
          int len = i - start;
          if (i == buf.length - 1)
            len += 1;

          String scriptCmd = new String(buf, start, len);
          System.out.println("baratine> " + scriptCmd);

          obj = script.eval((scriptCmd));
          System.out.println(obj);

          isScriptExit = "exit".equals(scriptCmd);

          if (isScriptExit)
            break;

          for (; i < buf.length && (buf[i] == ' ' || buf[i] == '\n'); i++) ;

          start = i;
        }
      }
    }

    if (!isScriptExit) {
      Console console = System.console();

      String command;
      while ((command = console.readLine("baratine> ")) != null) {
        try {
          obj = script.eval(command);
          System.out.println(obj);

          if ("exit".equals(command))
            break;
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void executeExternal()
    throws MojoExecutionException, MojoFailureException
  {
    String bar = getBarLocation();

    String cp = getBaratine();
    cp = cp + File.pathSeparatorChar;
    cp = cp + getBaratineApi();

    String javaHome = System.getProperty("java.home");

    List<String> command = new ArrayList<>();
    command.add(javaHome + "/bin/java");
    command.add("-cp");
    command.add(cp);
    command.add("com.caucho.cli.baratine.BaratineCommandLine");

    ExecutorService x = Executors.newFixedThreadPool(3);
    Process process = null;

    OutputStream out = null;

    try {
      cleanWorkDir();

      ProcessBuilder processBuilder = new ProcessBuilder(command);
      process = processBuilder.start();

      InputStream in = process.getInputStream();
      InputStream err = process.getErrorStream();
      out = process.getOutputStream();

      x.submit(new StreamPiper(in, System.out));
      x.submit(new StreamPiper(err, System.err));
      x.submit(new StreamPiper(System.in, out));

      out.write(getStartCmd().getBytes());
      out.flush();
      Thread.sleep(2 * 1000);

      Set artifacts = project.getDependencyArtifacts();

      for (Object a : artifacts) {
        Artifact artifact = (Artifact) a;
        if (!"bar".equals(artifact.getType()))
          continue;

        String file = getDeployableBar(artifact);

        out.write(getDeployCmd(file).getBytes());
        out.flush();

        Thread.sleep(this.deployInterval * 1000);
      }

      out.write(getDeployCmd(bar).getBytes());
      out.flush();

      Thread.sleep(deployInterval * 1000);

      if (script != null) {
        byte[] buf = script.getBytes(StandardCharsets.UTF_8);

        int i = 0;

        for (; i < buf.length && buf[i] == ' '; i++) ;

        int start = i;

        getLog().info("running Baratine Script");

        for (; i < buf.length; i++) {
          if (buf[i] == '\n' || i == buf.length - 1) {
            int len = i - start;
            if (i == buf.length - 1)
              len += 1;

            String scriptCmd = new String(buf, start, len);
            System.out.println("baratine>" + scriptCmd);

            out.write((scriptCmd + '\n').getBytes());
            out.flush();
            Thread.sleep(400);

            for (; i < buf.length && (buf[i] == ' ' || buf[i] == '\n'); i++) ;

            start = i;
          }
        }
      }

      getLog().info("Baratine terminated: " + process.waitFor());
    } catch (Exception e) {
      String message = String.format("exception running baratine %1$s",
                                     e.getMessage());
      if (out != null)
        try {

          out.write("stop\nexit\n".getBytes());
        } catch (Throwable t) {
          getLog().debug(t);
        }
      throw new MojoExecutionException(message, e);
    } finally {
      try {
        x.shutdown();
        x.awaitTermination(1, TimeUnit.SECONDS);
      } catch (Throwable t) {
      } finally {
        x.shutdownNow();
      }

      try {
        if (process != null)
          process.waitFor(2, TimeUnit.SECONDS);
      } catch (Throwable t) {
      } finally {
        if (process.isAlive())
          process.destroyForcibly();
      }
    }
  }

  private String getStartCmd()
  {
    String cmd = "start -bg";

    if (this.conf != null)
      cmd += " --conf " + this.conf.getAbsolutePath();

    cmd += " --root-dir " + this.workDir;

    cmd += " -p " + this.port;

    if (this.verbose)
      cmd += " -vv";

    cmd += "\n";

    return cmd;
  }

  private String getDeployCmd(String file)
  {
    return String.format("deploy %1$s\n", file);
  }
}

class ScriptEngineHandle
{
  private ScriptEngine _scriptEngine;

  private ClassLoader _classLoader;

  public ScriptEngineHandle(ScriptEngine scriptEngine,
                            ClassLoader classLoader)
  {
    _scriptEngine = scriptEngine;
    _classLoader = classLoader;
  }

  public ScriptEngine getScriptEngine()
  {
    return _scriptEngine;
  }

  public ClassLoader getClassLoader()
  {
    return _classLoader;
  }
}