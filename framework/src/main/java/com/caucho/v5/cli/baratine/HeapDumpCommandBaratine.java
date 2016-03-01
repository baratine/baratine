/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Alex Rojkov
 */

package com.caucho.v5.cli.baratine;

import java.io.IOException;

import com.caucho.v5.cli.spi.CommandBase;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.profile.HeapDump;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.WriteStream;

public class HeapDumpCommandBaratine extends CommandBase<ArgsCli>
{
  private static final L10N L = new L10N(HeapDumpCommandBaratine.class);

  @Override
  protected void initBootOptions()
  {
    super.initBootOptions();
  }

  @Override
  public String getDescription()
  {
    return "gathers a heap dump";
  }

  @Override
  protected ExitCode doCommandImpl(ArgsCli args)
  {
    doHeapDump(args.getOut());

    return ExitCode.OK;
  }

  private void doHeapDump(WriteStream out)
  {
    try {
      HeapDump dump = HeapDump.create();

      dump.writeExtendedHeapDump(out.getPrintWriter());

      out.flush();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public String executeJson()
    throws IOException
  {
    HeapDump dump = HeapDump.create();

    return dump.jsonHeapDump();
  }
}
