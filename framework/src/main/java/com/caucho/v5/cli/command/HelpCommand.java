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

package com.caucho.v5.cli.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.caucho.v5.cli.spi.ArgsBase;
import com.caucho.v5.cli.spi.Command;
import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.cli.spi.CommandBase;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;

public class HelpCommand extends CommandBase<ArgsBase>
{
  private static final L10N L = new L10N(HelpCommand.class);

  @Override
  public String name()
  {
    return "help";
  }

  @Override
  public String getDescription()
  {
    return "help information about the available commands";
  }

  @Override
  protected  ExitCode doCommandImpl(ArgsBase args)
    throws CommandArgumentException
  {
    printUsage(args);

    return ExitCode.OK;
  }

  @Override
  public int getTailArgsMinCount()
  {
    return 0;
  }

  private void printUsage(ArgsBase args)
  {
    ArrayList<String> tailArgs = args.getTailArgs();
    String tailArg = null;

    if (tailArgs.size() > 0) {
      tailArg = tailArgs.get(0);
    }

    Command command = null;

    if (tailArg != null) {
      command = args.getCommandManager().getCommand(tailArg);
    }

    if (command != null) {
      System.err.println(command.usage(args));
    }
    else if (tailArg == null) {
      printBaseUsage(args);
    }
    else if (tailArg.startsWith("-")) {
      System.err.println(L.l("'{0}' is an unknown option\n  {1}", tailArg,
                             Arrays.asList(args.getArgv())));
      printUsageHeader(args);
    }
    else {
      System.err.println(L.l("'{0}' is an unknown command\n  {1}", tailArg,
                             Arrays.asList(args.getArgv())));
      printUsageHeader(args);
    }
  }

  private void printBaseUsage(ArgsBase args)
  {
    printUsageHeader(args);

    System.err.println(L.l(""));
    System.err.println(L.l("where command is one of:"));
    System.err.println(formatCommandList(args));
  }

  private void printUsageHeader(ArgsBase args)
  {
    System.err.println(L.l("usage: {0} <command> [option...] [values]",
                           args.getCommandName()));
    System.err.println(L.l("       {0} help [command]",
                           args.getCommandName()));
  }

  private String formatCommandList(ArgsBase args)
  {
    HashMap<CommandType,ArrayList<Command<?>>> commandMap
      = new HashMap<>();

    for (Command<?> command : args.getCommandMap().values()) {
      CommandType category = command.getCategory();

      ArrayList<Command<?>> list = commandMap.get(category);

      if (list == null) {
        list = new ArrayList<>();

        commandMap.put(category, list);
      }
      
      list.add(command);
    }
    
    ArrayList<CommandType> categoryList = new ArrayList<>();
    
    categoryList.addAll(commandMap.keySet());
    
    Collections.sort(categoryList);

    StringBuilder sb = new StringBuilder();

    for (CommandType category : categoryList) {
      ArrayList<Command<?>> list = commandMap.get(category);

      sb.append("\n");
      sb.append(category);
      sb.append(L.l(" commands:\n"));

      formatCommandList(sb, list);
    }

    return sb.toString();
  }

  private String formatCommandList(StringBuilder sb,
                                   ArrayList<Command<?>> commands)
  {
    Command<?> lastCommand = null;
    
    Collections.sort(commands, (x,y)->x.name().compareTo(y.name()));

    for (Command<?> command : commands) {
      if (lastCommand != null && lastCommand.getClass() == command.getClass()) {
        continue;
      }

      if (command.isHide()) {
        continue;
      }

      sb.append("  ");

      String name = command.name();

      sb.append(name);
      for (int i = name.length(); i < 15; i++) {
        sb.append(" ");
      }

      sb.append(" - ");
      sb.append(command.getDescription());
      sb.append("\n");

      lastCommand = command;
    }

    return sb.toString();
  }

  private static class CommandNameComparator implements Comparator<Command<?>>
  {
    @Override
    public int compare(Command<?> a, Command<?> b)
    {
      return a.name().compareTo(b.name());
    }
  }
}
