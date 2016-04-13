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

import java.io.IOException;
import java.util.ArrayList;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.proc.AdminServiceSync;
import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.vfs.VfsOld;
import com.caucho.v5.vfs.WriteStreamOld;

public class StoreSaveCommand extends RemoteCommandBase
{
  private String _context = "web-app:production/webapp/default/ROOT";
  
  public void setContext(String context)
  {
    _context = context;
  }
  
  public String getContext()
  {
    return _context;
  }
  
  @Override
  protected void initBootOptions()
  {
    addValueOption("output", "file", "file name where dump will be stored").tiny("o");
    
    super.initBootOptions();
  }
  
  @Override
  public String getDescription()
  {
    return "saves the store to an archive";
  }

  @Override
  public ExitCode doCommandImpl(ArgsCli args,
                            ServiceManagerClient client)
  {
    AdminServiceSync admin = client.service("remote:///management")
                               .as(AdminServiceSync.class);

    String name = "test";

    ArrayList<String> tail = args.getTailArgs();
    
    if (tail.size() > 0) {
      name = tail.get(0);
    }
    
    String context = getContext();
    String pod = "pod";
    
    StreamSource result = null;//admin.doStoreDump(pod, name);

    String fileName = args.getArg("-o");
    
    if (fileName == null) {
      fileName = args.getArg("-output");
    }

    if (fileName == null) {
      WriteStreamOld out = args.envCli().getOut();
      
      if (result != null) {
        try {
          out.writeStream(result.getInputStream());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }

      return ExitCode.OK;
    }

    try (WriteStreamOld out = VfsOld.lookup(fileName).openWrite()) {
      out.print(result);

      System.out.println("store dump was written to `"
                         + fileName
                         + "'");

      return ExitCode.OK;
    } catch (IOException e) {
      e.printStackTrace();

      return ExitCode.FAIL;
    }
  }
}
