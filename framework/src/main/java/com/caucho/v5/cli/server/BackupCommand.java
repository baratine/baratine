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

package com.caucho.v5.cli.server;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.proc.AdminServiceSync;
import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.QDate;

public class BackupCommand extends RemoteCommandBase
{
  @Override
  protected void initBootOptions()
  {
    addValueOption("tag", "name", "tag name for the snapshot").tiny("t");
    
    super.initBootOptions();
  }
  
  @Override
  public String getDescription()
  {
    return "saves the store to an archive";
  }
  
  @Override
  public boolean isTailArgsAccepted()
  {
    return true;
  }

  @Override
  public ExitCode doCommandImpl(ArgsCli args,
                            ServiceManagerClient client)
  {
    AdminServiceSync admin = client.service("remote:///management")
                                   .as(AdminServiceSync.class);

    String tag = args.getArg("tag");
    
    if (tag == null) {
      tag = QDate.formatLocal(CurrentTime.currentTime(),
                              "%Y%m%dT%H%M%S");
    }
    
    String result = admin.backup(tag);
    
    args.envCli().println(result);
    
    return ExitCode.OK;
  }
}
