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

package com.caucho.v5.web.cli;

import java.util.Objects;

import com.caucho.v5.cli.args.CommandBase;
import com.caucho.v5.cli.args.CommandLineException;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;
import com.caucho.v5.web.builder.WebServerBuilderImpl;

import io.baratine.web.WebServer;

public class CommandStart extends CommandBase<ArgsBaratine>
{
  private static final L10N L = new L10N(CommandStart.class);
  
  @Override
  protected void initBootOptions()
  {
    super.initBootOptions();
    
    addValueOption("port", "int", "TCP port for the server")
                  .config("server.port").tiny("p");
  }
  
  @Override
  protected  ExitCode doCommandImpl(ArgsBaratine args)
    throws CommandLineException
  {
    WebServerBuilderImpl builder = args.env().get(WebServerBuilderImpl.class);
    
    if (builder == null) {
      throw new ConfigException(L.l("Baratine must be started by an applications Web.go or Web.start.\n"
          + "\nA 'start' on the command line has no application to start."));
    }
    
    WebServer server = args.env().get(WebServer.class);
    
    if (server != null) {
      throw new ConfigException(L.l("can't start because server is already started"));
    }
    
    server = builder.doStart();
    
    args.env().put(WebServer.class, server);
    
    return ExitCode.OK;
  }
}
