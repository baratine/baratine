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

package com.caucho.v5.kelp;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.db.journal.JournalStream;
import com.caucho.v5.db.journal.JournalStream.ReplayCallback;
import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.BitsUtil;

/**
 * Implementation of the mauka journal.
 */
class JournalKelpImpl
{
  private static final Logger log
    = Logger.getLogger(JournalKelpImpl.class.getName());
  
  private static final int CODE_PUT = 1;
  private static final int CODE_REMOVE = 2;
  /*
  private static final int CODE_QUERY_REPLY = 3;
  private static final int CODE_QUERY_ERROR = 4;
  */
  
  private static final int CODE_CHECKPOINT_START = 5;
  private static final int CODE_CHECKPOINT_END = 6;
  
  private final TableKelp _table;
  
  private final JournalStream _jOut;
  private final JournalOutputStream _jOs;
  
  private final TempBuffer _tBuf = TempBuffer.allocate();
  private final byte []_buffer = _tBuf.buffer();
  
  private final RowCursor _workCursor;
  
  JournalKelpImpl(TableKelp table, JournalStream jOut)
  {
    _table = table;
    
    if (jOut == null) {
      jOut = createJournalStream();
    }
    
    _jOut = jOut;
    _jOs = new JournalOutputStream(_jOut);
    
    _workCursor = _table.cursor();
  }
  
  protected JournalStream createJournalStream()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  private int getKeyLength()
  {
    return _table.getRow().getKeyLength();
  }
  
  boolean isCheckpointRequired()
  {
    return _jOut.isSaveRequired();
  }
  
  /**
   * Writes the put to the journal.
   */
  void put(RowCursor cursor)
  {
    boolean isValid;

    do {
      isValid = true;
      
      try (JournalOutputStream os = openItem()) {
        os.write(CODE_PUT);
      
        cursor.writeJournal(os);
        
        isValid = os.complete();
      } catch (IOException e) {
        log.log(Level.FINER, e.toString(), e);
      }
    } while (! isValid);
  }
  
  /**
   * Writes the remove to the journal.
   */
  void remove(RowCursor cursor)
  {
    boolean isValid;
    do {
      isValid = true;

      try (JournalOutputStream os = openItem()) {
        os.write(CODE_REMOVE);
      
        cursor.getKey(_buffer, 0);
      
        os.write(_buffer, 0, getKeyLength());
        
        try {
          BitsUtil.writeLong(os, cursor.getVersion());
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        
        isValid = os.complete();
      }
    } while (! isValid);
  }
  
  private void replayJournal(ReadBuffer is,
                             PageServiceImpl pageActor)
    throws IOException
  {
    int code = is.read();
    RowCursor cursor = _workCursor;

    switch (code) {
    case CODE_PUT: {
      cursor.readJournal(pageActor, is);

      pageActor.replayJournalPut(cursor);

      break;
    }
    
    case CODE_REMOVE: {
      int len = getKeyLength();
      
      is.readAll(_buffer, 0, len);
      
      cursor.setKey(_buffer, 0);
      cursor.setVersion(BitsUtil.readLong(is));
      
      pageActor.replayJournalRemove(cursor);

      break;
    }

    default:
      throw new IllegalStateException(String.valueOf(code) + " pos=" + is.getPosition());
    }
  }
  
  void replayJournal(PageServiceImpl putActor)
  {
    _jOut.replay(new ReplayJournalCallback(putActor));
  }
  
  private JournalOutputStream openItem()
  {
    _jOs.start();
    
    return _jOs;
  }

  public void flush()
  {
    _jOut.flush();
  }
  
  private class JournalOutputStream extends OutputStream
  {
    private final JournalStream _jOut;
    private final byte []_data = new byte[1];
    
    JournalOutputStream(JournalStream jOut)
    {
      _jOut = jOut;
    }
    
    public void start()
    {
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
    
    public boolean complete()
    {
      return _jOut.complete();
    }
    
    @Override
    public void close()
    {
      _jOut.complete();
    }
  }
  
  class ReplayJournalCallback implements ReplayCallback
  {
    private PageServiceImpl _putActor;
    
    ReplayJournalCallback(PageServiceImpl putActor)
    {
      _putActor = putActor;
    }

    @Override
    public void onItem(ReadBuffer is) throws IOException
    {
      replayJournal(is, _putActor);
    }

    @Override
    public void completed()
    {
    }
  }
}
