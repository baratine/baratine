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

package com.caucho.v5.amp.spi;

import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.SaveResult;

import io.baratine.service.Result;


/**
 * An AMP Actor sends and receives messages as the core class in a
 * service-oriented architecture.
 *
 * <h2>Core API</h2>
 *
 * Each actor has a unique address, which is the address for messages sent to
 * the actor.  addresses are typically URLs.
 *
 */
public interface ActorAmpState extends StubAmp
{
  boolean isJournalReplay();
  void queuePendingReplayMessage(MessageAmp msg);
  void queuePendingMessage(MessageAmp msg);
  void setLoadState(LoadState state);
  void deliverPendingMessages(InboxAmp inbox);
  void deliverPendingReplay(InboxAmp inbox);
  boolean onSaveStartImpl(Result<Boolean> addBean);
  void onLoad(Result<? super Boolean> result);
  void afterBatchImpl();
  void onSaveChildren(SaveResult saveResult);
}
