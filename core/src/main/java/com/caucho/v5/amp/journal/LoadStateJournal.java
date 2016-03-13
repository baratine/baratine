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

package com.caucho.v5.amp.journal;

import io.baratine.service.Result;
import io.baratine.stream.ResultStream;

import java.util.Objects;

import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LoadState;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodAmp;


/**
 * State/dispatch for a loadable actor.
 */
public class LoadStateJournal implements LoadState
{
  private ActorJournal _journalActor;
  
  LoadStateJournal(ActorJournal journalActor)
  {
    Objects.requireNonNull(journalActor);
    
    _journalActor = journalActor;
  }
  
  @Override
  public LoadState load(ActorAmp actor,
                        InboxAmp inbox,
                        MessageAmp msg)
  {
    return this;
  }

  @Override
  public void onModify(ActorAmp  actorAmpBase)
  {
  }

  @Override
  public void send(ActorAmp actorDeliver,
                   ActorAmp actorMessage,
                   MethodAmp method, 
                   HeadersAmp headers)
  {
    send(actorDeliver, actorMessage, method, headers, new Object[0]);
  }

  @Override
  public void send(ActorAmp actorDeliver,
                   ActorAmp actorMessage,
                   MethodAmp method, 
                   HeadersAmp headers, 
                   Object arg0)
  {
    send(actorDeliver, actorMessage, method, headers, new Object[] { arg0 });
  }

  @Override
  public void send(ActorAmp actorDeliver,
                   ActorAmp actorMessage,
                   MethodAmp method, 
                   HeadersAmp headers, 
                   Object arg0,
                   Object arg1)
  {
    send(actorDeliver, actorMessage, method, headers, 
         new Object[] { arg0, arg1 });
  }

  @Override
  public void send(ActorAmp actorDeliver,
                   ActorAmp actorMessage,
                   MethodAmp method, 
                   HeadersAmp headers, 
                   Object arg0,
                   Object arg1,
                   Object arg2)
  {
    send(actorDeliver, actorMessage, method, headers, 
         new Object[] { arg0, arg1, arg2 });
  }

  @Override
  public void send(ActorAmp actorDeliver,
                   ActorAmp actorMessage,
                   MethodAmp method, 
                   HeadersAmp headers, 
                   Object []args)
  {
    if (! method.isModify()) {
      return;
    }
    
    // ActorAmp actorInvoke = method.getActorInvoke(actorMessage);
    ActorAmp actorInvoke = actorMessage;
    
    ActorJournal journalActor = _journalActor;
    
    JournalAmp journal = journalActor.getJournal();
    InboxAmp inbox = journalActor.getInbox();
    
    journal.writeSend(actorInvoke, method.name(), args, inbox);
    
    JournalAmp toPeerJournal = getToPeerJournal();
    if (toPeerJournal != null) {
      toPeerJournal.writeSend(actorInvoke, method.name(), args, 
                              getJournalInbox());
    }
  }
  
  @Override
  public void query(ActorAmp actorDeliver,
                    ActorAmp actorMessage,
                    MethodAmp method,
                    HeadersAmp headers,
                    Result<?> result)
  {
    query(actorDeliver, actorMessage, method, headers, result, new Object[] {});
  }
  
  @Override
  public void query(ActorAmp actorDeliver,
                    ActorAmp actorMessage,
                    MethodAmp method,
                    HeadersAmp headers,
                    Result<?> result,
                    Object arg0)
  {
    query(actorDeliver, actorMessage, method, headers, result, 
          new Object[] { arg0 });
  }
  
  
  @Override
  public void query(ActorAmp actorDeliver,
                    ActorAmp actorMessage,
                    MethodAmp method,
                    HeadersAmp headers,
                    Result<?> result,
                    Object arg0,
                    Object arg1)
  {
    query(actorDeliver, actorMessage, method, headers, result, 
          new Object[] { arg0, arg1 });
  }
  
  @Override
  public void query(ActorAmp actorDeliver,
                    ActorAmp actorMessage,
                    MethodAmp method,
                    HeadersAmp headers,
                    Result<?> result,
                    Object arg0,
                    Object arg1,
                    Object arg2)
  {
    query(actorDeliver, actorMessage, method, headers, result, 
          new Object[] { arg0, arg1, arg2 });
  }

  @Override
  public void query(ActorAmp actorDeliver,
                    ActorAmp actorMessage,
                    MethodAmp method,
                    HeadersAmp headers,
                    Result<?> result, 
                    Object[] args)
  {
    if (! method.isModify()) {
      return;
    }

    // ActorAmp actorInvoke = method.getActorInvoke(actorMessage);
    ActorAmp actorInvoke = actorMessage;
    
    ActorJournal journalActor = _journalActor;
    
    JournalAmp journal = journalActor.getJournal();
    InboxAmp inbox = journalActor.getInbox();

    journal.writeQuery(actorInvoke, method.name(), args, inbox);
    
    JournalAmp toPeerJournal = getToPeerJournal();

    if (toPeerJournal != null) {
      toPeerJournal.writeQuery(actorInvoke, method.name(), args, 
                               getJournalInbox());
    }
  }
  
  @Override
  public void stream(ActorAmp actorDeliver,
                      ActorAmp actorMessage,
                      MethodAmp method,
                      HeadersAmp headers,
                      ResultStream<?> result, 
                      Object[] args)
  {
  }
  
  private JournalAmp getToPeerJournal()
  {
    return _journalActor.getToPeerJournal();
  }
  
  private InboxAmp getJournalInbox()
  {
    return _journalActor.getInbox();
  }
}
