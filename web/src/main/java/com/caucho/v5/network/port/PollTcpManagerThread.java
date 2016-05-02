/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.network.port;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.health.meter.ActiveMeter;
import com.caucho.v5.health.meter.MeterService;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.CurrentTime;

/**
 * poll manager that spawns a thread
 */
public class PollTcpManagerThread
  extends PollTcpManagerBase
{
  private static final Logger log
    = Logger.getLogger(PollTcpManagerThread.class.getName());
  
  private static final ActiveMeter _keepaliveAsyncMeter
    = MeterService.createActiveMeter("Caucho|Port|Keepalive Poll");
  
  private final ThreadPool _threadPool;
  
  //private final Executor _executor;

  private int _selectMax;
  private long _maxSelectTime = 60000L;
  
  private long _checkInterval = 15000L;
  
  // private final ServiceQueue<KeepaliveConnection> _keepaliveQueue;

  private final AtomicInteger _connectionCount = new AtomicInteger();

  private final Lifecycle _lifecycle = new Lifecycle();

  public PollTcpManagerThread()
  {
    _threadPool = ThreadPool.current();
    
    _selectMax = _threadPool.getThreadMax() / 2;
    
    //_executor = _threadPool; // XXX: s/b a throttle
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
    _maxSelectTime = timeout;
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
    if (! _lifecycle.toActive()) {
      return false;
    }

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
    else if (_selectMax <= _connectionCount.get()) {
      log.warning(this + " keepalive overflow "
                  + _connectionCount + " max=" + _selectMax);
      System.out.println("OVERFLOW:");
      
      return PollResult.CLOSED;
    }
    
    if (! conn.toKeepaliveStart()) {
      return PollResult.CLOSED;
    }

    return poll(conn);
  }
  
  private PollResult poll(PollController conn)
  {
    try {
      long expireTime = conn.getIdleExpireTime();
      int result;
      
      long timeout = expireTime - CurrentTime.currentTime();
      
      timeout = Math.max(timeout, 0);
      
      result = conn.fillWithTimeout(timeout);

      if (result > 0) {
        System.out.println("RES: " + result);
        return PollResult.DATA;
      }
      else if (expireTime <= CurrentTime.currentTime()) {
        log.finer("timeout " + conn);
      }
      else {
        log.fine("close-read " + conn);
      }
    } catch (IOException e) {
      log.log(Level.FINER, e.toString(), e);
    } finally {
      _connectionCount.decrementAndGet();
    }
    
    return PollResult.CLOSED;
  }

  @Override
  public void closePoll(PollController conn)
  {
    // removeConnection(conn);
  }

  @Override
  public void onPortClose(PortSocket port)
  {
    // wakeConnections(port);
  }
  
  /**
   * Closing the manager.
   */
  @Override
  public boolean stop()
  {
    if (! _lifecycle.toStopping()) {
      return false;
    }

    log.finest(this + " stopping");

    // closeConnections();

    destroy();

    return true;
  }

  private void destroy()
  {
    _lifecycle.toDestroy();
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
}
