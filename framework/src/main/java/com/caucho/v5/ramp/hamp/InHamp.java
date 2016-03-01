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
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpException;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.MethodRefError;
import com.caucho.v5.amp.actor.ServiceRefLazyInvalid;
import com.caucho.v5.amp.actor.ServiceRefNull;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.message.SendMessage_N;
import com.caucho.v5.amp.message.StreamCallMessage;
import com.caucho.v5.amp.message.StreamResultMessage;
import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.remote.GatewayReply;
import com.caucho.v5.amp.remote.GatewayReplyBase;
import com.caucho.v5.amp.remote.QueryGatewayReadMessage_N;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.h3.H3;
import com.caucho.v5.h3.InH3;
import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.http.pod.PodContainer;
import com.caucho.v5.ramp.jamp.InAmpWebSocket;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;

import io.baratine.service.ResultStream;
import io.baratine.service.ServiceException;

/**
 * HmtpReader stream handles client packets received from the server.
 */
public class InHamp implements InAmpWebSocket
{
  private static final L10N L = new L10N(InHamp.class);
  private static final Logger log
    = Logger.getLogger(InHamp.class.getName());

  private final ChannelAmp _channelIn;
  
  private String _id;
  
  private InputStream _is;
  //private Hessian2StreamingInput _in;
  private InH3 _hIn;
  
  private int _methodCacheIndex;
  private MethodRefHamp []_methodCacheRing = new MethodRefHamp[256];
  
  private int _toAddressCacheIndex;
  private ServiceRefAmp []_toAddressCacheRing = new ServiceRefAmp[256];
  
  private int _fromAddressCacheIndex;
  private GatewayReply []_fromAddressCacheRing = new GatewayReply[256];

  private ServiceManagerAmp _ampManager;
  private Supplier<OutboxAmp> _outboxFactory;
  
  private final Level _logLevel = Level.FINEST;
  // private final Level _logLevel = Level.FINER;
  
  private long _queueTimeout = 1000L;
  
  private int _dId;
  private OutFactoryH3 _serializer;
  private PodContainer _podContainer;
  private static AtomicInteger _idGen = new AtomicInteger();

  public InHamp(ServiceManagerAmp rampManager,
                ChannelAmp channel)
  {
    _ampManager = rampManager;
    _outboxFactory = rampManager.getOutboxFactory();
    
    if (_ampManager.isClosed()) {
      throw new IllegalStateException(String.valueOf(_ampManager));
    }
    
    _channelIn = channel;
    
    _serializer = H3.newOutFactory().get();
    //_hIn = new Hessian2Input();
    
    //_hIn.setSerializerFactory(_factory);
    
    if (channel == null) {
      throw new IllegalStateException("HampReader requires a valid channel for callbacks");
    }
    
    //_outbox = _channelIn.createOutbox();
    //_outbox.setInbox(rampManager.getSystemInbox());
    //_outbox.setMessage(rampManager.getSystemMessage());
    
    //_outbox = new OutboxAmpContextImpl();
    //_outbox.setInbox(rampManager.getSystemInbox());
    //_outbox.setMessage(rampManager.getSystemMessage());
    
    _podContainer = PodContainer.getCurrent();
    
    _dId = _idGen.incrementAndGet();
  }
  
  /*
  private ClassLoader loadClassLoaderJar(String pathName)
  {
    if (pathName == null
        || ! pathName.startsWith("bfs:///system/lambda")
        || ! pathName.endsWith(".jar")) {
      return null;
    }
    
    Path path = Vfs.lookup(pathName);
    
    ClassLoader loader = SimpleLoader.create(path);
    
    return loader;
  }
  */
  
  protected ServiceManagerAmp getManager()
  {
    return _ampManager;
  }
  
  private InboxAmp getInboxCaller()
  {
    return _ampManager.inboxSystem();
  }
  
  public void setId(String id)
  {
    _id = id;
  }

  public void init(InputStream is)
  {
    //_hIn.reset();
    _hIn = _serializer.in(is);
    
    /*
    _is = is;
    
    if (log.isLoggable(Level.FINEST)) {
      HessianDebugInputStream hIs
        = new HessianDebugInputStream(is, log, Level.FINEST);
      
      hIs.startStreaming();
      is = hIs;
    }
    
    private Hessian2Input _hIn = new Hessian2Input();
    _in = new Hessian2StreamingInput(is);
    */
  }

  /**
   * Returns true if buffered read data is already available, i.e.
   * the read stream will not block.
   */
  public boolean isDataAvailable()
  {
    //return _in != null && _in.isDataAvailable();
    return _hIn != null;
  }

  /**
   * Reads the next HMTP packet from the stream, returning false on
   * end of file.
   */
  public boolean readMessage(InputStream is)
    throws IOException
  { 
    Thread thread = Thread.currentThread();
    ClassLoader loader = thread.getContextClassLoader();
    
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
      return readMessage(is, outbox);
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw e;
    } finally {
      thread.setContextClassLoader(loader);
    }
  }

  /**
   * Reads the next HMTP packet from the stream, returning false on
   * end of file.
   */
  public boolean readMessage(InputStream is, OutboxAmp outbox)
    throws IOException
  {    
    InH3 hIn = _hIn;
    
    if (is.available() < 0) {
      return false;
    }

    //hIn.initPacket(is);
    
    try {
      return readMessage(hIn, outbox);
    } finally {
      //outbox.setInbox(null);
    }
  }
  
  private boolean readMessage(InH3 hIn,
                              OutboxAmp outbox)
    throws IOException
  {
    int type;
    
    try {
      type = (int) hIn.readLong();
    } catch (Exception e) {
      log.log(_logLevel, e.getMessage() + " (in " + this + ")");
      
      throw e;
    }
    
    outbox.inbox(_ampManager.inboxSystem());
    
    Thread thread = Thread.currentThread();
    
    HeadersAmp headers = readHeaders(hIn);
    
    switch (MessageTypeHamp.TYPES[type]) {
    case SEND:
    {
      readSend(hIn, outbox, headers);
      break;
    }
    
    case QUERY:
    {
      readQuery(hIn, outbox, headers);
      //ServiceRef.flushOutbox();
      break;
    }
    
    case QUERY_RESULT:
    {
      readQueryResult(hIn, headers);
      break;
    }
    
    case QUERY_ERROR:
    {
      ServiceRefAmp serviceRef = readToAddress(hIn);
      
      long id = hIn.readLong();
      
      QueryRefAmp queryRef = serviceRef.removeQueryRef(id);
      
      if (queryRef != null) {
        ClassLoader loader = queryRef.getClassLoader();
      
        thread.setContextClassLoader(loader);
        // XXX: _serializer.setClassLoader(loader);
      }
      
      String code = hIn.readString();
      String msg = hIn.readString();
      Object detail = hIn.readObject();
      
      if (log.isLoggable(_logLevel)) {
        log.log(_logLevel, "query-error " + code + " " + msg + " (in " + this + ")"
                 + "\n  {id:" + id + ", to:" + serviceRef + "," + headers + "}");
      }
      
      if (queryRef == null) {
        log.warning(L.l("Expected queryRef for {0} and qid {1}",
                        serviceRef, id));
      }
      else if (detail instanceof Throwable) {
        queryRef.fail(headers, AmpException.createAndRethrow((Throwable) detail));
      }
      else {
        AmpException exn = new AmpException(msg);
        exn.fillInStackTrace();
        
        queryRef.fail(headers, exn);
      }
      break;
    }
    
    case STREAM:
    {
      readStream(hIn, outbox, headers);
      break;
    }
    
    case STREAM_RESULT:
    {
      readStreamResult(hIn, headers);
      break;
    }
    
    case STREAM_CANCEL:
    {
      readStreamCancel(hIn, headers);
      break;
    }

    default:
      throw new UnsupportedOperationException("ERROR: " + type + " "+ MessageTypeHamp.TYPES[type]);
    }
    // ServiceRef.flushOutbox();

    return true;
  }
  
  /**
   * The send message is a on-way call to a service.
   */
  private boolean readSend(InH3 hIn,
                           OutboxAmp outbox,
                           HeadersAmp headers)
      throws IOException
  {
    MethodRefHamp methodHamp = null;
    
    try {
      methodHamp = readMethod(hIn);
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
      
      skipArgs(hIn);
      
      return true;
    }

    MethodRefAmp method = methodHamp.getMethod();
    
    //ClassLoader loader = method.getService().getManager().getClassLoader();
    ClassLoader loader = methodHamp.getClassLoader();
    Thread thread = Thread.currentThread();
    thread.setContextClassLoader(loader);
    // XXX: _serializer.setClassLoader(loader);

    Object []args = readArgs(methodHamp, hIn);

    if (log.isLoggable(_logLevel)) {
      log.log(_logLevel, this + " send-r " + method.getName() + debugArgs(args)
                + " {to:" + method + ", " + headers + "}");
    }
    
    // XXX: s/b systemMailbox
    SendMessage_N sendMessage
      = new SendMessage_N(outbox,
                          headers, 
                          method.getService(), method.method(),
                          args);
    
    long timeout = 1000L; // mailbox delay timeout

    try {
      //sendMessage.offer(timeout);
      //sendMessage.offerQueue(timeout);
      //        // sendMessage.getWorker().wake();
      sendMessage.offer(timeout);
    } catch (Throwable e) {
      log.fine(e.toString());
      
      if (log.isLoggable(Level.FINEST)) {
        log.log(Level.FINEST, e.toString(), e);
      }
    }
    
    return true;
  }
  
  /**
   * The query message is a RPC call to a service.
   */
  private void readQuery(InH3 hIn,
                         OutboxAmp outbox,
                         HeadersAmp headers)
      throws IOException
  {
    GatewayReply from = readFromAddress(hIn);
    
    long qid = hIn.readLong();
    
    long timeout = 120 * 1000L;
    /*
    
    AmpQueryRef queryRef
      = new QueryItem(NullMethodRef.NULL, _context, headers, timeout);
      */

    MethodRefHamp methodHamp = null;
    MethodRefAmp methodRef = null; 
    
    try {
      try {
        methodHamp = readMethod(hIn);
      } catch (Throwable e) {
        skipArgs(hIn);
        
        throw e;
      }
      
      methodRef = methodHamp.getMethod();
      
      ClassLoader loader = methodHamp.getClassLoader();

      Thread thread = Thread.currentThread();
      thread.setContextClassLoader(loader);
      // XXX: _serializer.setClassLoader(loader);
      
      Object []args = readArgs(methodHamp, hIn);

      QueryGatewayReadMessage_N msg
        = new QueryGatewayReadMessage_N(outbox,
                                        getInboxCaller(),
                                        headers, 
                                        from, qid, 
                                        methodRef.getService(),
                                        methodRef.method(), 
                                        timeout, args);

      //msg.offer(_queueTimeout);

      //msg.offerQueue(_queueTimeout);
      // msg.getWorker().wake();
      
      msg.offer(_queueTimeout);
      //outbox.flush();
      
      if (log.isLoggable(_logLevel)) {
        log.log(_logLevel, "hamp-query " + methodRef.getName() + " " + debugArgs(args)
                   + " (in " + this + ")"
                   + "\n  {qid:" + qid + ", to:" + methodRef.getService() + ", from:" + from + "," + headers + "}");
      }
    } catch (Throwable e) {
      if (log.isLoggable(Level.FINE)) {
        log.fine("hamp-query error " + e
                 + " (in " + this + ")"
                 + "\n  {id:" + qid + ", from:" + from + "," + headers + "," + methodRef + "}");
      }
      if (log.isLoggable(Level.FINER)) {
        log.log(Level.FINER, e.toString(), e);
      }
      
      ServiceRefAmp serviceRef = getInboxCaller().serviceRef();
      
      MethodRefError methodErr;
      
      if (methodRef == null) {
        methodErr = new MethodRefError(serviceRef, "unknown-method");
      }
      else {
        methodErr = new MethodRefError(serviceRef, methodRef.getName());
      }

      QueryGatewayReadMessage_N queryRef
        = new QueryGatewayReadMessage_N(outbox,
                                        getInboxCaller(),
                                        headers, 
                                        from, qid, 
                                        serviceRef, methodErr.method(),
                                        timeout, null); 

      queryRef.toSent();
      // queryRef.failed(headers, e);

      //System.out.println("FAIL: " + e); 
      if (e instanceof ServiceException) {
        queryRef.fail(e);
        outbox.flush();
      }
      else {
        HampException exn = new HampException(L.l("{0}\n  while reading {1}", e.toString(), methodRef), e);
        
        queryRef.fail(exn);
      }
      
    }
  }
  
  /**
   * query reply parsing
   * 
   * <pre><code>
   * ["reply", {headers}, "from", qid, value]
   * </code></pre>
   * 
   * @param hIn the hessian input stream
   * @param headers the message's headers
   */
  private void readQueryResult(InH3 hIn, HeadersAmp headers)
      throws IOException
  {
    ServiceRefAmp serviceRef = readToAddress(hIn);
  
    long id = hIn.readLong();

    QueryRefAmp queryRef = serviceRef.removeQueryRef(id);
  
    if (queryRef != null) {
      ClassLoader loader = queryRef.getClassLoader();

      Thread thread = Thread.currentThread();
      thread.setContextClassLoader(loader);
      // XXX: _serializer.setClassLoader(loader);
    }
    else {
      // XX: _serializer.setClassLoader(null);
    }

    Object value = hIn.readObject();

    if (log.isLoggable(_logLevel)) {
      log.log(_logLevel, "query-result-r " + value + " (in " + this + ")"
              + "\n  {id:" + id + ", to:" + serviceRef + "," + headers + "}");
    }

    if (queryRef != null) {
      queryRef.complete(headers, value);
    }
    else if (log.isLoggable(Level.WARNING)) {
      log.warning("query-result qid=" + id + " for service " + serviceRef +
                  " does not match any known queries.\n" + headers);
    }
  }
  
  private void readStream(InH3 hIn,
                          OutboxAmp outbox,
                          HeadersAmp headers)
      throws IOException
  {
    GatewayReply from = readFromAddress(hIn);

    long qid = hIn.readLong();

    long timeout = 120 * 1000L;
    /*

  AmpQueryRef queryRef
    = new QueryItem(NullMethodRef.NULL, _context, headers, timeout);
     */

    MethodRefHamp methodHamp = null;
    MethodRefAmp method = null;
    
    try {
      // String jarPath = hIn.readString();

      // ClassLoader loader = loadClassLoaderJar(jarPath);

      //if (loader != null) {
      //  thread.setContextClassLoader(loader);
      //}

      try {
        methodHamp = readMethod(hIn);
      } catch (Throwable e) {
        hIn.readObject();
        skipArgs(hIn);

        throw e;
      }

      ClassLoader loader = methodHamp.getClassLoader();
      Thread thread = Thread.currentThread();
      thread.setContextClassLoader(loader);
      // XXX: _serializer.setClassLoader(loader);
      
      ResultStream<Object> result = (ResultStream) hIn.readObject();
      
      //GatewayResultStream gatewayResult = _channelIn.createGatewayResultStream(from.getAddress(), qid);

      result = result.createFork(from.stream(headers, qid));

      method = methodHamp.getMethod();
      Object []args;
      args = readArgs(methodHamp, hIn);

      StreamCallMessage<?> msg
      = new StreamCallMessage<Object>(outbox,
                                      getInboxCaller(),
                                      headers, 
                                      method.getService(), method.method(), 
                                      result,
                                      timeout,
                                      args);

      //msg.offer(_queueTimeout);
      msg.offer(_queueTimeout);
      // msg.getWorker().wake();

      if (log.isLoggable(_logLevel)) {
        log.log(_logLevel, "stream-r " + method.getName() + " " + debugArgs(args)
                + " (in " + this + ")"
                + "\n  {qid:" + qid + ", to:" + method.getService() + ", from:" + from + "," + headers + "}");
      }

    } catch (Throwable e) {
      if (log.isLoggable(Level.FINE)) {
        log.fine("stream-r error " + e
                 + " (in " + this + ")"
                 + "\n  {id:" + qid + ", from:" + from + "," + headers + "," + method + "}");
      }

      ServiceRefAmp serviceRef = getInboxCaller().serviceRef();

      MethodRefError methodErr;

      if (method == null) {
        methodErr = new MethodRefError(serviceRef, "unknown-method");
      }
      else {
        methodErr = new MethodRefError(serviceRef, method.getName());
      }

      StreamResultMessage<Object> msg;
      msg = new StreamResultMessage<>(outbox, 
                                      getInboxCaller(),
                                      from.stream(headers, qid));

      // queryRef.failed(headers, e);

      if (e instanceof ServiceException) {
        msg.fail(e);
      }
      else {
        HampException exn = new HampException(L.l("{0}\n  while reading {1}", e.toString(), method), e);

        msg.fail(exn);
      }

      msg.offer(timeout);
    }
  }
  
  /**
   * The stream result message is a partial or final result from the target's
   * stream.
   */
  private void readStreamResult(InH3 hIn, HeadersAmp headers)
    throws IOException
  {
    ServiceRefAmp serviceRef = readToAddress(hIn);
    
    long id = hIn.readLong();

    QueryRefAmp queryRef = serviceRef.getQueryRef(id);

    if (queryRef != null) {
      ClassLoader loader = queryRef.getClassLoader();

      Thread thread = Thread.currentThread();
      thread.setContextClassLoader(loader);
      
      // XXX: _serializer.setClassLoader(loader);
    }
    
    int sequence = hIn.readInt();
    List<Object> values = (List) hIn.readObject();
    Throwable exn = (Throwable) hIn.readObject(Throwable.class);
    boolean isComplete = hIn.readBoolean();
    
    if (log.isLoggable(_logLevel)) {
      log.log(_logLevel, "stream-result-r " + values + "," + isComplete + " (in " + this + ")"
               + "\n  {id:" + id + ", to:" + serviceRef + "," + headers + "}");
    }
    
    if (queryRef != null) {
      if (queryRef.accept(headers, values, sequence, isComplete)) {
        serviceRef.removeQueryRef(id);
      }
      
      if (exn != null) {
        serviceRef.removeQueryRef(id);
        queryRef.fail(headers, exn);
      }

      /*
      if (isComplete) {
        // XXX: timing
        //serviceRef.removeQueryRef(id);
        
        // queryRef.completeStream(headers, sequence);
      }
      */
      /*
      else if (queryRef.isCancelled()) {
        
        System.out.println("CANCEL_ME: " + queryRef);
      }
      */
    }
    else if (log.isLoggable(Level.WARNING)) {
      log.warning("query-result qid=" + id + " for service " + serviceRef +
                 " does not match any known queries.\n" + headers);
    }
  }
  
  /**
   * The stream result message is a partial or final result from the target's
   * stream.
   */
  private void readStreamCancel(InH3 hIn, HeadersAmp headers)
    throws IOException
  {
    ServiceRefAmp serviceRef = readToAddress(hIn);
    
    GatewayReply from = readFromAddress(hIn);

    long qid = hIn.readLong();

    if (log.isLoggable(_logLevel)) {
      log.log(_logLevel, "stream-cancel-r " + from + "," + qid + " (in " + this + ")"
               + "\n  {id:" + qid + ", to:" + serviceRef + "," + headers + "}");
    }
    
    from.streamCancel(qid);
  }
  
  private static class ProxyReturnResult implements ResultStream<Object> {
    private GatewayReply _from;
    private ArrayList<Object> _values = new ArrayList<>();
    
    ProxyReturnResult(GatewayReply from)
    {
      _from = from;
    }

    @Override
    public void accept(Object value)
    {
      _values.add(value);
    }

    @Override
    public void ok()
    {
    }
    
    @Override
    public void handle(Object o, Throwable exn, boolean isEnd)
    {
      throw new IllegalStateException(getClass().getName());
    }
  }
  
  private HeadersAmp readHeaders(InH3 hIn)
    throws IOException
  {
    HeadersAmp headers = HeadersNull.NULL;
    
    int count = hIn.readInt();
    
    for (int i = 0; i < count; i++) {
      String key = hIn.readString();
      Object value = hIn.readObject();
      
      headers = headers.add(key, value);
    }
    
    return headers;
  }
  
  private void skipArgs(InH3 hIn)
    throws IOException
  {
    int count = hIn.readInt();
    
    for (int i = 0; i < count; i++) {
      hIn.readObject();
    }
  }
  
  private Object []readArgs(MethodRefHamp method, InH3 hIn)
    throws IOException
  {
    int count = hIn.readInt();
    
    MarshalHamp []marshalArgs = method.getMarshalArg();
    boolean isVarArgs = method.isVarArgs();
    
    int countFixed = Math.min(count, marshalArgs.length);
        
    Object []args;
    
    if (isVarArgs) {
      //countFixed -= 1;
      args = new Object[countFixed + 1];
    }
    else {
      args = new Object[count];
    }
    
    int i = 0;
    for (i = 0; i < countFixed; i++) {
      args[i] = hIn.readObject(marshalArgs[i].getType());
    }
    
    if (isVarArgs) {
      Class<?> tailType = method.getMarshalTail().getType();

      Object tailArray = Array.newInstance(tailType, count - i);
      
      if (i + 1 == count) {
        // Object arg = hIn.readObject(method.getMarshalTail().getType());
        Object arg = hIn.readObject();
        
        /*
        if (arg == null) {
          Object []newArgs = new Object[countFixed];
          System.arraycopy(args, 0, newArgs, 0, newArgs.length);
          args = newArgs;
        }
        */
        if (arg != null && arg.getClass().isArray()) {
          args[countFixed] = arg;
        }
        else {
          Array.set(tailArray, 0, arg);
          args[countFixed] = tailArray;
        }
      }
      else {
        for (; i < count; i++) {
          Object tailArg = hIn.readObject(method.getMarshalTail().getType());
          
          Array.set(tailArray, i - countFixed, tailArg); 
        }
        
        args[countFixed] = tailArray;
      }
      
    }
    else {
      // cloud/0910
      for (; i < count; i++) {
        args[i] = hIn.readObject();
      }
    }
    
    return args;
  }
  
  private String debugArgs(Object []args)
  {
    if (args == null || args.length == 0) {
      return "[]";
    }
    
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    
    for (int i = 0; i < args.length; i++) {
      if (i != 0) {
        sb.append(", ");
      }
      
      Object arg = args[i];
      
      if (arg instanceof byte[]) {
        sb.append(Hex.toShortHex((byte[]) arg));
      }
      else {
        sb.append(arg);
      }
    }
    
    sb.append("]");
    
    return sb.toString();
  }
  
  private ServiceRefAmp readToAddress(InH3 hIn)
    throws IOException
  {
    Object value = hIn.readObject();

    if (value == null) {
      return null;
    }
    else if (value instanceof String) {
      String address = (String) value;
      ServiceRefAmp serviceRef = _channelIn.service(address);

      if (serviceRef.isClosed()) {
        serviceRef = new ServiceRefLazyInvalid(_channelIn.getManager(),
                                               _channelIn, address);
      }

      _toAddressCacheRing[_toAddressCacheIndex] = serviceRef;
      
      _toAddressCacheIndex = (_toAddressCacheIndex + 1) % _toAddressCacheRing.length;
      
      return serviceRef;
    }
    else if (value instanceof Integer) {
      int index = (Integer) value;
      
      return _toAddressCacheRing[index];
    }
    else {
      throw new IllegalStateException(String.valueOf(value));
    }
  }
  
  private MethodRefHamp readMethod(InH3 hIn)
    throws IOException
  {
    Object addressValue = hIn.readObject();
    
    Objects.requireNonNull(addressValue);
    
    MethodRefHamp methodHamp = null;

    if (addressValue instanceof String) {
      String address = (String) addressValue;
      String methodName = hIn.readString();
      String podName = hIn.readString();
      
      // ServiceRefAmp serviceRef;
      // MethodRefAmp methodRef;
      
      methodHamp = new MethodRefHamp(address, methodName, podName, 
                                     _channelIn, 
                                     _podContainer);
      
      _methodCacheRing[_methodCacheIndex] = methodHamp;
      
      _methodCacheIndex = (_methodCacheIndex + 1) % _methodCacheRing.length;

      methodHamp.lookup();
      
      /*
      try {
        serviceRef = _registryIn.lookup(address);

        if (! serviceRef.isValid()) {
          serviceRef = new ServiceRefLazyInvalid(_registryIn.getServiceRefOut().getManager(),
                                                 _registryIn, address);
        }
        
        methodRef = serviceRef.getMethod(methodName);
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
        
        serviceRef = new ServiceRefLazyInvalid(_registryIn.getServiceRefOut().getManager(),
                                               _registryIn, address);
        methodRef = serviceRef.getMethod(methodName);
      }
      */
    }
    else if (addressValue instanceof Integer) {
      int index = (Integer) addressValue;
      
      methodHamp = _methodCacheRing[index];
      
      Objects.requireNonNull(methodHamp);
      
      methodHamp.lookup();
    }
    else {
      throw new IllegalStateException(String.valueOf(addressValue));
    }
    
    return methodHamp;
  }
  
  private GatewayReply readFromAddress(InH3 hIn)
    throws IOException
  {
    Object value = hIn.readObject();

    if (value == null) {
      return new GatewayReplyBase(new ServiceRefNull(_ampManager, "/null-gateway"));
    }
    else if (value instanceof String) {
      String address = (String) value;
      
      GatewayReply gatewayRef = _channelIn.createGatewayReply(address);

      _fromAddressCacheRing[_fromAddressCacheIndex] = gatewayRef;
      
      _fromAddressCacheIndex = (_fromAddressCacheIndex + 1) % _toAddressCacheRing.length;
      
      return gatewayRef;
    }
    else if (value instanceof Integer) {
      int index = (Integer) value;
      
      return _fromAddressCacheRing[index];
    }
    else {
      System.out.println("ILLEGAL:");
      throw new IllegalStateException(String.valueOf(value));
    }
  }

  public void close()
  {
    try {
      //Hessian2StreamingInput in = _in;
      //_in = null;
      InH3 in = _hIn;
      _hIn = null;

      if (in != null) {
        in.close();
      }
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }

    _channelIn.shutdown(ShutdownModeAmp.GRACEFUL);

    // _client.close();
  }

  @Override
  public String toString()
  {
    /*
    if (_id != null)
      return getClass().getSimpleName() + "[" + _id + "]";
    else
      return getClass().getSimpleName() + "[" + _is + "]";
      */
    
    if (_id != null)
      return getClass().getSimpleName() + "[" + _dId + "," + _id + "]";
    else
      return getClass().getSimpleName() + "[" + _dId + "," + _is + "]";
  }
  
  static class MethodKey {
    private String _address;
    private String _methodName;
    
    void init(String address, String methodName)
    {
      _address = address;
      _methodName = methodName;
    }
    
    @Override
    public int hashCode()
    {
      return 65521 * _address.hashCode() + _methodName.hashCode();
    }
    
    @Override
    public boolean equals(Object o)
    {
      if (! (o instanceof MethodKey)) {
        return false;
      }
      
      MethodKey key = (MethodKey) o;
      
      return (_address.equals(key._address)
              && _methodName.equals(key._methodName));
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _address + "," + _methodName + "]";
    }
  }
}
