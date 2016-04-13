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

package com.caucho.v5.web.webapp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.Vfs;

import io.baratine.config.Config;
import io.baratine.web.RequestWeb;
import io.baratine.web.ServiceWeb;

/**
 * Static files.
 */
public class WebStaticFile implements ServiceWeb
{
  private @Inject Config _config;
  
  private boolean _isInit;
  private Path _root;
  
  @PostConstruct
  private void init()
  {
    if (_isInit) {
      return;
    }
    _isInit = true;
    
    String root = _config.get("server.file", "classpath:/public");
    
    _root = Vfs.path(root);
  }
  
  /**
   * Service a request.
   *
   * @param request the http request facade
   * @param response the http response facade
   */
  @Override
  public void service(RequestWeb req)
  {
    init();
    
    String pathInfo = req.pathInfo();
    
    if (pathInfo.isEmpty() || pathInfo.equals("/")) {
      pathInfo = _config.get("server.index", "index.html");
    } else {
      pathInfo = pathInfo.substring(1);
    }

    Path path = _root.resolve(pathInfo);
    //PathImpl path = Vfs.lookup(root).lookup("./" + pathInfo);
    
    if (Files.isDirectory(path)) {
      path = path.resolve(_config.get("server.index", "index.html"));
    }
    
    long len;
    
    try {
      len = Files.size(path);
    } catch (IOException e) {
      
      req.fail(new FileNotFoundException("file not found: " + req.uri()));
      return;
    }
    
    try (InputStream is = Files.newInputStream(path)) {
      if (is == null) {
        req.fail(new FileNotFoundException(pathInfo));
        return;
      }
      
      req.header("content-type", mimeType(pathInfo));
      
      // XXX:
      if (len > 0) {
        req.length(len);
      }
      
      TempBuffer tBuf = TempBuffer.create();
      byte []buffer = tBuf.buffer();
      
      int sublen;
      
      while ((sublen = is.read(buffer, 0, buffer.length)) > 0) {
        req.write(buffer, 0, sublen);
      }
      
      tBuf.free();
    } catch (IOException e) {
      req.fail(e);
    }
    
    req.ok();
  }
  
  private String mimeType(String pathInfo)
  {
    int p = pathInfo.lastIndexOf('.');
    
    String enc = "utf-8";
    
    if (p < 0) {
      return "text/plain; charset=" + enc;
    }
    
    String ext = pathInfo.substring(p);
    
    switch (ext) {
    case ".html":
      return "text/html; charset=" + enc;
      
    case ".css":
      return "text/css; charset=" + enc;
      
    case ".js":
      return "application/javascript; charset=" + enc;
      
    case ".png":
      return "image/png";
      
    case ".pdf":
      return "application/pdf";
      
    case ".gif":
      return "image/gif";
      
    case ".jpg":
      return "image/jpeg";
      
    default:
      return "text/plain; charset=" + enc;
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
