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

package com.caucho.v5.cli.server;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.cli.daemon.ArgsDaemon;
import com.caucho.v5.cli.daemon.ArgsDaemon.ServerAddress;
import com.caucho.v5.cli.daemon.ArgsDaemon.ServerId;
import com.caucho.v5.cli.daemon.ArgsDaemon.ServerPort;
import com.caucho.v5.cli.daemon.ArgsDaemon.WatchdogPort;
import com.caucho.v5.cli.daemon.DaemonCommandBase;
import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.cli.spi.OptionCommandLine.ArgsType;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;

public abstract class ServerCommandBase<A extends ArgsDaemon>
  extends DaemonCommandBase<A> implements BootCommand<A>
{
  private static final Logger log
    = Logger.getLogger(ServerCommandBase.class.getName());

  protected ServerCommandBase()
  {
    initBootOptions();
  }

  @Override
  protected void initBootOptions()
  {
    super.initBootOptions();

    addOption(new ServerId()).tiny("s").type(ArgsType.GENERAL);
    addOption(new ServerAddress()).alias("address").tiny("sa").type(ArgsType.GENERAL).hide();
    addOption(new ServerPort()).alias("server-port").tiny("p").tiny("sp").type(ArgsType.GENERAL); //.hide();
    addOption(new WatchdogPort()).tiny("wp").type(ArgsType.GENERAL);
  }

  @Override
  public String getDescription()
  {
    return "";
  }

  /*
  @Override
  protected ExitCode doCommandImpl(A args)
    throws CommandArgumentException
  {
    ConfigBoot boot;

    try {
      boot = args.parseBoot(createSystem(args));
    } catch (ConfigException e) {
      throw e;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CommandArgumentException(e);
    }

    return doCommand(args); // , boot);
  }
  */

  protected SystemManager createSystem(A args)
    throws IOException
  {
    return new SystemManager(args.getProgramName());
  }

  protected void validateRootDirectory(ArgsDaemon args)
                                       // RootConfigBoot config)
  {
    //PathImpl root = config.getRootDirectory(args);
    PathImpl root = args.config().get("baratine.root", PathImpl.class,
                                      VfsOld.lookup("/tmp/baratine"));
        //)config.getRootDirectory(args);

    if (! root.exists()) {
      try {
        root.mkdirs();
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }

    if (! root.isDirectory() || ! root.canWrite()) {
      throw error(args,
                  "can't use data directory '{0}', which must be writable.\n  Either create it or configure with -d DIR",
                  root.getNativePath());
    }
  }

  @Override
  public boolean isRemote(A args)
  {
    return args.getArg("address") != null;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
