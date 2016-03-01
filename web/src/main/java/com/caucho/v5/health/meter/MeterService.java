/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
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

package com.caucho.v5.health.meter;

import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.subsystem.SubSystemBase;

//TODO: service cleanup
public class MeterService extends SubSystemBase {
  private static MeterService _manager = new MeterService();

  private final ConcurrentHashMap<String,MeterBase> _meterMap
    = new ConcurrentHashMap<>();
  
  protected MeterService()
  {
  }

  protected void setManager(MeterService manager)
  {
    if (manager == null)
      manager = new MeterService();
    else {
      manager._meterMap.putAll(_meterMap);
    }

    _manager = manager;
  }

  public static MeterService getCurrent()
  {
    return _manager;
  }

  public static MeterService create()
  {
    return _manager;
  }

  public static MeterBase getMeter(String name)
  {
    return create().getMeterImpl(name);
  }

  private MeterBase getMeterImpl(String name)
  {
    return _meterMap.get(name);
  }

  public static AverageTimeMeter createAverageTimeMeter(String name)
  {
    return create().createAverageTimeMeterImpl(name);
  }

  private AverageTimeMeter createAverageTimeMeterImpl(String name)
  {
    MeterBase meter = _meterMap.get(name);

    if (meter == null) {
      meter = createMeter(new AverageTimeMeter(name));
    }

    return (AverageTimeMeter) meter;
  }

  public static SampleCountMeter createSampleCountMeter(String name)
  {
    return create().createSampleCountMeterImpl(name);
  }

  private SampleCountMeter createSampleCountMeterImpl(String name)
  {
    MeterBase meter = _meterMap.get(name);

    if (meter == null) {
      meter = createMeter(new SampleCountMeter(name));
    }

    return (SampleCountMeter) meter;
  }

  public static CountMeter createCountMeter(String name)
  {
    return create().createCountMeterImpl(name);
  }

  private CountMeter createCountMeterImpl(String name)
  {
    MeterBase meter = _meterMap.get(name);

    if (meter == null) {
      meter = createMeter(new CountMeter(name));
    }

    return (CountMeter) meter;
  }

  public static MeterBase createJmx(String name,
                                        String objectName,
                                        String attribute)
  {
    return createJmx(name, objectName, attribute, false);
  }

  public static MeterBase createJmx(String name,
                                        String objectName,
                                        String attribute,
                                        boolean isOptional)
  {
    return create().createJmxImpl(name, objectName, attribute, isOptional);
  }


  private MeterBase createJmxImpl(String name, 
                                      String objectName,
                                      String attribute,
                                      boolean isOptional)
  {
    MeterBase meter = _meterMap.get(name);

    /*
    if (meter == null) {
      meter = createMeter(new JmxAttributeMeter(name,
                                                objectName,
                                                attribute,
                                                isOptional));
    }
    */

    return meter;
  }

  public static MeterBase createJmxDelta(String name,
                                             String objectName,
                                             String attribute)
  {
    return createJmxDelta(name, objectName, attribute, false);
  }

  public static MeterBase createJmxDelta(String name,
                                             String objectName,
                                             String attribute,
                                             boolean isOptional)
  {
    return create().createJmxDeltaImpl(name, objectName, attribute, isOptional);
  }

  private MeterBase createJmxDeltaImpl(String name,
                                           String objectName,
                                           String attribute,
                                           boolean isOptional)
  {
    MeterBase meter = _meterMap.get(name);

    /*
    if (meter == null) {
      meter = createMeter(new JmxDeltaMeter(name,
                                            objectName,
                                            attribute,
                                            isOptional));
    }
    */

    return meter;
  }

  /*
  public static MeterBase createJmxCalculation(String name, JmxExpr expr)
  {
    return create().createJmxCalculationImpl(name, expr);
  }

  private MeterBase createJmxCalculationImpl(String name, JmxExpr expr)
  {
    MeterBase meter = _meterMap.get(name);

    if (meter == null) {
      meter = createMeter(new JmxCalculationMeterImpl(name, expr));
    }

    return meter;
  }
  */

  public static TimeMeter createTimeMeter(String name)
  {
    return create().createTimeMeterImpl(name);
  }

  private TimeMeter createTimeMeterImpl(String name)
  {
    MeterBase meter = _meterMap.get(name);

    if (meter == null) {
      meter = createMeter(new TimeMeter(name));
    }

    return (TimeMeter) meter;
  }

  public static TimeRangeMeter createTimeRangeMeter(String baseName)
  {
    return create().createTimeRangeMeterImpl(baseName);
  }

  private TimeRangeMeter createTimeRangeMeterImpl(String baseName)
  {
    String timeName = baseName + " Time";

    MeterBase meter = _meterMap.get(timeName);

    if (meter == null) {
      meter = createMeter(new TimeRangeMeter(timeName));

      TimeRangeMeter timeRangeMeter = (TimeRangeMeter) meter;

      String countName = baseName + " Count";
      createMeter(timeRangeMeter.createCount(countName));

      String maxName = baseName + " Max";
      createMeter(timeRangeMeter.createMax(maxName));
    }

    return (TimeRangeMeter) meter;
  }

  public static MeterAverage createAverageMeter(String name, String type)
  {
    return create().createAverageMeterImpl(name, type);
  }

  private MeterAverage createAverageMeterImpl(String baseName, String type)
  {
    String name;

    if (! "".equals(type))
      name = baseName + " " + type;
    else
      name = baseName;

    MeterBase meter = _meterMap.get(name);

    if (meter == null) {
      meter = createMeter(new MeterAverage(name));

      MeterAverage averageMeter = (MeterAverage) meter;

      String countName = baseName + " Count";
      createMeter(averageMeter.createCount(countName));

      String sigmaName = name + " 95%";
      createMeter(averageMeter.createSigma(sigmaName, 3));

      String maxName = name + " Max";
      createMeter(averageMeter.createMax(maxName));
    }

    return (MeterAverage) meter;
  }

  public static MeterActiveTime createActiveTimeMeter(String name)
  {
    return create().createActiveTimeMeterImpl(name, "Time", null);
  }

  public static MeterActiveTime createActiveTimeMeter(String name,
                                                      String type,
                                                      String subName)
  {
    return create().createActiveTimeMeterImpl(name, type, subName);
  }

  private MeterActiveTime
    createActiveTimeMeterImpl(String baseName,
                              String type,
                              String subName)
  {
    if (subName == null || subName.equals(""))
      subName = "";
    else if (! subName.startsWith("|"))
      subName = "|" + subName;

    String name = baseName + " " + type + subName;

    MeterBase meter = _meterMap.get(name);

    if (meter == null) {
      meter = createMeter(new MeterActiveTime(name));

      MeterActiveTime activeTimeMeter = (MeterActiveTime) meter;

      /*
      String activeCountName = baseName + " Active" + subName;
      createMeter(activeTimeMeter.createActiveCount(activeCountName));
      */

      String sigmaName = baseName + " " + type + " 95%" + subName;
      createMeter(activeTimeMeter.createSigma(sigmaName, 3));

      String maxName = baseName + " " + type + " Max" + subName;
      createMeter(activeTimeMeter.createMax(maxName));

      String activeMaxName = baseName + " Active" + subName;
      createMeter(activeTimeMeter.createActiveCountMax(activeMaxName));

      String totalCountName = baseName + " Count" + subName;
      createMeter(activeTimeMeter.createTotalCount(totalCountName));
    }

    return (MeterActiveTime) meter;
  }

  /**
   * An ActiveMeter counts the number of an active resource, e.g. the
   * number of active connections.
   */
  public static ActiveMeter createActiveMeter(String name)
  {
    return create().createActiveMeterImpl(name, null);
  }

  public static ActiveMeter createActiveMeter(String name,
                                              String subName)
  {
    return _manager.createActiveMeterImpl(name, subName);
  }

  private ActiveMeter
    createActiveMeterImpl(String baseName,
                          String subName)
  {
    if (subName == null || subName.equals("")) {
      subName = "";
    }
    else if (! subName.startsWith("|")) {
      subName = "|" + subName;
    }

    String name = baseName + " Count" + subName;

    MeterBase meter = _meterMap.get(name);

    if (meter == null) {
      meter = createMeter(new ActiveMeter(name));

      ActiveMeter activeMeter = (ActiveMeter) meter;

      String maxName = baseName + " Active" + subName;
      createMeter(activeMeter.createMax(maxName));

      /*
      String totalName = baseName + " Total" + subName;
      createMeter(activeMeter.createTotal(totalName));
      */
    }

    return (ActiveMeter) meter;
  }

  public static SemaphoreMeter createSimpleSemaphoreMeter(String name)
  {
    return create().createSemaphoreMeterImpl(name, false);
  }

  /**
   * Creates a semaphore meter and generate Count, Min, and Max meter.
   */
  public static SemaphoreMeter createSemaphoreMeter(String name)
  {
    return create().createSemaphoreMeterImpl(name, true);
  }

  private SemaphoreMeter createSemaphoreMeterImpl(String baseName,
                                                  boolean isExtended)
  {
    String name = baseName;

    MeterBase meter = _meterMap.get(name);

    if (meter == null) {
      meter = createMeter(new SemaphoreMeter(name));
    }

    SemaphoreMeter semaphoreMeter = (SemaphoreMeter) meter;

    if (! isExtended)
      return semaphoreMeter;

    String countName = baseName + " Acquire";
    createMeter(semaphoreMeter.createCount(countName));

    String maxName = name + " Max";
    createMeter(semaphoreMeter.createMax(maxName));

    String minName = name + " Min";
    createMeter(semaphoreMeter.createMin(minName));

    return (SemaphoreMeter) meter;
  }

  public MeterBase createMeter(MeterBase newMeter)
  {
    MeterBase meter = _meterMap.putIfAbsent(newMeter.getName(), newMeter);

    if (meter != null) {
      return meter;
    }
    else {
      registerMeter(newMeter);

      return newMeter;
    }
  }

  protected void registerMeter(MeterBase meter)
  {
  }
}
