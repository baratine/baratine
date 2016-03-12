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

package com.caucho.v5.amp.deliver;

/**
 * Outbox for a delivery processor.
 */
public class OutboxImpl implements Outbox
{
  //private static final long OFFER_TIMEOUT = 3600 * 1000;
  private static final long OFFER_TIMEOUT = 10 * 1000;
  
  private MessageDeliver<?> _msg;
  private Object _context;
  
  public OutboxImpl()
  {
  }
  
  @Override
  public boolean isEmpty()
  {
    return _msg == null;
  }
  
  @Override
  public final void offer(MessageDeliver<?> msg)
  {
    MessageDeliver<?> prevMsg = _msg;
    _msg = msg;
    
    if (prevMsg != null) {
      try {
        prevMsg.offerQueue(OFFER_TIMEOUT);
      } catch (Exception e) {
        System.err.println("PREVM: " + prevMsg + " " + msg);
        e.printStackTrace();
      }
      
      if (prevMsg.worker() != msg.worker()) {
        prevMsg.worker().wake();
      }
    }
  }
  
  @Override
  public void flush()
  {
    MessageDeliver<?> prevMsg;

    //Thread.dumpStack();
    
    if ((prevMsg = _msg) != null) {
      _msg = null;
      
      prevMsg.offerQueue(OFFER_TIMEOUT);
      prevMsg.worker().wake();
    }
  }
  
  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public final boolean flushAndExecuteLast()
  {
    MessageDeliver<?> tailMsg = _msg;
    
    if (tailMsg == null) {
      return false;
    }
    
    _msg = null;
    
    WorkerDeliver worker = tailMsg.worker();
      
    if (worker.runOne(this, tailMsg)) {
      return _msg != null;
    }
    else {
      tailMsg.offerQueue(OFFER_TIMEOUT);
      worker.wake();
      
      return _msg != null;
    }
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public final void flushAndExecuteAll()
  {
    MessageDeliver<?> tailMsg;
    
    while ((tailMsg = _msg) != null) {
      _msg = null;
      
      WorkerDeliver worker = tailMsg.worker();
      
      if (! worker.runAs(this, tailMsg)) {
        tailMsg.offerQueue(OFFER_TIMEOUT);
        tailMsg.worker().wake();
        return;
      }
    }
  }

  /*
  @Override
  public MessageOutbox<?> tailMessage()
  {
    MessageOutbox<?> prevMsg = _msg;
    
    if (prevMsg != null) {
      _msg = null;
    }
    
    return prevMsg;
  }
  */
  
  @Override
  public Object context()
  {
    return _context;
  }
  
  @Override
  public Object getAndSetContext(Object context)
  {
    Object oldContext = _context;
    
    _context = context;
    
    return oldContext;
  }
  
  @Override
  public void open()
  {
  }
  
  @Override
  public void close()
  {
    flush();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
