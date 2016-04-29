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

package com.caucho.v5.view.thymeleaf;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.TemplateResolver;

import io.baratine.config.Config;
import io.baratine.web.RequestWeb;
import io.baratine.web.View;
import io.baratine.web.ViewResolver;

public class ViewThymeleaf implements ViewResolver<View>
{
  private Config _config;
  private TemplateEngine _engine;

  public ViewThymeleaf(Config config)
  {
    _config = config;

    init();
  }

  private void init()
  {
    TemplateResolver resolver = null;

    String templatePath = _config.get("view.thymeleaf.templates",
                                      "classpath:/templates");

    if (templatePath.startsWith("classpath:")) {
      String root = templatePath.substring("classpath:".length());

      resolver = new ClassLoaderTemplateResolver();

      resolver.setPrefix(root);
    }
    else {
      resolver = new TemplateResolver();
    }

    _engine = new TemplateEngine();
    _engine.setTemplateResolver(resolver);
  }

  @Override
  public boolean render(RequestWeb req, View view)
  {
    String viewName = view.name();

    if (! viewName.endsWith(".html")) {
      return false;
    }

    try {
      Context ctx = new Context();
      ctx.setVariables(view.map());

      req.type("text/html; charset=utf-8");

      _engine.process(viewName, ctx, req.writer());

      req.ok();
    }
    catch (Exception e) {
      req.fail(e);
    }

    return true;
  }
}
