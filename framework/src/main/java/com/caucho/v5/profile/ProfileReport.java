/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.profile;

import java.util.ArrayList;
import java.util.Date;

import com.caucho.v5.util.CurrentTime;

/**
 * Report from a profile run.
 */
public class ProfileReport
{
  private ProfileEntry []_entries;
  
  private long _startTime;
  private long _endTime;
  
  private long _period;
  private long _ticks;
  private long _gcTime;
  
  public ProfileReport(ProfileEntry []entries,
                       long startTime,
                       long endTime,
                       long period,
                       long ticks,
                       long gcTime)
  {
    _entries = entries;
    
    _startTime = startTime;
    _endTime = endTime;
    _period = period;
    _ticks = ticks;
    _gcTime = gcTime;
  }
  
  public long getStartTime()
  {
    return _startTime;
  }
  
  public long getEndTime()
  {
    return _endTime;
  }

  public int getDepth()
  {
    return 16;
  }

  public long getPeriod()
  {
    return _period;
  }

  public long getTicks()
  {
    return _ticks;
  }

  public long getRunTime()
  {
    return _ticks * _period;
  }
  
  public long getGcTime()
  {
    return _gcTime;
  }
  
  public ProfileEntry[]getEntries()
  {
    return _entries;
  }

  /**
   * @return
   */
  public String getJson()
  {
    Profile profile = Profile.create();
    
    if (profile == null) {
      return null;
    }
    
    ProfileReport report = profile.report();
    
    if (report == null) {
      return null;
    }
    
    ProfileEntry []entries = report.getEntries();

    if (entries == null || entries.length == 0) {
      return null;
    }
    
    StringBuilder sb = new StringBuilder();
    
    long timestamp = CurrentTime.getCurrentTime();
    
    sb.append("{\n");
    sb.append("  \"create_time\": \"" + new Date(timestamp) + "\"");
    sb.append(",\n  \"timestamp\": " + timestamp);
    sb.append(",\n  \"ticks\" : " + report.getTicks());
    sb.append(",\n  \"depth\" : " + report.getDepth());
    sb.append(",\n  \"period\" : " + report.getPeriod());
    sb.append(",\n  \"end_time\" : " + report.getEndTime());
    sb.append(",\n  \"gc_time\" : " + report.getGcTime());
    sb.append(",\n  \"profile\" :  [\n");
    
    for (int i = 0; i < entries.length; i++) {
      if (i != 0)
        sb.append(",\n");
     
      jsonEntry(sb, entries[i]);
    }
    
    long gcTicks = (report.getGcTime() + report.getPeriod() - 1)
      / report.getPeriod();
    
    
    if (entries.length > 0)
      sb.append(",\n");
    
    jsonGc(sb, gcTicks);
    
    sb.append("\n  ]");
    sb.append("\n}");
 
    return sb.toString();
  }
  
  private void jsonEntry(StringBuilder sb, ProfileEntry entry)
  {
    sb.append("{");
    sb.append("\n  \"name\" : \"");
    escapeString(sb, entry.getDescription());
    sb.append("\"");
    
    sb.append(",\n  \"ticks\" : " + entry.getCount());
    sb.append(",\n  \"state\" : \"" + entry.getState() + "\"");
    
    if (entry.getStackTrace() != null && entry.getStackTrace().size() > 0) {
      jsonStackTrace(sb, entry.getStackTrace());
    }

    sb.append("\n}");
  }
  
  private void jsonGc(StringBuilder sb, long ticks)
  {
    sb.append("{");
    sb.append("\n  \"name\" : \"HeapMemory.gc\"");
    
    sb.append(",\n  \"ticks\" : " + ticks);
    sb.append(",\n  \"state\" : \"RUNNABLE\"");

    sb.append("\n}");
  }
  
  private void jsonStackTrace(StringBuilder sb, 
                              ArrayList<? extends StackEntry> stack)
  {
    sb.append(",\n  \"stack\" : ");
    sb.append("[\n");
    
    int size = stack.size();
    
    for (int i = 0; i < size; i++) {
      StackEntry entry = stack.get(i);
      
      if (i != 0)
        sb.append(",\n");
      
      sb.append("  {");
      
      sb.append("\n    \"class\" : \"" + entry.getClassName() + "\"");
      sb.append(",\n    \"method\" : \"" + entry.getMethodName() + "\"");
      
      if (entry.getArg() != null && ! "".equals(entry.getArg())) {
        sb.append(",\n    \"arg\" : \"");
        escapeString(sb, entry.getArg());
        sb.append("\"");
        
      }
      sb.append("\n  }");
    }
    sb.append("\n  ]");
  }
  
  private void escapeString(StringBuilder sb, String value)
  {
    if (value == null)
      return;
    
    int len = value.length();
    
    for (int i = 0; i < len; i++) {
      char ch = value.charAt(i);
      
      switch (ch) {
      case '"':
        sb.append("\\\"");
        break;
      case '\\':
        sb.append("\\\\");
        break;
      default:
        sb.append(ch);
        break;
      }
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
