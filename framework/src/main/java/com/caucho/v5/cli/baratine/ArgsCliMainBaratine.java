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

import com.caucho.v5.cli.server.BackupCommand;
import com.caucho.v5.cli.server.BackupLoadCommand;
import com.caucho.v5.cli.server.BfsCatCommand;
import com.caucho.v5.cli.server.BfsCpCommand;
import com.caucho.v5.cli.server.BfsDuCommand;
import com.caucho.v5.cli.server.BfsGetCommand;
import com.caucho.v5.cli.server.BfsLsCommand;
import com.caucho.v5.cli.server.BfsPutCommand;
import com.caucho.v5.cli.server.BfsRmCommand;
import com.caucho.v5.cli.server.BfsTestCommand;
import com.caucho.v5.cli.server.BfsTouchCommand;
import com.caucho.v5.cli.server.KillCommand;
import com.caucho.v5.cli.server.ProgramInfoDaemon;
import com.caucho.v5.cli.server.ReportPdfCommand;
import com.caucho.v5.cli.shell_old.EnvCliOld;
import com.caucho.v5.cli.shell_old.ShellCommandOld;
import com.caucho.v5.cli.spi.CommandManager;

public class ArgsCliMainBaratine extends ArgsCli
{
  private static final CommandManager<? extends ArgsCliMainBaratine> _commandManagerBaratine;

  protected ArgsCliMainBaratine()
  {
    this(new String[0]);
  }

  public ArgsCliMainBaratine(String[] argv)
  {
    super(new EnvCliOld(), argv, new ProgramInfoBaratine());
  }

  public ArgsCliMainBaratine(EnvCliOld env,
                             String[] argv,
                             ProgramInfoDaemon programInfo)
  {
    super(env, argv, new ProgramInfoBaratine());
  }

  @Override
  public ArgsCliMainBaratine createChild(String []argv)
  {
    return new ArgsCliMainBaratine(envCli(), argv, getProgramInfo());
  }


  @Override
  public CommandManager<? extends ArgsCliMainBaratine> getCommandManager()
  {
    return _commandManagerBaratine;
  }

  @Override
  protected void initCommands(CommandManager<?> commandManager)
  {
    super.initCommands(commandManager);

    CommandManager<? extends ArgsCli> manager = (CommandManager) commandManager;

    //manager.addCommand(new StoreSaveCommandBaratine());
    //manager.addCommand(new StoreLoadCommandBaratine());

    manager.addCommand(new BackupCommand());
    manager.addCommand(new BackupLoadCommand());

    manager.addCommand(new BenchHttpCommand().hide());
    manager.addCommand(new BenchJampCommand().hide());
    
    manager.addCommand(new BfsCatCommand());
    manager.addCommand(new BfsGetCommand());
    manager.addCommand(new BfsLsCommand());
    manager.addCommand(new BfsPutCommand());
    manager.addCommand(new BfsRmCommand());
    manager.addCommand(new BfsCpCommand());
    //manager.addCommand(new BfsMvCommand());
    manager.addCommand(new BfsDuCommand());
    manager.addCommand(new BfsTouchCommand());
    manager.addCommand(new BfsTestCommand());

    //manager.addCommand(new DeployCommandService());

    manager.addCommand(new GcCommandBaratine().hide());

    manager.addCommand(new HeapDumpCommandBaratine().hide());

    manager.addCommand(new JampCommand());

    manager.addCommand(new KillCommand().hide());

    manager.addCommand(new PackageCommand().hide());
    manager.addCommand(new ProfileCommandBaratine().hide());

    manager.addCommand(new ReportPdfCommand().hide());

    manager.addCommand(new ShellCommandOld().hide());
    //manager.addCommand(new ShutdownCommand());
    manager.addCommand(new SleepCommand().hide());
    //manager.addCommand(new StartCommandBaratine());
    //manager.addCommand(new StatusCommand());
    //manager.addCommand(new StopCommand());

    //manager.addCommand(new TestCommand().hide());

    //manager.addCommand(new UndeployCommand());

    manager.addCommand(new GenerateIfaceCommand().hide());
  }

  static {
    _commandManagerBaratine = new CommandManager<>();

    new ArgsCliMainBaratine().initCommands(_commandManagerBaratine);
  }
}
