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
public interface ProfilerService
{
  /**
   * Sets the time interval for the background profile.
   */
  void setBackgroundInterval(long time, TimeUnit unit);
  
  /**
   * Sets the stack depth
   */
  void setDepth(int depth);
  
  void reset();
  
  ProfileReport report();

  /**
   * Returns true if the service has a dedicated profile started.
   */
  boolean isActive();

  /**
   * Returns true if the service has a background service.
   */
  boolean isBackground();
  
  /**
   * Starts a dedicated profile.
   */
  void start(long time, TimeUnit unit);
  
  /**
   * Ends the dedicated profile, restarting the background.
   */
  ProfileReport stop();
}
