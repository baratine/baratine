/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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
package com.caucho.v5.config.types;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.util.Date;
import java.util.regex.Pattern;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.util.L10N;

/**
 * Implements a unix cron-style trigger.
 */
public class CronType implements Trigger
{
  private static final L10N L = new L10N(CronType.class);

  private String _text;

  private boolean[] _minutes;
  private boolean[] _hours;
  private boolean[] _days;
  private boolean[] _months;
  private boolean[] _daysOfWeek;

  public CronType()
  {
  }

  public CronType(String cron)
  {
    addText(cron);
  }

  /**
   * Creates new cron trigger.
   * 
   * @param second
   *          Second expression.
   * @param minute
   *          Minute expression.
   * @param hour
   *          Hour expression.
   * @param dayOfWeek
   *          Day of week expression.
   * @param dayOfMonth
   *          Day of month expression.
   * @param month
   *          Month expression.
   * @param year
   *          Year expression.
   * @param start
   *          Schedule start date.
   * @param end
   *          Schedule end date.
   */
  public CronType(String second, String minute, String hour,
                  String dayOfWeek, String dayOfMonth,
                  String month, String year, Date start, Date end)
  {
    _text = String.format("%s %s %s %s %s %s %s", second, minute, hour,
                          dayOfWeek, dayOfMonth, month, year);

    _minutes = parseRange(minute, 0, 59);
    _hours = parseRange(hour, 0, 23);

    _daysOfWeek = parseRange(dayOfWeek, 0, 7);

    if (_daysOfWeek[7]) {
      _daysOfWeek[0] = _daysOfWeek[7];
    }

    _days = parseRange(dayOfMonth, 1, 31);
    _months = parseRange(month, 1, 12);
  }

  /**
   * Sets the text.
   */
  public void addText(String text) throws ConfigException
  {
    text = text.trim();
    _text = text;

    String[] split = Pattern.compile("\\s+").split(text);

    if (split.length > 0)
      _minutes = parseRange(split[0], 0, 59);

    if (split.length > 1)
      _hours = parseRange(split[1], 0, 23);
    else
      _hours = parseRange("*", 0, 23);

    if (split.length > 2)
      _days = parseRange(split[2], 1, 31);

    if (split.length > 3)
      _months = parseRange(split[3], 1, 12);

    if (split.length > 4) {
      _daysOfWeek = parseRange(split[4], 0, 7);
      
      if (_daysOfWeek[7])
        _daysOfWeek[0] = _daysOfWeek[7];
    }
  }

  /**
   * parses a range, following cron rules.
   */
  private boolean[] parseRange(String range, int rangeMin, int rangeMax)
      throws ConfigException
  {
    boolean[] values = new boolean[rangeMax + 1];

    int j = 0;
    while (j < range.length()) {
      char ch = range.charAt(j);

      int min = 0;
      int max = 0;
      int step = 1;

      if (ch == '*') {
        min = rangeMin;
        max = rangeMax;
        j++;
      } else if ('0' <= ch && ch <= '9') {
        for (; j < range.length() && '0' <= (ch = range.charAt(j)) && ch <= '9'; j++) {
          min = 10 * min + ch - '0';
        }

        if (j < range.length() && ch == '-') {
          for (j++; j < range.length() && '0' <= (ch = range.charAt(j))
              && ch <= '9'; j++) {
            max = 10 * max + ch - '0';
          }
        } else
          max = min;
      } else
        throw new ConfigException(L.l("'{0}' is an illegal cron range", range));

      if (min < rangeMin)
        throw new ConfigException(L.l(
            "'{0}' is an illegal cron range (min value is too small)", range));
      else if (rangeMax < max)
        throw new ConfigException(L.l(
            "'{0}' is an illegal cron range (max value is too large)", range));

      if (j < range.length() && (ch = range.charAt(j)) == '/') {
        step = 0;

        for (j++; j < range.length() && '0' <= (ch = range.charAt(j))
            && ch <= '9'; j++) {
          step = 10 * step + ch - '0';
        }

        if (step == 0)
          throw new ConfigException(L
              .l("'{0}' is an illegal cron range", range));
      }

      if (range.length() <= j) {
      } else if (ch == ',')
        j++;
      else {
        throw new ConfigException(L.l("'{0}' is an illegal cron range", range));
      }

      for (; min <= max; min += step)
        values[min] = true;
    }

    return values;
  }

  @Override
  public long nextTime(long now)
  {
    long time = now + 60000 - now % 60000;
    
    LocalDateTime cal;
    cal = LocalDateTime.ofEpochSecond(time / 1000, 0, ZoneOffset.UTC);

    int minute = nextInterval(_minutes, cal.getMinute());

    if (minute < 0) {
      minute = nextInterval(_minutes, 0);

      cal.withHour(cal.getHour() + 1);
    }

    int hour = nextInterval(_hours, cal.getHour());
    if (hour < 0) {
      hour = nextInterval(_hours, 0);
      minute = nextInterval(_minutes, 0);

      cal.withDayOfMonth(cal.getDayOfMonth() + 1);
    }

    int day = cal.getDayOfMonth();

    if (_days != null) {
      day = nextInterval(_days, cal.getDayOfMonth());

      if (day < 0) {
        cal.withMonth(cal.getMonthValue() + 1);
        cal.withDayOfMonth(1);

        day = nextInterval(_days, cal.getDayOfMonth());
        hour = nextInterval(_hours, 0);
        minute = nextInterval(_minutes, 0);
      }
    }

    if (_daysOfWeek != null) {
      int oldDayOfWeek = cal.get(ChronoField.DAY_OF_WEEK) - 1;
      int dayOfWeek = nextInterval(_daysOfWeek, oldDayOfWeek);

      if (dayOfWeek >= 0) {
        day += (dayOfWeek - oldDayOfWeek) % 7;
      } else {
        dayOfWeek = nextInterval(_daysOfWeek, 0);

        day += (dayOfWeek - oldDayOfWeek + 7) % 7;
      }
    }

    int month = cal.getMonthValue();
    int year = (int) cal.getYear();

    long nextTime = nextTime(year, month, day, hour, minute);

    if (now < nextTime)
      return nextTime;
    else
      return nextTime(now + 3600000L); // DST
  }

  private long nextTime(int year, int month, int day, int hour, int minute)
  {
    LocalDateTime dateTime = LocalDateTime.of(year, month, day, hour, minute);
    /*
    //QDate cal = allocateCalendar();

    cal.setLocalTime(0);

    cal.setYear(year);
    cal.setMonth(month);
    cal.setDayOfMonth(day);
    cal.setHour(hour);
    cal.setMinute(minute);

    long time = cal.getGMTTime();

    freeCalendar(cal);
    */

    return dateTime.toEpochSecond(ZoneOffset.UTC) * 1000;
  }

  public int nextInterval(boolean[] values, int now)
  {
    for (; now < values.length; now++) {
      if (values[now])
        return now;
    }

    return -1;
  }

  public long prevTime(long now)
  {
    long time = now + 60000 - now % 60000;
    
    LocalDateTime cal
      = LocalDateTime.ofEpochSecond(time / 1000, 0, ZoneOffset.UTC);

    //cal.setGMTTime(time);

    int minute = prevInterval(_minutes, cal.getMinute());

    if (minute < 0) {
      minute = prevInterval(_minutes, _minutes.length - 1);

      cal.withHour(cal.getHour() - 1);
    }

    int hour = prevInterval(_hours, cal.getHour());
    if (hour < 0) {
      hour = prevInterval(_hours, _hours.length - 1);
      minute = prevInterval(_minutes, _minutes.length - 1);

      cal.withDayOfMonth(cal.getDayOfMonth() - 1);
    }

    int day = cal.getDayOfMonth();

    if (_days != null) {
      day = prevInterval(_days, cal.getDayOfMonth());

      if (day < 0) {
        cal.withDayOfMonth(0);

        day = prevInterval(_days, cal.getDayOfMonth());
        hour = prevInterval(_hours, _hours.length - 1);
        minute = prevInterval(_minutes, _minutes.length - 1);
      }
    }

    if (_daysOfWeek != null) {
      int oldDayOfWeek = cal.get(ChronoField.DAY_OF_WEEK) - 1;
      int dayOfWeek = prevInterval(_daysOfWeek, oldDayOfWeek);

      if (dayOfWeek >= 0) {
        day += (dayOfWeek - oldDayOfWeek);
      } else {
        dayOfWeek = prevInterval(_daysOfWeek, _daysOfWeek.length - 1);

        day += (dayOfWeek - oldDayOfWeek + 7);
      }
    }

    int month = cal.getMonthValue();
    int year = (int) cal.getYear();

    long prevTime = prevTime(year, month, day, hour, minute);

    return prevTime;
  }

  private long prevTime(int year, int month, int day, int hour, int minute)
  {
    LocalDateTime time = LocalDateTime.of(year, month, day, hour, minute);
    
    /*
    Instant instant = Instant.QDate cal = allocateCalendar();
    
    cal.setLocalTime(0);

    cal.setYear(year);
    cal.setMonth(month);
    cal.setDayOfMonth(day);
    cal.setHour(hour);
    cal.setMinute(minute);

    long time = cal.getGMTTime();

    freeCalendar(cal);
    */

    return time.toEpochSecond(ZoneOffset.UTC);
  }

  public int prevInterval(boolean[] values, int now)
  {
    for (; now >= 0; now--) {
      if (values[now])
        return now;
    }

    return -1;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _text + "]";
  }
}
