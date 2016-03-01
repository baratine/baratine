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
 * @author Scott Ferguson
 */

package com.caucho.v5.view.freemarker;

import javax.annotation.PostConstruct;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import io.baratine.config.Config;
import io.baratine.web.RequestWeb;
import io.baratine.web.ViewAndMap;
import io.baratine.web.ViewWeb;

/**
 * freemarker view.
 */
public class ViewFreemarker implements ViewWeb<ViewAndMap>
{
  private Config _config;
  private Configuration _cfg;

  public ViewFreemarker(Config config)
  {
    _config = config;
    
    init();
  }

  @PostConstruct
  public void init()
  {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_22);
    
    cfg.setDefaultEncoding("UTF-8");
    
    String templatePath = _config.get("view.freemarker.templates",
                                     "classpath:/templates");
    
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    if (templatePath.startsWith("classpath:")) {
      String path = templatePath.substring(("classpath:").length());
      
      cfg.setClassLoaderForTemplateLoading(loader, path);
    }
    else {
      throw new UnsupportedOperationException();
    }
    
    cfg.setAutoFlush(false);
    
    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
    
    _cfg = cfg;
  }
  
  @Override
  public boolean render(RequestWeb req, ViewAndMap view)
  {
    String viewName = view.view();
    
    if (! viewName.endsWith(".ftl")) {
      return false;
    }
    
    try {
      Template tmpl = _cfg.getTemplate(viewName);
      
      req.type("text/html; charset=utf-8");

      tmpl.process(view.map(), req.writer());
    
      req.ok();
    } catch (Exception e) {
      e.printStackTrace();
      req.fail(e);
    }
    
    return true;
  }
}
