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
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ErrorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.json.io.JsonWriter;
import com.caucho.v5.json.ser.JsonSerializerFactory;
import com.caucho.v5.vfs.WriteStream;

/**
 * JampWriteStream writes HMTP packets to an OutputStream.
 */
public class OutJamp
{
  private static final Logger log
    = Logger.getLogger(OutJamp.class.getName());
  
  private final JsonSerializerFactory _jsonFactory;

  private JsonWriter _jOut;
  
  private Writer _writer;

  public OutJamp()
  {
    this(new JsonSerializerFactory());
  }

  public OutJamp(JsonSerializerFactory jsonFactory)
  {
    Objects.requireNonNull(jsonFactory);
    
    _jsonFactory = jsonFactory;
    
    _jOut = new JsonWriter(_jsonFactory);
  }
  
  public void init(Writer writer)
  {
    Objects.requireNonNull(writer);
    
    _writer = writer;
    _jOut.init(writer);
  }
  
  public void init(WriteStream os)
  {
    Objects.requireNonNull(os);
    
    _writer = os.getPrintWriter();
    _jOut.init(_writer);
  }

  //
  // message
  //

  /**
   * Sends a message to a given address
   */
  public void send(HeadersAmp headers,
                   String to, 
                   String methodName,
                   Object ...args)
    throws IOException
  {
    if (log.isLoggable(Level.FINEST)) {
      log.finest(this + " send " + methodName + Arrays.asList(args)
                + "\n  {to:" + to + "," + headers + "}");
    }
    
    try {
      JsonWriter jOut = _jOut;

      jOut.init();

      jOut.writeStartArray();
      jOut.write("send");

      writeHeaders(jOut, headers);

      jOut.write(to);
      jOut.write(methodName);

      writeArgs(jOut, args);

      jOut.writeEndArray();

      jOut.close();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }

  //
  // query
  //

  /**
   * query message to a given address
   */
  public void query(HeadersAmp headers,
                    String from,
                    long qid,
                    String to, 
                    String methodName,
                    Object ...args)
    throws IOException
  {
    if (log.isLoggable(Level.FINEST)) {
      log.finest(this + " query " + methodName + Arrays.asList(args)
                + "\n  {to:" + to + "," + headers + "}");
    }
 
    try {
      JsonWriter jOut = _jOut;
      jOut.init();

      jOut.writeStartArray();
      jOut.write("query");

      writeHeaders(jOut, headers);

      jOut.write(from);
      jOut.write(qid);

      jOut.write(to);
      jOut.write(methodName);

      writeArgs(jOut, args);

      jOut.writeEndArray();

      jOut.close();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }

  //
  // query
  //

  /**
   * Sends a message to a given address
   */
  public void reply(HeadersAmp headers,
                    String to,
                    long qid,
                    Object value)
    throws IOException
  {
    if (log.isLoggable(Level.FINEST)) {
      log.finest(this + " reply " + value
                + "\n  {to:" + to + "," + headers + "}");
    }

    try {
      JsonWriter jOut = _jOut;
      jOut.init();

      jOut.writeStartArray();
      jOut.write("reply");

      writeHeaders(jOut, headers);

      jOut.write(to);
      jOut.write(qid);

      jOut.write(value);

      jOut.writeEndArray();

      jOut.close();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }

  /**
   * Sends a message to a given address
   */
  public void queryError(HeadersAmp headers,
                         String to,
                         long qid,
                         Throwable exn)
    throws IOException
  {
    if (log.isLoggable(Level.FINEST)) {
      log.finest(this + " error " + exn
                + "\n  {to:" + to + "," + headers + "}");
      
    }
    
    if (log.isLoggable(Level.FINER)) {
      log.log(Level.FINER, exn.toString(), exn);
    }
    
    try {
      JsonWriter jOut = _jOut;
      jOut.init();

      jOut.writeStartArray();
      jOut.write("error");

      writeHeaders(jOut, headers);

      jOut.write(to);
      jOut.write(qid);

      ErrorAmp error = ErrorAmp.create(exn);

      jOut.writeStartObject();
      jOut.writeKey("code");
      jOut.writeString(error.getCode().toString());
      
      jOut.writeKey("message");
      jOut.writeString(exn.getMessage());
      
      jOut.writeKey("class");
      jOut.writeString(exn.getClass().getName());
      
      jOut.writeEndObject();

      jOut.writeEndArray();

      jOut.close();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }

  //
  // query
  //

  /**
   * stream message to a given address
   */
  public void stream(HeadersAmp headers,
                     String from,
                     long qid,
                     int credit,
                     String to, 
                     String methodName,
                     Object ...args)
    throws IOException
  {
    if (log.isLoggable(Level.FINEST)) {
      log.finest(this + " stream" + methodName + Arrays.asList(args)
                + "\n  {to:" + to + "," + headers + "}");
    }
 
    try {
      JsonWriter jOut = _jOut;
      jOut.init();

      jOut.writeStartArray();
      jOut.write("stream");

      writeHeaders(jOut, headers);

      jOut.write(from);
      jOut.write(qid);
      
      jOut.write(credit);

      jOut.write(to);
      jOut.write(methodName);

      writeArgs(jOut, args);

      jOut.writeEndArray();

      jOut.close();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }

  public void streamResult(HeadersAmp headers,
                           String addressFrom,
                           long qid,
                           List<Object> values)
  {
    if (log.isLoggable(Level.FINEST)) {
      log.finest(this + " stream-result " + values
                + "\n  {from:" + addressFrom + "," + qid + "," + headers + "}");
    }
    
    try {
      JsonWriter jOut = _jOut;
      jOut.init();

      jOut.writeStartArray();
      jOut.write("stream-result");

      writeHeaders(jOut, headers);

      jOut.write(addressFrom);
      jOut.write(qid);
      
      jOut.write(values);

      jOut.writeEndArray();

      jOut.close();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }

  public void streamComplete(HeadersAmp headers,
                             String addressFrom,
                             long qid)
  {
    if (log.isLoggable(Level.FINEST)) {
      log.finest(this + " stream-complete"
                + "\n  {from:" + addressFrom + "," + qid + "," + headers + "}");
    }
 
    try {
      JsonWriter jOut = _jOut;
      jOut.init();

      jOut.writeStartArray();
      jOut.write("stream-complete");

      writeHeaders(jOut, headers);

      jOut.write(addressFrom);
      jOut.write(qid);

      jOut.writeEndArray();

      jOut.close();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }

  public void streamError(HeadersAmp headers,
                             String addressFrom,
                             long qid,
                             Throwable exn)
  {
    if (log.isLoggable(Level.FINEST)) {
      log.finest(this + " stream-exn"
                + "\n  {from:" + addressFrom + "," + qid + "," + headers + "}");
    }
 
    try {
      JsonWriter jOut = _jOut;
      jOut.init();

      jOut.writeStartArray();
      jOut.write("stream-error");

      writeHeaders(jOut, headers);

      jOut.write(addressFrom);
      jOut.write(qid);

      jOut.writeEndArray();

      jOut.close();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }

  public void comma()
    throws IOException
  {
    _writer.write(",\n");
  }
        
  private void writeHeaders(JsonWriter jOut, HeadersAmp headers)
  {
    jOut.writeStartObject();
    
    for (Entry<String,Object> entry : headers) {
      jOut.writeKey(entry.getKey());
      jOut.writeString((String) entry.getValue());
    }
    
    jOut.writeEndObject();
  }
  
  private void writeArgs(JsonWriter jOut, Object []args)
    throws IOException
  {
    int argLen = args != null ? args.length : 0;
  
    for (int i = 0; i < argLen; i++) {
      jOut.write(args[i]);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
