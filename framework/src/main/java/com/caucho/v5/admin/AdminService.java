/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package com.caucho.v5.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.RegistryAmp;

import io.baratine.config.Config;
import io.baratine.service.Services;
import io.baratine.web.IncludeWeb;
import io.baratine.web.RequestWeb;
import io.baratine.web.ServiceWeb;
import io.baratine.web.WebBuilder;

public class AdminService implements IncludeWeb, ServiceWeb {
  @Inject
  private Config _config;

  @Override
  public void build(WebBuilder builder)
  {
    String url = _config.get("admin.path", "/baratine-admin");

    builder.get(url).to(this);
  }

  @Override
  public void service(RequestWeb request) throws Exception
  {
    ServicesAmp services = ServicesAmp.current();
    RegistryAmp registry = services.registry();

    StringBuilder sb = new StringBuilder();

    sb.append("<html>");
    sb.append("<head><title>Baratine Admin</title></head.");
    sb.append("<body>");
    sb.append("\n");

    sb.append("<h1>Baratine Admin</h1>");
    sb.append("\n");

    sb.append("<h2>Services</h2>");
    sb.append("<table border=\"1\" cellpadding=\"5\">");
    sb.append("<th>Service Address</th><th>Service API</th><th>Injection Example</th>");
    sb.append("\n");

    ArrayList<ServiceRefAmp> systemServices = new ArrayList<>();
    ArrayList<ServiceRefAmp> userServices = new ArrayList<>();

    for (ServiceRefAmp service : registry.getServices()) {
      String className = service.api().getType().getTypeName();

      if (className.startsWith("java.lang")
          || className.startsWith("com.caucho")) {
        systemServices.add(service);
      }
      else {
        userServices.add(service);
      }
    }

    Collections.sort(systemServices, (a, b) -> a.address().compareTo(b.address()));
    Collections.sort(userServices, (a, b) -> a.address().compareTo(b.address()));

    for (ServiceRefAmp service : systemServices) {
      sb.append("<tr>");

      sb.append("<td>");
      sb.append(service.address());
      sb.append("</td>");

      sb.append("<td>");
      sb.append(service.api().getType().getTypeName());
      sb.append("</td>");

      sb.append("</tr>");
      sb.append("\n");
    }

    for (ServiceRefAmp service : userServices) {
      sb.append("<tr>");

      sb.append("<td><b>");
      sb.append(service.address());
      sb.append("</b></td>");

      sb.append("<td>");
      sb.append(service.api().getType().getTypeName());
      sb.append("</td>");

      if (service.address().startsWith("/")) {
        sb.append("<td>");
        sb.append("@Inject @Service " + service.address().substring(1) + " service");
        sb.append("</td>");
      }

      sb.append("</tr>");
      sb.append("\n");
    }

    sb.append("</table>");
    sb.append("\n");

    appendConfig(sb);

    sb.append("</body>");
    sb.append("</html>");

    request.type("text/html");
    request.write(sb.toString());

    request.ok();
  }

  private void appendConfig(StringBuilder sb)
  {
    Config config = Services.current().injector().config();

    ArrayList<String> keyList = new ArrayList<>();

    for (Map.Entry<String,String> entry : config.entrySet()) {
      keyList.add(entry.getKey());
    }

    Collections.sort(keyList);

    sb.append("<h2>Configuration</h2>");
    sb.append("<table border=\"1\" cellpadding=\"5\">");
    sb.append("<th>Key</th><th>Value</th>");
    sb.append("\n");

    for (String key : keyList) {
      String value = config.get(key).replace("\n", "\\n").replace("\r", "\\r");

      sb.append("<tr>");

      sb.append("<td>");
      sb.append(key);
      sb.append("</td>");

      sb.append("<td>");
      sb.append(value);
      sb.append("</td>");

      sb.append("</tr>");
      sb.append("\n");
    }

    sb.append("</table>");
    sb.append("\n");
  }
}