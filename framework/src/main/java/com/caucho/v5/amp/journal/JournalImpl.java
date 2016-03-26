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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.deliver.QueueDeliver;
import com.caucho.v5.amp.message.OnSaveMessage;
import com.caucho.v5.amp.message.OnSaveRequestMessage;
import com.caucho.v5.amp.message.ReplayQueryMessage;
import com.caucho.v5.amp.message.ReplaySendMessage;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.db.journal.JournalStream;
import com.caucho.v5.db.journal.JournalStream.ReplayCallback;
import com.caucho.v5.h3.H3;
import com.caucho.v5.h3.InH3;
import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.h3.OutH3;
import com.caucho.v5.util.Alarm;
import com.caucho.v5.util.AlarmListener;
import com.caucho.v5.util.WeakAlarm;
import com.caucho.v5.vfs.ReadStream;

import io.baratine.service.Result;

/**
 * Interface to the journal itself. Journal writers open a stream and write to
 * it. Journal readers receive events.
 */
public class JournalImpl implements JournalAmp
{
  private static final Logger log
    = Logger.getLogger(JournalImpl.class.getName());
  
  private static final int CODE_SEND = 1;
  private static final int CODE_QUERY = 2;
  /*
  private static final int CODE_QUERY_REPLY = 3;
  private static final int CODE_QUERY_ERROR = 4;
  */
  
  //private static final int CODE_CHECKPOINT_START = 5;
  //private static final int CODE_CHECKPOINT_END = 6;
  
  private InH3 _hIn;
  private OutH3 _hOut;
  private final JournalStream _jOut;
  private final OutputStreamJournal _jOs;
  
  private final int _maxCount;
  
  private long _count;
  private long _nextMaxCount;
  private long _lastFlushCount;
  
  private long _delay;
  private long _nextTimeout;
  
  private Alarm _timeoutAlarm;
  private TimeoutListener _timeoutListener;

  private OutFactoryH3 _serializer;
  
  protected JournalImpl()
  {
    this(null, -1, -1);
  }
  
  public JournalImpl(JournalStream jOut,
                     int maxCount,
                     long timeout)
  {
    if (jOut == null) {
      jOut = createJournalStream();
    }

    _jOut = jOut;
    
    _serializer = H3.newOutFactory().get();
    /*
    _hOut = new Hessian2Output();
    //_hOut.setUnshared(true);
    
    _hIn = new Hessian2Input();
    // _hIn.setUnshared(true);
     * 
     */
    
    if (maxCount < 0) {
      maxCount = Integer.MAX_VALUE;
    }
    
    _maxCount = Math.max(1, maxCount);
    _nextMaxCount = _maxCount;
    
    if (timeout < 0 || Integer.MAX_VALUE / 2 < timeout) {
      timeout = -1;
    }

    _delay = timeout;

    _jOs = new OutputStreamJournal(_jOut);
  }
  
  protected JournalStream createJournalStream()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public long getDelay()
  {
    return _delay;
  }

  @Override
  public void setInbox(InboxAmp inbox)
  {
    if (_delay > 0 && inbox != null) {
      _timeoutListener = new TimeoutListener(inbox);
      _timeoutAlarm = new WeakAlarm(_timeoutListener);
    }
  }
  
  @Override
  public boolean isSaveRequest()
  {
    /*
    boolean result = false;
    
    if (_nextMaxCount <= _count) {
      _nextMaxCount = _count + _maxCount;
      
      result = true;
    }
    else if (_lastFlushCount < _count && _timeout >= 0) {
      long now = CurrentTime.getCurrentTime();
      
      if (_nextTimeout < now) {
        result = true;
        _nextTimeout = now + _timeout;
      }
      else  {
        if (_nextTimeout <= 0) {
          _nextTimeout = now + _timeout;
        }
        
        if (_timeoutAlarm != null) {
          _timeoutAlarm.queue(_timeout);
        }
      }
    }
    
    _lastFlushCount = _count;
    
    return result;
    */
    
    return false;
  }
  
  /**
   * Writes the send to the journal. The queryRef values are not
   * saved, because restoring them does not make sense.
   */
  @Override
  public void writeSend(StubAmp actor,
                        String methodName,
                        Object[] args,
                        InboxAmp inbox)
  {
    try (OutputStream os = openItem(inbox)) {
      // XXX: should keep open
      try (OutH3 out = _serializer.out(os)) {
        String key = actor.journalKey();
      
        out.writeLong(CODE_SEND);
        out.writeString(key);
        out.writeString(methodName);
        out.writeLong(args.length);
      
        for (Object arg : args) {
          out.writeObject(arg);
        }
      }
      
      _count++;
    } catch (IOException e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }
  
  /**
   * Writes the query to the journal. The queryRef values are not
   * saved, because restoring them does not make sense.
   */
  @Override
  public void writeQuery(StubAmp actor,
                         String methodName,
                         Object[] args,
                         InboxAmp inbox)
  {
    try (OutputStream os = openItem(inbox)) {
      try (OutH3 out = _serializer.out(os)) {
      
        //hOut.initPacket(os);
      
        String key = actor.journalKey();
      
        out.writeLong(CODE_QUERY);
        out.writeString(key);
        out.writeString(methodName);
        out.writeLong(args.length);
      
        for (Object arg : args) {
          out.writeObject(arg);
        }
      
        //out.flush();
      
        _count++;
      }
    } catch (IOException e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }
  
  @Override
  public boolean saveStart()
  {
    return _jOut.saveStart();
  }
  
  @Override
  public void saveEnd(boolean isComplete)
  {
    _jOut.saveEnd();
  }
  
  private OutputStream openItem(InboxAmp inbox)
  {
    Objects.requireNonNull(inbox);
    
    _jOs.init(inbox);
    
    return _jOs;
  }

  @Override
  public void flush()
  {
    _jOut.flush();
  }
  
  //
  // replay
  //
  
  private void readItem(InputStream is,
                        QueueDeliver<MessageAmp> queue)
    throws IOException
  {
    //_hIn.initPacket(is);

    try (InH3 in = _serializer.in(is)) {
      int ch = in.readInt();
    
      switch (ch) {
      case CODE_SEND:
        readSend(in, queue);
        break;
      
      case CODE_QUERY:
        readQuery(in, queue);
        break;
      }
    }
  }
  
  private void readSend(InH3 hIn,
                        QueueDeliver<MessageAmp> queue)
    throws IOException
  {
    String keyPath = hIn.readString();
    
    String methodName = hIn.readString();
    
    int count = hIn.readInt();
    
    Object []args = new Object[count];
    
    for (int i = 0; i < args.length; i++ ){
      args[i] = hIn.readObject();
    }
    
    ReplaySendMessage msg = new ReplaySendMessage(keyPath, methodName, args);

    queue.offer(msg, 60, TimeUnit.SECONDS);
  }
  
  private void readQuery(InH3 hIn,
                         QueueDeliver<MessageAmp> queue)
    throws IOException
  {
    String keyPath = hIn.readString();
    
    String methodName = hIn.readString();
    
    int count = hIn.readInt();
    
    Object []args = new Object[count];

    for (int i = 0; i < args.length; i++ ){
      args[i] = hIn.readObject();
    }
    
    ReplayQueryMessage msg = new ReplayQueryMessage(keyPath, methodName, args);
    queue.offer(msg, 60, TimeUnit.SECONDS);
  }
  
  @Override
  public long getReplaySequence()
  {
    return _jOs.getReplaySequence();
  }

  @Override
  public void replayStart(Result<Boolean> result,
                          InboxAmp inbox,
                          QueueDeliver<MessageAmp> queue)
  {
    Objects.requireNonNull(inbox);
    
    ReplayCallbackImpl replayTask = new ReplayCallbackImpl(inbox, queue, result);
    
    _jOs.replay(replayTask);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _jOut + "]";
  }
  
  private class OutputStreamJournal extends OutputStream
  {
    private final JournalStream _jOut;
    private final byte []_data = new byte[1];
    private InboxAmp _inbox;
    
    OutputStreamJournal(JournalStream jOut)
    {
      _jOut = jOut;
    }
    
    public long getReplaySequence()
    {
      return _jOut.getReplaySequence();
    }

    public void replay(ReplayCallback replayCallback)
    {
      _jOut.replay(replayCallback);
    }

    void init(InboxAmp inbox)
    {
      Objects.requireNonNull(inbox);
      _inbox = inbox;
      
      _jOut.start();
    }
    
    @Override
    public void write(int value)
    {
      _data[0] = (byte) value;
      
      _jOut.write(_data, 0, 1);
    }
    
    @Override
    public void write(byte []buffer, int offset, int length)
    {
      _jOut.write(buffer, offset, length);
    }
    
    @Override
    public void close()
    {
      InboxAmp inbox = _inbox;
      _inbox = null;
      
      _jOut.complete();
      
      
      if (inbox != null && _jOut.isSaveRequired()) {
        JournalAmp journal = JournalImpl.this;
        
        long timeout = InboxAmp.TIMEOUT_INFINITY;

        inbox.offer(new OnSaveMessage(journal, inbox), timeout);
        // queue.getQueueWorker().wake();
      }
    }
  }
  
  private class ReplayCallbackImpl implements ReplayCallback
  {
    private InboxAmp _inbox;
    private QueueDeliver<MessageAmp> _queue;
    private Result<Boolean> _result;
    
    ReplayCallbackImpl(InboxAmp inbox,
                       QueueDeliver<MessageAmp> queue,
                       Result<Boolean> result)
    {
      Objects.requireNonNull(inbox);
      Objects.requireNonNull(queue);
      
      _inbox = inbox;
      _queue = queue;
      _result = result;
    }

    @Override
    public void onItem(ReadStream is) throws IOException
    {
      readItem(is, _queue);
    }

    @Override
    public void completed()
    {
      _queue.wake();
      _inbox.worker().wake();
      
      if (_result != null) {
        _result.ok(true);
      }
      
      // _inbox.offerAndWake(new OnSaveRequestMessage(_inbox, Result.ignore()), 0);
    }
  }
  
  private class TimeoutListener implements AlarmListener {
    private InboxAmp _inbox;
    
    TimeoutListener(InboxAmp inbox)
    {
      _inbox = inbox;
    }
    
    @Override
    public void handleAlarm(Alarm alarm)
    {
      _inbox.offerAndWake(new OnSaveRequestMessage(_inbox, Result.ignore()), 0);
    }
  }
}
