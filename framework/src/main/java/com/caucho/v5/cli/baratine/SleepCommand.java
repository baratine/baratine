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

package com.caucho.v5.cli.baratine;

import com.caucho.v5.cli.server.ServerCommandBase;
import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;

/**
 * Command to benchmark a jamp service.
 */
public class SleepCommand extends ServerCommandBase<ArgsCli>
{
  private static final L10N L = new L10N(SleepCommand.class);

  @Override
  public String getDescription()
  {
    return "sleeps, for benchmarking";
  }

  @Override
  public String getUsageTailArgs()
  {
    return " time";
  }

  @Override
  public int getTailArgsMinCount()
  {
    return 0;
  }

  @Override
  protected void initBootOptions()
  {
    super.initBootOptions();
  }

  @Override
  public ExitCode doCommandImpl(ArgsCli args)
    throws CommandArgumentException
  {
    try {
      double time = 0;

      if (args.getTailArgs().size() > 0) {
        time = Double.parseDouble(args.getTail(0));
      }

      Thread.sleep((long) (1000 * time));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CommandArgumentException(e);
    }

    return ExitCode.OK;
  }
}
