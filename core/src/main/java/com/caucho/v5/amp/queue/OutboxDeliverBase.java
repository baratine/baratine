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

package com.caucho.v5.amp.queue;


/**
 * Outbox for a delivery processor.
 */
abstract public class OutboxDeliverBase<M extends MessageDeliver>
  implements OutboxDeliver<M>
{
  //private static final long OFFER_TIMEOUT = 3600 * 1000;
  private static final long OFFER_TIMEOUT = 10 * 1000;
  private M _msg;
  
  protected OutboxDeliverBase()
  {
  }
  
  @Override
  public boolean isEmpty()
  {
    return _msg == null;
  }
  
  @Override
  public final void offer(M msg)
  {
    M prevMsg = _msg;
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
    M prevMsg;
    
    if ((prevMsg = _msg) != null) {
      _msg = null;
      
      prevMsg.offerQueue(OFFER_TIMEOUT);
      prevMsg.worker().wake();
    }
  }
  
  @Override
  public boolean flushAndExecuteLast()
  {
    M tailMsg = _msg;
    
    if (tailMsg == null) {
      return true;
    }
    
    _msg = null;
    
    WorkerDeliver nextWorker = tailMsg.worker();

    if (! (nextWorker instanceof WorkerDeliverMessage)) {
      tailMsg.offerQueue(OFFER_TIMEOUT);
      nextWorker.wake();
      return true;
    }
      
    WorkerDeliverMessage<M> worker = (WorkerDeliverMessage<M>) nextWorker;
      
    if (worker.runOne(this, tailMsg)) {
      return _msg == null;
    }
    else {
      tailMsg.offerQueue(OFFER_TIMEOUT);
      nextWorker.wake();
      
      return _msg == null;
    }
  }
  
  @Override
  public M flushAfterTask()
  {
    M prevMsg = _msg;
    
    if (prevMsg != null) {
      _msg = null;
    }
    
    return prevMsg;
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
