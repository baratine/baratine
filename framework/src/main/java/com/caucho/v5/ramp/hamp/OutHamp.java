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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ErrorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.bartender.pod.PodRef;
import com.caucho.v5.h3.H3;
import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.h3.OutH3;

import io.baratine.service.ResultStream;

/**
 * OutHamp writes HAMP messages to an OutputStream.
 */
public class OutHamp
{
  private static final Logger log
    = Logger.getLogger(OutHamp.class.getName());
  
  private Level _level = Level.FINEST;
  
  private OutH3 _out;
  //private HessianDebugOutputStream _dOut;
  
  private int _methodCacheIndex;
  private HashMap<MethodKey,Integer> _methodCache = new HashMap<>(256);
  private MethodKey []_methodCacheRing = new MethodKey[256];
  
  private MethodKey _methodKey = new MethodKey();
  
  private int _toAddressCacheIndex;
  private HashMap<String,Integer> _toAddressCache = new HashMap<>(256);
  private String []_toAddressCacheRing = new String[256];
  
  private int _fromAddressCacheIndex;
  private HashMap<String,Integer> _fromAddressCache = new HashMap<>(256);
  private String []_fromAddressCacheRing = new String[256];

  private OutFactoryH3 _serializer;

  public OutHamp()
  {
    _serializer = H3.newOutFactory().get();
    
    //_out = new Hessian2Output();
    //_out.getSerializerFactory().setAllowNonSerializable(true);
    //_out.setUnshared(true);
    
    /*
    if (log.isLoggable(Level.FINEST)) {
      _dOut = new HessianDebugOutputStream(log, Level.FINEST);
    }
    */
  }

  protected void init(OutputStream os)
  {
    /*
    if (_dOut != null) {
      _dOut.initPacket(os);
      os = _dOut;
    }
    
    _out.initPacket(os);
    */
    _out = _serializer.out(os);
  }

  //
  // message
  //

  /**
   * Sends a message to a given address
   */
  public void send(OutputStream os, 
                   HeadersAmp headers,
                   String address, 
                   String methodName,
                   PodRef podCaller,
                   Object []args)
    throws IOException
  {
    init(os);

    OutH3 out = _out;

    if (out == null) {
      return;
    }
    
    if (log.isLoggable(Level.FINEST)) {
      log.finest("hamp-send-w " + methodName + Arrays.asList(args)
                + "\n  {to:" + address + "}");
    }
  
    try {
      out.writeLong(MessageTypeHamp.SEND.ordinal());
      writeHeaders(out, headers);
    
      writeMethod(out, address, methodName, podCaller);
      writeArgs(out, args);

      // XXX: out.flushBuffer();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }
  
  /**
   * Sends a message to a given address
   */
  public void query(OutputStream os,
                    HeadersAmp headers,
                    String from, 
                    long qId,
                    String address, 
                    String methodName,
                    PodRef podCaller,
                    Object []args)
    throws IOException
  {
    init(os);

    OutH3 out = _out;

    if (out == null) {
      return;
    }
    
    if (log.isLoggable(Level.FINEST)) {
      log.finest("hamp-query-w " + methodName + (args != null ? Arrays.asList(args) : "[]")
                + " {to:" + address + ", from:" + from + "}");
    }
    
    try {
      out.writeLong(MessageTypeHamp.QUERY.ordinal());
      writeHeaders(out, headers);

      writeFromAddress(out, from);
      out.writeLong(qId);
    
      writeMethod(out, address, methodName, podCaller);
      writeArgs(out, args);
    
      // XXX: out.flushBuffer();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }
  
  /**
   * Sends a message to a given address
   */
  public void queryResult(OutputStream os,
                          HeadersAmp headers,
                          String address,
                          long qId,
                          Object value)
    throws IOException
  {
    init(os);

    OutH3 out = _out;

    if (out == null) {
      return;
    }

    if (log.isLoggable(_level)) {
      log.log(_level, "hamp-query-result-w " + value + " (in " + this + ")"
                 + "\n  {id:" + qId + " to:" + address + ", " + headers + "," + os + "}");
    }

    try {
      out.writeLong(MessageTypeHamp.QUERY_RESULT.ordinal());
    
      writeHeaders(out, headers);
    
      writeToAddress(out, address);
      out.writeLong(qId);

      out.writeObject(value);

      // XXX: out.flushBuffer();
      // out.flush();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }
  
  /**
   * Sends a message to a given address
   */
  public void queryError(OutputStream os,
                         HeadersAmp headers,
                         String address,
                         long qId,
                         Throwable exn)
    throws IOException
  {
    init(os);

    OutH3 out = _out;

    if (out == null)
      return;

    if (log.isLoggable(_level)) {
      log.log(_level, "query-error " + exn
                 + " (in " + this + ")" + " {to:" + address + ", " + headers + "}");
      
      if (log.isLoggable(Level.FINEST)) {
        log.log(Level.FINEST, exn.toString(), exn);
      }
    }

    try {
      out.writeLong(MessageTypeHamp.QUERY_ERROR.ordinal());
      writeHeaders(out, headers);
      
      writeToAddress(out, address);
      out.writeLong(qId);

      ErrorAmp error = ErrorAmp.create(exn);

      out.writeString(error.getCode().toString());
      out.writeString(error.getMessage());
      out.writeObject(error.getDetail());

      // XXX: out.flushBuffer();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }
  
  /**
   * Sends a stream message to a given address
   */
  public void stream(OutputStream os,
                     HeadersAmp headers,
                     String from, 
                     long qId,
                     String address, 
                     String methodName,
                     PodRef podCaller,
                     ResultStream<?> result,
                     Object []args)
    throws IOException
  {
    init(os);

    OutH3 out = _out;

    if (out == null) {
      return;
    }
    
    if (log.isLoggable(Level.FINEST)) {
      log.finest("hamp-stream-w " + methodName + (args != null ? Arrays.asList(args) : "[]")
                + " {to:" + address + ", from:" + from + "}");
    }
    
    out.writeLong(MessageTypeHamp.STREAM.ordinal());
    writeHeaders(out, headers);

    writeFromAddress(out, from);
    out.writeLong(qId);
    
    writeMethod(out, address, methodName, podCaller);

    out.writeObject(result);

    writeArgs(out, args);
    
    //out.flushBuffer();
    out.flush();
  }
  
  /**
   * Sends a message to a given address
   */
  public void streamResult(OutputStream os,
                          HeadersAmp headers,
                          String address,
                          long qId,
                          int sequence,
                          List<Object> values,
                          Throwable exn,
                          boolean isComplete)
     throws IOException
  {
    init(os);

    OutH3 out = _out;

    if (out == null) {
      return;
    }
    
    if (log.isLoggable(_level)) {
      log.log(_level, "hamp-stream-result-w " + values + "," + isComplete + " (in " + this + ")"
                 + "\n  {id:" + qId + " to:" + address + ", " + headers + "," + os + "}");
    }
    
    try {
      out.writeLong(MessageTypeHamp.STREAM_RESULT.ordinal());
    
      writeHeaders(out, headers);
    
      writeToAddress(out, address);
      out.writeLong(qId);
      
      out.writeLong(sequence);

      out.writeObject(values);
      out.writeObject(exn);
      out.writeBoolean(isComplete);
      
      //out.flushBuffer();
      out.flush();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }

  public void streamCancel(OutputStream os, 
                           HeadersAmp headers, 
                           String address,
                           String addressFrom, 
                           long qId)
    throws IOException
  {
    init(os);

    OutH3 out = _out;

    if (out == null) {
      return;
    }

    if (log.isLoggable(_level)) {
      log.log(_level, "hamp-stream-cancel-w " + addressFrom + "," + qId + " (in " + this + ")"
                 + "\n  {id:" + qId + " to:" + address + ", " + headers + "," + os + "}");
    }
    
    try {
      out.writeLong(MessageTypeHamp.STREAM_CANCEL.ordinal());
    
      writeHeaders(out, headers);
    
      writeToAddress(out, address);
      out.writeString(addressFrom);
      out.writeLong(qId);

      //out.flushBuffer();
      out.flush();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    }
  }
  
  /**
   * Sends a message to a given address
   */
  public void error(OutputStream os,
                    String to,
                    HeadersAmp headers,
                    ErrorAmp error)
    throws IOException
  {
    init(os);

    OutH3 out = _out;

    if (out == null)
      return;

    if (log.isLoggable(Level.FINEST)) {
      log.finest("error" + error
                 + " (in " + this + ")" + " {to:" + to + ", " + headers + "}");
    }

    out.writeLong(MessageTypeHamp.ERROR.ordinal());
    writeHeaders(out, headers);
    
    writeFromAddress(out, to);

    out.writeString(error.getCode().toString());
    out.writeString(error.getMessage());
    out.writeObject(error.getDetail());

    //out.flushBuffer();
    out.flush();
  }
  
  private void writeMethod(OutH3 out, 
                           String address,
                           String methodName,
                           PodRef podCaller)
      throws IOException
  {
    String podCallerId = null;
    
    if (podCaller != null) {
      podCallerId = podCaller.getPodId();
    }
    
    if (address == null) {
      out.writeString(null);
      out.writeString(methodName);
      out.writeString(podCallerId);
      return;
    }
    
    // XXX: add pod caller
    _methodKey.init(address, methodName);
    
    Integer value = _methodCache.get(_methodKey);
    
    if (value != null) {
      out.writeLong(value);
    }
    else {
      out.writeString(address);
      out.writeString(methodName);
      out.writeString(podCallerId);
      
      int index = _methodCacheIndex;
      
      _methodCacheIndex = (index + 1) % _methodCacheRing.length;
      
      if (_methodCacheRing[index] != null) {
        _methodCache.remove(_methodCacheRing[index]);
      }
      
      _methodCacheRing[index] = new MethodKey(address, methodName);
      _methodCache.put(_methodCacheRing[index], index);
    }
  }
  
  private void writeFromAddress(OutH3 out, String address)
      throws IOException
  {
    if (address == null) {
      out.writeString(null);
      return;
    }
    
    Integer value = _fromAddressCache.get(address);
    
    if (value != null) {
      out.writeLong(value);
    }
    else {
      out.writeString(address);

      int index = _fromAddressCacheIndex;
      
      _fromAddressCacheIndex = (index + 1) % _fromAddressCacheRing.length;
      
      if (_fromAddressCacheRing[index] != null) {
        _fromAddressCache.remove(_fromAddressCacheRing[index]);
      }
      
      _fromAddressCacheRing[index] = address;
      _fromAddressCache.put(address, index);
    }
  }
  
  private void writeToAddress(OutH3 out, String address)
    throws IOException
  {
    if (address == null) {
      out.writeString(null);
      return;
    }
    
    Integer value = _toAddressCache.get(address);
    
    if (value != null) {
      out.writeLong(value);
    }
    else {
      out.writeString(address);
      
      int index = _toAddressCacheIndex;
      
      _toAddressCacheIndex = (index + 1) % _toAddressCacheRing.length;
      
      if (_toAddressCacheRing[index] != null) {
        _toAddressCache.remove(_toAddressCacheRing[index]);
      }
      
      _toAddressCacheRing[index] = address;
      _toAddressCache.put(address, index);
    }
  }
  
  private void writeHeaders(OutH3 out, HeadersAmp headers)
    throws IOException
  {
    int size = headers.getSize();
    
    out.writeLong(size);
    
    writeHeadersRec(out, headers.iterator(), size);
    /*
    for (Map.Entry<String,Object> entry : headers) {
      out.writeString(entry.getKey());
      out.writeObject(entry.getValue());
    }
    */
  }
  
  private void writeHeadersRec(OutH3 out, 
                               Iterator<Map.Entry<String,Object>> iter,
                               int size)
    throws IOException
  {
    if (! iter.hasNext()) {
      if (size != 0) {
        log.warning("Invalid headers: " + size + " " + iter);
      }
      return;
    }
    else if (size <= 0) {
      log.warning("Invalid headers: " + size + " " + iter);
      return;
    }
    
    Map.Entry<String,Object> entry = iter.next();
    
    writeHeadersRec(out, iter, size - 1);
    
    out.writeString(entry.getKey());
    out.writeObject(entry.getValue());
  }
  
  private void writeArgs(OutH3 out, Object []args)
    throws IOException
  {
    
    if (args != null) {
      out.writeLong(args.length);
      
      for (Object arg : args) {
        out.writeObject(arg);
      }
    }
    else {
      out.writeLong(0);
    }
  }

  public void flushBuffer()
    throws IOException
  {
    OutH3 out = _out;

    if (out != null) {
      //out.flushBuffer();
      out.flush();
    }
  }

  public void flush()
    throws IOException
  {
    OutH3 out = _out;

    if (out != null) {
      out.flush();
    }
  }

  public void close()
  {
    if (log.isLoggable(Level.FINER))
      log.finer(this + " close");

    try {
      OutH3 out = _out;
      _out = null;

      if (out != null) {
        out.close();
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  static class MethodKey {
    private String _address;
    private String _method;
    
    MethodKey()
    {
    }
    
    MethodKey(String address, String method)
    {
      _address = address;
      _method = method;
    }
    
    void init(String address, String method)
    {
      _address = address;
      _method = method;
    }
    
    @Override
    public int hashCode()
    {
      return _address.hashCode() * 65521 + _method.hashCode();
    }
    
    @Override
    public boolean equals(Object v)
    {
      if (! (v instanceof MethodKey)) {
        return false;
      }
      
      MethodKey method = (MethodKey) v;
      
      return (_address.equals(method._address)
              && _method.equals(method._method));
    }
  }
}
