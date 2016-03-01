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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.caucho.v5.cli.args.OptionCli.ArgsType;

public class CommandManager<A extends ArgsBase>
  implements OptionContainer<A>
{
  private final HashMap<String,Command<? super A>> _commandMap
    = new HashMap<>();

  private final HashMap<String,OptionCli<? super A>> _optionMap
    = new LinkedHashMap<>();

  public boolean isOption(String arg)
  {
    OptionCli<?> option = _optionMap.get(arg);
    
    if (option == null) {
      return false;
    }
    
    return true;
  }

  public boolean isFlag(String arg)
  {
    OptionCli<?> option = _optionMap.get(arg);
    
    if (option == null) {
      return false;
    }
    
    return option.isFlag();
  }

  @Override
  public void fillOptions(List<OptionCli<?>> options)
  {
    for (OptionCli<? super A> option : _optionMap.values()) {
      options.add(option);
    }
  }

  public Map<String,Command<? super A>> getCommandMap()
  {
    return _commandMap;
  }

  public Command<? super A> getCommand(String tailArg)
  {
    return _commandMap.get(tailArg);
  }

  public
  Command<?> command(Command<? super A> command)
  {
    command(command.name(), command);
    
    return command;
  }

  public void command(String name, Command<? super A> command)
  {
    _commandMap.put(name, command);
    
    command.parser(this);
  }

  public OptionCli<?> getOption(String name)
  {
    return _optionMap.get(name);
  }

  @Override
  public OptionCli<? super A> option(OptionCli<? super A> option)
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
  public OptionCli<? super A> addTinyOption(OptionCli<? super A> option)
  {
    String name = option.getName();
    
    return addOption("-" + name, option);
  }

  @Override
  public OptionCli<? super A> addOption(String name, 
                                         OptionCli<? super A> option)
  {
    _optionMap.put(name, option);
    
    option.parser(this);
    
    if (option.getType() == ArgsType.DEFAULT) {
      option.type(ArgsType.GENERAL);
    }
    
    return option;
  }
}
