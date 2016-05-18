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

package com.caucho.v5.cli.args;

import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.BindException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.caucho.v5.cli.args.OptionCli.ArgsType;
import com.caucho.v5.cli.shell.EnvCli;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.Configs;
import com.caucho.v5.config.UserMessage;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;

public class ArgsBase
{
  private static L10N L = new L10N(ArgsBase.class);

  private static final Logger log
    = Logger.getLogger(ArgsBase.class.getName());

  private static final CommandManager<ArgsBase> _managerCommandLine;

  private final String[] _argv;
  
  private final EnvCli _env;
  
  private final Config.ConfigBuilder _configBuilder = Configs.config();

  private HashMap<String,ValueCliArg> _valueMap = new HashMap<>();
  private ArrayList<String> _tailArgs = new ArrayList<>();
  
  private String []_defaultArgs;
  
  private Command<?> _command;

  private String _commandDefault;
  
  /**
   * For the commandManager init.
   */
  protected ArgsBase()
  {
    _argv = null;
    
    _env = null;
  }

  public ArgsBase(String[] argv)
  {
    this(new EnvCli(), argv);
  }
  
  public ArgsBase(EnvCli env, String[] argv)
  {
    Objects.requireNonNull(env);
    Objects.requireNonNull(argv);
    
    _env = env;
    
    _argv = fillArgv(argv);

    // initDefaults();
  }
  
  public EnvCli env()
  {
    return _env;
  }
  
  public PrintStream out()
  {
    return env().out();
  }
  
  protected boolean isEmbedded()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
  
    return (loader instanceof EnvironmentClassLoader);
  }

  public void property(String envName, String value)
  {
    _configBuilder.add(envName, value);
  }
  
  public Config config()
  {
    return _configBuilder.get();
  }
  
  public void config(Config env)
  {
    _configBuilder.add(env);
  }


  public ExitCode parse()
  {
    CliParser parser = new CliParser();
    
    return parser.parseCommandLine(this);
  }

  public ExitCode doCommand()
  {
    ExitCode code = parse();
    
    if (code != ExitCode.OK) {
      return code;
    }
    
    try {
      Command<ArgsBase> command = (Command) command();
    
      return command.doCommand(this);
    } catch (ConfigException e) {
      log.warning(e.getMessage());
      log.log(Level.FINE, e.toString(), e);
      
      return ExitCode.BIND;
    }
  }
  
  public void doMain()
  {
    try {
      //envCli().initLogging();
      //initHomeClassPath();
      
      final String jvmVersion = System.getProperty("java.runtime.version");

      if ("1.8".compareTo(jvmVersion) > 0) {
        throw new ConfigException(L.l("{0} requires Java 1.8 or later but was started with {1}",
                                      programName(), jvmVersion));
      }

      ExitCode code = doCommand();

      env().exit(code);
    } catch (CommandLineException e) {
      printException(e);

      env().exit(ExitCode.UNKNOWN_ARGUMENT);
    } catch (ConfigException e) {
      printException(e);

      env().exit(ExitCode.BAD_CONFIG);
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

        env().exit(ExitCode.BIND);
      }

      printException(e);
      
      env().exit(ExitCode.UNKNOWN);
    }
  }

  private void printException(Throwable e)
  {
    System.err.println(e.getMessage());

    if (e.getMessage() == null
        || log.isLoggable(Level.FINER)) {
      e.printStackTrace();
    }
  }

  /*
  protected ProgramInfo getProgramInfo()
  {
    return _programInfo;
  }
  */
  
  public final String programName()
  {
    return "Baratine";
  }
  
  public final String getCommandName()
  {
    return "baratine";
  }
  
  /*
  protected final String getMainJarName()
  {
    return getProgramInfo().getMainJarName();
  }
  
  public final String getDisplayName()
  {
    return getProgramInfo().getDisplayName();
  }
  */

  public String []getRawArgv()
  {
    return _argv;
  }

  public String[] getArgv()
  {
    return _argv;
  }
  
  public String commandDefault()
  {
    return _commandDefault;
  }
  
  public void commandDefault(String command)
  {
    Objects.requireNonNull(command);
    
    _commandDefault = command;
  }

  public Command<?> command()
  {
    return _command;
  }

  public void command(Command<?> command)
  {
    _command = command;
  }
  
  public OptionCli<?> getOption(String name)
  {
    return getCommandManager().getOption(name);
  }
  
  public void addOption(String name, ValueCliArg value)
  {
    _valueMap.put(name, value);
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

  public Map<String,Command<?>> commandMap()
  {
    return (Map) getCommandManager().getCommandMap();
  }

  protected void init()
  {
  }
  
  public String getArg(String arg)
  {
    ValueCliArg value = _valueMap.get(arg);
    
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
    ValueCliArg value = _valueMap.get(arg);
    
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
    ValueCliArg value = _valueMap.get(arg);
    
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
    ValueCliArg value = _valueMap.get(arg);
    
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
      CommandLineException e1
        = new CommandLineException(L.l("{0} argument is not a number '{1}'",
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
      CommandLineException e1
        = new CommandLineException(L.l("{0} argument is not a number '{1}'",
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

  public ArgsBase createChild(String[] args)
  {
    return new ArgsBase(args);
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
  
  protected void initCommands(CommandManager<?> manager)
  {
    /*
    manager.addOption(new HomeDir()).alias("home-directory")
           .alias(getProgramName() + "-home")
           .alias("-" + getProgramName() + "-home");
           */
    //manager.addOption(new HomeDir()).alias("home-directory");

    manager.option(new VerboseFine()).tiny("v").type(ArgsType.DEBUG);
    manager.option(new VerboseFiner()).tiny("vv").type(ArgsType.DEBUG);
    manager.option(new VerboseFinest()).hide().tiny("vvv").type(ArgsType.DEBUG);
    manager.option(new VerboseInfo()).hide().tiny("vi").type(ArgsType.DEBUG);
    
    manager.option(new Quiet()).hide().type(ArgsType.DEBUG);

    manager.command(new CommandVersion());
    manager.command(new CommandHelp());
    manager.command(new CommandExit()); // XXX: .hide();
  }
  
  /**
   * --quiet disables the standard headers 
   */
  private static class Quiet extends OptionCli.Base<ArgsBase>
  {
    @Override
    public boolean isFlag()
    {
      return true;
    }
    
    @Override
    public int parse(ArgsBase args, String[] argv, int index)
        throws CommandLineException
    {
      args.property("baratine.quiet", "true");
      //args.setQuiet(true);
     
      // baratine/8610
      Logger.getLogger("").setLevel(Level.OFF);

      return index;
    }
  }

  abstract private static class VerboseBase extends OptionCli.Base<ArgsBase>
  {
  }
  
  private static class VerboseInfo extends VerboseBase
  {
    public String name()
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
      throws CommandLineException
    {
      args.property("logger.level", "info");
      // args.setVerbose(true);
      Logger.getLogger("").setLevel(Level.INFO);
      
      return index;
    }
  }
  
  private static class VerboseFine extends VerboseBase
  {
    public String name()
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
      throws CommandLineException
    {
      args.property("logger.level", "fine");
      Logger.getLogger("").setLevel(Level.FINE);
      
      return index;
    }
  }
  
  private static class VerboseFiner extends VerboseBase
  {
    @Override
    public String name()
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
      throws CommandLineException
    {
      args.property("logger.level", "finer");
      Logger.getLogger("").setLevel(Level.FINER);

      return index;
    }
  }
  
  private static class VerboseFinest extends VerboseBase
  {
    @Override
    public String name()
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
      throws CommandLineException
    {
      args.property("logger.level", "finest");
      Logger.getLogger("").setLevel(Level.FINEST);

      return index;
    }
  }

  static {
    _managerCommandLine = new CommandManager<>();
    
    ArgsBase args = new ArgsBase();
    
    args.initCommands(_managerCommandLine);
  }
}
