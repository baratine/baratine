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

package com.caucho.v5.server.container;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Logger;

import com.caucho.v5.cli.daemon.ArgsDaemon;
import com.caucho.v5.cli.server.ProgramInfoDaemon;
import com.caucho.v5.cli.shell_old.EnvCliOld;
import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.cli.spi.CommandManager;
import com.caucho.v5.cli.spi.OptionCommandLine;
import com.caucho.v5.jni.ServerSocketJni;
import com.caucho.v5.util.L10N;
import com.caucho.v5.web.server.ServerBase;

/**
 * The parsed server command-line arguments
 */
public class ArgsServerBase extends ArgsDaemon
{
  private static final L10N L = new L10N(ArgsServerBase.class);
  private static final Logger log
    = Logger.getLogger(ArgsServerBase.class.getName());
  
  // private String _serverId;
  
  private static final CommandManager<ArgsServerBase> _commandManagerServer;

  private Socket _pingSocket;
  
  private ArrayList<BoundPort> _boundPortList
    = new ArrayList<>();
  
  private boolean _isOpenSource;
  
  private boolean _isDumpHeapOnExit;
  
  protected ArgsServerBase(boolean dummy)
  {
    super();
  }
  
  public ArgsServerBase()
  {
    this(new String[0]);
  }
  
  public ArgsServerBase(String []args)
  {
    this(args, new ProgramInfoDaemon());
  }
  
  public ArgsServerBase(String []args, ProgramInfoDaemon programInfo)
  {
    this(new EnvCliOld(), args, programInfo);
  }
  
  public ArgsServerBase(EnvCliOld env, 
                        String []args,
                        ProgramInfoDaemon programInfo)
  {
    super(env, args, programInfo);
    
    //initLogging();
  }
  
  /*
  public void setServerId(String serverId)
  {
    if ("".equals(serverId)) {
      serverId = "default";
    }
    
    _serverId = serverId;
  }

  public String getServerId()
  {
    return _serverId;
  }
  */
  
  public Socket getPingSocket()
  {
    return _pingSocket;
  }
  
  public void setOpenSource(boolean isOpenSource)
  {
    _isOpenSource = isOpenSource;
  }
  
  public boolean isOpenSource()
  {
    return _isOpenSource;
  }
  
  /**
   * Returns the bound port list.
   */
  public ArrayList<BoundPort> getBoundPortList()
  {
    return _boundPortList;
  }
  
  public String getUser()
  {
    return null;
  }
  
  public String getPassword()
  {
    return null;
  }
  
  public boolean isDumpHeapOnExit()
  {
    return _isDumpHeapOnExit;
  }

  public void addBoundPort(int fd, String addr, int port)
  {
    try {
      _boundPortList.add(new BoundPort(ServerSocketJni.openJNI(fd, port),
                                       addr,
                                       port));
    } catch (IOException e) {
      throw new CommandArgumentException(e);
    }
  }

  public void setPingSocket(Socket socket)
  {
    _pingSocket = socket;
  }

  public ServerBase createServer(String programName) // , ServerConfigBoot server)
  {
    //return ServerBuilder.create(programName, this, server).build();
    //return ServerBuilder.create(this).build();
    throw new UnsupportedOperationException();
  }
  
  static class DynamicServer {
    private final String _cluster;
    private final String _address;
    private final int _port;

    DynamicServer(String cluster, String address, int port)
    {
      _cluster = cluster;
      _address = address;
      _port = port;
    }

    String getCluster()
    {
      return _cluster;
    }

    String getAddress()
    {
      return _address;
    }

    int getPort()
    {
      return _port;
    }
  }
  
  /*
  static class BoundPort {
    private QServerSocket _ss;
    private String _address;
    private int _port;

    BoundPort(QServerSocket ss, String address, int port)
    {
      if (ss == null)
        throw new NullPointerException();

      _ss = ss;
      _address = address;
      _port = port;
    }

    public QServerSocket getServerSocket()
    {
      return _ss;
    }

    public int getPort()
    {
      return _port;
    }

    public String getAddress()
    {
      return _address;
    }
  }
  */
  
  @Override
  public CommandManager<? extends ArgsServerBase> getCommandManager()
  {
    return _commandManagerServer;
  }
  
  @Override
  protected void initCommands(CommandManager<?> commandManager)
  {
    super.initCommands(commandManager);
    
    CommandManager<? extends ArgsServerBase> manager = (CommandManager) commandManager;
    
    //manager.addOption(new SeedOption());
    
    /*
    manager.addCommand(new StartCommandServer());
    // manager.addCommand("console", new StartServerCommand());
    manager.addCommand("run", new StartCommandServer());
    manager.addCommand("start", new StartCommandServer());
    manager.addCommand("restart", new StartCommandServer());
    manager.addCommand("start-all", new StartCommandServer());
    */
  }
  
  public static class ClusterOption extends OptionCommandLine.Base<ArgsServerBase>
  {
    @Override
    public String getName()
    {
      return "cluster";
    }

    @Override
    public int parse(ArgsServerBase args, String[] argv, int index)
        throws CommandArgumentException
    {
      args.setClusterId(argv[index + 1]);
      
      return index + 1;
    }
  }
  
  /**
   * --port fd address port
   * 
   * Pre-bound port for setuid.
   */
  public static class BoundPortOption extends OptionCommandLine.Base<ArgsServerBase>
  {
    @Override
    public String getName()
    {
      return "bound-port";
    }

    @Override
    public int parse(ArgsServerBase args, String[] argv, int index)
      throws CommandArgumentException
    {
      int fd = Integer.parseInt(argv[index + 1]);
      String addr = argv[index + 2];
      
      if ("null".equals(addr)) {
        addr = null;
      }
      
      int port = Integer.parseInt(argv[index + 3]);

      args.addBoundPort(fd, addr, port);

      return index + 4;
    }
  }
  
  /**
   * --socketwait port
   * 
   * parent socket
   */
  public static class SocketWaitOption extends OptionCommandLine.Base<ArgsServerBase>
  {
    @Override
    public String getName()
    {
      return "socketwait";
    }

    @Override
    public int parse(ArgsServerBase args, String[] argv, int index)
      throws CommandArgumentException
    {
      int socketport = Integer.parseInt(argv[index + 1]);

      Socket socket = null;
      for (int k = 0; k < 15 && socket == null; k++) {
        try {
          socket = new Socket("127.0.0.1", socketport);
        } catch (Throwable e) {
          log.warning(L.l("Can't connect to watchdog port {0}\n", socketport));
        }

        if (socket == null) {
          try {
            Thread.sleep(1000);
          } catch (Exception e) {
          }
        }
      }

      if (socket == null) {
        System.err.println("Can't connect to parent process through socket " + socketport);
        System.err.println("Resin needs to connect to its parent.");
        System.exit(1);
      }

      //if (argv[i].equals("-socketwait") || argv[i].equals("--socketwait"))
      //  _waitIn = socket.getInputStream();

      // _pingSocket = socket;

      //socket.setSoTimeout(60000);

      args.setPingSocket(socket);

      return index + 1;
    }
  }

  static {
    _commandManagerServer = new CommandManager<>();
    
    new ArgsServerBase(true).initCommands(_commandManagerServer);
  }
}
