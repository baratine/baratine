/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.log.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import com.caucho.v5.amp.deliver.Deliver;
import com.caucho.v5.amp.deliver.Outbox;
import com.caucho.v5.amp.deliver.QueueDeliver;
import com.caucho.v5.amp.deliver.QueueDeliverBuilderImpl;
import com.caucho.v5.util.L10N;

/**
 * Configures a log handler
 */
abstract public class LogHandlerBase extends Handler
{
  private QueueDeliver<LogItem<?>> _logQueue;

  private Filter _filter;
  
  protected LogHandlerBase()
  {
  }

  /**
   * Sets the filter.
   */
  @Override
  public void setFilter(Filter filter)
  {
    _filter = filter;
  }

  public Filter getFilter()
  {
    return _filter;
  }
  
  protected void init()
  {
    _logQueue = createQueue();
    
    Objects.requireNonNull(_logQueue);
  }
  
  protected QueueDeliver createQueue()
  {
    QueueDeliverBuilderImpl builder
      = new QueueDeliverBuilderImpl();
    builder.size(256);
    builder.sizeMax(16 * 1024);
    
    return builder.build(new LogQueue());
  }

  /**
   * Publishes the record.
   */
  @Override
  public final void publish(LogRecord record)
  {
    if (! isLoggable(record)) {
      return;
    }

    Filter filter = getFilter();

    if (filter != null && ! filter.isLoggable(record))
      return;

    if (record == null) {
      System.out.println(this + ": no record");
      return;
    }

    //synchronized (this) {
      processPublish(record);
      //processFlush();
    //}

    /*
    _logQueue.offer(record);

    if (CurrentTime.isTest()) {
      waitForEmpty();
    }
    */
  }

  private void waitForEmpty()
  {
    _logQueue.wake();

    for (int i = 0; i < 20 && ! _logQueue.isEmpty(); i++) {
      try {
        Thread.sleep(1);
      } catch (Exception e) {
      }
    }
  }

  protected void processPublish(LogRecord record)
  {
    String msg = format(record);
    
    if (msg == null) {
      return;
    }

    if (! _logQueue.offer(new LogItemString(null, msg), 10, TimeUnit.SECONDS)) {
      System.out.println(msg);
    }
    
    _logQueue.wake();
  }
  
  protected boolean isNullDelimited()
  {
    return false;
  }
  
  /*
  public TimestampFilter getTimestampFilter()
  {
    return null;
  }
  */
  
  protected String format(LogRecord record)
  {
    if (! isLoggable(record)) {
      return null;
    }
    
    StringBuilder sb = new StringBuilder();
    
    if (record == null) {
      sb.append("no record");
          
      if (isNullDelimited()) {
        sb.append('\0');
      }

      return sb.toString();
    }

    try {
      Formatter formatter = getFormatter();
      
      if (formatter != null) {
        String value = formatter.format(record);
  
        sb.append(value).append('\n');
        
        if (isNullDelimited()) {
          sb.append('\0');
        }
          
        return sb.toString();
      }
        
      String message = record.getMessage();
      Throwable thrown = record.getThrown();
        
      if (thrown == null
          && message != null
          && message.indexOf("java.lang.NullPointerException") >= 0) {
        thrown = new IllegalStateException();
        thrown.fillInStackTrace();
      }
            
      if (thrown != null) {
        if (message != null
            && ! message.equals(thrown.toString()) 
            && ! message.equals(thrown.getMessage())) {
          printMessage(sb, message, record.getParameters());
        }
          
        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        thrown.printStackTrace(pw);
        pw.close();
        
        sb.append(writer.toString());
      }
      else {
        printMessage(sb, message, record.getParameters());
      }

      /*
      TimestampFilter timestamp = getTimestampFilter();
      if (timestamp != null) {
        sb = timestamp.format(sb);
      }
      */
        
      if (isNullDelimited()) {
        sb.append('\0');
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }

    return sb.toString();
  }
  
  abstract protected void deliverLog(String log);

  abstract protected void processFlush();

  protected void printMessage(StringBuilder sb,
                              String message,
                              Object []parameters)
    throws IOException
  {
    if (parameters == null || parameters.length == 0) {
      sb.append(message).append('\n');;
    }
    else {
      sb.append(L10N.fillMessage(message, parameters)).append('\n');
    }
  }

  /**
   * Flushes the buffer.
   */
  @Override
  public void flush()
  {
  }

  /**
   * Closes the handler.
   */
  @Override
  public void close()
  {
    long timeout = 10000;
    long expires = System.currentTimeMillis() + timeout;
    
    if (_logQueue.size() > 0 && System.currentTimeMillis() < expires) {
      _logQueue.wake();
      
      //try { Thread.sleep(1); } catch (Exception e) {}
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }

  private class LogQueue
    implements LogItemStringHandler, Deliver<LogItemString>
  {
    @Override
    public void deliver(LogItemString value, Outbox outbox)
      throws Exception
    {
      // value.deliver(this);
      
      deliverLog(value.getLog());
    }
    
    @Override
    public void onString(RotateStream stream, String msg)
    {
      deliverLog(msg);
    }
    
    @Override
    public void flush()
    {
      processFlush();
    }

    @Override
    public void afterBatch()
    {
      processFlush();
    }

    @Override
    public void flush(RotateStream stream)
    {
      processFlush();
    }
  }
}
