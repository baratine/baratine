/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.network.port;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.deliver.Deliver;
import com.caucho.v5.amp.deliver.Outbox;
import com.caucho.v5.amp.deliver.QueueDeliverBuilderImpl;
import com.caucho.v5.amp.queue.QueueRingFixed;
import com.caucho.v5.health.meter.ActiveMeter;
import com.caucho.v5.health.meter.MeterService;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.health.shutdown.Shutdown;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.CurrentTime;

/**
 * Represents a protocol connection.
 */
public class PollTcpManagerNio
  extends PollTcpManagerBase implements Runnable
{
  private static final Logger log
    = Logger.getLogger(PollTcpManagerNio.class.getName());

  private static final AtomicReference<PollTcpManagerNio> _nioSelectManager
    = new AtomicReference<PollTcpManagerNio>();
  
  private static final ActiveMeter _keepaliveAsyncMeter
    = MeterService.createActiveMeter("Caucho|Port|Keepalive Poll");
  
  private static int _gId;
  
  private final BlockingQueue<PollController> _registerQueue;
  private final BlockingQueue<PollController> _newRegisterQueue;

  private int _selectMax;
  
  private long _checkInterval = 15000L;

  private Thread _thread;

  private final Selector _selector;

  private final AtomicInteger _connectionCount = new AtomicInteger();

  private final AtomicInteger _activeCount = new AtomicInteger();

  private final Lifecycle _lifecycle = new Lifecycle();

  private PollTcpManagerNio()
  {
    try {
      _selector = Selector.open();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    
    QueueDeliverBuilderImpl builder
      = new QueueDeliverBuilderImpl<>();
    builder.sizeMax(8 * 1024);
    
    _registerQueue = builder.build(new RegisterProcessor());
    _newRegisterQueue = new QueueRingFixed<>(8 * 1024);
  }

  /**
   * Returns a jni select manager.
   */
  public static PollTcpManagerNio create()
  {
    synchronized (_nioSelectManager) {
      if (_nioSelectManager.get() == null) {
        PollTcpManagerNio selectManager = new PollTcpManagerNio();
          
        if (selectManager.start()) {
          _nioSelectManager.set(selectManager);
        }
      }

      return _nioSelectManager.get();
    }
  }

  /**
   * Returns the available keepalive.
   */
  @Override
  public int getFreeKeepalive()
  {
    return _selectMax - _connectionCount.get();
  }

  /**
   * Returns the keepalive count.
   */
  @Override
  public int getSelectCount()
  {
    return _connectionCount.get();
  }

  /**
   * Sets the max select.
   */
  @Override
  public void setSelectMax(int max)
  {
    _selectMax = max;
  }

  /**
   * Sets the max select.
   */
  @Override
  public int pollMax()
  {
    return _selectMax;
  }

  /**
   * Sets the select timeout
   */
  @Override
  public void setSelectTimeout(long timeout)
  {
    // _maxSelectTime = timeout;
  }
  
  /**
   * Returns the check interface.
   */
  public long getCheckInterval()
  {
    return _checkInterval;
  }
  
  /**
   * Sets the check interval.
   */
  public void setCheckInterval(long checkInterval)
  {
    _checkInterval = checkInterval;
  }
  
  public boolean isActive()
  {
    return _lifecycle.isActive();
  }

  /**
   * Starts the manager.
   */
  @Override
  public boolean start()
  {
    if (! _lifecycle.toStarting()) {
      return false;
    }

    log.isLoggable(Level.FINER);

    String name = "resin-nio-select-manager-" + _gId++;
    _thread = new Thread(this, name);
    _thread.setDaemon(true);

    _thread.start();

    _lifecycle.waitForActive(2000);

    if (log.isLoggable(Level.FINER))
      log.finer(this + " active");

    log.fine("Non-blocking keepalive enabled with max sockets = "
             + _selectMax);

    return true;
  }

  /**
   * Adds a keepalive connection.
   *
   * @param conn the connection to register as keepalive
   *
   * @return true if the connection changes to the keepalive state
   */
  @Override
  public PollResult startPoll(PollController conn)
  {
    if (! _lifecycle.isActive()) {
      log.warning(this + " select disabled");

      return PollResult.CLOSED;
    }

    SocketBar socket = conn.getSocket();

    if (socket == null) {
      log.warning(this + " socket empty for " + conn);

      return PollResult.CLOSED;
    }

    SelectableChannel selChannel = socket.selectableChannel();

    if (selChannel == null) {
      log.warning(this + " no channel for " + socket);

      return PollResult.CLOSED;
    }

    _connectionCount.incrementAndGet();

    _activeCount.incrementAndGet();

    _registerQueue.offer(conn);
    
    return PollResult.START;
  }

  private void registerItem(PollController conn)
  {
    try {
      SelectableChannel selChannel = conn.getSocket().selectableChannel();

      SelectionKey key = selChannel.register(_selector, 
                                             SelectionKey.OP_READ,
                                             conn);

      if (key == null) {
        log.warning(this + " selection failed for " + conn);

        return;
      }

      if (! _lifecycle.isActive()) {
        return;
      }

      if (_activeCount.decrementAndGet() == 0 && _lifecycle.isDestroyed()) {
        destroy();
      }

      if (log.isLoggable(Level.FINER)) {
        log.finer(conn + " add keepalive (select fd=" + key + 
                  ",timeout=" + (conn.getIdleExpireTime() - CurrentTime.getCurrentTime()) + "ms)");
      }

      _keepaliveAsyncMeter.start();
    } catch (Exception e) {
      _lifecycle.toError();
      log.log(Level.WARNING, e.toString(), e);
    }
  }
  
  @Override
  public void closePoll(PollController conn)
  {
    removeConnection(conn);
  }

  /**
   * Running process accepting connections.
   */
  @Override
  public void run()
  {
    if (! _lifecycle.toActive()) {
      log.fine(this + " cannot start because an instance is active");
      return;
    }
    
    // Thread thread = Thread.currentThread();
    // int priority = thread.getPriority();
    
    try {
      // thread.setPriority(Thread.MAX_PRIORITY - 1);
      
      runImpl();
    } finally {
      // thread.setPriority(priority);
    }
  }
  
  private void addNewItems()
  {
    PollController conn;
    
    while ((conn = _newRegisterQueue.poll()) != null) {
      registerItem(conn);
    }
  }
  
  private void runImpl()
  {
    log.finer(this + " active");

    int interruptCount = 0;
    int exceptionCount = 0;

    synchronized (_thread) {
      _thread.notify();
    }

    while (_lifecycle.isActive()) {
      try {
        long checkInterval = getCheckInterval();
        long selectWaitTime = 5000L;
        
        if (checkInterval < selectWaitTime && checkInterval > 0) {
          selectWaitTime = checkInterval;
        }
        
        addNewItems();

        int selectCount = _selector.select(selectWaitTime);

        if (selectCount >= 0) {
          Set<SelectionKey> selectedKeys = _selector.selectedKeys();
          
          Iterator<SelectionKey> iter = selectedKeys.iterator();
          
          while (iter.hasNext()) {
            SelectionKey key = iter.next();
            iter.remove();
            
            key.cancel();
            // key.attach(null);
            
            PollController conn = (PollController) key.attachment();
            
            wakeConnection(conn);
          }
        }

        interruptCount = 0;
        exceptionCount = 0;
      } catch (InterruptedIOException e) {
        log.log(Level.FINER, e.toString(), e);

        Thread.interrupted();

        // If there's some sort of terminal exception, throw it
        if (interruptCount++ > 100) {
          log.fine("closing because too many interrupts");

          log.log(Level.FINE, e.toString(), e);
          close();
          break;
        }
      } catch (Throwable e) {
        log.log(Level.FINER, e.toString(), e);

        // If there's some sort of terminal exception, throw it
        if (exceptionCount++ > 100) {
          String msg = "closing because too many JniSelectManager exceptions\n  " + e;
          
          log.log(Level.SEVERE, e.toString(), e);

          Shutdown.shutdownActive(ExitCode.NETWORK, msg);
          break;
        }
      }
    }

    _thread = null;

    stop();

    log.finer(this + " stopped");
  }
  
  private void wakeConnection(PollController conn)
  {
    if (conn != null) {
      _connectionCount.decrementAndGet();
      _keepaliveAsyncMeter.end();
      
      long now = CurrentTime.getCurrentTime();

      if (conn.getIdleExpireTime() < now) {
        if (log.isLoggable(Level.FINER))
          log.finer(conn + " timeout keepalive" +
                    ", " + (now - conn.getIdleExpireTime()) + "ms)");

        conn.onKeepaliveTimeout();
      }
      else {
        if (log.isLoggable(Level.FINER))
          log.finer(conn + " wake keepalive"
                    + ", " + (now - conn.getIdleExpireTime()) + "ms)");

        conn.onPollRead();
      }
    }
  }

  @Override
  public void onPortClose(PortSocket port)
  {
    wakeConnections(port);
  }
  
  /**
   * Closing the manager.
   */
  @Override
  public boolean stop()
  {
    if (! _lifecycle.toStopping())
      return false;

    log.finest(this + " stopping");

    closeConnections();

    destroy();

    return true;
  }
  
  private void closeConnections()
  {
    wakeConnections(null);
  }
  
  private void wakeConnections(PortSocket port)
  {
    for (SelectionKey key : _selector.keys()) {
      PollController conn = (PollController) key.attachment();
      
      if (conn != null && (port == null || conn.getPort() == port)) {
        key.cancel();
      
        remove(conn);

        try {
          conn.onPollReadClose();
        } catch (Throwable e) {
          log.log(Level.WARNING, e.toString(), e);
        }
      }
    }
  }
  
  private boolean removeConnection(PollController conn)
  {
    if (conn == null) {
      return false;
    }
    
    SocketBar socket = conn.getSocket();
    SelectableChannel channel = socket.selectableChannel();
    
    SelectionKey key = channel.keyFor(_selector);
    
    if (key != null) {
      key.cancel();
    }
    
    remove(conn);
        
    return true;
  }

  /**
   * Removes the connection from the selection.
   */
  private void remove(PollController conn)
  {
    if (conn == null) {
      return;
    }

    if (_lifecycle.isDestroyed()) {
      return;
    }

    _activeCount.incrementAndGet();

    if (_activeCount.decrementAndGet() == 0 && _lifecycle.isDestroyed()) {
      destroy();
    }
  }

  private void destroy()
  {
  }

  @Override
  protected void finalize()
  {
    close();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[max=" + _selectMax + "]";
  }
  
  private class RegisterProcessor
    implements Deliver<PollController>
  {
    @Override
    public void deliver(PollController item, Outbox outbox)
    {
      _newRegisterQueue.offer(item);
    }
    
    @Override
    public void afterBatch()
    {
      _selector.wakeup();
    }
  }
}
