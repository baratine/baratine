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

package com.caucho.v5.view.velocity;

import java.io.InputStream;
import java.io.Writer;
import java.util.Properties;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.loader.ResourceLoader;

import io.baratine.config.Config;
import io.baratine.web.RequestWeb;
import io.baratine.web.ViewAndMap;
import io.baratine.web.ViewWeb;

public class ViewVelocity implements ViewWeb<ViewAndMap>
{
  private Config _config;
  private VelocityEngine _engine;

  public ViewVelocity(Config config)
  {
    _config = config;

    init();
  }

  private void init()
  {
    Properties properties = new Properties();

    String templatePath = _config.get("view.velocity.templates",
                                      "classpath:/templates");


    if (templatePath.startsWith("classpath:")) {
      String root = templatePath.substring("classpath:".length());

      properties.setProperty("resource.loader", "classpath");
      properties.setProperty("classpath.resource.loader.class", ClasspathLoader.class.getName());
      properties.setProperty("classpath.resource.loader.path", root);

      _engine = new VelocityEngine(properties);
    }
    else {
      // default is file
    }

    _engine = new VelocityEngine(properties);
  }

  @Override
  public boolean render(RequestWeb req, ViewAndMap view)
  {
    String viewName = view.view();

    if (! viewName.endsWith(".vm")) {
      return false;
    }

    try {
      Template template = _engine.getTemplate(viewName);
      VelocityContext context = new VelocityContext(view.map());

      req.type("text/html; charset=utf-8");

      Writer writer = req.writer();

      template.merge(context, writer);

      req.ok();
    }
    catch (Exception e) {
      req.fail(e);
    }

    return true;
  }

  public static class ClasspathLoader extends ResourceLoader {
    private String _root = "";

    public ClasspathLoader()
    {
    }

    @Override
    public void init(ExtendedProperties configuration)
    {
      String path = (String) configuration.getProperty("path");

      if (path != null) {
        _root = path;
      }
    }

    @Override
    public InputStream getResourceStream(String source) throws ResourceNotFoundException
    {
      InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(_root + "/" + source);

      return is;
    }

    @Override
    public boolean isSourceModified(Resource resource)
    {
      return false;
    }

    @Override
    public long getLastModified(Resource resource)
    {
      return 0;
    }

  }
}
