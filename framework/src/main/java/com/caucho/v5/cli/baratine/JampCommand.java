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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.baratine.client.BaratineClient;
import com.caucho.v5.cli.server.BootArgumentException;
import com.caucho.v5.cli.server.ServerCommandBase;
import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.json.io.JsonWriter;
import com.caucho.v5.util.L10N;

import io.baratine.service.MethodRef;
import io.baratine.service.ResultFuture;
import io.baratine.service.ServiceRef;

/**
 * Command to query a jamp command.
 */
public class JampCommand extends ServerCommandBase<ArgsCli>
{
  private static final L10N L = new L10N(JampCommand.class);

  @Override
  public String getDescription()
  {
    return "jamp call";
  }

  @Override
  public String getUsageTailArgs()
  {
    return " address method <args>";
  }

  @Override
  public int getTailArgsMinCount()
  {
    return 2;
  }

  @Override
  protected void initBootOptions()
  {
    super.initBootOptions();

    addValueOption("pod", "name", "name of the service's pod (default 'pod')");
    addValueOption("url", "url", "url of the service");
  }

  @Override
  public ExitCode doCommandImpl(ArgsCli args)
      throws BootArgumentException
  {
    //ServerConfigBoot server = config.findServer(args);

    String addressServer = args.config().get("server.address", "127.0.0.1");
    /*
    server.getAddress();
    if ("".equals(addressServer)) {
      addressServer = "127.0.0.1";
    }
    */
    int portServer = args.config().get("server.port", int.class, 8080);

    String pod = args.getArg("pod", "pod");
    
    String url = args.getArg("url");

    if (url == null) {
      url = "hamp://" + addressServer + ":" + portServer + "/s/" + pod + "/";
    }

    try (BaratineClient client = new BaratineClient(url)) {
      String address = args.getTail(0);

      if (address == null) {
        throw new CommandArgumentException(L.l("jamp-query needs a Baratine address"));
      }

      String methodName = args.getTail(1);

      if (methodName == null) {
        throw new CommandArgumentException(L.l("jamp-query needs a Baratine method"));
      }

      ArrayList<String> argList = new ArrayList<>();
      for (int i = 2; i < args.getTailArgs().size(); i++) {
        argList.add(args.getTail(i));
      }

      if (address.startsWith("/")) {
        address = "remote://" + address;
      }

      ServiceRef service = client.service(address);

      MethodRef methodRef = service.getMethod(methodName);

      ResultFuture<Object> future = new ResultFuture<>();
      Object []argsMethod = new Object[argList.size()];
      argList.toArray(argsMethod);

      methodRef.query(future, argsMethod);

      //Object result = future.get(65, TimeUnit.SECONDS);
      Object result = future.get(30, TimeUnit.SECONDS);

      PrintWriter out = args.envCli().getOut().getPrintWriter();
      try (JsonWriter jOut = new JsonWriter(out)) {
        jOut.writeObjectTop(result);
      }
      out.println();
      out.flush();

        // System.out.println(value);

      return ExitCode.OK;
    } catch (Exception e) {
      //e.printStackTrace();
      
      throw e;
    }
  }
}
