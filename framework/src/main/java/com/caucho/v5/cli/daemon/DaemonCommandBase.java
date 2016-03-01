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

package com.caucho.v5.cli.daemon;

import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.cli.spi.CommandBase;
import com.caucho.v5.cli.spi.OptionCommandLine;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;

abstract public class DaemonCommandBase<A extends ArgsDaemon> extends CommandBase<A>
{
  @Override
  protected void initBootOptions()
  {
    super.initBootOptions();
  }

  static class Conf extends OptionCommandLine.Base<ArgsDaemon>
  {
    @Override
    public String getValueDescription()
    {
      return "FILE";
    }

    @Override
    public String getDescription()
    {
      return "configuration file";
    }

    @Override
    public int parse(ArgsDaemon args,
                     String[] argv,
                     int index)
        throws CommandArgumentException
    {
      args.setConfigPathRaw(argv[index + 1]);
      argv[index + 1] = args.getConfigPath().getFullPath();

      return index + 1;
    }
  }

  static class RootDir
    extends OptionCommandLine.Base<ArgsDaemon>
  {
    @Override
    public String getDescription()
    {
      return "root deployment directory";
    }

    @Override
    public String getValueDescription()
    {
      return "DIR";
    }

    @Override
    public int parse(ArgsDaemon args,
                     String[] argv,
                     int index)
        throws CommandArgumentException
    {
      args.setRootDirectory(VfsOld.lookup(argv[index + 1]));

      String value = args.getRootDirectory().getFullPath();
      argv[index + 1] = value;

      addStringValue(args, value);

      return index + 1;
    }
  }

  static class DataDir
    extends OptionCommandLine.Base<ArgsDaemon>
  {
    @Override
    public String getValueDescription()
    {
      return "DIR";
    }

    @Override
    public String getDescription()
    {
      return "internal database directory";
    }

    @Override
    public int parse(ArgsDaemon args,
                     String[] argv,
                     int index)
        throws CommandArgumentException
    {
      args.setDataDirectory(VfsOld.lookup(argv[index + 1]));
      argv[index + 1] = args.getDataDirectory().getFullPath();

      return index + 1;
    }
  }

  static class LogDir
    extends OptionCommandLine.Base<ArgsDaemon>
  {
    @Override
    public String getValueDescription()
    {
      return "DIR";
    }

    @Override
    public String getDescription()
    {
      return "log file directory";
    }

    @Override
    public int parse(ArgsDaemon args,
                     String[] argv,
                     int index)
        throws CommandArgumentException
    {
      args.setLogDirectory(VfsOld.lookup(argv[index + 1]));

      return index + 1;
    }
  }

  static class UserProperties
    extends OptionCommandLine.Base<ArgsDaemon>
  {
    @Override
    public String getValueDescription()
    {
      return "FILE";
    }

    @Override
    public int parse(ArgsDaemon args,
                     String[] argv,
                     int index)
        throws CommandArgumentException
    {
      PathImpl path = VfsOld.lookup(argv[index + 1]);

      args.setUserProperties(path);

      addStringValue(args, path.getURL());

      return index + 1;
    }
  }
}
