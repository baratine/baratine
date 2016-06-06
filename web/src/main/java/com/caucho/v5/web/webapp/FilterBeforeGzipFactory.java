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

import com.caucho.v5.config.Priority;

import io.baratine.web.RequestWeb;
import io.baratine.web.ServiceWeb;

/**
 * View with associated type meta-data
 */
@Priority(-100)
class FilterBeforeGzipFactory implements FilterFactory<ServiceWeb>
{
  @Override
  public ServiceWeb apply(RouteBuilderAmp builder)
  {
    return new FilterBeforeGzip();
  }
  
  private static class FilterBeforeGzip implements ServiceWeb
  {

    @Override
    public void service(RequestWeb request) throws Exception
    {
      String acceptEncoding = request.header("accept-encoding");
      
      System.out.println("ENC: " + acceptEncoding);
      
      if (acceptEncoding.indexOf("gzip") >= 0) {
        pushGzip();
      }
      
      request.ok();
    }
    
    protected void pushGzip()
    {
      
    }
    
  }
}
