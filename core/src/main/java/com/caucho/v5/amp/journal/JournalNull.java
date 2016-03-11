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

import com.caucho.v5.amp.outbox.QueueService;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;

/**
 * Null implementation of the journal.
 */
public class JournalNull implements JournalAmp
{
  @Override
  public void writeSend(ActorAmp actor,
                        String methodName, 
                        Object[] args,
                        InboxAmp mailbox)
  {
  }
  
  @Override
  public void writeQuery(ActorAmp actor,
                         String methodName, 
                         Object[] args,
                         InboxAmp mailbox)
  {
  }

  @Override
  public void setInbox(InboxAmp inbox)
  {
  }

  @Override
  public void flush()
  {
  }
  
  @Override
  public boolean isSaveRequest()
  {
    return false;
  }

  @Override
  public boolean saveStart()
  {
    return true;
  }

  @Override
  public void saveEnd(boolean isComplete)
  {
  }

  @Override
  public void replayStart(Result<Boolean> cont,
                          InboxAmp inbox,
                          QueueService<MessageAmp> queue)
  {
    cont.ok(true);
  }

  @Override
  public long getDelay()
  {
    return 0;
  }

  @Override
  public long getReplaySequence()
  {
    return 0;
  }
}
