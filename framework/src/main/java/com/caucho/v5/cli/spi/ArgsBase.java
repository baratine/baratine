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

package com.caucho.v5.cli.spi;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.caucho.v5.cli.command.CommandExit;
import com.caucho.v5.cli.command.HelpCommand;
import com.caucho.v5.cli.command.CommandVersion;
import com.caucho.v5.cli.server.BootArgumentException;
import com.caucho.v5.cli.shell_old.EnvCliOld;
import com.caucho.v5.cli.spi.OptionCommandLine.ArgsType;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.Configs;
import com.caucho.v5.config.UserMessage;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.util.CauchoUtil;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.Version;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;
import com.caucho.v5.vfs.WriteStream;

import io.baratine.config.Config;

public class ArgsBase
{
  private static L10N L = new L10N(ArgsBase.class);

  private static final Logger log
    = Logger.getLogger(ArgsBase.class.getName());

  private static final CommandManager<ArgsBase> _managerCommandLine;

  private final String[] _argv;
  
  private final ProgramInfo _programInfo;
  
  private final long _startTime;
  
  private final EnvCliOld _env;
  
  private final Config.ConfigBuilder _envBuilder = Configs.config();

  private HashMap<String,ArgValueCli> _valueMap = new HashMap<>();
  private ArrayList<String> _tailArgs = new ArrayList<>();
  
  private String []_defaultArgs;
  
  private Command<?> _command;
  
  private PathImpl _javaHome;
  private PathImpl _homeDirectory;
  
  private boolean _isVerbose;
  private boolean _isQuiet;
  private boolean _isHelp;

  private boolean _is64bit;
  
  /**
   * For the commandManager init.
   */
  protected ArgsBase()
  {
    _argv = null;
    _startTime = 0;
    
    _programInfo = null;
    _env = null;
  }

  public ArgsBase(String[] argv, ProgramInfo programInfo)
  {
    this(new EnvCliOld(), argv, programInfo);
  }
  
  public ArgsBase(EnvCliOld env, String[] argv, ProgramInfo programInfo)
  {
    Objects.requireNonNull(env);
    Objects.requireNonNull(argv);
    Objects.requireNonNull(programInfo);
    
    _env = env;
    _programInfo = programInfo;
    
    _startTime = CurrentTime.currentTime();
    
    _argv = fillArgv(argv);

    initDefaults();
  }
  
  public EnvCliOld envCli()
  {
    return _env;
  }
  
  public WriteStream getOut()
  {
    return envCli().getOut();
  }
  
  protected boolean isEmbedded()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
  
    return (loader instanceof EnvironmentClassLoader);
  }

  public void property(String envName, String value)
  {
    _envBuilder.add(envName, value);
  }
  
  public Config config()
  {
    return _envBuilder.get();
  }
  
  public void config(Config env)
  {
    _envBuilder.add(env);
  }


  public ExitCode parse()
  {
    CommandLineParser parser = new CommandLineParser();
    
    return parser.parseCommandLine(this);
  }

  public void copyFrom(ArgsBase args)
  {
    _isQuiet = args._isQuiet;
    _isVerbose = args._isVerbose;
  }

  public ExitCode doCommand()
  {
    ExitCode code = parse();
    
    if (code != ExitCode.OK) {
      return code;
    }
    
    Command command = (Command) getCommand();
    
    return command.doCommand(this);
  }
  
  public void doMain()
  {
    try {
      envCli().initLogging();
      initHomeClassPath();
      
      final String jvmVersion = System.getProperty("java.runtime.version");

      if ("1.8".compareTo(jvmVersion) > 0) {
        throw new ConfigException(L.l("{0} requires Java 1.8 or later but was started with {1}",
                                      getDisplayName(), jvmVersion));
      }

      ExitCode code = doCommand();

      envCli().exit(code);
    } catch (BootArgumentException e) {
      printException(e);

      envCli().exit(ExitCode.UNKNOWN_ARGUMENT);
    } catch (ConfigException e) {
      printException(e);

      envCli().exit(ExitCode.BAD_CONFIG);
    } catch (Exception e) {
      Throwable cause;
      
      for (cause = e;
           cause != null && cause.getCause() != null;
           cause = cause.getCause()) {
        if (cause instanceof UserMessage) {
          break;
        }
      }
      
      if (cause instanceof BindException) {
        System.err.println(e.getMessage());

        log.severe(e.toString());

        log.log(Level.FINE, e.toString(), e);

        envCli().exit(ExitCode.BIND);
      }

      printException(e);
      
      envCli().exit(ExitCode.UNKNOWN);
    }
  }

  private void printException(Throwable e)
  {
    System.err.println(e.getMessage());

    if (e.getMessage() == null
        || isVerbose()) {
      e.printStackTrace();
    }
  }
  
  protected ProgramInfo getProgramInfo()
  {
    return _programInfo;
  }
  
  public final String getProgramName()
  {
    return getProgramInfo().getProgramName();
  }
  
  public final String getCommandName()
  {
    return getProgramInfo().getCommandName();
  }
  
  protected final String getMainJarName()
  {
    return getProgramInfo().getMainJarName();
  }
  
  public final String getDisplayName()
  {
    return getProgramInfo().getDisplayName();
  }

  public String []getRawArgv()
  {
    return _argv;
  }

  public PathImpl getJavaHome()
  {
    return _javaHome;
  }

  public PathImpl getHomeDirectory()
  {
    return _homeDirectory;
  }

  public String[] getArgv()
  {
    return _argv;
  }

  public boolean isVerbose()
  {
    return _isVerbose;
  }

  public boolean isQuiet()
  {
    return _isQuiet;
  }

  public void setQuiet(boolean isQuiet)
  {
    _isQuiet = isQuiet;
  }

  protected void setHomeDirectory(PathImpl homeDirectory)
  {
    _homeDirectory = homeDirectory;

    System.setProperty(getProgramName() + ".home", 
                       homeDirectory.getNativePath());
  }

  public boolean is64Bit()
  {
    return _is64bit;
  }

  public Command<?> getCommand()
  {
    return _command;
  }

  public void setCommand(Command<?> command)
  {
    _command = command;
  }
  
  public OptionCommandLine<?> getOption(String name)
  {
    return getCommandManager().getOption(name);
  }
  
  public void addOption(String name, ArgValueCli value)
  {
    _valueMap.put(name, value);
  }
  
  public long getStartTime()
  {
    return _startTime;
  }

  /**
   * Adds an arg after the command and any options.
   */
  public void addTailArg(String arg)
  {
    _tailArgs.add(arg);
  }

  public ArrayList<String> getTailArgs()
  {
    return _tailArgs;
  }

  public String getTail(int index)
  {
    if (index < _tailArgs.size()) {
      return _tailArgs.get(index);
    }
    else {
      return null;
    }
  }

  public void setDefaultArgs(String[] args)
  {
    _defaultArgs = args;
  }

  public String []getDefaultArgs()
  {
    return _defaultArgs;
  }

  public boolean isHelp()
  {
    return _isHelp;
  }

  public void setHelp(boolean isHelp)
  {
    _isHelp = isHelp;
  }

  public void set64Bit(boolean isSet)
  {
    _is64bit = isSet;
  }

  public void setVerbose(boolean isSet)
  {
    _isVerbose = isSet;
    
    if (isSet) {
      Logger.getLogger("").setLevel(Level.CONFIG);
    }
  }

  public Map<String,Command<?>> getCommandMap()
  {
    return (Map) getCommandManager().getCommandMap();
  }

  protected void initDefaults()
  {
    _javaHome = VfsOld.lookup(System.getProperty("java.home"));

    _is64bit = CauchoUtil.is64Bit();

    setHomeDirectory(calculateHomeDirectory());
  }
  
  public void initHomeClassPath()
  {
    try {
      ClassLoader loader = getClass().getClassLoader();

      if (loader instanceof URLClassLoader) {
        URLClassLoader urlLoader = (URLClassLoader) loader;
        
        Method addURL = findAddURL();

        PathImpl lib = getHomeDirectory().lookup("lib");
        String classPath = System.getProperty("java.class.path");
        
        for (String file : lib.list()) {
          if (classPath.contains(file)) {
            continue;
          }
          
          try {
            URL url = new URL("file://" + lib.lookup(file).getFullPath());

            addURL.invoke(urlLoader, url);
          } catch (Exception e) {
            System.err.println("Exn: " + e);
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private static Method findAddURL()
  {
    for (Method method : URLClassLoader.class.getDeclaredMethods()) {
      if (method.getName().equals("addURL")
          && method.getParameterTypes().length == 1) {
        method.setAccessible(true);
        
        return method;
      }
    }
    
    return null;
  }

  protected void init()
  {
  }
  
  public String getArg(String arg)
  {
    ArgValueCli value = _valueMap.get(arg);
    
    if (value != null) {
      return value.getString();
    }
    else {
      // return getArgImpl(arg);
      
      return null;
    }
  }
  
  public String getArg(String arg, String valueDefault)
  {
    ArgValueCli value = _valueMap.get(arg);
    
    if (value != null) {
      return value.getString();
    }
    else {
      // return getArgImpl(arg);
      
      return valueDefault;
    }
  }
  
  public Iterable<String> getArgList(String arg)
  {
    ArgValueCli value = _valueMap.get(arg);
    
    if (value != null) {
      return value.getList();
    }
    else {
      return new ArrayList<>();
    }
  }

  public String getArgImpl(String arg)
  {
    for (int i = 0; i + 1 < _argv.length; i++) {
      if (_argv[i].equals(arg) || _argv[i].equals("-" + arg)) {
        return _argv[i + 1];
      }
    }

    return null;
  }

  public boolean getArgFlag(String arg)
  {
    ArgValueCli value = _valueMap.get(arg);
    
    if (value != null) {
      return value.getBoolean();
    }
    else {
      return false;
    }
  }

  public boolean getArgBoolean(String arg)
  {
    return getArgBoolean(arg, false);
  }
  
  public boolean getArgBoolean(String arg, boolean defaultValue)
  {
    String value = getArg(arg);

    if (value == null) {
      return defaultValue;
    }

    if ("no".equals(value) || "false".equals(value)) {
      return false;
    }
    else {
      return true;
    }
  }

  public int getArgInt(String arg, int defaultValue)
  {
    String value = getArg(arg);

    if (value == null) {
      return defaultValue;
    }

    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      BootArgumentException e1
        = new BootArgumentException(L.l("{0} argument is not a number '{1}'",
                                        arg, value));
      e1.setStackTrace(e.getStackTrace());

      throw e1;
    }
  }

  public double getArgDouble(String arg, double defaultValue)
  {
    String value = getArg(arg);

    if (value == null)
      return defaultValue;

    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      BootArgumentException e1
        = new BootArgumentException(L.l("{0} argument is not a number '{1}'",
                                        arg, value));
      e1.setStackTrace(e.getStackTrace());

      throw e;
    }
  }

  public boolean hasOption(String option)
  {
    for (String arg : _argv) {
      if (option.equals(arg)) {
        return true;
      }
    }

    return false;
  }

  /**
   * finds first argument that follows no dash prefixed token
   * @return
   */
  public String getDefaultArg()
  {
    String defaultArg = null;

    if (_defaultArgs.length > 0) {
      defaultArg = _defaultArgs[0];
    }

    return defaultArg;
  }
  
  public CommandManager<?> getCommandManager()
  {
    return _managerCommandLine;
  }

  //
  // Utility static methods
  //

  String []fillArgv(String []argv)
  {
    ArrayList<String> args = new ArrayList<String>();

    EnvLoader.init();

    String []jvmArgs = getJvmArgs();

    if (jvmArgs != null) {
      for (int i = 0; i < jvmArgs.length; i++) {
        String arg = jvmArgs[i];

        if (args.contains(arg)) {
          continue;
        }
        
        if (arg.startsWith("-Djava.class.path=")) {
          // IBM JDK
        }
        else if (arg.startsWith("-D")) {
          int eqlSignIdx = arg.indexOf('=');
          if (eqlSignIdx == -1) {
            args.add("-J" + arg);
          } else {
            String key = arg.substring(2, eqlSignIdx);
            String value = System.getProperty(key);

            if (value == null)
              value = "";

            args.add("-J-D" + key + "=" + value);
          }
        }
      }
    }

    for (int i = 0; i < argv.length; i++) {
      args.add(argv[i]);
    }

    argv = new String[args.size()];

    args.toArray(argv);

    return argv;
  }
  
  String []getJvmArgs()
  {
    try {
      MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
      ObjectName name = new ObjectName("java.lang:type=Runtime");
      
      return (String []) mbeanServer.getAttribute(name, "InputArguments");
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
      
      return null;
    }
    
  }

  PathImpl calculateHomeDirectory()
  {
    return calculateHomeDirectory(getMainJarName());
  }
  
  PathImpl calculateHomeDirectory(String jarFile)
  {
    String homePath = System.getProperty(getProgramName() + ".home");
    
    if (homePath != null) {
      return VfsOld.lookup(homePath);
    }

    // find the resin.jar as described by the classpath
    // this may differ from the value given by getURL() because getURL()
    // unwinds symbolic links.
    String classPath = System.getProperty("java.class.path");

    if (jarFile != null && classPath.indexOf(jarFile) >= 0) {
      int q = classPath.indexOf(jarFile) + jarFile.length();
      int p = classPath.lastIndexOf(File.pathSeparatorChar, q - 1);

      String mainJar;

      if (p >= 0) {
        mainJar = classPath.substring(p + 1, q);
      }
      else {
        mainJar = classPath.substring(0, q);
      }
      
      PathImpl jar = VfsOld.lookup(mainJar);

      if (jar.canRead()) {
        PathImpl libDir = jar.getParent();
        PathImpl homeDir = libDir.getParent();

        
        if (homeDir.lookup("lib").equals(libDir)) {
          return homeDir;
        }
        else {
          return libDir;
        }
      }
    }

    PathImpl pwd = CauchoUtil.getHomeDir();
    
    if (pwd != null) {
      return pwd;
    }

    throw new RuntimeException(L.l("{0}/{1}: can't discover home.dir for {2} in {3}",
                                   getDisplayName(),
                                   Version.getVersion(),
                                   CauchoUtil.class.getName()));
  }
  
  protected void initCommands(CommandManager<?> manager)
  {
    /*
    manager.addOption(new HomeDir()).alias("home-directory")
           .alias(getProgramName() + "-home")
           .alias("-" + getProgramName() + "-home");
           */
    manager.addOption(new HomeDir()).alias("home-directory");

    manager.addOption(new VerboseFine()).tiny("v").type(ArgsType.DEBUG);
    manager.addOption(new VerboseFiner()).tiny("vv").type(ArgsType.DEBUG);
    manager.addOption(new VerboseFinest()).hide().tiny("vvv").type(ArgsType.DEBUG);
    manager.addOption(new VerboseInfo()).hide().tiny("vi").type(ArgsType.DEBUG);
    
    manager.addOption(new Quiet()).hide().type(ArgsType.DEBUG);

    manager.addCommand(new CommandVersion());
    manager.addCommand(new HelpCommand());
    manager.addCommand(new CommandExit()); // XXX: .hide();
  }
  
  static class HomeDir extends OptionCommandLine.Base<ArgsBase>
  {
    @Override
    public String getDescription()
    {
      return "installation directory";
    }
    
    @Override
    public String getValueDescription()
    {
      return "DIR";
    }
    
    @Override
    public int parse(ArgsBase args, String[] argv, int index)
      throws CommandArgumentException
    {
      args.setHomeDirectory(VfsOld.lookup(argv[index + 1]));
      argv[index + 1] = args.getHomeDirectory().getFullPath();
      
      property(args, argv[index + 1]);
      
      return index + 1;
    }
  }
  
  /**
   * --quiet disables the standard headers 
   */
  private static class Quiet extends OptionCommandLine.Base<ArgsBase>
  {
    @Override
    public boolean isFlag()
    {
      return true;
    }
    
    @Override
    public int parse(ArgsBase args, String[] argv, int index)
        throws CommandArgumentException
    {
      args.setQuiet(true);
     
      // baratine/8610
      Logger.getLogger("").setLevel(Level.OFF);

      return index;
    }
  }

  abstract private static class VerboseBase extends OptionCommandLine.Base<ArgsBase>
  {
    protected void startLogging(ArgsBase args)
      throws CommandArgumentException
    {
      args.envCli().initLogging();
    }
  }
  
  private static class VerboseInfo extends VerboseBase
  {
    public String getName()
    {
      return "verbose";
    }
    
    @Override
    public boolean isFlag()
    {
      return true;
    }
    
    @Override
    public String getDescription()
    {
      return "verbose output (fine)";
    }
    
    @Override
    public int parse(ArgsBase args, String[] argv, int index)
      throws CommandArgumentException
    {
      // args.setVerbose(true);
      Logger.getLogger("").setLevel(Level.INFO);
      
      startLogging(args);
      
      return index;
    }
  }
  
  private static class VerboseFine extends VerboseBase
  {
    public String getName()
    {
      return "verbose";
    }
    
    @Override
    public boolean isFlag()
    {
      return true;
    }
    
    @Override
    public String getDescription()
    {
      return "verbose output (fine)";
    }
    
    @Override
    public int parse(ArgsBase args, String[] argv, int index)
      throws CommandArgumentException
    {
      args.setVerbose(true);
      Logger.getLogger("").setLevel(Level.FINE);
      
      startLogging(args);
      
      return index;
    }
  }
  
  private static class VerboseFiner extends VerboseBase
  {
    @Override
    public String getName()
    {
      return "verbose-finer";
    }
    
    @Override
    public boolean isFlag()
    {
      return true;
    }
    
    @Override
    public String getDescription()
    {
      return "verbose output (finer)";
    }
    
    @Override
    public int parse(ArgsBase args, String[] argv, int index)
      throws CommandArgumentException
    {
      args.setVerbose(true);

      Logger.getLogger("").setLevel(Level.FINER);

      startLogging(args);

      return index;
    }
  }
  
  private static class VerboseFinest extends VerboseBase
  {
    @Override
    public String getName()
    {
      return "verbose-finest";
    }
    
    @Override
    public boolean isFlag()
    {
      return true;
    }
    
    @Override
    public String getDescription()
    {
      return "verbose output (finest)";
    }
    
    @Override
    public int parse(ArgsBase args, String[] argv, int index)
      throws CommandArgumentException
    {
      args.setVerbose(true);

      Logger.getLogger("").setLevel(Level.FINEST);
      
      startLogging(args);

      return index;
    }
  }

  static {
    _managerCommandLine = new CommandManager<>();
    
    ArgsBase args = new ArgsBase();
    
    args.initCommands(_managerCommandLine);
  }
}
