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
 * @author Alex Rojkov
 */

package com.caucho.v5.cli.shell_old;

import java.io.Console;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.cli.daemon.ArgsDaemon;
import com.caucho.v5.cli.server.BootArgumentException;
import com.caucho.v5.cli.server.ServerCommandBase;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.Version;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.ReadStreamOld;
import com.caucho.v5.vfs.VfsOld;

/**
 * Command to run Resin in console mode
 */
public class ShellCommandOld extends ServerCommandBase<ArgsCli>
{
  private static final L10N L = new L10N(ShellCommandOld.class);

  private static final Logger log
    = Logger.getLogger(ShellCommandOld.class.getName());

  @Override
  public String name()
  {
    return "shell";
  }

  @Override
  public String getDescription()
  {
    return L.l("start Baratine in shell mode");
  }

  @Override
  protected void initBootOptions()
  {
    super.initBootOptions();

    addValueOption("input", "FILE", "input script").tiny("i");
  }

  @Override
  public ExitCode doCommandImpl(ArgsCli args) // , ConfigBoot resinBoot)
      throws BootArgumentException
  {
    Console console = System.console();
    
    if (args.envCli().isEmbedded()) {
      console = null;
    }

    String fileName = args.getArg("input");
    
    if (fileName != null) {
      PathImpl pwd = VfsOld.getPwd();
      PathImpl scriptPath = VfsOld.lookup(fileName);

      try (ReadStreamOld is = scriptPath.openRead()) {
        // XXX: change to __DIR__ and __FILE__
        VfsOld.setPwd(scriptPath.getParent());

        doBatch(is, args);
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        VfsOld.setPwd(pwd);
      }

      return ExitCode.OK;
    }
    else if (console != null) {
      System.err.println(L.l("{0}\n{1}",
                             Version.getFullVersion(),
                             Version.getCopyright()));
      System.err.println(L.l("Use 'help' for help and 'exit' to exit the {0} shell.",
                             args.getDisplayName()));

      doConsole(console, args);
    }
    else {
      doBatch(args);
    }

    return ExitCode.OK;
  }

  private void doBatch(ArgsCli args)
  {
    try (ReadStreamOld is = VfsOld.openRead(System.in)) {
      doBatch(is, args);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void doBatch(ReadStreamOld is, ArgsCli args)
    throws IOException
  {
    String line;

    while ((line = is.readLine()) != null) {
      line = line.trim();

      if (line.equals("")) {
        continue;
      }

      if (line.startsWith("#")) {
        continue;
      }

      executeCommand(args, line);
    }
  }

  private void doConsole(Console console, ArgsCli args)
  {
    String prompt = args.getProgramName() + "> ";
    String command ;

    while ((command = readCommand(console, prompt)) != null) {
      if (command.trim().equals("")) {
        continue;
      }

      executeCommand(args, command);
    }
  }

  private void executeCommand(ArgsCli argsParent, String command)
  {
    String []args = command.trim().split("[\\s]+");

    try {
      ArgsDaemon argsCommand = argsParent.createChild(args);

      argsCommand.doCommand();
    } catch (Exception e) {
      System.err.println(e.toString());
      
      if (log.isLoggable(Level.FINER)) {
        e.printStackTrace();
      }
    }
  }

  private String readCommand(Console console, String prompt)
  {
    String command = readLine(console, prompt);

    if (command == null) {
      return null;
    }

    while (command.endsWith("\\")) {
      command = command.substring(0, command.length() - 1);

      String line = readLine(console, "> ");

      if (line == null) {
        return null;
      }

      command += line;
    }

    return command;
  }

  private String readLine(Console console, String prompt)
  {
    return console.readLine(prompt);
  }
}
