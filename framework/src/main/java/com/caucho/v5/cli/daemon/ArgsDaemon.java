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

package com.caucho.v5.cli.daemon;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import com.caucho.v5.cli.daemon.DaemonCommandBase.Conf;
import com.caucho.v5.cli.daemon.DaemonCommandBase.DataDir;
import com.caucho.v5.cli.daemon.DaemonCommandBase.LogDir;
import com.caucho.v5.cli.daemon.DaemonCommandBase.RootDir;
import com.caucho.v5.cli.server.ProgramInfoDaemon;
import com.caucho.v5.cli.shell_old.EnvCliOld;
import com.caucho.v5.cli.spi.ArgsBase;
import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.cli.spi.CommandManager;
import com.caucho.v5.cli.spi.OptionCommandLine;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;

public class ArgsDaemon extends ArgsBase
{
  private static L10N L = new L10N(ArgsDaemon.class);

  private static final CommandManager<ArgsDaemon> _commandManagerDaemon;

  private PathImpl _rootDirectory;
  private PathImpl _rootDirectoryDefault;

  private PathImpl _dataDirectory;

  private PathImpl _logDirectory;

  private PathImpl _configPath;
  private PathImpl _configPathDefault;
  private PathImpl _configPathTemplate;

  private PathImpl _userProperties;

  private String _serverId;
  private String _clusterId;

  private String _serverAddress;
  private int _serverPort = -1;

  private Set<SeedServer> _seedServers = new TreeSet<>();

  private int _watchdogPort;

  private ArrayList<PathImpl> _configPaths = new ArrayList<>();
  //private ContainerProgram _containerProgram = new ContainerProgram();

  private boolean _isClient;

  protected ArgsDaemon()
  {
  }

  public ArgsDaemon(EnvCliOld env,
                    String[] argv,
                    ProgramInfoDaemon programInfo)
  {
    super(env, argv, programInfo);
  }

  public PathImpl getRootDirectory()
  {
    return _rootDirectory;
  }

  public PathImpl getDefaultRootDirectory()
  {
    return _rootDirectoryDefault;
  }

  public PathImpl getDataDirectory()
  {
    return _dataDirectory;
  }

  public PathImpl getLogDirectory()
  {
    return _logDirectory;
  }

  public PathImpl getConfigPath()
  {
    return _configPath;
  }

  public PathImpl getConfigPathDefault()
  {
    return _configPathDefault;
  }

  public void setConfigPathDefault(PathImpl path)
  {
    _configPathDefault = path;
  }

  public PathImpl getConfigPathTemplate()
  {
    return _configPathTemplate;
  }

  public PathImpl getUserProperties()
  {
    return _userProperties;
  }

  @Override
  public void setHomeDirectory(PathImpl homeDirectory)
  {
    super.setHomeDirectory(homeDirectory);

    System.setProperty(getProgramName() + ".home",
                       homeDirectory.getNativePath());
  }

  public void setRootDirectory(PathImpl rootDirectory)
  {
    _rootDirectory = rootDirectory;

    System.setProperty(getProgramName() + ".root",
                       rootDirectory.getNativePath());
  }

  public void setConfigPath(PathImpl configPath)
  {
    _configPath = configPath;
  }

  void setUserProperties(PathImpl propPath)
  {
    _userProperties = propPath;
  }

  @Override
  protected void initDefaults()
  {
    super.initDefaults();

    _rootDirectoryDefault = calculateRootDirectory(getHomeDirectory());

    _configPathDefault = calculateConfigPath();
    _configPathTemplate = calculateConfigPathTemplate();
    
    String userHome =  System.getProperty("user.home");
    String propFile = userHome + "/." + getProgramName();

    setUserProperties(VfsOld.lookup(propFile));
  }

  @Override
  protected void init()
  {
    super.init();
  }

  protected PathImpl calculateConfigPath()
  {
    String name = getProgramName();

    PathImpl configPath;

    configPath = getHomeDirectory().lookup("conf/" + name + ".cf");

    if (! configPath.canRead()) {
      configPath = getHomeDirectory().lookup("conf/" + name + ".xml");
    }

    if (! configPath.canRead()) {
      configPath = getHomeDirectory().lookup("conf/" + name + ".conf");
    }

    if (! configPath.canRead()) {
      configPath = getHomeDirectory().lookup("classpath:/META-INF/" + name + "/" + name + ".cf");
    }

    // XXX:classpath

    return configPath;
  }

  protected PathImpl calculateConfigPathTemplate()
  {
    String name = getProgramName();

    return getHomeDirectory().lookup("classpath:/META-INF/" + name + "/" + name + ".tmpl.cf");
  }

  protected PathImpl calculateRootDirectory(PathImpl homeDir)
  {
    String rootDir = System.getProperty(getProgramName() + ".root");

    if (rootDir != null) {
      return VfsOld.lookup(rootDir);
    }

    /*
    resinRoot = System.getProperty("server.root");

    if (resinRoot != null) {
      return Vfs.lookup(resinRoot);
    }
    */

    return homeDir.lookup("/tmp/" + getProgramName());
  }

  public void setDataDirectory(PathImpl dataDirectory)
  {
    _dataDirectory = dataDirectory;
  }

  public void setLogDirectory(PathImpl dir)
  {
    _logDirectory = dir;
  }

  public void setConfigPathRaw(String confPath)
  {
    _configPath = VfsOld.getPwd().lookup(confPath);

    if (! _configPath.exists() && _rootDirectory != null) {
      _configPath = _rootDirectory.lookup(confPath);
    }

    if (! _configPath.exists() && getHomeDirectory() != null) {
      _configPath = getHomeDirectory().lookup(confPath);
    }

    if (! _configPath.exists()) {
      throw new ConfigException(L.l("Can't find configuration file '{0}'",
                                    _configPath.getNativePath()));
    }
  }

  public void addSeedServer(String address, int port, String cluster)
  {
    _seedServers.add(new SeedServer(address, port));
  }

  public void setClient(boolean isClient)
  {
    _isClient = isClient;
  }
  
  public boolean isClient()
  {
    return _isClient;
  }

  /*
  public void postConfig(RootConfigBoot rootConfig)
  {
    for (SeedServer seed : _seedServers) {
      ClusterConfigBoot cluster = rootConfig.findCluster(seed.getCluster());

      if (cluster != null) {
        cluster.addServer(seed.getAddress(), seed.getPort());
      }
    }

    if (_seedServers.size() > 0) {
      rootConfig.init();
    }
  }
  */

  public void copyFrom(ArgsDaemon args)
  {
    super.copyFrom(args);

    _rootDirectory = args._rootDirectory;
    _rootDirectoryDefault = args._rootDirectoryDefault;
    _dataDirectory = args._dataDirectory;
    _logDirectory = args._logDirectory;
    _configPath = args._configPath;
    _configPathDefault = args._configPathDefault;
    _userProperties = args._userProperties;

    _seedServers.addAll(args._seedServers);
    _isClient = args._isClient;
  }

  @Override
  protected ProgramInfoDaemon getProgramInfo()
  {
    return (ProgramInfoDaemon) super.getProgramInfo();
  }

  /**
   * The server-id to be managed.
   */
  public String getServerId()
  {
    return _serverId;
  }

  /**
   * The server-id to be managed.
   */
  public void setServerId(String serverId)
  {
    if ("".equals(serverId)) {
      serverId = "default";
    }

    _serverId = serverId;
  }

  public void setClusterId(String clusterId)
  {
    _clusterId = clusterId;
  }

  public String getClusterId()
  {
    return _clusterId;
  }

  /**
   * The server IP address to be managed.
   */
  public void setServerAddress(String address)
  {
    _serverAddress = address;
  }

  public String getServerAddress()
  {
    return _serverAddress;
  }

  /**
   * The server IP port to be managed.
   */
  public int getServerPort()
  {
    return _serverPort;
  }

  /**
   * The server IP port to be managed.
   */
  public void setServerPort(int serverPort)
  {
    _serverPort = serverPort;
  }

  /**
   * Default server port if none is specified.
   */
  public final int getServerPortDefault()
  {
    return getProgramInfo().getServerPortDefault();
  }

  //
  // watchdog config
  //

  public boolean isWatchdog()
  {
    return false;
  }

  public void setWatchdogPort(int port)
  {
    _watchdogPort = port;
  }

  public int getWatchdogPort()
  {
    return _watchdogPort;
  }

  public final int getWatchdogPortDefault()
  {
    return getProgramInfo().getWatchdogPortDefault();
  }

  public void addConfigPath(PathImpl path)
  {
    _configPaths.add(path);
  }

  public Iterable<PathImpl> getConfigPaths()
  {
    return _configPaths;
  }

  public boolean isSkipLog()
  {
    return false;
  }

  /*
  public ConfigBoot parseBoot(SystemManager system)
  {
    BootConfigParser parser = createParser();

    return parser.parseBoot(this, system);
  }

  protected BootConfigParser createParser()
  {
    return new BootConfigParser();
  }
  */

  /**
   * The extra program is passed for embedded systems like the QA that
   * parse config files externally.
   */
  /*
  public void addProgram(ConfigProgram program)
  {
    _containerProgram.addProgram(program);
  }

  public ContainerProgram getProgram()
  {
    return _containerProgram;
  }

  public ServerELContext<? extends ArgsDaemon> getELContext()
  {
    return new ServerELContext<ArgsDaemon>(this);
  }
  */

  public ArgsDaemon createChild(String[] args)
  {
    return new ArgsDaemon(envCli(), args, getProgramInfo());
  }

  @Override
  public CommandManager<? extends ArgsDaemon> getCommandManager()
  {
    return _commandManagerDaemon;
  }

  public static class ServerId
    extends OptionCommandLine.Base<ArgsDaemon>
  {
    @Override
    public String getName()
    {
      return "server";
    }

    @Override
    public String getDescription()
    {
      return "server id of the managed server";
    }

    @Override
    public String getValueDescription()
    {
      return "ID";
    }

    @Override
    public int parse(ArgsDaemon args,
                     String[] argv,
                     int index)
      throws CommandArgumentException
    {
      args.setServerId(argv[index + 1]);

      return index + 1;
    }
  }

  public static class ServerAddress
    extends OptionCommandLine.Base<ArgsDaemon>
  {
    @Override
    public String getDescription()
    {
      return "IP address of the managed server";
    }

    @Override
    public String getValueDescription()
    {
      return "IP";
    }

    @Override
    public int parse(ArgsDaemon args,
                     String[] argv,
                     int index)
      throws CommandArgumentException
    {
      String value = argv[index + 1];

      int p = value.indexOf(':');

      if (p > 0) {
        String address = value.substring(0, p);
        int port = Integer.parseInt(value.substring(p + 1));

        args.setServerAddress(address);
        args.setServerPort(port);
      }
      else {
        args.setServerAddress(argv[index + 1]);
      }

      return index + 1;
    }
  }

  public static class ServerPort
    extends OptionCommandLine.Base<ArgsDaemon>
  {
    @Override
    public String getName()
    {
      return "port";
    }

    @Override
    public String getDescription()
    {
      return "TCP port of the managed server";
    }

    @Override
    public String getValueDescription()
    {
      return "PORT";
    }

    @Override
    public int parse(ArgsDaemon args,
                     String[] argv,
                     int index)
      throws CommandArgumentException
    {
      args.setServerPort(Integer.parseInt(argv[index + 1]));

      return index + 1;
    }
  }

  public static class WatchdogPort
    extends OptionCommandLine.Base<ArgsDaemon>
  {

    @Override
    public String getDescription()
    {
      return "the TCP port for the watchdog to listen on";
    }

    @Override
    public String getValueDescription()
    {
      return "PORT";
    }

    @Override
    public int parse(ArgsDaemon args,
                     String[] argv,
                     int index)
        throws CommandArgumentException
    {
      args.setWatchdogPort(Integer.parseInt(argv[index + 1]));

      return index + 1;
    }
  }

  public static class ClusterId
    extends OptionCommandLine.Base<ArgsDaemon>
  {
    @Override
    public String getName()
    {
      return "cluster";
    }

    @Override
    public String getDescription()
    {
      return "cluster id containing the managed server";
    }

    @Override
    public String getValueDescription()
    {
      return "ID";
    }

    @Override
    public int parse(ArgsDaemon args,
                     String[] argv,
                     int index)
      throws CommandArgumentException
    {
      args.setClusterId(argv[index + 1]);

      return index + 1;
    }
  }

  private class SeedServer implements Comparable<SeedServer> {
    private String _address;
    private int _port;

    SeedServer(String address, int port)
    {
      _address = address;
      _port = port;
    }

    String getCluster()
    {
      return "cluster";
    }

    String getAddress()
    {
      return _address;
    }

    int getPort()
    {
      return _port;
    }

    @Override
    public int compareTo(SeedServer server)
    {
      int cmp = _address.compareTo(server._address);

      if (cmp != 0) {
        return cmp;
      }

      return Integer.signum(_port - server._port);
    }

    @Override
    public int hashCode()
    {
      return _address.hashCode() & 65521 + _port;
    }

    public boolean equals(Object o)
    {
      if (! (o instanceof SeedServer)) {
        return false;
      }

      SeedServer seed = (SeedServer) o;

      return compareTo(seed) == 0;
    }

    public String toString()
    {
      return getClass().getSimpleName() + "[" + _address + ":" + _port + "]";
    }
  }

  /**
   * --seed address:port
   */
  public static class SeedOption extends OptionCommandLine.Base<ArgsDaemon>
  {
    @Override
    public String getName()
    {
      return "seed";
    }
    
    @Override
    public String getValueDescription()
    {
      return "IP:port";
    }
    
    @Override
    public String getDescription()
    {
      return "address of a seed server";
    }
    
    @Override
    public ArgsType getType()
    {
      return ArgsType.GENERAL;
    }

    @Override
    public int parse(ArgsDaemon args, String[] argv, int index)
      throws CommandArgumentException
    {
      String value = argv[index + 1];

      int p = value.indexOf(':');

      String address;
      int port;

      if (p > 0) {
        address = value.substring(0, p);
        port = Integer.parseInt(value.substring(p + 1));
      }
      else if (value.indexOf('.') >= 0 || value.indexOf("::") >= 0) {
        throw new ConfigException(L.l("'{0}' is an invalid seed address",
                                      value));
      }
      else {
        address = "";
        port = Integer.parseInt(value);
      }

      args.addSeedServer(address, port, "");

      return index + 1;
    }
  }

  @Override
  protected void initCommands(CommandManager<?> commandManager)
  {
    CommandManager<? extends ArgsDaemon> manager = (CommandManager) commandManager;

    manager.addOption(new Conf()).alias("-conf");
    manager.addOption(new DataDir()).alias("data-directory");
    manager.addOption(new LogDir()).alias("log-directory").alias("-log-directory");

    manager.addOption(new RootDir()).tiny("d")
                                    .alias("root-directory")
                                    .alias("-root-directory");
                                    //.alias(getProgramName() + "-root")
                                    //.alias("-" + getProgramName() + "-root");

    super.initCommands(manager);

  }

  static {
    _commandManagerDaemon = new CommandManager<>();

    new ArgsDaemon().initCommands(_commandManagerDaemon);
  }
}
