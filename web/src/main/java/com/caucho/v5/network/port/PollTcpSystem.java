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

package com.caucho.v5.network.port;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;

import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.jni.SelectManagerFactoryJni;


/**
 * System for managing connections that can wait for data async.
 */
public class PollTcpSystem
{
  private final Executor _executor;
  private final PollTcpManager _pollManager;
  private final SocketSystem _socketSystem;

  public PollTcpSystem()
  {
    this(null);
  }

  public PollTcpSystem(PollTcpManager selectManager)
  {
    _socketSystem = SocketSystem.current();
    _executor = ThreadPool.current();

    if (selectManager == null && _socketSystem.isJni()) {
      selectManager = SelectManagerFactoryJni.createJni();
    }

    if (selectManager == null) {
      selectManager = new PollTcpManagerThread();
    }

    _pollManager = selectManager;
    _pollManager.start();
  }

  public PollContext createContext(Runnable task)
  {
    return new PollContext(_socketSystem, task, _executor, _pollManager);
  }

  public void connect(PollContext cxt,
                      InetSocketAddress address)
    throws IOException
  {
    Objects.requireNonNull(cxt);
    
    long timeout = -1;
    boolean isSSL = false;

    SocketBar s = _socketSystem.connect(cxt.getSocket(),
                                        address,
                                        null,
                                        timeout,
                                        isSSL);

    cxt.init(s);
  }

  public void connectUnix(PollContext cxt, Path path)
    throws IOException
  {
    /*
    SocketBar s = _socketSystem.connectUnix(cxt.getSocket(), path);

    cxt.init(s);
    */
  }
}
