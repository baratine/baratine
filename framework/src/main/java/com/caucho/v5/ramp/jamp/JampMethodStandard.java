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

package com.caucho.v5.ramp.jamp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.stub.ParameterAmp;
import com.caucho.v5.json.io.InJson;
import com.caucho.v5.json.io.InJson.Event;
import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.json.io.JsonWriter;
import com.caucho.v5.json.ser.JsonFactory;

import io.baratine.service.MethodRef;
import io.baratine.service.ResultFuture;
import io.baratine.service.ServiceException;
import io.baratine.service.ServiceExceptionIllegalArgument;
import io.baratine.web.HttpStatus;
import io.baratine.web.RequestWeb;

/**
 * Method introspected using jaxrs
 */
public class JampMethodStandard extends JampMethodRest
{
  private static final Logger log
    = Logger.getLogger(JampMethodStandard.class.getName());

  private final JsonFactory _factory;

  private MethodRefAmp _methodRef;
  private JampArg []_params;

  private ParameterAmp[] _paramTypes;

  private JampArg _paramVarArgs;
  private int _argLength;

  private JampMarshal _varArgsMarshal;

  private ServicesAmp _manager;

  JampMethodStandard(JampMethodBuilder builder)
  {
    _factory = builder.getJsonFactory();
    
    _methodRef = builder.getMethod();
    _manager = (ServicesAmp) _methodRef.serviceRef().services();


    _params = builder.getParams();
    _paramTypes = _methodRef.parameters();

    _varArgsMarshal = builder.getVarArgMarshal();

    if (_varArgsMarshal != null) {
      _argLength = _params.length + 1;
    }
    else if (_params != null) {
      _argLength = _params.length;
    }
    else {
      _argLength = 0;
    }
  }

  /*
  JampMethodStandard(MethodRef method)
  {
    this(new JampMethodBuilder(method));
  }
  */

  protected MethodRefAmp getMethod()
  {
    return _methodRef;
  }

  @Override
  public boolean isClosed()
  {
    MethodRefAmp method = getMethod();

    return method.isClosed();
  }

  @Override
  public void doGet(RequestWeb req,
                    String pathInfo)
    throws IOException
  {
    Object []args = null;

    if (_params == null) {
      ArrayList<Object> paramList = new ArrayList<>();

      for (int i = 0; i < 10; i++) {
        String param = req.path("p" + i);

        if (param == null) {
          break;
        }

        paramList.add(param);
      }

      args = new Object[paramList.size()];
      paramList.toArray(args);
    }
    else if (_varArgsMarshal != null) {
      args = new Object[_argLength];

      int i = 0;
      for (i = 0; i < _params.length; i++) {
        args[i] = _params[i].get(req, pathInfo);
      }

      ArrayList<Object> varList = new ArrayList<>();
      for (; i < 10; i++) {
        String param = req.path("p" + i);

        if (param == null) {
          break;
        }

        Object paramObject = _varArgsMarshal.toObject(param);

        varList.add(paramObject);
      }

      Object []varArgs = new Object[varList.size()];
      varList.toArray(varArgs);

      args[_argLength - 1] = varArgs;
    }
    else {
      args = new Object[_argLength];

      for (int i = 0; i < _argLength; i++) {
        args[i] = _params[i].get(req, pathInfo);
      }
    }

    evalQuery(req, args);
  }
  
  private void evalQuery(RequestWeb req, 
                         Object []args)
    throws IOException
  {
    try {
      // ResultFuture<Object> future = new ResultFuture<>();

      // _methodRef.query(future, args);
      
      //HeadersAmp headers = HeadersNull.NULL;
      
      ServicesAmp manager = _methodRef.serviceRef().services();
      
      Object result = manager.run(60, TimeUnit.SECONDS, 
                                  r->{ _methodRef.query(r, args); });
      /*
      Object result = _manager.run(future, 60, TimeUnit.SECONDS, ()->{
        //_methodRef.query(HeadersNull.NULL, future, args);
        _methodRef.query(future, args);
        //ServiceRef.flushOutbox();
      });
      */

      printValue(req, result);
    } catch (Throwable e) {
      if (log.isLoggable(Level.FINER)) {
        log.log(Level.FINER, e.toString(), e);
      }

      try {
        printException(req, e);
      } catch (IOException e1) {
        if (log.isLoggable(Level.FINER)) {
          log.log(Level.FINER, e.toString(), e);
        }
      }
    }
  }

  @Override
  public void doPost(RequestWeb req,
                     String pathInfo)
    throws IOException
  {
    JsonReader jIn = null;//new JsonReader(req.body(Reader.class));

    if (jIn.peek() != InJson.Event.START_ARRAY) {
      req.halt(HttpStatus.FORBIDDEN);
      return;
    }

    jIn.next();

    try {
      Object []args = readArgs(jIn, _paramTypes);

      ResultFuture<Object> future = new ResultFuture<>();

      _methodRef.query(HeadersNull.NULL, future, args);

      printFuture(req, future);
    } catch (Throwable e) {
      if (log.isLoggable(Level.FINER)) {
        log.log(Level.FINER, e.toString(), e);
      }
    
      printException(req, e);
    }
  }

  @Override
  public void doPut(RequestWeb req,
                     String pathInfo)
    throws IOException
  {
    if (! "put".equals(_methodRef.getName())) {
      super.doPut(req, pathInfo);
      return;
    }

    JsonReader jIn = null;//new JsonReader(req.body(Reader.class));

    if (jIn.peek() != InJson.Event.START_ARRAY) {
      ServiceException exn = new ServiceExceptionIllegalArgument();
      
      printException(req, exn);
      return;
    }
    
    jIn.next();

    try {
      ArrayList<Object> args = new ArrayList<>();
      int i = 0;
      
      for (; i < _paramTypes.length; i++) {
        Object arg = jIn.readObject(_paramTypes[i].type());
        
        args.add(arg);
        
        if (jIn.peek() != InJson.Event.END_ARRAY) {
          break;
        }
      }
      
      Event event = jIn.peek();
      while ((event = jIn.peek()) != InJson.Event.END_ARRAY
             && event != null) {
        jIn.readObject(Object.class);
      }
      
      while (args.size() < _paramTypes.length) {
        args.add(null);
      }

      Object []argArray= new Object[args.size()];
      args.toArray(argArray);

      ResultFuture<Object> future = new ResultFuture<>();

      _methodRef.query(HeadersNull.NULL, future, argArray);

      printFuture(req, future);
    } catch (Throwable e) {
      if (log.isLoggable(Level.FINER)) {
        log.log(Level.FINER, e.toString(), e);
      }

      printException(req, e);
    }
  }

  @Override
  public void doDelete(RequestWeb req,
                       String pathInfo)
    throws IOException
  {
    if (! "delete".equals(_methodRef.getName())) {
      super.doDelete(req, pathInfo);
      return;
    }

    Object []args = new Object[] {};

    try {
      ResultFuture<Object> future = new ResultFuture<>();

      _methodRef.query(HeadersNull.NULL, future, args);

      printFuture(req, future);
    } catch (Throwable e) {
      if (log.isLoggable(Level.FINER)) {
        log.log(Level.FINER, e.toString(), e);
      }
  
      printException(req, e);
    }
  }

  protected void printFuture(RequestWeb req,
                             ResultFuture<Object> future)
    throws IOException
  {
    try {
      Object result = future.get(180, TimeUnit.SECONDS);

      printValue(req, result);
    } catch (Throwable exn) {
      if (log.isLoggable(Level.FINER)) {
        log.log(Level.FINER, exn.toString(), exn);
      }
        
      printException(req, exn);
    }
  }
  
  protected void printValue(RequestWeb req, 
                            Object result)
    throws IOException
  {
    // nam: 2014-05-08
    String jsonCallback = req.path("jsoncallback");

    try (Writer out = req.writer()) {
      if (jsonCallback != null) {
        out.write(jsonCallback);
        out.write("(");
      }

      try {
        try (JsonWriter jOut = new JsonWriter(out, _factory)) {
          jOut.writeStartObject();
          
          jOut.writeKey("status");
          jOut.writeString("ok");
          
          jOut.writeKey("value");
          jOut.writeObjectValue(result);
        
          jOut.writeEndObject();
        }
      } catch (Throwable exn) {
        if (log.isLoggable(Level.FINER)) {
          log.log(Level.FINER, exn.toString(), exn);
        }
        
        out.write("{status:\"exception\",\n message:\"" + exn.toString() + "\"}");
      }

      if (jsonCallback != null) {
        out.write(")");
      }
    }
  }
  
  protected void printException(RequestWeb req,
                                Throwable exn)
    throws IOException
  {
    // nam: 2014-05-08
    String jsonCallback = req.path("jsoncallback");

    if (jsonCallback != null) {
      req.write(jsonCallback);
      req.write("(");
    }

    if (exn instanceof ServiceException) {
      ServiceException sExn = (ServiceException) exn;
        
      req.write("{\"status\":\"" + sExn.getCode() + "\"");
      req.write(",\"message\":\"" + exn.toString() + "\"}");
    }
    else {
      req.write("{\"status\":\"exception\",\"message\":\"" + exn.toString() + "\"}");
    }
      
    // exn.printStackTrace();
    
    if (log.isLoggable(Level.FINER)) {
      log.log(Level.FINER, exn.toString(), exn);
    }

    if (jsonCallback != null) {
      req.write(")");
    }
  }

  private Object []readArgs(JsonReader jIn, ParameterAmp []paramTypes)
  {
    InJson.Event event;
    ArrayList<Object> args = null;

    int i = 0;

    while ((event = jIn.peek()) != InJson.Event.END_ARRAY) {
      if (args == null) {
        args = new ArrayList<>();
      }

      if (paramTypes != null && i < paramTypes.length) {
        Class<?> paramType = (Class<?>) paramTypes[i].rawClass();

        Object value;

        try {
          if (paramType.isInterface()) {
            // XXX: jamp/2112 - hack
            if ((event = jIn.next()) != InJson.Event.VALUE_STRING) {
              throw new IllegalStateException();
            }
            value = jIn.getString();
          }
          else {
            value = jIn.readObject(paramType);
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }

        args.add(value);
      }
      else {
        args.add(jIn.readObject(Object.class));
      }

      i++;
    }

    if (args == null) {
      return null;
    }
    else {
      Object []argArray = new Object[args.size()];

      args.toArray(argArray);

      return argArray;
    }
  }
}
