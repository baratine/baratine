/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.profile;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.util.L10N;

/**
 * Resin's bootstrap class.
 */
public class Profile
{
  private static final Logger log = Logger.getLogger(Profile.class.getName());
  private static final L10N L = new L10N(Profile.class);
  
  private ProfilerService _profilerService;
  
  private long _activePeriod = 1000;

  private Profile()
  {
    ServicesAmp manager = ServicesAmp.newManager().start();
    
    ProfileTask task = ProfileTask.create();
    _profilerService = manager.newService(new ProfilerServiceImpl(task))
                              .as(ProfilerService.class);
  }
  
  public static Profile create()
  {
    return new Profile();
  }

  /*
  public int getDepth()
  {
    return _profilerService.getDepth();
  }
  */

  public void setDepth(int depth)
  {
    _profilerService.setDepth(depth);
  }

  /**
   * Period is sampling time.  It does not stop the profiler after period.
   */
  public void setPeriod(long period)
  {
    if (period < 1) {
      throw new ConfigException(L.l("profile period '{0}ms' is too small.  The period must be greater than 10ms.",
                                    period));
    }

    _activePeriod = period;
  }

  /**
   * Sets the background interval. If the background profile hasn't started, start it.
   */
  public void setBackgroundPeriod(long period)
  {
    if (period < 1) {
      throw new ConfigException(L.l("profile period '{0}ms' is too small.  The period must be greater than 10ms.",
                                    period));
    }

    _profilerService.setBackgroundInterval(period, TimeUnit.MILLISECONDS);
  }

  public boolean isActive()
  {
    return _profilerService.isActive();
  }

  public boolean isBackground()
  {
    return _profilerService.isActive();
  }

  public void start()
  {
    _profilerService.start(_activePeriod, TimeUnit.MILLISECONDS);
  }

  public ProfileReport stop()
  {
    return _profilerService.stop();
  }

  public ProfileReport report()
  {
    return _profilerService.report();
  }

  public void reset()
  {
    _profilerService.reset();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
