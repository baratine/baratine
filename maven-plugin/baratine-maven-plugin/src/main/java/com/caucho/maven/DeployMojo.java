package com.caucho.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Mojo 'deploy' deploys .bar file to a remote Baratine server.
 *
 * The mojo will look for the .bar artifact of the project to deploy.
 *
 * Mojo should typically be invoked using mvn command with the sequence of goals
 * below.
 *
 * <code>mvn clean package baratine:deploy</code>
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.NONE,
      requiresProject = true, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.RUNTIME)
public class DeployMojo extends BaratineExecutableMojo
{
  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

  /**
   * IP or DNS name of a machine running Baratine server. Defaults to 'localhost'
   */
  @Parameter(defaultValue = "localhost", property = "baratine.host")
  private String host;

  /**
   * Port on which Baratine server is listening. Defaults to 8085.
   */
  @Parameter(defaultValue = "8085", property = "baratine.port")
  private int port;

  /**
   * A user to be used for authentication
   */
  @Parameter(property = "baratine.user")
  private String user;

  /**
   * A password to be used for authentication
   */
  @Parameter(property = "baratine.password")
  private String password;

  public void execute() throws MojoExecutionException, MojoFailureException
  {
    String cp = getBaratine();
    cp = cp + File.pathSeparatorChar;
    cp = cp + getBaratineApi();

    String javaHome = System.getProperty("java.home");

    List<String> command = new ArrayList<>();
    command.add(javaHome + "/bin/java");
    command.add("-cp");
    command.add(cp);
    command.add("com.caucho.cli.baratine.BaratineCommandLine");
    command.add("deploy");

    command.add("--host");
    command.add("localhost");

    command.add("--port");
    command.add(Integer.toString(port));

    if (user != null) {
      command.add("--user");
      command.add(user);
    }

    if (password != null) {
      command.add("--password");
      command.add(password);
    }

    String bar = getBarLocation();
    command.add(bar);

    Process process = null;

    StringBuilder builder = new StringBuilder();
    for (String s : command) {
      builder.append(s).append(' ');
    }

    System.out.println(builder);

    try {
      ProcessBuilder processBuilder = new ProcessBuilder(command);

      processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);
      processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
      processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

      process = processBuilder.start();

      int x = process.waitFor();

      if (x != 0)
        throw new MojoFailureException("failed to deploy bar" + bar);

    } catch (Exception e) {
      String message = String.format("exception deploying %1$s",
                                     bar);
      throw new MojoExecutionException(message, e);
    } finally {
      try {
        if (process != null && !process.waitFor(2, TimeUnit.SECONDS))
          process.destroyForcibly();
      } catch (Exception e) {
      }
    }
  }
}
