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

import io.baratine.service.ServiceException;
import io.baratine.service.ServiceExceptionIllegalArgument;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ErrorAmp;
import com.caucho.v5.amp.ErrorCodesAmp;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.manager.OutboxFactoryAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.message.SendMessage_N;
import com.caucho.v5.amp.message.StreamCallMessage;
import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.remote.GatewayReply;
import com.caucho.v5.amp.remote.GatewayResultStream;
import com.caucho.v5.amp.remote.QueryGatewayReadMessage_N;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;
import com.caucho.v5.json.io.InJson;
import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.json.ser.JsonSerializerFactory;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.ReadStream;

/**
 * HmtpReader stream handles client packets received from the server.
 */
public class InJamp
{
  private static final L10N L = new L10N(InJamp.class);
  private static final Logger log
    = Logger.getLogger(InJamp.class.getName());
  
  private final Object []NULL_ARGS = new Object[0];

  private final ChannelAmp _channelIn;
  private final JsonSerializerFactory _jsonFactory;
  
  private String _id;
  
  private ReadStream _is;
  // private OutboxAmp _outbox;
  
  private long _queueTimeout = 1000;
  private Supplier<OutboxAmp> _outboxFactory;

  public InJamp(ChannelAmp channel)
  {
    this(channel, new JsonSerializerFactory());
  }
  
  public InJamp(ChannelAmp channel, JsonSerializerFactory factory)
  {
    this(channel, factory, null);
  }
  
  public InJamp(ChannelAmp channel, 
                JsonSerializerFactory factory,
                OutboxAmp outbox)
  {
    Objects.requireNonNull(channel);
    Objects.requireNonNull(factory);
    
    _jsonFactory = factory;
    _channelIn = channel;
    
    // AmpManager ampManager = (AmpManager) ServiceManager.getCurrent();
    _outboxFactory = OutboxFactoryAmp.createFactory();
    
    /*
    if (outbox == null) {
      outbox = channel.createOutbox();
    }
    
    _outbox = outbox;
    */
  }

  /*
  public OutboxAmp getOutbox()
  {
    return _outbox;
  }
  */
  
  /*
  private InboxAmp getInboxCaller()
  {
    return _outbox.getInbox();
  }
  */
  
  private ServiceManagerAmp getManager()
  {
    return _channelIn.getManager();
  }
  
  private InboxAmp inboxCaller()
  {
    return _channelIn.getInbox();
  }
  
  public void setId(String id)
  {
    _id = id;
  }

  public void init(ReadStream is)
  {
    _is = is;
  }

  public JsonReader startSequence(Reader is)
    throws IOException
  {
    JsonReader jIn = new JsonReader(is, _jsonFactory);
    
    InJson.Event event;
    
    if ((event = next(jIn)) == InJson.Event.START_ARRAY) {
    }
    else if ((event = next(jIn)) == InJson.Event.END_ARRAY) {
      return null;
    }
    else if (event == null) {
      throw error(L.l("Unexpected empty JSON event while parsing a JAMP message.{0}",
                      usageSyntax()));
    }
    else {
      throw error(L.l("Unexpected JSON {0} ({1}) at start of JAMP message. Expected JSON '['.{2}",
                      eventName(event),
                      event,
                      usageSyntax()));
                                
    }
    
    return jIn;
  }

  /**
   * Reads the next HMTP packet from the stream, returning false on
   * end of file.
   */
  public MessageType readMessage(Reader is)
    throws IOException
  {
    JsonReader jIn = new JsonReader(is, _jsonFactory);
    
    return readMessage(jIn);
  }

  /**
   * Reads the next HMTP packet from the stream, returning false on
   * end of file.
   */
  public int readMessages(Reader is)
    throws IOException
  {
    //InboxAmp oldInbox = null;
    
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
      //OutboxThreadLocal.setCurrent(_outbox);
      return readMessages(is, outbox);
    } finally {
      //OutboxThreadLocal.setCurrent(null);
    }
  }

  /**
   * Reads the next HMTP packet from the stream, returning false on
   * end of file.
   */
  public int readMessages(Reader is, OutboxAmp outbox)
    throws IOException
  {
    Objects.requireNonNull(outbox);
    
    JsonReader jIn = new JsonReader(is, _jsonFactory);
      
    return readMessages(jIn, outbox);
  }
  
  public int readMessages(JsonReader jIn, OutboxAmp outbox)
    throws IOException
  {
    int queryCount = 0;
    
    InJson.Event event;

    if ((event = next(jIn)) != InJson.Event.START_ARRAY) {
    }
    else if (event == null) {
      throw error(L.l("Unexpected JSON {0} while parsing a JAMP message stream.{1}",
                      event,
                      usageSyntax()));
    }
    
    MessageType type;
    
    while ((type = readMessage(jIn, outbox)) != null) {
      if (type == MessageType.QUERY) {
        queryCount++;
      }
    }
    
    return queryCount;
  }
  
  public MessageType readMessage(JsonReader jIn)
    throws IOException
  {
    try (OutboxAmp outbox = _outboxFactory.get()) {
      //OutboxThreadLocal.setCurrent(_outbox);
      
      return readMessage(jIn, outbox);
    } finally {
      //OutboxThreadLocal.setCurrent(null);
    }
  }
    
  public MessageType readMessage(JsonReader jIn, OutboxAmp outbox)
      throws IOException
  {
    MessageType value = readMessageImpl(jIn, outbox);
    
    //_outbox.flush();
    
    return value;
  }
  
  private MessageType readMessageImpl(JsonReader jIn, OutboxAmp outbox)
      throws IOException
  {
    InJson.Event event;

    if ((event = next(jIn)) == InJson.Event.START_ARRAY) {
    }
    else if (event == InJson.Event.END_ARRAY) {
      return null;
    }
    else if (event == null) {
      throw error(L.l("Unexpected empty JSON event while parsing a JAMP message.{0}",
                      usageSyntax()));
    }
    else {
      throw error(L.l("Unexpected JSON {0} ({1}) at start of JAMP message. Expected JSON '['.{2}",
                      eventName(event),
                      event,
                      usageSyntax()));
                                
    }
    
    if ((event = next(jIn)) != InJson.Event.VALUE_STRING) {
      throw error(L.l("Unexpected JSON {0} ({1}) at JAMP message type. Expected JSON string for the JAMP message type.{2}",
                      eventName(event),
                      event,
                      usageSyntax()));
    }
    
    String code = jIn.getString();

    HeadersAmp headers = parseHeaders(jIn);

    switch (code) {
    case "send":
      return parseSend(jIn, headers);
      
    case "query":
      return parseQuery(jIn, outbox, headers);
      
    case "reply":
      return parseReply(jIn, headers);
      
    case "error":
      return parseError(jIn, headers);
      
    case "stream":
      return parseStream(jIn, outbox, headers);
      
    case "stream-cancel":
      return parseStreamCancel(jIn, headers);
      
    default:
      throw error(L.l("Unexpected JAMP message type \"{0}\"."
                      + " JAMP message types are \"send\", \"query\", \"reply\", \"error\""
                      + "\n  headers: {1}{2}",
                      code, headers, usageSyntax()));
    }
  }
  
  private MessageType parseSend(JsonReader jIn,
                                HeadersAmp headers)
    throws IOException
  {
    InJson.Event event;

    if ((event = next(jIn)) != InJson.Event.VALUE_STRING) {
      throw error(L.l("Unexpected JSON {0} ({1}) when parsing a JSON string for a JAMP address.\n  headers: {2}\n  syntax: {3}",
                      eventName(event),
                      event,
                      headers,
                      usageSend()));
    }
    
    String address = jIn.getString();

    if ((event = jIn.next()) != InJson.Event.VALUE_STRING) {
      throw error(L.l("Unexpected JSON {0} ({1}) when parsing a JSON string for a JAMP method.\n  headers: {2}\n  syntax: {3}",
                      eventName(event),
                      event,
                      headers,
                      usageSend()));
    }
    
    String methodName = jIn.getString();

    MethodRefAmp method = _channelIn.lookupMethod(address, methodName);
    
    Type []paramTypes = method.getParameterTypes();
    boolean isVarArgs = method.isVarArgs();
    
    method = method.getActive();
    
    ServiceRefAmp serviceRef = method.getService();
    
    String serviceRefAddress = serviceRef.address();
    
    OutboxAmp outbox = OutboxAmp.current();

    Object []args = readArgs(jIn, serviceRefAddress, methodName, paramTypes, isVarArgs);
    SendMessage_N msg = new SendMessage_N(outbox, headers, serviceRef, method.method(), args);
    
    if (log.isLoggable(Level.FINER)) {
      log.finer("jamp-send " + methodName + Arrays.asList(args)
                + " {to=" + address + ", " + headers + "}"); 
    }

    try {
      msg.offer(_queueTimeout);
    } catch (RuntimeException e) {
      // makai/2523
      throw e;
    } catch (Exception e) {
      log.fine(e.toString());
      
      if (log.isLoggable(Level.FINEST)) {
        log.log(Level.FINEST, e.toString(), e);
      }
    }
    
    return MessageType.SEND;
  }
  
  private MessageType parseQuery(JsonReader jIn,
                                 OutboxAmp outbox,
                                 HeadersAmp headers)
    throws IOException
  {
    InJson.Event event;

    if ((event = jIn.next()) != InJson.Event.VALUE_STRING) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting a JSON string for JAMP from-address.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageQuery()));
    }
    
    String from = jIn.getString();

    GatewayReply fromService = _channelIn.createGatewayReply(from);

    if ((event = jIn.next()) != InJson.Event.VALUE_LONG) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting a JSON integer for JAMP query id.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageQuery()));
    }
    
    long qid = jIn.getLong();
    
    try {
      return parseQueryMethod(jIn, outbox, headers, from, fromService, qid);
    } catch (ServiceException e) {
      fromService.queryFail(headers, qid, e);
      
      return MessageType.QUERY;
    } catch (Exception e) {
      fromService.queryFail(headers, qid, e);
      
      throw e;
    }
  }
    
  MessageType parseQueryMethod(JsonReader jIn,
                               OutboxAmp outbox,
                               HeadersAmp headers,
                               String from,
                               GatewayReply fromService,
                               long qid)
    throws IOException
  {
    InJson.Event event;
    
    if ((event = jIn.next()) != InJson.Event.VALUE_STRING) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting a JSON string for JAMP query target address.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageQuery()));
    }
    
    String address = jIn.getString();

    if ((event = jIn.next()) != InJson.Event.VALUE_STRING) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting a JSON string for JAMP query method name.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageQuery()));
    }
    
    String methodName = jIn.getString();
  
    ServiceRefAmp serviceRef;
    MethodRefAmp method;

    Type []paramTypes;
    boolean isVarArgs;
    
    try {
      method = _channelIn.lookupMethod(address, methodName);
      
      paramTypes = method.getParameterTypes();
      isVarArgs = method.isVarArgs();
      
      method = method.getActive();

      serviceRef = method.getService();
    } catch (Exception e) {
      //e.printStackTrace();
      
      readArgs(jIn, address, methodName, new Class[0], false);
      
      throw e;
    }

    String serviceRefAddress = serviceRef.address();
    
    Object []args = readArgs(jIn, serviceRefAddress, methodName, paramTypes, isVarArgs);

    // RampQueryRef queryRef = fromActor.getQueryRef(qid);
    long timeout = 120000L;
    
    if (log.isLoggable(Level.FINER)) {
      log.finer("jamp-query " + methodName + Arrays.asList(args)
                + " {to=" + address + ", " + headers + "," + qid + "," + from + "}"); 
    }
    
    QueryGatewayReadMessage_N msg;
    
    /*
    msg = _registryIn.createQuery(_outbox, getInboxCaller(),
                          headers, fromService, qid, serviceRef,
                          method.getMethod(), timeout, args);
                          */

    msg = new QueryGatewayReadMessage_N(outbox,
                                        inboxCaller(),
                                      headers, 
                                      fromService, qid, 
                                      serviceRef,
                                      method.method(), 
                                      timeout, args);
    
    try {
      msg.offer(_queueTimeout);
    } catch (Throwable e) {
      msg.fail(e);
    }

    // method.query(headers, queryRef, args);
    // method.query(headers, args);
    
    return MessageType.QUERY;
  }
  
  private MessageType parseReply(JsonReader jIn,
                                 HeadersAmp headers)
    throws IOException
  {
    InJson.Event event;

    if ((event = jIn.next()) != InJson.Event.VALUE_STRING) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting JSON string for reply address.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageReply()));
    }
    
    String address = jIn.getString();

    if ((event = jIn.next()) != InJson.Event.VALUE_LONG) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting JSON integer for reply id.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageReply()));
    }
    
    long qid = jIn.getLong();
    
    ServiceRefAmp service = _channelIn.service(address);
    
    QueryRefAmp queryRef = service.removeQueryRef(qid);

    Object result = jIn.readObject(Object.class);
    
    if (log.isLoggable(Level.FINER)) {
      log.finer("jamp-reply " + result
                + " {to=" + address + ", " + headers + "," + qid + "}"); 
    }

    if (queryRef != null) {
      queryRef.complete(headers, result);
    }
    else {
      log.warning("jamp-reply with missing query-ref " 
                  + " {to=" + address + ", " + headers + "," + qid + "}"); 
    }
    
    return MessageType.QUERY_REPLY;
  }
  
  private MessageType parseError(JsonReader jIn,
                                 HeadersAmp headers)
    throws IOException
  {
    InJson.Event event;

    if ((event = jIn.next()) != InJson.Event.VALUE_STRING) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting JSON string for error address.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageError()));
    }
    
    String address = jIn.getString();

    if ((event = jIn.next()) != InJson.Event.VALUE_LONG) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting JSON integer for error id.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageError()));
    }
    
    long qid = jIn.getLong();
    
    String code = null;
    String msg = null;
    HashMap<String,Object> detail = new HashMap<>();

    if ((event = jIn.next()) != InJson.Event.START_OBJECT) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting object for error detail.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageError()));
    }
    
    while ((event = jIn.next()) == InJson.Event.KEY_NAME) {
      String key = jIn.getString();
      
      switch (key) {
      case "code":
        code = readString(jIn);
        break;
      case "message":
        msg = readString(jIn);
        break;
      default:
        detail.put(key, jIn.readObject());
      }
    }
        
    if (event != InJson.Event.END_OBJECT) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting object end for error detail.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageError()));
    }
    
    ServiceRefAmp serviceRef = _channelIn.service(address);
    
    ErrorAmp error = new ErrorAmp(ErrorCodesAmp.UNKNOWN, msg);

    QueryRefAmp queryRef = serviceRef.removeQueryRef(qid);
    
    queryRef.fail(headers, error.toException());
    
    return MessageType.QUERY_ERROR;
  }
  
  private MessageType parseStream(JsonReader jIn,
                                  OutboxAmp outbox,
                                 HeadersAmp headers)
    throws IOException
  {
    InJson.Event event;

    if ((event = jIn.next()) != InJson.Event.VALUE_STRING) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting a JSON string for JAMP from-address.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageQuery()));
    }
    
    String from = jIn.getString();

    if ((event = jIn.next()) != InJson.Event.VALUE_LONG) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting a JSON integer for JAMP query id.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageQuery()));
    }
    
    long qid = jIn.getLong();
    
    GatewayResultStream gatewayResult = _channelIn.createGatewayResultStream(from, qid);

    try {
      return parseStreamMethod(jIn, outbox, headers, from, gatewayResult, qid);
    } catch (ServiceException e) {
      gatewayResult.fail(e);
      
      return MessageType.QUERY;
    } catch (Exception e) {
      gatewayResult.fail(e);
      
      throw e;
    }
  }

  /**
   * <code><pre>
   * [stream ...]
   * </pre></code>
   */
  MessageType parseStreamMethod(JsonReader jIn,
                                OutboxAmp outbox,
                                HeadersAmp headers,
                                String from,
                                GatewayResultStream gatewayResult,
                                long qid)
    throws IOException
  {
    InJson.Event event;

    if ((event = jIn.next()) != InJson.Event.VALUE_LONG) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting a JSON long for JAMP stream credit.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageQuery()));
    }

    int credit = jIn.getInt();

    if ((event = jIn.next()) != InJson.Event.VALUE_STRING) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting a JSON string for JAMP query target address.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageQuery()));
    }

    String address = jIn.getString();

    if ((event = jIn.next()) != InJson.Event.VALUE_STRING) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting a JSON string for JAMP query method name.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageQuery()));
    }

    String methodName = jIn.getString();

    ServiceRefAmp serviceRef;
    MethodRefAmp method;

    Type []paramTypes;
    boolean isVarArgs;

    try {
      method = _channelIn.lookupMethod(address, methodName);

      paramTypes = method.getParameterTypes();
      isVarArgs = method.isVarArgs();

      method = method.getActive();

      serviceRef = method.getService();
    } catch (Exception e) {
      e.printStackTrace();

      readArgs(jIn, address, methodName, new Class[0], false);

      throw e;
    }

    String serviceRefAddress = serviceRef.address();

    Object []args = readArgs(jIn, serviceRefAddress, methodName, paramTypes, isVarArgs);

    // RampQueryRef queryRef = fromActor.getQueryRef(qid);
    long timeout = 120000L;

    if (log.isLoggable(Level.FINER)) {
      log.finer("jamp-query " + methodName + Arrays.asList(args)
                + " {to=" + address + ", " + headers + "," + qid + "," + from + "}"); 
    }

    StreamCallMessage msg;

    /*
  msg = _registryIn.createQuery(_outbox, getInboxCaller(),
                        headers, fromService, qid, serviceRef,
                        method.getMethod(), timeout, args);
     */
    
    msg = new StreamCallMessage(outbox,
                                inboxCaller(),
                                headers, 
                                serviceRef,
                                method.method(), 
                                gatewayResult,
                                timeout, args);

    try {
      msg.offer(_queueTimeout);
    } catch (Throwable e) {
      msg.fail(e);
    }

    // method.query(headers, queryRef, args);
    // method.query(headers, args);

    return MessageType.QUERY;
  }
  
  private MessageType parseStreamCancel(JsonReader jIn,
                                        HeadersAmp headers)
    throws IOException
  {
    InJson.Event event;

    if ((event = jIn.next()) != InJson.Event.VALUE_STRING) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting a JSON string for JAMP from-address.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageQuery()));
    }
    
    String from = jIn.getString();

    if ((event = jIn.next()) != InJson.Event.VALUE_LONG) {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecting a JSON integer for JAMP query id.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageQuery()));
    }
    
    long qid = jIn.getLong();
    
    GatewayResultStream gatewayResult = _channelIn.removeGatewayResultStream(from, qid);

    if (gatewayResult != null) {
      gatewayResult.cancel();
    }
    
    return MessageType.NONE;
    /*

    try {
      return MessageType.NONE;
    } catch (ServiceException e) {
      gatewayResult.fail(e);
      
      return MessageType.QUERY;
    } catch (Exception e) {
      gatewayResult.fail(e);
      
      throw e;
    }
    */
  }
  
  private String readString(JsonReader jIn)
    throws IOException
  {
    InJson.Event event;
    
    if ((event = jIn.next()) == InJson.Event.VALUE_STRING) {
      return jIn.getString();
    }
    else if (event == InJson.Event.VALUE_NULL) {
      return null;
    }
    else {
      throw error(L.l("Unexpected JSON {0} ({1}) when expecing JSON string for error message.\n  syntax: {2}",
                      eventName(event),
                      event,
                      usageError()));
    }
  }
  
  private Object []readArgs(JsonReader jIn, 
                            String address,
                            String method,
                            Type []paramTypes,
                            boolean isVarArgs)
    throws IOException
  {
    ArrayList<Object> args = null;
  
    InJson.Event event;
    int i = 0;

    while ((event = jIn.peek()) != InJson.Event.END_ARRAY) {
      
      if (event == null) {
        throw new ProtocolExceptionJamp(L.l("JAMP argument parsing failure at end of file for service '{0}' and method '{1}'",
                                            address, method));
      }

      if (args == null) {
        args = new ArrayList<>();
      }
      
      Type type = getType(i, paramTypes, isVarArgs);

      if (isVarArgs && paramTypes.length - 1 <= i) {
        args.add(readVarArgs(jIn, address, method, type));
        break;
      }
      
      if (type != null) {
        Class<?> cl = null;
        
        if (type instanceof Class<?>) {
          cl = (Class<?>) type;
        }
        
        Object value;
        
        try {
          if (cl != null && cl.isInterface()
              && jIn.peek() == InJson.Event.VALUE_STRING) {
            // XXX: baratine/2111 - for @Service, need true marshal

            String refAddress = jIn.readString();
            
            if (address.startsWith("session:")) {
              ServiceRefAmp fromService = _channelIn.createGatewayRef(refAddress);
            
              if (fromService == null) {
                value = null;
              }
              else if (cl.isAssignableFrom(fromService.getClass())) {
                value = fromService;
              }
              else {
                value = fromService.as(cl);
              }
            }
            else {
              throw new ServiceExceptionIllegalArgument(L.l("ServiceRef arg is only allowed for session:, in service '{0}' and method '{1}'",
                                                            address, method));
            }
            /*
            else {
              log.warning(L.l("ServiceRef arg is only allowed for session: for service {0} and method {1}",
                              address, method));
              
              value = null;
            }
            */
          }
          else {
            value = jIn.readObject(type);
          }
        } catch (Exception e) {
          throw new ProtocolExceptionJamp(L.l("JAMP argument parsing failure for service '{0}' and method '{1}'\n  {2}",
                                              address, method, e.toString()), e);
        }

        args.add(value);
      }
      else {
        args.add(jIn.readObject(Object.class));
      }
      
      i++;
    }
    
    jIn.next(); // consume ']'
    
    if (args == null) {
      return NULL_ARGS;
    }
    else {
      Object []argArray = new Object[args.size()];
      
      args.toArray(argArray);
      
      return argArray;
    }
  }
  
  private Object readVarArgs(JsonReader jIn,
                             String address,
                             String method,
                             Type type)
    throws IOException
  {
    ArrayList<Object> varArgs = new ArrayList<>();
  
    InJson.Event event;

    while ((event = jIn.peek()) != InJson.Event.END_ARRAY) {
      Object value;
        
      try {
        value = jIn.readObject(type);
      } catch (Exception e) {
        throw new ProtocolExceptionJamp(L.l("JAMP argument parsing failure for service '{0}' and method '{1}'\n  {2}",
                                            address, method, e.toString()), e);
      }

      varArgs.add(value);
    }
    
    Class <?> cl = (Class<?>) type;

    Object []args = (Object []) Array.newInstance(cl, varArgs.size());
    varArgs.toArray(args);
    
    return args;
  }

  private Type getType(int i, Type []paramTypes, boolean isVarArgs)
  {
    if (paramTypes == null) {
      return null;
    }
    else if (isVarArgs) {
      if (i < paramTypes.length - 1) {
        return paramTypes[i];
      }
      else {
        Type tailType = paramTypes[paramTypes.length - 1];
        
        if (tailType instanceof Class<?>) {
          Class<?> tailClass = (Class<?>) tailType;
          
          return (tailClass).getComponentType();
        }
        else {
          return tailType;
        }
      }
    }
    else {
      if (i < paramTypes.length) {
        return paramTypes[i];
      }
      else {
        return null;
      }
    }
  }
  
  private HeadersAmp parseHeaders(JsonReader jIn)
    throws IOException
  {
    InJson.Event event;
    
    HeadersAmp headers = HeadersNull.NULL;
    
    if ((event = jIn.next()) == InJson.Event.START_OBJECT) {
    }
    else if (event == InJson.Event.VALUE_NULL) {
      return headers;
    }
    else {
      throw error(L.l("Unexpected JSON {0} ({1}) while parsing JAMP headers. Expected a JSON object.",
                      eventName(event),
                      event));
    }
    
    while ((event = jIn.next()) == InJson.Event.KEY_NAME) {
      String key = jIn.getString();
      Object value = jIn.readObject(Object.class);
      
      headers = headers.add(key, value);
    }
    
    if (event == InJson.Event.END_OBJECT) {
      return headers;
    }
    else {
      throw error(L.l("Unexpected JSON {0} ({1}) while parsing JAMP headers. Expected a JSON object key.",
                      eventName(event),
                      event));
                                
    }
  }
  
  private String eventName(InJson.Event event)
  {
    if (event == null) {
      return "end of file";
    }
    
    switch (event) {
    case VALUE_NULL:
      return "'null'";
      
    case VALUE_FALSE:
      return "'false'";
      
    case VALUE_TRUE:
      return "'true'";
      
    case VALUE_STRING:
      return "string";
      
    case VALUE_LONG:
      return "number";
      
    case START_ARRAY:
      return "'['";
      
    case END_ARRAY:
      return "']'";
      
    case START_OBJECT:
      return "'{'";
      
    case END_OBJECT:
      return "'}'";
      
    case KEY_NAME:
      return "object key";
      
    default:
      return String.valueOf(event);
    }
  }
  
  private String usageSyntax()
  {
    return ("\n  syntax:"
           + "\n  " + usageSend()
           + "\n  " + usageQuery()
           + "\n  " + usageReply()
           + "\n  " + usageError());
  }
  
  private String usageSend()
  {
    return "[\"send\", {headers}, \"address\", \"method\", arg1, ...]";
  }
  
  private String usageQuery()
  {
    return "[\"query\", {headers}, \"from_address\", query_id, \"address\", \"method\", arg1, ...]";
  }
  
  private String usageReply()
  {
    return "[\"reply\", {headers}, \"from_address\", query_id, value]";
  }
  
  private String usageError()
  {
    return "[\"error\", {headers}, \"from_address\", query_id, {detail}]";
    
  }
  
  private InJson.Event next(JsonReader jIn)
  {
    return jIn.next();
  }
  
  private IOException error(String msg)
  {
    return new ProtocolExceptionJamp(msg);
  }

  @Override
  public String toString()
  {
    if (_id != null)
      return getClass().getSimpleName() + "[" + _id + "]";
    else
      return getClass().getSimpleName() + "[" + _is + "]";
  }
  
  public enum MessageType {
    NONE,
    SEND,
    QUERY,
    QUERY_REPLY,
    QUERY_ERROR;
  }
}
