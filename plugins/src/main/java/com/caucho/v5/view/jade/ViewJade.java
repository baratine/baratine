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

package com.caucho.v5.view.jade;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import de.neuland.jade4j.JadeConfiguration;
import de.neuland.jade4j.template.FileTemplateLoader;
import de.neuland.jade4j.template.JadeTemplate;
import de.neuland.jade4j.template.TemplateLoader;
import io.baratine.config.Config;
import io.baratine.web.RequestWeb;
import io.baratine.web.View;
import io.baratine.web.ViewResolver;

public class ViewJade implements ViewResolver<View>
{
  private Config _config;
  private JadeConfiguration _jadeConfig;

  public ViewJade(Config config)
  {
    _config = config;

    init();
  }

  private void init()
  {
    TemplateLoader templateLoader = null;

    String templatePath = _config.get("view.jade.templates",
                                      "classpath:/templates");

    if (templatePath.startsWith("classpath:")) {
      String root = templatePath.substring("classpath:".length());

      templateLoader = new ClasspathTemplateLoader(root);
    }
    else {
      templateLoader = new FileTemplateLoader(templatePath, "UTF-8");
    }

    _jadeConfig = new JadeConfiguration();
    _jadeConfig.setTemplateLoader(templateLoader);
  }

  @Override
  public boolean render(RequestWeb req, View view)
  {
    String viewName = view.name();

    if (! viewName.endsWith(".jade")) {
      return false;
    }

    try {
      JadeTemplate template = _jadeConfig.getTemplate(viewName);

      req.type("text/html; charset=utf-8");

      _jadeConfig.renderTemplate(template, view.map(), req.writer());

      req.ok();
    }
    catch (Exception e) {
      req.fail(e);
    }

    return true;
  }

  static class ClasspathTemplateLoader implements TemplateLoader {
    private String _root;

    public ClasspathTemplateLoader(String root)
    {
      _root = root;
    }

    @Override
    public Reader getReader(String name)
      throws IOException
    {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();

      InputStream is = loader.getResourceAsStream(_root + "/" + name);

      return new InputStreamReader(is, "UTF-8");
    }

    @Override
    public long getLastModified(String name)
    {
      return -1;
    }
  }
}
