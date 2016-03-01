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

package com.caucho.v5.cli.args;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.config.UserMessage;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;

public class CliParser
{
  private static L10N L = new L10N(CliParser.class);
  private static final Logger log
    = Logger.getLogger(CliParser.class.getName());

  public CliParser()
  {
  }

  public <A extends ArgsBase>
  ExitCode parseCommandLine(final A args)
  {
    try {
      parseCommandLineImpl(args);
    } catch (Exception e) {
      if (e instanceof UserMessage) {
        System.err.println(args.programName() + ": " + e.getMessage());
      }
      else {
        System.err.println(args.programName() + ": " + e.toString());
      }

      if (log.isLoggable(Level.FINE)) {
        log.log(Level.FINE, e.toString(), e);
      }

      Command<? super A> command = (Command) args.command();

      if (command != null) {
        System.err.println(command.usage(args));
        args.command(null);
      }
      else {
        command = new CommandHelp();
        args.command(command);
        command.doCommand(args);
        // usage();
      }

      return ExitCode.UNKNOWN_ARGUMENT;
    }

    Command<? super A> command = (Command) args.command();

    if (command == null) {
      String commandDefault = args.commandDefault();
      
      if (commandDefault != null) {
        command = (Command) args.commandMap().get(commandDefault);
        
        if (command == null) {
          throw new CommandLineException(L.l("{0} is an unknown default command",
                                             commandDefault));
        }
      }
      else {
        command = new CommandDefault();
      }
      
      args.command(command);
    }

    return ExitCode.OK;
  }

  private <A extends ArgsBase>
  void parseCommandLineImpl(final A args)
  {
    final String []argv = args.getRawArgv();

    Command<? super A> command = null;

    for (int i = 0; i < argv.length; i++) {
      String rawArg = argv[i];

      String arg = rawArg;

      /*
      if (! arg.startsWith("--")
          && arg.startsWith("-")) {
        arg = "-" + arg;
      }
      */

      if (rawArg.startsWith("-J")
               || rawArg.startsWith("-D")
               || rawArg.startsWith("-X")) {
      }
      else if (args.getOption(arg) != null) {
        OptionCli option = args.getOption(arg);

        i = option.parse(args, argv, i);
      }
      else if (command != null) {
        i = parseCommandOption(command, args, arg, i);
      }
      else if (args.commandMap().get(rawArg) != null) {
        command = (Command) args.commandMap().get(rawArg);

        args.command(command);
      }
      else if (rawArg.startsWith("-")) {
        //command = (Command<? super A>) new ShellCommand();
        String commandName = args.commandDefault();
        
        command = (Command) args.commandMap().get(commandName);
        
        if (command != null) {
        }
        else if (commandName != null) {
          throw new CommandLineException(L.l("'{0}' is an unexpected default command.",
                                             commandName));
        }
        else {
          command = new CommandDefault();
        }

        args.command(command);

        i = parseCommandOption(command, args, arg, i);
      }
      else {
        throw new CommandLineException(L.l("'{0}' is an unexpected command.",
                                           rawArg));
      }
    }

    List<String> defaultArgs = parseDefaultArgs(command, args.getTailArgs());

    args.setDefaultArgs(defaultArgs.toArray(new String[defaultArgs.size()]));

    args.init();
  }

  private <A extends ArgsBase>
  int parseCommandOption(Command<? super A> command,
                         A args,
                         String arg,
                         int i)
  {
    final String []argv = args.getRawArgv();
    OptionCli<? super A> option = command.getOption(arg);

    if (option != null) {
      i = option.parse(args, argv, i);
    }
    else if (arg.startsWith("--") && arg.indexOf("=") > 0) {
      int p = arg.indexOf("=");
      
      String key = arg.substring(2, p);
      String value = arg.substring(p + 1);
      
      args.property(key, value);
    }
    else if (arg.startsWith("-")) {
      throw new CommandLineException(L.l("'{0}' is an unexpected option for {1}.",
                                             arg,
                                             command.name()));
    }
    else if (command.getTailArgsMinCount() >= 0) {
      args.addTailArg(arg);
    }
    else {
      throw new CommandLineException(L.l("'{0}' is an unexpected argument for {1}.",
                                             arg,
                                             command.name()));
    }

    return i;
  }

  private <A extends ArgsBase>
  List<String> parseDefaultArgs(Command<? super A> command,
                                List<String> tailArgs)
  {
    LinkedList<String> defaultArgs = new LinkedList<String>();

    for (int i = tailArgs.size() - 1; i >= 0; i--) {
      String arg = tailArgs.get(i);

      defaultArgs.addFirst(arg);
    }

    return defaultArgs;
  }

  /*
  private boolean matchName(Command<? super A> command, String name)
  {
    Set<Map.Entry<String,Command<? super A>>> entries = _commandMap.entrySet();

    for (Map.Entry<String,Command<? super A>> entry : entries) {
      if (! command.getClass().equals(entry.getValue().getClass())) {
        continue;
      }

      if (name.equals(entry.getKey())) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void fillOptions(List<OptionCommandLine<?>> options)
  {
    for (OptionCommandLine<? super A> option : _optionMap.values()) {
      options.add(option);
    }
  }

  private void usage()
  {
    System.err.println(L.l("usage: {0} <command> [option...] [values]",
                             getCommandName()));
    System.err.println(L.l("       {0} help <command>",
                             getCommandName()));
    System.err.println(L.l(""));
    System.err.println(L.l("where command is one of:"));
    System.err.println(getCommandList());
  }

  private String getCommandList()
  {
    StringBuilder sb = new StringBuilder();

    ArrayList<Command<? super A>> commands = new ArrayList<>();
    commands.addAll(_commandMap.values());

    Collections.sort(commands, new CommandNameComparator());

    Command<? super A> lastCommand = null;

    for (Command<? super A> command : commands) {
      if (lastCommand != null && lastCommand.getClass() == command.getClass()) {
        continue;
      }

      sb.append("\n  ");

      String name = command.getName();

      sb.append(name);
      for (int i = name.length(); i < 15; i++) {
        sb.append(" ");
      }

      sb.append(" - ");
      sb.append(command.getDescription());

      lastCommand = command;
    }

    return sb.toString();
  }

  protected void addCommand(Command<? super A> command)
  {
    addCommand(command.getName(), command);
  }

  protected void addCommand(String name, Command<? super A> command)
  {
    _commandMap.put(name, command);

    command.parser(this);
  }

  @Override
  public OptionCommandLine<? super A> addOption(OptionCommandLine<? super A> option)
  {
    String name = option.getName();

    if (name.startsWith("-")) {
      return addOption(name, option);
    }
    else {
      return addOption("--" + name, option);
    }
  }

  @Override
  public OptionCommandLine<? super A> addTinyOption(OptionCommandLine<? super A> option)
  {
    String name = option.getName();

    return addOption("-" + name, option);
  }

  @Override
  public OptionCommandLine<? super A> addOption(String name,
                                         OptionCommandLine<? super A> option)
  {
    _optionMap.put(name, option);

    option.parser(this);

    if (option.getType() == ArgsType.DEFAULT) {
      option.type(ArgsType.GENERAL);
    }

    return option;
  }

  private static class CommandNameComparator implements Comparator<Command<?>>
  {
    public int compare(Command<?> a, Command<?> b)
    {
      return a.getName().compareTo(b.getName());
    }
  }
  */
}
