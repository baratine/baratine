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

package com.caucho.v5.util;

import com.caucho.v5.vfs.WriteStream;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Date object
 */
public class QDate {
  private static final Logger log
    = Logger.getLogger(QDate.class.getName());

  static final public int YEAR = 0;
  static final public int MONTH = YEAR + 1;
  static final public int DAY_OF_MONTH = MONTH + 1;
  static final public int DAY = DAY_OF_MONTH + 1;
  static final public int DAY_OF_WEEK = DAY + 1;
  static final public int HOUR = DAY_OF_WEEK + 1;
  static final public int MINUTE = HOUR + 1;
  static final public int SECOND = MINUTE + 1;
  static final public int MILLISECOND = SECOND + 1;
  static final public int TIME = MILLISECOND + 1;
  static final public int TIME_ZONE = TIME + 1;

  static final long MS_PER_DAY = 24 * 60 * 60 * 1000L;
  static final long MS_PER_EON = MS_PER_DAY * (365 * 400 + 100 - 3);

  static final int []DAYS_IN_MONTH = {
    31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
  };

  static final String []DAY_NAMES = {
    "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
  };
  static final String []MONTH_NAMES = {
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
  };

  private static final String []SHORT_WEEKDAY = {
    "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
  };
  private static final String []LONG_WEEKDAY = {
    "Sunday", "Monday", "Tuesday", "Wednesday",
    "Thursday", "Friday", "Saturday"
  };
  private static final String []SHORT_MONTH = {
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
  };
  private static final String []LONG_MONTH = {
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
  };

  private static TimeZone _localTimeZone = TimeZone.getDefault();
  private static TimeZone _gmtTimeZone = TimeZone.getTimeZone("GMT");

  private static String _localDstName =
    _localTimeZone.getDisplayName(true, TimeZone.SHORT);
  private static String _localStdName =
    _localTimeZone.getDisplayName(false, TimeZone.SHORT);

  private static String _gmtDstName =
    _gmtTimeZone.getDisplayName(true, TimeZone.SHORT);
  private static String _gmtStdName =
    _gmtTimeZone.getDisplayName(false, TimeZone.SHORT);

  // static dates for the static formatting
  private static QDate _gmtDate = new QDate(false);
  private static QDate _localDate = new QDate(true);

  private static final FreeList<QDate> _freeLocalDate
    = new FreeList<QDate>(8);

  private static final FreeList<QDate> _freeGmtDate
    = new FreeList<QDate>(8);

  private TimeZone _timeZone;
  private Calendar _calendar;

  private String _dstName;
  private String _stdName;

  private DateFormat _dateFormat;
  private DateFormat _shortDateFormat;
  private DateFormat _shortTimeFormat;

  private Date _date = new Date();

  // All times are local
  private long _localTimeOfEpoch;

  private long _dayOfEpoch;
  private long _year;
  private int _dayOfYear;
  private long _month;
  private long _dayOfMonth;
  private long _hour;
  private long _minute;
  private long _second;
  private long _ms;
  private boolean _isLeapYear;
  private long _timeOfDay;

  private boolean _isDaylightTime;
  private long _zoneOffset;
  private String _zoneName;

  private long _lastTime;
  private String _lastDate;

  /**
   * Creates the date for GMT.
   */
  public QDate()
  {
    this(_gmtTimeZone);
  }

  /**
   * Creates the date for GMT.
   */
  public QDate(long time)
  {
    this(_localTimeZone);

    setGMTTime(time);
  }

  /**
   * Creates the date form local or GMT.
   */
  public QDate(boolean isLocal)
  {
    this(isLocal ? _localTimeZone : _gmtTimeZone);
  }

  /**
   * Creates the date from local or GMT.
   */
  public QDate(TimeZone zone)
  {
    _timeZone = zone;

    if (zone == _gmtTimeZone) {
      _stdName = _gmtStdName;
      _dstName = _gmtDstName;
    }
    else if (zone == _localTimeZone) {
      _stdName = _localStdName;
      _dstName = _localDstName;
    }
    else {
      _stdName = _timeZone.getDisplayName(false, TimeZone.SHORT);
      _dstName = _timeZone.getDisplayName(true, TimeZone.SHORT);
    }

    _calendar = new GregorianCalendar(_timeZone);

    setLocalTime(CurrentTime.currentTime());
  }

  /**
   * Creates the date from local or GMT.
   */
  public QDate(TimeZone zone, long now)
  {
    _timeZone = zone;

    if (zone == _gmtTimeZone) {
      _stdName = _gmtStdName;
      _dstName = _gmtDstName;
    }
    else if (zone == _localTimeZone) {
      _stdName = _localStdName;
      _dstName = _localDstName;
    }
    else {
      _stdName = _timeZone.getDisplayName(false, TimeZone.SHORT);
      _dstName = _timeZone.getDisplayName(true, TimeZone.SHORT);
    }

    _calendar = new GregorianCalendar(_timeZone);

    if (zone == _gmtTimeZone)
      setGMTTime(now);
    else
      setLocalTime(now);
  }

  /**
   * Creates the date for the local time zone.
   *
   * @see #setDate(long, long, long)
   */
  public QDate(long year, long month, long dayOfMonth)
  {
    this(_localTimeZone);
    setDate(year, month, dayOfMonth);
  }

  /**
   * Creates a local calendar.
   */
  public static QDate createLocal()
  {
    return new QDate(true);
  }

  public static QDate allocateLocalDate()
  {
    QDate date = _freeLocalDate.allocate();

    if (date == null)
      date = new QDate(true);

    return date;
  }

  public static void freeLocalDate(QDate date)
  {
    _freeLocalDate.free(date);
  }

  public static QDate allocateGmtDate()
  {
    QDate date = _freeGmtDate.allocate();

    if (date == null)
      date = new QDate(false);

    return date;
  }

  public static void freeGmtDate(QDate date)
  {
    _freeGmtDate.free(date);
  }



  /**
   * Sets the time in milliseconds since the epoch and calculate
   * the internal variables.
   */
  public void setLocalTime(long time)
  {
    // If this is a local time zone date, just set the time
    if (_timeZone != _gmtTimeZone) {
      calculateSplit(time);
    }
    // If this is a GMT date, convert from local to GMT
    else {
      calculateSplit(time - _localTimeZone.getRawOffset());

      try {
        long offset = _localTimeZone.getOffset(GregorianCalendar.AD,
                                               (int) _year,
                                               (int) _month,
                                               (int) _dayOfMonth + 1,
                                               getDayOfWeek(),
                                               (int) _timeOfDay);

        calculateSplit(time - offset);
      } catch (Exception e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }
  }

  /**
   * Returns the time in milliseconds since the epoch.
   */
  public long getLocalTime()
  {
    // If this is a local time zone date, just set the time
    if (_timeZone != _gmtTimeZone) {
      return _localTimeOfEpoch;
    }
    // If this is a GMT date, convert from local to GMT
    else {
      long offset = _localTimeZone.getOffset(GregorianCalendar.AD,
                                             (int) _year,
                                             (int) _month,
                                             (int) _dayOfMonth + 1,
                                             getDayOfWeek(),
                                             (int) _timeOfDay);

      return _localTimeOfEpoch + offset;
    }
  }

  /**
   * Return the current time as a java.util.Calendar.
   **/
  public Calendar getCalendar()
  {
    return _calendar;
  }

  /**
   * Sets the time in milliseconds since the epoch and calculate
   * the internal variables.
   */
  public void setGMTTime(long time)
  {
    calculateSplit(time + _timeZone.getOffset(time));
  }

  /**
   * Returns the time in milliseconds since the epoch.
   */
  public long getGMTTime()
  {
    return _localTimeOfEpoch - _zoneOffset;
  }

  /**
   * Returns the milliseconds since the beginning of the day.
   */
  public long getTimeOfDay()
  {
    return _timeOfDay;
  }

  /**
   * Returns the year.
   */
  public int getYear()
  {
    return (int) _year;
  }

  /**
   * Sets the year, recalculating the time since epoch.
   */
  public void setYear(int year)
  {
    _year = year;

    calculateJoin();
    calculateSplit(_localTimeOfEpoch);
  }

  /**
   * Returns the month in the year.
   */
  public int getMonth()
  {
    return (int) _month;
  }

  /**
   * Sets the month in the year.
   */
  public void setMonth(int month)
  {
    if (month < 0 || DAYS_IN_MONTH.length <= month)
      return;

    _month = month;

    if (DAYS_IN_MONTH[month] <= _dayOfMonth)
      _dayOfMonth = DAYS_IN_MONTH[month] - 1;

    calculateJoin();
    calculateSplit(_localTimeOfEpoch);
  }

  /**
   * Returns the day of the month, based on 1 for the first of the month.
   */
  public int getDayOfMonth()
  {
    return (int) _dayOfMonth + 1;
  }

  /**
   * sets the day of the month based on 1 for the first of the month.
   */
  public void setDayOfMonth(int day)
  {
    _dayOfMonth = day - 1;
    calculateJoin();
    calculateSplit(_localTimeOfEpoch);
  }

  /**
   * Returns the day of the month, based on 1 for the first of the month.
   */
  public int getDaysInMonth()
  {
    if (_month == 1)
      return _isLeapYear ? 29 : 28;
    else
      return DAYS_IN_MONTH[(int) _month];
  }

  /**
   * Returns the day of the week.
   */
  public int getDayOfWeek()
  {
    return (int) ((_dayOfEpoch % 7) + 11) % 7 + 1;
  }

  /**
   * Returns the day of the year, based on 0 for January 1.
   */
  public int getDayOfYear()
  {
    return (int) _dayOfYear;
  }

  /**
   * Returns the hour.
   */
  public int getHour()
  {
    return (int) _hour;
  }

  /**
   * Sets the hour, recalculating the localTimeOfEpoch.
   */
  public void setHour(int hour)
  {
    _hour = hour;

    calculateJoin();
    calculateSplit(_localTimeOfEpoch);
  }

  /**
   * Returns the minute.
   */
  public int getMinute()
  {
    return (int) _minute;
  }

  /**
   * Sets the minute, recalculating the localTimeOfEpoch.
   */
  public void setMinute(int minute)
  {
    _minute = minute;

    calculateJoin();
    calculateSplit(_localTimeOfEpoch);
  }

  /**
   * Returns the second.
   */
  public int getSecond()
  {
    return (int) _second;
  }

  /**
   * Sets the second, recalculating the localTimeOfEpoch.
   */
  public void setSecond(int second)
  {
    _second = second;

    calculateJoin();
    calculateSplit(_localTimeOfEpoch);
  }

  /**
   * Returns the millisecond.
   */
  public long getMillisecond()
  {
    return _ms;
  }

  /**
   * Sets the millisecond, recalculating the localTimeOfEpoch.
   */
  public void setMillisecond(long millisecond)
  {
    _ms = millisecond;

    calculateJoin();
    calculateSplit(_localTimeOfEpoch);
  }

  /**
   * Returns the time zone offset for that particular day.
   */
  public long getZoneOffset()
  {
    return _zoneOffset;
  }

  /**
   * Returns the name of the timezone
   */
  public String getZoneName()
  {
    return _zoneName;
  }

  /**
   * Returns true for DST
   */
  public boolean isDST()
  {
    return _isDaylightTime;
  }

  /**
   * Returns the current time zone.
   */
  public TimeZone getLocalTimeZone()
  {
    return _timeZone;
  }

  /**
   * Returns the week in the year.
   */
  public int getWeek()
  {
    int dow4th = (int) ((_dayOfEpoch - _dayOfYear + 3) % 7 + 10) % 7;
    int ww1monday = 3 - dow4th;

    if (_dayOfYear < ww1monday)
      return 53;

    int week = (_dayOfYear - ww1monday) / 7 + 1;

    if (_dayOfYear >= 360) {
      int days = 365 + (_isLeapYear ? 1 : 0);
      long nextNewYear = (_dayOfEpoch - _dayOfYear + days);

      int dowNext4th = (int) ((nextNewYear + 3) % 7 + 10) % 7;
      int nextWw1Monday = 3 - dowNext4th;

      if (days <= _dayOfYear - nextWw1Monday)
        return 1;
    }

    return week;
  }

  /**
   * Gets values based on a field.
   */
  public long get(int field)
  {
    switch (field) {
    case TIME:
      return getLocalTime();

    case YEAR:
      return getYear();

    case MONTH:
      return getMonth();

    case DAY_OF_MONTH:
      return getDayOfMonth();

    case DAY:
      return getDayOfWeek();

    case DAY_OF_WEEK:
      return getDayOfWeek();

    case HOUR:
      return getHour();

    case MINUTE:
      return getMinute();

    case SECOND:
      return getSecond();

    case MILLISECOND:
      return getMillisecond();

    case TIME_ZONE:
      return getZoneOffset() / 1000;

    default:
      return Long.MAX_VALUE;
    }
  }

  /**
   * Sets values based on a field.
   */
  public long set(int field, long value)
  {
    switch (field) {
    case YEAR:
      setYear((int) value);
      break;

    case MONTH:
      setMonth((int) value);
      break;

    case DAY_OF_MONTH:
      setDayOfMonth((int) value);
      break;

    case HOUR:
      setHour((int) value);
      break;

    case MINUTE:
      setMinute((int) value);
      break;

    case SECOND:
      setSecond((int) value);
      break;

    case MILLISECOND:
      setMillisecond(value);
      break;

    default:
      throw new RuntimeException();
    }

    return _localTimeOfEpoch;
  }

  /*
   * Mon, 17 Jan 1994 11:14:55 -0500 (EST)
   */
  public String printDate()
  {
    if (_lastDate != null && _lastTime == _localTimeOfEpoch)
      return _lastDate;

    CharBuffer cb = new CharBuffer();

    printDate(cb);

    _lastDate = cb.toString();
    _lastTime = _localTimeOfEpoch;

    return _lastDate;
  }

  /*
   * Mon, 17 Jan 1994 11:14:55 -0500 (EST)
   */
  public void printDate(CharBuffer cb)
  {
    cb.append(DAY_NAMES[(int) (_dayOfEpoch % 7 + 11) % 7]);
    cb.append(", ");
    cb.append((_dayOfMonth + 1) / 10);
    cb.append((_dayOfMonth + 1) % 10);
    cb.append(" ");
    cb.append(MONTH_NAMES[(int) _month]);
    cb.append(" ");
    cb.append(_year);
    cb.append(" ");
    cb.append((_timeOfDay / 36000000L) % 10);
    cb.append((_timeOfDay / 3600000L) % 10);
    cb.append(":");
    cb.append((_timeOfDay / 600000L) % 6);
    cb.append((_timeOfDay / 60000L) % 10);
    cb.append(":");
    cb.append((_timeOfDay / 10000L) % 6);
    cb.append((_timeOfDay / 1000L) % 10);

    if (_zoneName == null || _zoneName.equals("GMT")) {
      cb.append(" GMT");
      return;
    }

    long offset = _zoneOffset;

    if (offset < 0) {
      cb.append(" -");
      offset = - offset;
    } else
      cb.append(" +");

    cb.append((offset / 36000000) % 10);
    cb.append((offset / 3600000) % 10);
    cb.append((offset / 600000) % 6);
    cb.append((offset / 60000) % 10);

    cb.append(" (");
    cb.append(_zoneName);
    cb.append(")");
  }

  /**
   * Prints the date to a stream.
   */
  public void printDate(WriteStream os)
    throws IOException
  {
    os.print(DAY_NAMES[(int) (_dayOfEpoch % 7 + 11) % 7]);
    os.write(',');
    os.write(' ');
    os.print((_dayOfMonth + 1) / 10);
    os.print((_dayOfMonth + 1) % 10);
    os.write(' ');
    os.print(MONTH_NAMES[(int) _month]);
    os.write(' ');
    os.print(_year);
    os.write(' ');
    os.print((_timeOfDay / 36000000) % 10);
    os.print((_timeOfDay / 3600000) % 10);
    os.write(':');
    os.print((_timeOfDay / 600000) % 6);
    os.print((_timeOfDay / 60000) % 10);
    os.write(':');
    os.print((_timeOfDay / 10000) % 6);
    os.print((_timeOfDay / 1000) % 10);

    if (_zoneName == null) {
      os.print(" GMT");
      return;
    }

    long offset = _zoneOffset;

    if (offset < 0) {
      os.write(' ');
      os.write('-');
      offset = - offset;
    } else {
      os.write(' ');
      os.write('+');
    }

    os.print((offset / 36000000) % 10);
    os.print((offset / 3600000) % 10);
    os.print((offset / 600000) % 6);
    os.print((offset / 60000) % 10);

    os.write(' ');
    os.write('(');
    os.print(_zoneName);
    os.write(')');
  }

  /*
   * Mon, 17 Jan 1994 11:14:55 -0500
   */
  public void printRFC2822(CharBuffer cb)
  {
    cb.append(DAY_NAMES[(int) (_dayOfEpoch % 7 + 11) % 7]);
    cb.append(", ");
    cb.append((_dayOfMonth + 1) / 10);
    cb.append((_dayOfMonth + 1) % 10);
    cb.append(" ");
    cb.append(MONTH_NAMES[(int) _month]);
    cb.append(" ");
    cb.append(_year);
    cb.append(" ");
    cb.append((_timeOfDay / 36000000L) % 10);
    cb.append((_timeOfDay / 3600000L) % 10);
    cb.append(":");
    cb.append((_timeOfDay / 600000L) % 6);
    cb.append((_timeOfDay / 60000L) % 10);
    cb.append(":");
    cb.append((_timeOfDay / 10000L) % 6);
    cb.append((_timeOfDay / 1000L) % 10);

    long offset = _zoneOffset;

    if (offset < 0) {
      cb.append(" -");
      offset = - offset;
    } else
      cb.append(" +");

    cb.append((offset / 36000000) % 10);
    cb.append((offset / 3600000) % 10);
    cb.append((offset / 600000) % 6);
    cb.append((offset / 60000) % 10);
  }

  /**
   * Prints the time in ISO 8601
   */
  public String printISO8601()
  {
    StringBuilder sb = new StringBuilder();

    if (_year > 0) {
      sb.append((_year / 1000) % 10);
      sb.append((_year / 100) % 10);
      sb.append((_year / 10) % 10);
      sb.append(_year % 10);
      sb.append('-');
      sb.append(((_month + 1) / 10) % 10);
      sb.append((_month + 1) % 10);
      sb.append('-');
      sb.append(((_dayOfMonth + 1) / 10) % 10);
      sb.append((_dayOfMonth + 1) % 10);
    }

    long time = _timeOfDay / 1000;
    long ms = _timeOfDay % 1000;

    sb.append('T');
    sb.append((time / 36000) % 10);
    sb.append((time / 3600) % 10);

    sb.append(':');
    sb.append((time / 600) % 6);
    sb.append((time / 60) % 10);

    sb.append(':');
    sb.append((time / 10) % 6);
    sb.append((time / 1) % 10);

    if (ms != 0) {
      sb.append('.');
      sb.append((ms / 100) % 10);
      sb.append((ms / 10) % 10);
      sb.append(ms % 10);
    }

    if (_zoneName == null) {
      sb.append("Z");
      return sb.toString();
    }

    // server/1471 - XXX: was commented out
    long offset = _zoneOffset;

    if (offset < 0) {
      sb.append("-");
      offset = - offset;
    } else
      sb.append("+");

    sb.append((offset / 36000000) % 10);
    sb.append((offset / 3600000) % 10);
    sb.append(':');
    sb.append((offset / 600000) % 6);
    sb.append((offset / 60000) % 10);

    return sb.toString();
  }

  /**
   * Prints just the date component of ISO 8601
   */
  public String printISO8601Date()
  {
    CharBuffer cb = new CharBuffer();

    if (_year > 0) {
      cb.append((_year / 1000) % 10);
      cb.append((_year / 100) % 10);
      cb.append((_year / 10) % 10);
      cb.append(_year % 10);
      cb.append('-');
      cb.append(((_month + 1) / 10) % 10);
      cb.append((_month + 1) % 10);
      cb.append('-');
      cb.append(((_dayOfMonth + 1) / 10) % 10);
      cb.append((_dayOfMonth + 1) % 10);
    }

    return cb.toString();
  }

  /**
   * Formats a date.
   *
   * @param time the time to format
   * @param format the format string
   */
  public synchronized static String formatGMT(long gmtTime, String format)
  {
    _gmtDate.setGMTTime(gmtTime);

    return _gmtDate.format(new CharBuffer(), format).toString();
  }

  /**
   * Formats a date, using the default time format.
   *
   * @param time the time to format
   */
  public synchronized static String formatGMT(long gmtTime)
  {
    _gmtDate.setGMTTime(gmtTime);

    return _gmtDate.printDate();
  }

  /**
   * Formats a time in the local time zone.
   *
   * @param time in milliseconds, GMT, from the epoch.
   * @param format formatting string.
   */
  public synchronized static String formatLocal(long gmtTime, String format)
  {
    _localDate.setGMTTime(gmtTime);

    return _localDate.format(new CharBuffer(), format).toString();
  }

  /**
   * Formats a time in the local time zone, using the default format.
   *
   * @param time in milliseconds, GMT, from the epoch.
   */
  public synchronized static String formatLocal(long gmtTime)
  {
    _localDate.setGMTTime(gmtTime);

    return _localDate.printDate();
  }

  /**
   * Formats a time in the local time zone.
   *
   * @param time in milliseconds, GMT, from the epoch.
   * @param format formatting string.
   */
  public synchronized static CharBuffer formatLocal(CharBuffer cb,
                                                    long gmtTime,
                                                    String format)
  {
    _localDate.setGMTTime(gmtTime);

    return _localDate.format(cb, format);
  }

  public synchronized static String formatISO8601(long gmtTime)
  {
    if (_gmtDate == null)
      _gmtDate = new QDate();

    _gmtDate.setGMTTime(gmtTime);

    return _gmtDate.printISO8601();
  }

  /**
   * Global date must be synchronized before you can do anything on it.
   */
  public static QDate getGlobalDate()
  {
    return _localDate;
  }

  /**
   * Formats the current date.
   */
  public String format(String format)
  {
    CharBuffer cb = new CharBuffer();

    return format(cb, format).close();
  }

  /**
   * Format the date using % escapes:
   *
   * <table>
   * <tr><td>%a<td>day of week (short)
   * <tr><td>%A<td>day of week (verbose)
   * <tr><td>%b<td>month name (short)
   * <tr><td>%B<td>month name (verbose)
   * <tr><td>%c<td>Java locale date
   * <tr><td>%d<td>day of month (two-digit)
   * <tr><td>%F<td>%Y-%m-%d
   * <tr><td>%H<td>24-hour (two-digit)
   * <tr><td>%I<td>12-hour (two-digit)
   * <tr><td>%j<td>day of year (three-digit)
   * <tr><td>%l<td>12-hour (one-digit prefixed by space)
   * <tr><td>%m<td>month (two-digit)
   * <tr><td>%M<td>minutes
   * <tr><td>%p<td>am/pm
   * <tr><td>%P<td>AM/PM
   * <tr><td>%S<td>seconds
   * <tr><td>%s<td>milliseconds
   * <tr><td>%x<td>Java locale short date
   * <tr><td>%X<td>Java locale short time
   * <tr><td>%W<td>week in year (three-digit)
   * <tr><td>%w<td>day of week (one-digit)
   * <tr><td>%y<td>year (two-digit)
   * <tr><td>%Y<td>year (four-digit)
   * <tr><td>%Z<td>time zone (name)
   * <tr><td>%z<td>time zone (+/-0800)
   * </table>
   */
  public CharBuffer format(CharBuffer cb, String format)
  {
    int length = format.length();
    for (int i = 0; i < length; i++) {
      char ch = format.charAt(i);
      if (ch != '%') {
        cb.append(ch);
        continue;
      }

      switch (format.charAt(++i)) {
      case 'a':
        cb.append(SHORT_WEEKDAY[getDayOfWeek() - 1]);
        break;

      case 'A':
        cb.append(LONG_WEEKDAY[getDayOfWeek() - 1]);
        break;

      case 'h':
      case 'b':
        cb.append(SHORT_MONTH[(int) _month]);
        break;

      case 'B':
        cb.append(LONG_MONTH[(int) _month]);
        break;

      case 'c':
        cb.append(printLocaleDate());
        break;

      case 'd':
        cb.append((_dayOfMonth + 1) / 10);
        cb.append((_dayOfMonth + 1) % 10);
        break;

      case 'D':
        cb.append((_month + 1) / 10);
        cb.append((_month + 1) % 10);
        cb.append('/');
        cb.append((_dayOfMonth + 1) / 10);
        cb.append((_dayOfMonth + 1) % 10);
        cb.append('/');
        cb.append(_year / 10 % 10);
        cb.append(_year % 10);
        break;

      case 'e':
        if ((_dayOfMonth + 1) / 10 == 0)
          cb.append(' ');
        else
          cb.append((_dayOfMonth + 1) / 10);
        cb.append((_dayOfMonth + 1) % 10);
        break;

        // ISO year

      case 'F':
        {
          cb.append(_year / 1000 % 10);
          cb.append(_year / 100 % 10);
          cb.append(_year / 10 % 10);
          cb.append(_year % 10);

          cb.append('-');
          cb.append((_month + 1) / 10);
          cb.append((_month + 1) % 10);

          cb.append('-');
          cb.append((_dayOfMonth + 1) / 10);
          cb.append((_dayOfMonth + 1) % 10);

          break;
        }

      case 'H':
        {
          int hour = (int) (_timeOfDay / 3600000) % 24;
          cb.append(hour / 10);
          cb.append(hour % 10);
          break;
        }

      case 'I':
        {
          int hour = (int) (_timeOfDay / 3600000) % 12;
          if (hour == 0)
            hour = 12;
          cb.append(hour / 10);
          cb.append(hour % 10);
          break;
        }

      case 'j':
        cb.append((_dayOfYear + 1) / 100);
        cb.append((_dayOfYear + 1) / 10 % 10);
        cb.append((_dayOfYear + 1) % 10);
        break;

      case 'l':
        {
          int hour = (int) (_timeOfDay / 3600000) % 12;
          if (hour == 0)
            hour = 12;

          if (hour < 10) {
            cb.append(' ');
          }

          cb.append(hour);

          break;
        }

      case 'm':
        cb.append((_month + 1) / 10);
        cb.append((_month + 1) % 10);
        break;

      case 'M':
        cb.append((_timeOfDay / 600000) % 6);
        cb.append((_timeOfDay / 60000) % 10);
        break;

      case 'p':
        {
          int hour = (int) (_timeOfDay / 3600000) % 24;
          if (hour < 12)
            cb.append("am");
          else
            cb.append("pm");
          break;
        }

      case 'P':
      {
        int hour = (int) (_timeOfDay / 3600000) % 24;
        if (hour < 12)
          cb.append("AM");
        else
          cb.append("PM");
        break;
      }

      case 'S':
        cb.append((_timeOfDay / 10000) % 6);
        cb.append((_timeOfDay / 1000) % 10);
        break;

      case 's':
        cb.append((_timeOfDay / 100) % 10);
        cb.append((_timeOfDay / 10) % 10);
        cb.append(_timeOfDay % 10);
        break;

      case 'T':
        {
          int hour = (int) (_timeOfDay / 3600000) % 24;
          cb.append(hour / 10);
          cb.append(hour % 10);
          cb.append(':');
          cb.append((_timeOfDay / 600000) % 6);
          cb.append((_timeOfDay / 60000) % 10);
          cb.append(':');
          cb.append((_timeOfDay / 10000) % 6);
          cb.append((_timeOfDay / 1000) % 10);
          break;
        }

      case 'W':
        int week = getWeek();
        cb.append((week + 1) / 10);
        cb.append((week + 1) % 10);
        break;

      case 'w':
        cb.append(getDayOfWeek() - 1);
        break;

      case 'x':
        cb.append(printShortLocaleDate());
        break;

      case 'X':
        cb.append(printShortLocaleTime());
        break;

      case 'y':
        cb.append(_year / 10 % 10);
        cb.append(_year % 10);
        break;

      case 'Y':
        cb.append(_year / 1000 % 10);
        cb.append(_year / 100 % 10);
        cb.append(_year / 10 % 10);
        cb.append(_year % 10);
        break;

      case 'Z':
        if (_zoneName == null)
          cb.append("GMT");
        else
          cb.append(_zoneName);
        break;

      case 'z':
        long offset = _zoneOffset;

        if (offset < 0) {
          cb.append("-");
          offset = - offset;
        }
        else
          cb.append("+");

        cb.append((offset / 36000000) % 10);
        cb.append((offset / 3600000) % 10);
        cb.append((offset / 600000) % 6);
        cb.append((offset / 60000) % 10);
        break;

      case '%':
        cb.append('%');
        break;

      default:
        cb.append(format.charAt(i));
      }
    }

    return cb;
  }

  /*
   * XXX: buggy (Because cal is buggy), may have to implement the sdf
   */
  public String printLocaleDate()
  {
    _date.setTime(_localTimeOfEpoch);

    // SimpleDateFormat sdf = new SimpleDateFormat();
    // System.out.println("" + sdf.toPattern());

    if (_dateFormat == null)
      _dateFormat = DateFormat.getInstance();

    return _dateFormat.format(_date);
  }

  /**
   * Returns a date in M/dd/yy format (i.e. 11/30/69 in US locale).
   */
  public String printShortLocaleDate()
  {
    _date.setTime(_localTimeOfEpoch);

    if (_shortDateFormat == null)
      _shortDateFormat = DateFormat.getDateInstance(DateFormat.SHORT);

    return _shortDateFormat.format(_date);
  }

  /**
   * Returns a date in H:mm:ss PM format.
   */
  public String printShortLocaleTime()
  {
    _date.setTime(_localTimeOfEpoch);

    if (_shortTimeFormat == null)
      _shortTimeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);

    return _shortTimeFormat.format(_date);
  }

  /*
   * XXX: okay, this is vile.
   * Mon, 17 Jan 1994 11:14:55 -0500 (EST)
   *
   * In local time
   */
  public long parseLocalDate(String string) throws Exception
  {
    long time = parseDate(string);

    synchronized (this) {
      setLocalTime(time);
      return getGMTTime();
    }
  }

  /*
   * XXX: okay, this is vile.
   * Mon, 17 Jan 1994 11:14:55 -0500 (EST)
   *
   * In GMT time
   */
  public long parseDate(String string) throws Exception
  {
    try {
      int strlen = string.length();

      if (strlen == 0)
        return 0;

      int i = skipWhitespace(string, strlen, 0);

      int ch = string.charAt(i);
      if (ch >= '0' && ch <= '9'
          || (ch == 'T' && i + 1 < strlen
              && string.charAt(i + 1) >= '0' && string.charAt(i + 1) <= '9'))
        return parseISO8601Date(string, i);

      CharBuffer cb = new CharBuffer();

      i = scan(string, 0, cb, true);
      if (cb.length() == 0 || ! Character.isDigit(cb.charAt(0)))
        i = scan(string, i, cb, true);

      int dayOfMonth = parseInt(cb);
      i = scan(string, i, cb, true);
      String smonth = cb.toString();
      int month;
      for (month = 0; month < 12; month++) {
        if (MONTH_NAMES[(int) month].equalsIgnoreCase(smonth))
          break;
      }

      if (month == 12)
        throw new Exception("Unexpected month: " + month);

      i = scan(string, i, cb, true);

      int year = parseInt(cb);
      if (cb.length() < 3 && year < 50)
        year += 2000;
      else if (cb.length() < 3 && year < 100)
        year += 1900;

      i = scan(string, i, cb, false);
      long timeOfDay = parseInt(cb) * 3600000;

      i = scan(string, i, cb, false);
      timeOfDay += parseInt(cb) * 60000;

      i = scan(string, i, cb, false);
      timeOfDay += parseInt(cb) * 1000;

      // XXX: gross hack
      if (year <= 1600)
        dayOfMonth--;

      long time = (MS_PER_DAY * (yearToDayOfEpoch(year)
                                 + monthToDayOfYear(month, isLeapYear(year))
                                 + dayOfMonth - 1)
                   + timeOfDay);

      try {
        i = scan(string, i, cb, false);
        for (int j = 0; j < cb.length(); j++) {
          if ((ch = cb.charAt(j)) == ';' || ch == ' ')
            cb.length(j);
        }

        ch = cb.length() > 0 ? cb.charAt(0) : 0;
        if (ch == '-' || ch == '+' || ch >= '0' && ch <= '9') {
          long zoneOffset;
          zoneOffset = parseInt(cb);
          zoneOffset = 60000 * (60 * (zoneOffset / 100) + zoneOffset % 100);

          time -= zoneOffset;

          setGMTTime(time);
        } else if (cb.equalsIgnoreCase("gmt") ||
                   cb.equalsIgnoreCase("utc")) {
          setGMTTime(time);
        } else {
          setLocalTime(time);
        }
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      }

      return _localTimeOfEpoch - _zoneOffset;
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);

      return Long.MAX_VALUE;
    }
  }

  private long parseISO8601Date(String string, int pos)
    throws Exception
  {
    int strlen = string.length();
    int year = 0;
    char ch = string.charAt(pos);

    if ('0' <= ch && ch <= '9') {
      year = scanISOInt(string, pos, strlen, 4);
      pos += 4;
    }

    if (pos < strlen && string.charAt(pos) == '-')
      pos++;

    int month = 0;
    if (pos < strlen && '0' <= (ch = string.charAt(pos)) && ch <= '9') {
      month = scanISOInt(string, pos, strlen, 2);
      month--;
      pos += 2;
    } else if (ch == 'W')
      return Long.MAX_VALUE;

    if (pos < strlen && string.charAt(pos) == '-')
      pos++;

    int day = 0;
    if (pos < strlen && '0' <= (ch = string.charAt(pos)) && ch <= '9') {
      day = scanISOInt(string, pos, strlen, 2);
      day--;
      pos += 2;
    }

    int hour = 0;
    int minute = 0;
    int second = 0;
    int millisecond = 0;
    if (pos < strlen && string.charAt(pos) == 'T') {
      pos++;

      if (pos < strlen && '0' <= (ch = string.charAt(pos)) && ch <= '9') {
        hour = scanISOInt(string, pos, strlen, 2);
        pos += 2;
      }


      // XXX: fractions can technically be used anywhere by using a
      // , or . instead of a :
      // e.g. 14:30,5 == 14:30:30

      if (pos < strlen && string.charAt(pos) == ':')
        pos++;

      if (pos < strlen && '0' <= (ch = string.charAt(pos)) && ch <= '9') {
        minute = scanISOInt(string, pos, strlen, 2);
        pos += 2;
      }

      if (pos < strlen && string.charAt(pos) == ':')
        pos++;

      if (pos < strlen && '0' <= (ch = string.charAt(pos)) && ch <= '9') {
        second = scanISOInt(string, pos, strlen, 2);
        pos += 2;
      }

      if (pos < strlen &&
          (string.charAt(pos) == '.' || string.charAt(pos) == ',')) {
        pos++;
        // XXX: fractions can be any strlen, not just 3
        millisecond = scanISOInt(string, pos, strlen, 3);
        pos += 3;
      }
    }

    long timeOfDay = millisecond + 1000 * (second + 60 * (minute + 60 * hour));

    // XXX: gross hack
    if (year <= 1600)
      day--;

    long time = (MS_PER_DAY * (yearToDayOfEpoch(year) +
                               monthToDayOfYear(month, isLeapYear(year)) +
                               day) +
                timeOfDay);

    if (strlen <= pos) {
      setLocalTime(time);
      return _localTimeOfEpoch;
    }

    if (string.charAt(pos) == 'Z') {
      pos++;
    }

    else if (string.charAt(pos) == '-' || string.charAt(pos) == '+') {
      int sign = -1;
      if (string.charAt(pos) == '-')
        sign = 1;

      pos++;
      int tzHour = scanISOInt(string, pos, strlen, 2);
      pos += 2;
      int tzMinute = 0;

      if (pos < strlen && string.charAt(pos) == ':')
        pos++;
      if (pos < strlen && '0' <= (ch = string.charAt(pos)) && ch <= '9') {
        tzMinute = scanISOInt(string, pos, strlen, 2);
        pos += 2;
      }

      time += sign * 1000 * (60 * (tzMinute + 60 * tzHour));
    }

    else {
      setLocalTime(time);
      return _localTimeOfEpoch;
    }

    pos = skipWhitespace(string, strlen, pos);
    if (pos < strlen)
      throw new Exception("extra junk at end of ISO date");

    setGMTTime(time);

    return _localTimeOfEpoch;
  }

  /**
   * Based on the year, return the number of days since the epoch.
   */
  private long yearToDayOfEpoch(long year)
  {
    if (year > 0) {
      year -= 1601;
      return (365 * year + year / 4 - year / 100 + year / 400 -
              ((1970 - 1601) * 365 + (1970 - 1601) / 4 - 3));
    } else {
      year = 2000 - year;

      return ((2000 - 1970) * 365 + (2000 - 1970) / 4 -
              (365 * year + year / 4 - year / 100 + year / 400));
    }
  }

  /**
   * Calculates the day of the year for the beginning of the month.
   */
  private long monthToDayOfYear(long month, boolean isLeapYear)
  {
    long day = 0;

    for (int i = 0; i < month && i < 12; i++) {
      day += DAYS_IN_MONTH[i];
      if (i == 1 && isLeapYear)
        day++;
    }

    return day;
  }

  /**
   * Returns true if the given year is a leap year.
   */
  private boolean isLeapYear(long year)
  {
    return ! ((year % 4) != 0 || (year % 100) == 0 && (year % 400) != 0);
  }

  private int scanISOInt(String string, int pos, int length, int digits)
    throws Exception
  {
    int value = 0;
    for (int i = 0; i < digits; i++) {
      if (pos >= length)
        throw new Exception("expected ISO8601 digit");
      char ch = string.charAt(pos++);
      if ('0' <= ch && ch <= '9')
        value = 10 * value + ch - '0';
      else
        throw new Exception("expected ISO8601 digit");
    }

    return value;
  }

  private int skipWhitespace(String string, int strlen, int i)
  {
    char ch;

    for (; i < strlen
           && ((ch = string.charAt(i)) == ' ' || ch == '\t'
               || ch == '\n' || ch == '\r');
         i++) {
    }

    return i;
  }

  /*
   * Scan to whitespace or ':'
   */
  private int scan(String string, int i, CharBuffer cb, boolean dash)
    throws Exception
  {
    char ch;

    cb.length(0);

    int strlen = string.length();
    for (; i < strlen; i++) {
      if (! Character.isWhitespace(ch = string.charAt(i)) &&
          (ch != ':' && (! dash || ch != '-')))
        break;
    }

    for (; i < strlen; i++) {
      if (! Character.isWhitespace(ch = string.charAt(i)) &&
          (ch != ':' && (! dash || ch != '-')))
        cb.append((char) ch);
      else
        break;
    }

    if (cb.length() == 0)
      throw new Exception();

    return i;
  }

  private int parseInt(CharBuffer cb) throws Exception
  {
    int value = 0;
    int sign = 1;

    for (int i = 0; i < cb.length(); i++) {
      int ch = cb.charAt(i);
      if (i == 0 && ch == '-')
        sign = -1;
      else if (i == 0 && ch == '+') {
      }
      else if (ch >= '0' && ch <= '9')
        value = 10 * value + ch - '0';
      else
        throw new Exception();
    }

    return sign * value;
  }

  /**
   * Sets date in the local time.
   *
   * @param year
   * @param month where January = 0
   * @param day day of month where the 1st = 1
   */
  public long setDate(long year, long month, long day)
  {
    year += (long) Math.floor(month / 12.0);
    month -= (long) 12 * Math.floor(month / 12.0);

    _year = year;
    _month = month;
    _dayOfMonth = day - 1;

    calculateJoin();
    calculateSplit(_localTimeOfEpoch);

    return _localTimeOfEpoch;
  }

  public long setTime(long hour, long minute, long second, long ms)
  {
    _hour = hour;
    _minute = minute;
    _second = second;
    _ms = ms;

    calculateJoin();
    calculateSplit(_localTimeOfEpoch);

    return _localTimeOfEpoch;
  }

  /**
   * Calculate and set the calendar components based on the given time.
   *
   * @param localTime local time in milliseconds since the epoch
   */
  private void calculateSplit(long localTime)
  {
    _localTimeOfEpoch = localTime;
    _dayOfEpoch = divFloor(_localTimeOfEpoch, MS_PER_DAY);
    _timeOfDay = _localTimeOfEpoch - MS_PER_DAY * _dayOfEpoch;

    calculateYear();
    calculateMonth();

    _hour = _timeOfDay / 3600000;
    _minute = _timeOfDay / 60000 % 60;
    _second = _timeOfDay / 1000 % 60;
    _ms = _timeOfDay % 1000;

    if (_timeZone == _gmtTimeZone) {
      _isDaylightTime = false;
      _zoneName = _stdName;
      _zoneOffset = 0;
    }
    else {
      // server/1470
      long tempOffset = _timeZone.getOffset(_localTimeOfEpoch);
      _zoneOffset = _timeZone.getOffset(_localTimeOfEpoch - tempOffset);

      if (_zoneOffset == _timeZone.getRawOffset()) {
        _isDaylightTime = false;
        _zoneName = _stdName;
      }
      else {
        _isDaylightTime = true;
        _zoneName = _dstName;
      }
    }

    _calendar.setTimeInMillis(_localTimeOfEpoch);
  }

  /**
   * Calculates the year, the dayOfYear and whether this is a leap year
   * from the current days since the epoch.
   */
  private void calculateYear()
  {
    long days = _dayOfEpoch;

    // shift to using 1601 as a base
    days += (1970 - 1601) * 365 + (1970 - 1601) / 4 - 3;

    long n400 = divFloor(days, 400 * 365 + 100 - 3);
    days -= n400 * (400 * 365 + 100 - 3);

    long n100 = divFloor(days, 100 * 365 + 25 - 1);
    if (n100 == 4)
      n100 = 3;
    days -= n100 * (100 * 365 + 25 - 1);

    long n4 = divFloor(days, 4 * 365 + 1);
    if (n4 == 25)
      n4 = 24;
    days -= n4 * (4 * 365 + 1);

    long n1 = divFloor(days, 365);
    if (n1 == 4)
      n1 = 3;

    _year = 400 * n400 + 100 * n100 + 4 * n4 + n1 + 1601;
    _dayOfYear = (int) (days - 365 * n1);

    _isLeapYear = isLeapYear(_year);
  }

  public boolean isLeapYear()
  {
    return _isLeapYear;
  }

  /**
   * Calculates the month based on the day of the year.
   */
  private void calculateMonth()
  {
    _dayOfMonth = _dayOfYear;

    for (_month = 0; _month < 12; _month++) {
      if (_month == 1 && _isLeapYear) {
        if (_dayOfMonth < 29)
          return;
        else
          _dayOfMonth -= 29;
      }
      else if (_dayOfMonth < DAYS_IN_MONTH[(int) _month])
        return;
      else
        _dayOfMonth -= DAYS_IN_MONTH[(int) _month];
    }
  }

  /**
   * Based on the current data, calculate the time since the epoch.
   *
   * @return time since the epoch, given the calendar components
   */
  private long calculateJoin()
  {
    _year += divFloor(_month, 12);
    _month -= 12 * divFloor(_month, 12);

    _localTimeOfEpoch
      = MS_PER_DAY * (yearToDayOfEpoch(_year)
                      + monthToDayOfYear(_month, isLeapYear(_year))
                      + _dayOfMonth);

    _localTimeOfEpoch += _ms + 1000 * (_second + 60 * (_minute + 60 * _hour));

    return _localTimeOfEpoch;
  }

  private long divFloor(long n, long d)
  {
    if (n > 0)
      return n / d;
    else
      return (n - d + 1) / d;
  }

  @Override
  public Object clone()
  {
    QDate newObj = new QDate(_timeZone);

    newObj.calculateSplit(_localTimeOfEpoch);

    return newObj;
  }

  @Override
  public String toString()
  {
    return "QDate[" + printDate() + "]";
  }
}
