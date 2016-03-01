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

import java.util.logging.Logger;

import com.caucho.v5.amp.actor.MethodAmpWrapper;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MethodAmp;

/**
 * Sender for an actor ref.
 */
public final class MethodJournal extends MethodAmpWrapper
{
  private static final Logger log
    = Logger.getLogger(MethodJournal.class.getName());
  
  private final MethodAmp _delegate;
  private final JournalAmp _journal;
  private final JournalAmp _toPeerJournal;
  private final InboxAmp _inbox;
  
  public MethodJournal(MethodAmp delegate,
                           JournalAmp journal,
                           JournalAmp toPeerJournal,
                           InboxAmp mailbox)
  {
    _delegate = delegate;

    _journal = journal;
    _toPeerJournal = toPeerJournal;
    _inbox = mailbox;
    
    System.out.println("MJ:" + _delegate);
  }
  
  @Override
  protected MethodAmp delegate()
  {
    return _delegate;
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actor,
                   Object[] args)
  {
    System.out.println("SND: " + actor + " " + _toPeerJournal);
    /*
    if (actor instanceof ActorJournal) {
      _journal.writeSend(getName(), args, _mailbox);
      
      if (_toPeerJournal != null) {
        _toPeerJournal.writeSend(getName(), args, _mailbox);
      }
    }
    else {
      //getDelegate().send(headers, actor, args);
    }
    */
  }

  @Override
  public void query(HeadersAmp headers, 
                    Result<?> result,
                    ActorAmp actor,
                    Object[] args)
  {
    System.out.println("Q: " + actor + " " + _toPeerJournal);
    /*
    if (actor instanceof ActorJournal) {
      _journal.writeQuery(getName(), args, _mailbox);
      
      if (_toPeerJournal != null) {
        _toPeerJournal.writeQuery(getName(), args, _mailbox);
      }
    }
    else {
      //getDelegate().query(headers, queryRef, actor, args);
    }
    */
  }
}
