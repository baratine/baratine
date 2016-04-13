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

package com.caucho.v5.cli.server;

import java.io.IOException;
import java.io.InputStream;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.files.FilesDeployService;
import com.caucho.v5.bartender.proc.AdminServiceSync;
import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;
import com.caucho.v5.vfs.WriteStreamOld;

public class ReportPdfCommand extends RemoteCommandBase
{
  private static final L10N L = new L10N(ReportPdfCommand.class);
  
  public String name()
  {
    return "report-pdf";
  }
  
  @Override
  public String getDescription()
  {
    return "requests a pdf report";
  }
  
  @Override
  protected void initBootOptions()
  {
    super.initBootOptions();

    addValueOption("output-file", 
                   "file", "filename for the generated PDF").tiny("o");
  }

  @Override
  protected ExitCode doCommandImpl(ArgsCli args,
                               ServiceManagerClient client)
  {
    AdminServiceSync admin = client.service("remote:///management")
                                   .as(AdminServiceSync.class);
    
    String path = admin.reportPdf();
    
    String outputFile = args.getArg("output-file");
    
    if (outputFile == null) {
      outputFile = args.getProgramName() + ".pdf";
    }
    
    PathImpl localPath = VfsOld.lookup(outputFile);
    
    FilesDeployService files = client.service("remote:///bartender-files")
                                     .as(FilesDeployService.class);
    
    StreamSource ss = files.getFile("bfs://" + path);
    
    if (ss == null) {
      throw new ConfigException(L.l("PDF report failed"));
    }
    
    try (InputStream is = ss.openInputStream()) {
      try (WriteStreamOld os = localPath.openWrite()) {
        os.writeStream(is);
      }
      
      args.getOut().println("  writing PDF report to " + outputFile);
    } catch (IOException e) {
      throw ConfigException.wrap(e);
    }
    
    return ExitCode.OK;
  }
}
