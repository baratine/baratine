/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
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

package com.caucho.v5.cli.server;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.cli.daemon.ArgsDaemon.ServerAddress;
import com.caucho.v5.cli.daemon.ArgsDaemon.ServerId;
import com.caucho.v5.cli.daemon.ArgsDaemon.ServerPort;
import com.caucho.v5.cli.spi.OptionCommandLine.ArgsType;
import com.caucho.v5.health.shutdown.ExitCode;

public abstract class RemoteCommandBase extends ServerCommandBase<ArgsCli>
{
  @Override
  protected void initBootOptions()
  {
    addOption(new ServerId()).tiny("s").type(ArgsType.GENERAL);
    addOption(new ServerAddress()).alias("address").tiny("sa").type(ArgsType.GENERAL).hide();
    addOption(new ServerPort()).alias("server-port").tiny("sp").tiny("p").type(ArgsType.GENERAL); //.hide();

    addValueOption("user", "user", "admin user name for authentication").type(ArgsType.ADMIN);
    addValueOption("password", "password", "admin password for authentication").type(ArgsType.ADMIN);

    super.initBootOptions();
  }

  @Override
  public ExitCode doCommandImpl(ArgsCli args) // , ConfigBoot boot)
    throws BootArgumentException
  {
    try (ClientManage client = new ClientManage(args)) { // , boot)) {
      return doCommandImpl(args, client.getAmp());
    }
  }

  abstract protected ExitCode doCommandImpl(ArgsCli args,
                                        ServiceManagerClient client);
}
