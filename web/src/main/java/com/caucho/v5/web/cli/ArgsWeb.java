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

import com.caucho.v5.cli.args.ArgsBase;
import com.caucho.v5.cli.args.CommandManager;
import com.caucho.v5.cli.shell.EnvCli;

public class ArgsWeb extends ArgsBase
{
  private static final CommandManager<ArgsBase> _managerCommandLine;
  //private static final String COMMAND_DEFAULT = "start-console";

  /**
   * For the commandManager init.
   */
  protected ArgsWeb()
  {
  }

  public ArgsWeb(String[] argv)
  {
    super(argv);
  }
  
  public ArgsWeb(EnvCli env, String[] argv)
  {
    super(env, argv);
  }
  
  @Override
  public CommandManager<?> getCommandManager()
  {
    return _managerCommandLine;
  }
  
  @Override
  protected void initCommands(CommandManager<?> manager)
  {
    super.initCommands(manager);
    
    manager.command(new CommandPackage());
  }

  static {
    _managerCommandLine = new CommandManager<>();
    
    ArgsWeb args = new ArgsWeb();
    
    args.initCommands(_managerCommandLine);
  }
}
