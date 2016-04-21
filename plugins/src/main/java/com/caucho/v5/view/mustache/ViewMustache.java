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

package com.caucho.v5.view.mustache;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.github.mustachejava.MustacheResolver;
import com.github.mustachejava.resolver.ClasspathResolver;
import com.github.mustachejava.resolver.DefaultResolver;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import io.baratine.config.Config;
import io.baratine.web.RequestWeb;
import io.baratine.web.View;
import io.baratine.web.ViewRender;

public class ViewMustache implements ViewRender<View>
{
  private Config _config;

  private MustacheFactory _factory;

  public ViewMustache(Config config)
  {
    _config = config;

    init();
  }

  private void init()
  {
    String templatePath = _config.get("view.mustache.templates",
                                      "classpath:/templates");

    MustacheResolver resolver;

    if (templatePath.startsWith("classpath:")) {
      String root = templatePath.substring("classpath:".length());

      resolver = new ClasspathResolver(root);

      //ClassLoader loader = Thread.currentThread().getContextClassLoader();
      //resolver = new MustacheResolverImpl(loader, root);
    }
    else {
      resolver = new DefaultResolver(templatePath);
    }

    MustacheFactory factory = new DefaultMustacheFactory(resolver);

    _factory = factory;
  }

  @Override
  public boolean render(RequestWeb req, View view)
  {
    String viewName = view.view();

    if (! viewName.endsWith(".mustache")) {
      return false;
    }

    try {
      Mustache mustache = _factory.compile(viewName);

      req.type("text/html; charset=utf-8");

      Writer writer = req.writer();

      mustache.execute(writer, view.map()).flush();

      req.ok();
    }
    catch (Exception e) {
      req.fail(e);
    }

    return true;
  }

  static class MustacheResolverImpl implements MustacheResolver {
    private ClassLoader _loader;
    private String _root;

    public MustacheResolverImpl(ClassLoader loader, String root)
    {
      _loader = loader;
      _root = root;
    }

    @Override
    public Reader getReader(String resourceName)
    {
      InputStream is = _loader.getResourceAsStream(_root + "/" + resourceName);

      if (is == null) {
        return null;
      }

      try {
        return new InputStreamReader(is, "UTF-8");
      }
      catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
