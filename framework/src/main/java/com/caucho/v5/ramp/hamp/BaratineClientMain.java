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

package com.caucho.v5.ramp.hamp;

import io.baratine.service.MethodRef;
import io.baratine.service.ResultFuture;
import io.baratine.service.ServiceRef;

import java.util.concurrent.TimeUnit;

/**
 * Test class for hamp.
 */
public class BaratineClientMain
{
  public static void main(String []argv)
  {
    boolean isHello = false;
    int index = 0;
    
    if (argv.length == 3 && argv[0].equals("--hello")) {
      isHello = true;
      index = 1;
    }
    else if (argv.length < 3) {
      System.err.println("usage: java -jar baratine-client.jar url service method arg1 ...");
      return;
    }
    
    String url = argv[index];
    String serviceName = argv[index + 1];
    
    try (BaratineClient client = new BaratineClient(url)) {
      client.connect();
      
      ServiceRef serviceRef = client.service(serviceName);
      
      if (isHello) {
        // test for bytecode
        Hello hello = serviceRef.as(Hello.class);
        
        System.out.println("Hello: " + hello.hello());
      
      }
      else {
        String methodName = argv[2];
        MethodRef methodRef = serviceRef.getMethod(methodName);
      
        ResultFuture<Object> future = new ResultFuture<>();
      
        Object []args = new Object[argv.length - 3];
        for (int i = 0; i < args.length; i++) {
          args[i] = argv[i + 3];
        }
      
        methodRef.query(null, future, args);
      
        System.out.println("Result: " + future.get(10, TimeUnit.SECONDS));
      }
    }
  }
  
  public interface Hello {
    public String hello();
  }
}
