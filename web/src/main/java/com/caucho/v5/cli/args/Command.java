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

import com.caucho.v5.health.shutdown.ExitCode;

public interface Command<A extends ArgsBase>
{
  String name();

  ExitCode doCommand(A args)
    throws CommandLineException;

  String getDescription();

  CommandType getCategory();

  boolean isHide();
  
  default Command<A> hide()
  {
    return this;
  }

  OptionCli<? super A> getOption(String arg);

  int getTailArgsMinCount();

  default boolean isTailArgsAccepted()
  {
    return false;
  }

  String usage(ArgsBase args);

  //
  // builder
  //

  void parser(OptionContainer<?> parser);
  
  enum CommandType {
    general,
    bfs;
  }

  abstract static class Base<X extends ArgsBase>
    implements Command<X>
  {
    @Override
    public String name()
    {
      return getClass().getSimpleName();
    }

    @Override
    public boolean isHide()
    {
      return false;
    }

    @Override
    public String getDescription()
    {
      return null;
    }

    public void parser(OptionContainer<?> parser)
    {
    }

    @Override
    public OptionCli<? super X> getOption(String arg)
    {
      return null;
    }

    @Override
    public ExitCode doCommand(ArgsBase args)
        throws CommandLineException
    {
      throw new UnsupportedOperationException(getClass().getName());
    }

    /*
    @Override
    public boolean isValueOption(String key)
    {
      return false;
    }

    @Override
    public boolean isIntValueOption(String key)
    {
      return false;
    }

    @Override
    public boolean isFlag(String key)
    {
      return false;
    }
    */

    @Override
    public int getTailArgsMinCount()
    {
      return -1;
    }

    @Override
    public String usage(ArgsBase args)
    {
      StringBuilder sb = new StringBuilder();

      sb.append("\n  unknown usage ").append(getClass().getSimpleName());

      return sb.toString();
    }
  }

}
