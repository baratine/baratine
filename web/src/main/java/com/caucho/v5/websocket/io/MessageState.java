/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package com.caucho.v5.websocket.io;

import static com.caucho.v5.websocket.io.WebSocketConstants.FLAG_FIN;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_BINARY;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_CLOSE;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_CONT;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_PING;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_PONG;
import static com.caucho.v5.websocket.io.WebSocketConstants.OP_TEXT;

public enum MessageState {
  IDLE {
    @Override
    public MessageState toText()
    {
      return TEXT_INIT;
    }

    @Override
    public MessageState toBinary()
    {
      return BINARY_INIT;
    }

    @Override
    public MessageState toClose()
    {
      return CLOSE;
    }
  },

  BINARY_INIT
  {
    @Override
    public boolean isActive() { return true; }

    @Override
    public int code()
    {
      return OP_BINARY;
    }

    @Override
    public MessageState toCont()
    {
      return CONT;
    }

    @Override
    public MessageState toFinal()
    {
      return BINARY;
    }
  },

  BINARY
  {
    @Override
    public boolean isActive() { return true; }

    @Override
    public int code()
    {
      return FLAG_FIN | OP_BINARY;
    }

    @Override
    public MessageState toFinal()
    {
      return this;
    }
  },

  TEXT_INIT
  {
    @Override
    public boolean isActive() { return true; }

    @Override
    public int code()
    {
      return OP_TEXT;
    }

    @Override
    public MessageState toCont()
    {
      return CONT;
    }

    @Override
    public MessageState toFinal()
    {
      return TEXT;
    }
  },

  TEXT
  {
    @Override
    public boolean isActive() { return true; }

    @Override
    public int code()
    {
      return FLAG_FIN | OP_TEXT;
    }

    @Override
    public MessageState toCont()
    {
      return CONT;
    }

    @Override
    public MessageState toFinal()
    {
      return this;
    }
  },

  CONT {
    @Override
    public boolean isActive() { return true; }

    @Override
    public int code() { return OP_CONT; }

    @Override
    public MessageState toBinary()
    {
      return this;
    }

    @Override
    public MessageState toText()
    {
      return this;
    }

    @Override
    public MessageState toCont()
    {
      return this;
    }

    @Override
    public MessageState toFinal()
    {
      return FINAL;
    }
  },

  FINAL {
    @Override
    public boolean isActive() { return true; }

    @Override
    public int code() { return FLAG_FIN; }

    @Override
    public MessageState toBinary()
    {
      return BINARY_INIT;
    }

    @Override
    public MessageState toText()
    {
      return TEXT_INIT;
    }
  },

  PING
  {
    @Override
    public boolean isActive() { return true; }

    @Override
    public int code() { return FLAG_FIN | OP_PING; }

    @Override
    public MessageState toFinal()
    {
      return this;
    }
  },

  PONG
  {
    @Override
    public boolean isActive() { return true; }

    @Override
    public int code() { return FLAG_FIN | OP_PONG; }

    @Override
    public MessageState toFinal()
    {
      return this;
    }
  },

  CLOSE {
    @Override
    public int code() { return FLAG_FIN | OP_CLOSE; }

    @Override
    public MessageState toFinal()
    {
      return this;
    }
  },

  DESTROYED {

  };

  public boolean isActive()
  {
    return false;
  }

  public MessageState toText()
  {
    throw new IllegalStateException(toString());
  }

  public MessageState toBinary()
  {
    Thread.dumpStack();

    throw new IllegalStateException(toString());
  }

  public MessageState toCont()
  {
    Thread.dumpStack();

    throw new IllegalStateException(toString());
  }

  public MessageState toIdle()
  {
    return IDLE;
  }

  public MessageState toFinal()
  {
    Thread.dumpStack();

    throw new IllegalStateException(toString());
  }

  public MessageState toClose()
  {
    Thread.dumpStack();

    throw new IllegalStateException(toString());
  }

  public int code()
  {
    Thread.dumpStack();

    throw new IllegalStateException(toString());
  }
}
