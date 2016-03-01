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

package com.caucho.v5.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.util.ArrayList;

/**
 * Formats a timestamp
 */
class DateFormatter
{
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
  
  private final TimestampBase []_timestamp;

  /**
   * Create formatter.
   */
  public DateFormatter(String format)
  {
    _timestamp = parse(format);
  }

  /**
   * Formats the timestamp
   */
  void format(StringBuilder sb, Temporal localDate)
  {
    //Instant localDate = ClockCurrent.GMT.instant();

    int len = _timestamp.length;
    for (int j = 0; j < len; j++) {
      _timestamp[j].format(sb, localDate);
    }
  }

  private TimestampBase []parse(String format)
  {
    ArrayList<TimestampBase> timestampList = new ArrayList<TimestampBase>();
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < format.length(); i++) {
      char ch = format.charAt(i);
      
      int count = getCount(format, i);

      switch (ch) {
      case 'a': case 'A': case 'b': case 'B': case 'c':
      case 'I': case 'j': case 'p':
      case 'W': case 'w': case 'x': case 'X':
      case 'Y': case 'Z': case 'z':
          if (sb.length() > 0)
            timestampList.add(new Text(sb.toString()));
          sb.setLength(0);
          timestampList.add(new Code(ch));
          break;
          
      case 'd':
        addText(timestampList, sb);
        timestampList.add(new DayItem());
        i += count - 1;
        break;
          
      case 'H':
        addText(timestampList, sb);
        timestampList.add(new HourItem());
        i += count - 1;
        break;
        
      case 'm':
        addText(timestampList, sb);
        timestampList.add(new MinuteItem());
        i += count - 1;
        break;
        
      case 'M':
        addText(timestampList, sb);
        timestampList.add(new MonthItem());
        i += count - 1;
        break;
        
      case 's':
        addText(timestampList, sb);
        timestampList.add(new SecondItem());
        i += count - 1;
        break;
        
      case 'S':
        addText(timestampList, sb);
        timestampList.add(new MillisecondItem());
        i += count - 1;
        break;
        
      case 'y':
        addText(timestampList, sb);
        timestampList.add(new YearItem());
        i += count - 1;
        break;
          
      default:
        sb.append(ch);
        break;
      }
    }

    if (sb.length() > 0) {
      timestampList.add(new Text(sb.toString()));
    }

    TimestampBase []timestamp = new TimestampBase[timestampList.size()];
    timestampList.toArray(timestamp);
    
    return timestamp;
  }
  
  private void addText(ArrayList<TimestampBase> list, StringBuilder sb)
  {
    if (sb.length() > 0) {
      list.add(new Text(sb.toString()));
      sb.setLength(0);
    }
  }
  
  private int getCount(String format, int i)
  {
    int count = 0;
    
    int ch = format.charAt(i);
    
    for (; i < format.length() && format.charAt(i) == ch; i++) {
      count++;
    }
    
    return count;
  }

  static class TimestampBase
  {
    public void format(StringBuilder sb, Temporal cal)
    {
    }
  }

  static class Text extends TimestampBase {
    private final char []_text;

    Text(String text)
    {
      _text = text.toCharArray();
    }
    
    @Override
    public void format(StringBuilder sb, Temporal cal)
    {
      sb.append(_text, 0, _text.length);
    }
  }

  static class Code extends TimestampBase {
    private final char _code;

    Code(char code)
    {
      _code = code;
    }
    
    @Override
    public void format(StringBuilder sb, Temporal cal)
    {
      switch (_code) {
      case 'a':
        sb.append(SHORT_WEEKDAY[cal.get(ChronoField.DAY_OF_WEEK)]);
        break;

      case 'A':
        sb.append(LONG_WEEKDAY[cal.get(ChronoField.DAY_OF_WEEK)]);
        break;

      case 'b':
        sb.append(SHORT_MONTH[cal.get(ChronoField.MONTH_OF_YEAR)]);
        break;

      case 'B':
        sb.append(LONG_MONTH[cal.get(ChronoField.MONTH_OF_YEAR)]);
        break;

      case 'c':
        sb.append(DateTimeFormatter.ISO_LOCAL_DATE.format(cal));
        break;

      case 'd':
        int day = cal.get(ChronoField.DAY_OF_MONTH);
        sb.append(day / 10);
        sb.append(day % 10);
        break;

      case 'H':
        int hour = (int) cal.get(ChronoField.HOUR_OF_DAY);
        sb.append(hour / 10);
        sb.append(hour % 10);
        break;

      case 'I':
        hour = (int) (cal.get(ChronoField.HOUR_OF_AMPM));
        if (hour == 0)
          hour = 12;
        sb.append(hour / 10);
        sb.append(hour % 10);
        break;

      case 'j':
        day = cal.get(ChronoField.DAY_OF_YEAR);
        sb.append((day + 1) / 100);
        sb.append((day + 1) / 10 % 10);
        sb.append((day + 1) % 10);
        break;

      case 'm':
        int month = cal.get(ChronoField.MONTH_OF_YEAR);
        sb.append((month) / 10);
        sb.append((month) % 10);
        break;

      case 'M':
        int minute = cal.get(ChronoField.MINUTE_OF_HOUR);
        sb.append((minute / 10) % 6);
        sb.append((minute) % 10);
        break;

      case 'p':
        hour = cal.get(ChronoField.HOUR_OF_DAY);
        if (hour < 12)
          sb.append("am");
        else
          sb.append("pm");
        break;

      case 'S':
        int second = cal.get(ChronoField.SECOND_OF_MINUTE);
        sb.append(second / 10 % 6);
        sb.append(second % 10);
        break;

      case 's':
        int milli = cal.get(ChronoField.MILLI_OF_SECOND);
        sb.append((milli / 100) % 10);
        sb.append((milli / 10) % 10);
        sb.append(milli % 10);
        break;

      case 'W':
        int week = cal.get(ChronoField.ALIGNED_WEEK_OF_YEAR);
        sb.append((week + 1) / 10);
        sb.append((week + 1) % 10);
        break;

      case 'w':
        sb.append(cal.get(ChronoField.DAY_OF_WEEK));
        break;

      case 'x':
        sb.append(DateTimeFormatter.ISO_LOCAL_DATE.format(cal));
        //sb.append(cal.printShortLocaleDate());
        break;
        
      case 'X':
        sb.append(DateTimeFormatter.ISO_LOCAL_TIME.format(cal));
        //sb.append(cal.printShortLocaleTime());
        break;
    
      case 'y':
        {
          int year = cal.get(ChronoField.YEAR);
          sb.append(year / 10 % 10);
          sb.append(year % 10);
          break;
        }

      case 'Y':
        {
          int year = cal.get(ChronoField.YEAR);
          sb.append(year / 1000 % 10);
          sb.append(year / 100 % 10);
          sb.append(year / 10 % 10);
          sb.append(year % 10);
          break;
        }

      case 'Z':
        /*
        if (cal.getZoneName() == null)
          sb.append("GMT");
        else
          sb.append(cal.getZoneName());
          */
        sb.append("GMT");
        break;

      case 'z':
        long offset = 0;//cal.getZoneOffset();

        if (offset < 0) {
          sb.append("-");
          offset = - offset;
        }
        else
          sb.append("+");

        sb.append((offset / 36000000) % 10);
        sb.append((offset / 3600000) % 10);
        sb.append((offset / 600000) % 6);
        sb.append((offset / 60000) % 10);
        break;
      }
    }
  }

  static class YearItem extends TimestampBase
  {
    @Override
    public void format(StringBuilder sb, Temporal cal)
    {
      int year = cal.get(ChronoField.YEAR);
      sb.append(year / 1000 % 10);
      sb.append(year / 100 % 10);
      sb.append(year / 10 % 10);
      sb.append(year % 10);
    }
  }

  static class MonthItem extends TimestampBase
  {
    @Override
    public void format(StringBuilder sb, Temporal cal)
    {
      int month = cal.get(ChronoField.MONTH_OF_YEAR);
      
      sb.append((month) / 10);
      sb.append((month) % 10);
    }
  }

  static class DayItem extends TimestampBase
  {
    @Override
    public void format(StringBuilder sb, Temporal cal)
    {
      int day = cal.get(ChronoField.DAY_OF_MONTH);
      
      sb.append(day / 10);
      sb.append(day % 10);
    }
  }

  static class HourItem extends TimestampBase
  {
    @Override
    public void format(StringBuilder sb, Temporal cal)
    {
      int hour = cal.get(ChronoField.HOUR_OF_DAY);
      
      sb.append(hour / 10);
      sb.append(hour % 10);
    }
  }

  static class MinuteItem extends TimestampBase
  {
    @Override
    public void format(StringBuilder sb, Temporal cal)
    {
      int minute = cal.get(ChronoField.MINUTE_OF_HOUR);
      
      sb.append((minute / 10) % 6);
      sb.append((minute) % 10);
    }
  }

  static class SecondItem extends TimestampBase
  {
    @Override
    public void format(StringBuilder sb, Temporal cal)
    {
      int second = cal.get(ChronoField.SECOND_OF_MINUTE);
      
      sb.append((second / 10) % 6);
      sb.append((second) % 10);
    }
  }

  static class MillisecondItem extends TimestampBase
  {
    @Override
    public void format(StringBuilder sb, Temporal cal)
    {
      int milli = cal.get(ChronoField.MILLI_OF_SECOND);
      
      sb.append((milli / 100) % 10);
      sb.append((milli / 10) % 10);
      sb.append((milli) % 10);
    }
  }
}
