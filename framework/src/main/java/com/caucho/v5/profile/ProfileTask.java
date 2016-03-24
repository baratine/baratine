/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.profile;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.jni.JniTroubleshoot;
import com.caucho.v5.jni.JniUtil;
import com.caucho.v5.jni.JniUtil.JniLoad;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * Resin's bootstrap class.
 */
class ProfileTask
{
  private static final Logger log = Logger.getLogger(ProfileTask.class.getName());
  private static final L10N L = new L10N(ProfileTask.class);

  private static final JniTroubleshoot _jniTroubleshoot;

  private static final ProfileTask _task;
  
  private long _jniProfile;
  
  private AtomicReference<ProfileThread> _profileThread
    = new AtomicReference<>();
    
  private volatile long _period = 1000L;
  private volatile long _ticks;
    
  private int _maxDepth = 32;
  
  private long _startTime;
  private long _endTime;
  
  private long _gcStartTime;
  private long _gcEndTime;
  
  //private MemoryMXBean _memoryBean;
  private MemoryUtil _memoryBean;

  private ProfileTask()
  {
    try {
      _jniTroubleshoot.checkIsValid();

      synchronized (ProfileTask.class) {
        if (_jniProfile == 0) {
          _jniProfile = nativeCreateProfile(16 * 1024);
        }
      }

      _memoryBean = MemoryUtil.create();
    } catch (Throwable e) {
      log.finer(e.toString());
      log.log(Level.FINEST, e.toString(), e);
    }
  }
  
  static ProfileTask create()
  {
    return _task;
  }
  
  boolean isValid()
  {
    return _jniProfile != 0;
  }


  public int getDepth()
  {
    return _maxDepth;
  }

  public void setDepth(int depth)
  {
    _maxDepth = depth;
  }

  /**
   * Period is sampling time.  It does not stop the profiler after period.
   */
  public void setPeriod(long period)
  {
    if (period < 1)
      throw new ConfigException(L.l("profile period '{0}ms' is too small.  The period must be greater than 1ms.",
                                    period));

    _period = period;
  }

  public long getPeriod()
  {
    return _period;
  }

  public boolean isActive()
  {
    return _profileThread.get() != null;
  }

  public void start()
  {
    synchronized (ProfileTask.class) {
      ProfileThread profileThread = new ProfileThread();
      
      if (! _profileThread.compareAndSet(null, profileThread)) {
        return;
      }
      
      _endTime = 0;
      _startTime = CurrentTime.currentTime();

      try {
        nativeClear(_jniProfile);

        profileThread.start();
      } catch (Exception e) {
        log.log(Level.WARNING, e.toString(), e);
      }
    }
  }

  public void stop()
  {
    ProfileThread profileThread = _profileThread.getAndSet(null);
    
    if (profileThread != null)
      LockSupport.unpark(profileThread);
    
    _endTime = CurrentTime.currentTime();
  }

  public long getTicks()
  {
    return _ticks;
  }

  public long getRunTime()
  {
    return _ticks * _period;
  }
  
  public long getEndTime()
  {
    if (isActive())
      return CurrentTime.currentTime();
    else
      return _endTime;
  }
  
  public long getStartTime()
  {
    return _startTime;
  }
  
  public long getGcTime()
  {
    if (_memoryBean == null)
      return 0;
    
    if (_gcStartTime == 0) {
      return 0;
    }
    else if (_gcStartTime < _gcEndTime) {
      return _gcEndTime - _gcStartTime;
    }
    else {
      return _memoryBean.getGarbageCollectionTime() - _gcStartTime; 
    }
  }

  public ProfileReport getReport()
  {
    ProfileEntry []entries = getResults();
    
    return new ProfileReport(entries,
                             getStartTime(),
                             getEndTime(),
                             _period,
                             _ticks, 
                             getGcTime());
  }
  
  public ProfileEntry[]getResults()
  {
    ProfileEntry []rawResults;
    
    synchronized (ProfileTask.class) {
      rawResults = nativeDisplay(_jniProfile);
    }
    
    if (rawResults == null) {
      return null;
    }
    
    long gcTime = getGcTime();
    
    if (gcTime == 0)
      return rawResults;
    
    ProfileEntry []results = new ProfileEntry[rawResults.length + 1];
    System.arraycopy(rawResults, 0, results, 0, rawResults.length);
    
    ProfileEntry gcResult = new ProfileEntry(ProfileEntry.THREAD_RUNNABLE,
                                                   (gcTime + _period - 1) / _period,
                                                   1);
    
    gcResult.addStack("jvm.HeapMemory", "gc", "");
    
    results[results.length - 1] = gcResult;
    
    Arrays.sort(results, new TickComparator());

    return results;
  }

  /*
  public void doNative()
  {
    synchronized (Profile.class) {
      _ticks++;
      nativeProfile(_jniProfile, _maxDepth);
    }
  }
  */

  class ProfileThread extends Thread {
    ProfileThread()
    {
      super("profile-thread");
    }
    
    @Override
    public void run()
    {
      try {
        long period = _period;

        long startRun = System.currentTimeMillis();
        long startSample = startRun;

        _ticks = 0;
        
        _gcStartTime = 0;
        _gcEndTime = 0;
        
        if (_memoryBean != null) {
          _gcStartTime = _memoryBean.getGarbageCollectionTime();
        }

        while (_profileThread.get() == this) {
          long expires = startSample + _period;

          synchronized (ProfileTask.class) {
            _ticks++;

            nativeProfile(_jniProfile, _maxDepth);
          }

          long endSample = System.currentTimeMillis();

          if (endSample < expires) {
            expires = expires - expires % period;

            while (endSample < expires) {
              Thread.interrupted();
              LockSupport.parkUntil(expires);

              endSample = System.currentTimeMillis();
            }
          }

          startSample = endSample;
        }

        long time = System.currentTimeMillis() - startRun;
        long expectedTicks = time / period;
        
        if (_memoryBean != null)
          _gcEndTime = _memoryBean.getGarbageCollectionTime();
        
        log.info(this + " complete: " + time / 1000 + "s, "
                 + " period=" + period + "ms,"
                 + " gc-time=" + (_gcEndTime - _gcStartTime) + "ms,"
                 + " missed ticks=" + (expectedTicks - _ticks)
                 + String.format(" (%.2f%%),",
                                 100.0 * (expectedTicks - _ticks) / _ticks)
                                 + " total ticks=" + _ticks);
      } catch (Exception e) {
        log.log(Level.WARNING, e.toString(), e);
      } finally {
        _profileThread.compareAndSet(this, null);
      }
    }
  }

  private static native long nativeCreateProfile(int size);

  private static native boolean nativeProfile(long profile, int maxDepth);

  private static native void nativeClear(long profile);

  private static native ProfileEntry []nativeDisplay(long profile);

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  static class TickComparator implements Comparator<ProfileEntry> {
    public int compare(ProfileEntry a, ProfileEntry b)
    {
      return (int) (b.getCount() - a.getCount());
    }
    
  }

  static {
    _jniTroubleshoot
    = JniUtil.load(ProfileTask.class,
                   new JniLoad() { 
                     public void load(String path) { 
                       System.load(path); 
                     }},
                   "baratine");

    ProfileTask task = new ProfileTask();
    
    if (task.isValid()) {
      _task = task;
    }
    else {
      _task = null;
    }
  }
}
