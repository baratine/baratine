/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.profile;

import java.util.concurrent.TimeUnit;


/**
 * Service managing the profiler.
 */
public class ProfilerServiceImpl
{
  private final ProfileTask _profileTask;
  
  private long _backgroundPeriod;
  private StateProfile _state = StateProfile.IDLE;
  
  ProfilerServiceImpl(ProfileTask profileTask)
  {
    _profileTask = profileTask;
  }
  
  /**
   * Sets the time interval for the background profile.
   */
  public void setBackgroundInterval(long interval, TimeUnit unit)
  {
    if (! isValid()) {
      return;
    }
    
    long period = unit.toMillis(interval);
    
    if (_state == StateProfile.IDLE) {
      if (period > 0) {
        _profileTask.setPeriod(period);
        _profileTask.start();
        
        _state = StateProfile.BACKGROUND;
      }
    }
    else if (_state == StateProfile.BACKGROUND) {
      if (period <= 0) {
        _profileTask.stop();
        
        _state = StateProfile.IDLE;
      }
      else if (period != _backgroundPeriod) {
        _profileTask.stop();
        _profileTask.setPeriod(period);
        _profileTask.start();
      }
    }
    
    _backgroundPeriod = period;
  }
  
  public void setDepth(int depth)
  {
    if (! isValid()) {
      return;
    }
    
    _profileTask.setDepth(depth);
  }
  
  public boolean isActive()
  {
    return _state == StateProfile.ACTIVE;
  }
  
  public boolean isBackground()
  {
    return _state == StateProfile.BACKGROUND;
  }
  
  public void reset()
  {
    if (! isValid()) {
      return;
    }
    
    _profileTask.stop();
    
    if (_state != StateProfile.IDLE) {
      _profileTask.start();
    }
  }
  
  public ProfileReport report()
  {
    if (! isValid()) {
      return null;
    }
    
    return _profileTask.getReport();
  }
  
  private boolean isValid()
  {
    return _profileTask != null;
  }
  
  /**
   * Starts a dedicated profile.
   */
  public void start(long interval, TimeUnit unit)
  {
    if (! isValid()) {
      return;
    }
    
    long period = unit.toMillis(interval);
    
    if (period < 0) {
      return;
    }
    
    _profileTask.stop();
    _profileTask.setPeriod(period);
    _profileTask.start();
    
    _state = StateProfile.ACTIVE;
  }
  
  /**
   * Ends the dedicated profile, restarting the background.
   */
  public ProfileReport stop()
  {
    if (! isValid()) {
      return null;
    }
    
    if (_state != StateProfile.ACTIVE) {
      return null;
    }
    
    _profileTask.stop();
    
    ProfileReport report = _profileTask.getReport();
    
    if (_backgroundPeriod > 0) {
      _profileTask.setPeriod(_backgroundPeriod);
      _profileTask.start();
      _state = StateProfile.BACKGROUND;
    }
    else {
      _state = StateProfile.IDLE;
    }
    
    return report;
  }
  
  private enum StateProfile {
    IDLE,
    BACKGROUND,
    ACTIVE;
  }
}
