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

package com.caucho.v5.cli.baratine;

import com.caucho.v5.cli.server.ProgramInfoDaemon;
import com.caucho.v5.cli.shell_old.EnvCliOld;
import com.caucho.v5.cli.spi.CommandManager;

public class ArgsCliMainBaratineApp extends ArgsCliMainBaratine
{
  private static final CommandManager<? extends ArgsCliMainBaratineApp> _commandManagerBaratineApp;

  protected ArgsCliMainBaratineApp()
  {
    this(new String[0]);
  }

  public ArgsCliMainBaratineApp(String[] argv)
  {
    super(new EnvCliOld(), argv, new ProgramInfoBaratine());
  }

  public ArgsCliMainBaratineApp(EnvCliOld env,
                             String[] argv,
                             ProgramInfoDaemon programInfo)
  {
    super(env, argv, new ProgramInfoBaratine());
  }

  @Override
  public ArgsCliMainBaratineApp createChild(String []argv)
  {
    return new ArgsCliMainBaratineApp(envCli(), argv, getProgramInfo());
  }


  @Override
  public CommandManager<? extends ArgsCliMainBaratineApp> getCommandManager()
  {
    return _commandManagerBaratineApp;
  }

  @Override
  protected void initCommands(CommandManager<?> commandManager)
  {
    super.initCommands(commandManager);

    CommandManager<? extends ArgsCli> manager = (CommandManager) commandManager;
    
    //manager.addCommand(new StartCommandBaratineApp());
  }

  static {
    _commandManagerBaratineApp = new CommandManager<>();

    new ArgsCliMainBaratineApp().initCommands(_commandManagerBaratineApp);
  }
}
