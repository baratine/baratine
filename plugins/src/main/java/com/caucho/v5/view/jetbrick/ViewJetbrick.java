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

package com.caucho.v5.view.jetbrick;

import java.util.Properties;

import io.baratine.config.Config;
import io.baratine.web.RequestWeb;
import io.baratine.web.View;
import io.baratine.web.ViewRender;
import jetbrick.template.JetContext;
import jetbrick.template.JetEngine;
import jetbrick.template.JetTemplate;

public class ViewJetbrick implements ViewRender<View>
{
  private Config _config;
  private JetEngine _engine;

  public ViewJetbrick(Config config)
  {
    _config = config;

    init();
  }

  private void init()
  {
    Properties properties = new Properties();

    String templatePath = _config.get("view.jade.templates",
                                      "classpath:/templates");

    if (templatePath.startsWith("classpath:")) {
      String root = templatePath.substring("classpath:".length());

      properties.put("jetx.template.loaders", "jetbrick.template.loader.ClasspathResourceLoader");
      properties.put("jetbrick.template.loader.ClasspathResourceLoader.root", root);
    }
    else {
    }

    _engine = JetEngine.create(properties);
  }

  @Override
  public boolean render(RequestWeb req, View view)
  {
    String viewName = view.view();

    if (! viewName.endsWith(".jetx")) {
      return false;
    }

    try {
      JetTemplate template = _engine.getTemplate(viewName);
      JetContext ctx = new JetContext();
      ctx.putAll(view.map());

      req.type("text/html; charset=utf-8");

      template.render(ctx, req.writer());

      req.ok();
    }
    catch (Exception e) {
      req.fail(e);
    }

    return true;
  }
}
