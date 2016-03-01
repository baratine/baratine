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

package com.caucho.v5.cli.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Logger;

import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.cli.daemon.ArgsDaemon;
import com.caucho.v5.cli.spi.OptionCommandLine.ArgsType;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.Version;

abstract public class CommandBase<A extends ArgsBase>
  implements Command<A>, OptionContainer<A>
{
  private static final L10N L = new L10N(CommandBase.class);

  private OptionContainer<ArgsBase> _parser;

  private final Map<String,OptionCommandLine<?>> _optionMap = new LinkedHashMap<>();
  private boolean _isHide;

  protected CommandBase()
  {
    initBootOptions();
  }

  public Command<A> hide()
  {
    _isHide = true;

    return this;
  }

  public boolean isHide()
  {
    return _isHide;
  }

  protected void initBootOptions()
  {
    /*
    addOption(new HomeDir()).alias("home-directory");
                            //.alias(getProgramName() + "-home")
                            //.alias("-" + getProgramName() + "-home");

    addOption(new VerboseFine()).tiny("v").type(ArgsType.DEBUG);
    addOption(new VerboseFiner()).tiny("vv").type(ArgsType.DEBUG);
    */
    /*
    addSubsectionHeaderOption("general options:");

    addValueOption("conf", "file", "alternate resin.xml file");
    addValueOption("user-properties", "file", "select an alternate $HOME/.resin file");
    addValueOption("mode", "string", "select .resin properties mode");

    addSpacerOption();

    addValueOption("resin-home", "dir", "alternate resin home");
    addValueOption("root-directory", "dir", "alternate root directory");
    addValueOption("resin-root", "dir", "alternate root directory", true);
    addValueOption("server-root", "dir", "alternate root directory", true);
    addValueOption("log-directory", "dir", "alternate log directory");
    addValueOption("license-directory", "dir", "alternate license directory");
    addValueOption("data-directory", "dir", "alternate resin-data directory");

    addSpacerOption();

    addFlagOption("verbose", "produce verbose output");
    */
  }

  /*
  @Override
  public int doCommand(A args)
    throws CommandArgumentException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  /*
  @Override
  public String usage(A args)
  {
    StringBuilder sb = new StringBuilder();

    sb.append("\n  unknown usage ").append(getClass().getSimpleName());

    return sb.toString();
  }
  */

  @Override
  public void parser(OptionContainer<?> parser)
  {
    _parser = (OptionContainer) parser;
  }

  @Override
  public String name()
  {
    String name = getClass().getSimpleName();

    int p = name.indexOf("Command");
    if (p >= 0)
      name = name.substring(0, p);

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);

      if (Character.isUpperCase(ch)) {
        if (i > 0)
          sb.append('-');

        sb.append(Character.toLowerCase(ch));
      }
      else {
        sb.append(ch);
      }
    }

    return sb.toString();
  }

  @Override
  public String getDescription()
  {
    return "";
  }

  @Override
  public CommandType getCategory()
  {
    return CommandType.general;
  }

  @Override
  public int getTailArgsMinCount()
  {
    return -1;
  }

  public OptionCommandLine<? super A>
  addOption(OptionCommandLine<? super A> option)
  {
    String name = option.getName();

    if (! name.startsWith("-")) {
      name = "--" + name;
    }

    return addOption(name, option);
  }

  public OptionCommandLine<? super A>
  addTinyOption(OptionCommandLine<? super A> option)
  {
    String name = option.getName();

    if (! name.startsWith("-")) {
      name = "-" + name;
    }

    return addOption(name, option);
  }

  public OptionCommandLine<? super A>
  addOption(String name, OptionCommandLine<? super A> option)
  {
    OptionCommandLine optionOld = _optionMap.get(name);

    if (optionOld != null) {
      return optionOld;
    }

    _optionMap.put(name, option);

    option.parser(this);

    if (option.getType() == ArgsType.DEFAULT) {
      option.type(ArgsType.COMMAND);
    }

    return option;
  }

  public OptionCommandLine<?> addOption(OptionCommandLine<?> option, int orderOffset)
  {
    // _optionMap.put(option.getName(), option);

    //return option;

    return (OptionCommandLine) addOption((OptionCommandLine) option);
  }

  @Override
  public OptionCommandLine<? super A> getOption(String name)
  {
    return (OptionCommandLine) _optionMap.get(name);
  }

  public OptionCommandLine<?> addFlagOption(String name, String description)
  {
    return addOption(new BootOptionFlag(name, description));
  }

  public OptionCommandLine<?> addValueOption(String name,
                                         String value,
                                         String description)
  {
    return addOption(new BootOptionValue(name, value, description));
  }

  public OptionCommandLine<?> addMultiValueOption(String name,
                                                  String value,
                                                  String description)
  {
    return addOption(new BootOptionMultiValue(name, value, description));
  }

  protected OptionCommandLine<?> addValueOption(String name,
                                         String value,
                                         String description,
                                         boolean deprecated)
  {
    return addValueOption(name, value, description).deprecated();
  }

  public OptionCommandLine<?> addIntValueOption(String name,
                                                String value,
                                                String description)
  {
    return addOption(new BootOptionValueInt(name, value, description));
  }

  protected void addSpacerOption()
  {
    // addOption(new SpacerBootOption());
  }

  protected void addSubsectionHeaderOption(String header)
  {
    // addOption(new SubsectionBootOption(header));
  }

  @Override
  public void fillOptions(List<OptionCommandLine<?>> options)
  {
    if (_parser != null) {
      _parser.fillOptions(options);
    }

    for (OptionCommandLine<?> option : _optionMap.values()) {
      options.add(option);
    }
  }

  @Override
  public final ExitCode doCommand(A args) throws CommandArgumentException
  {
    validateArgs(args);

    return doCommandImpl(args);
  }

  abstract protected ExitCode doCommandImpl(A args)
    throws CommandArgumentException;

  protected void validateArgs(A args)
  {
    int tailArgsCount = args.getTailArgs().size();
    int tailMinCount = getTailArgsMinCount();

    if (tailArgsCount < tailMinCount) {
      System.out.println(usage(args));

      throw new CommandArgumentException(L.l("error: expected {0} tail args but only saw {1}",
                                             tailMinCount, tailArgsCount));
    }
  }

  @Override
  public final String usage(ArgsBase args)
  {
    StringBuilder sb = new StringBuilder();

    sb.append("usage: " + args.getCommandName() + " " + name() + " [options...]"
              + getUsageTailArgs() + "\n");
    sb.append("\n");
    sb.append("  " + getDescription() + "\n");
    sb.append("\n");
    sb.append("where command options include:\n");

    ArrayList<OptionCommandLine<?>> options = new ArrayList<>();

    fillOptions(options);

    Collections.sort(options, new Comparator<OptionCommandLine<?>>() {
      public int compare(OptionCommandLine<?> a, OptionCommandLine<?> b)
      {
        int cmp = a.getType().ordinal() - b.getType().ordinal();

        if (cmp != 0) {
          return cmp;
        }

        return a.getName().compareTo(b.getName());
      }
    });

    ArgsType lastType = null;
    OptionCommandLine<?> lastOption = null;

    for (OptionCommandLine<?> option : options) {
      String usage = option.usage(args);

      if (usage != null) {
        if (lastOption != null && option.getName().equals(lastOption.getName())) {
          continue;
        }

        if (lastType != null && lastType != option.getType()) {
          sb.append("\n");
        }

        sb.append(usage);
        //sb.append(" (" + option.getType() + ")";);

        lastType = option.getType();
        lastOption = option;
      }
    }

    return sb.toString();
  }

  public String getUsageTailArgs()
  {
    return "";
  }

  protected RuntimeException error(ArgsDaemon args, String msg, Object ...param)
  {
    return new ConfigException(args.getCommandName()
                               + "/" + Version.getVersion()
                               + " " + L.l(msg, param));
  }

  protected RuntimeException error(String msg, Object ...args)
  {
    return new ConfigException(L.l(msg, args));
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
