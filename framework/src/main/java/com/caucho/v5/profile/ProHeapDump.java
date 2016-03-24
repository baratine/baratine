/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.profile;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.jni.JniTroubleshoot;
import com.caucho.v5.jni.JniUtil;
import com.caucho.v5.jni.JniUtil.JniLoad;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * Resin's profiling class.
 */
public class ProHeapDump extends HeapDump {
  private static final L10N L = new L10N(ProHeapDump.class);
  private static final JniTroubleshoot _jniTroubleshoot;
  private static HeapEntry []_lastDump;

  public ProHeapDump()
  {
    _jniTroubleshoot.checkIsValid();
  }

  public static boolean isEnabled()
  {
    return _jniTroubleshoot.isEnabled();
  }

  @Override
  public Object getLastHeapDump()
  {
    return _lastDump;
  }

  @Override
  public Object dump()
  {
    if (! isEnabled())
      return null;

    synchronized (ProHeapDump.class) {
      _lastDump = (HeapEntry []) nativeHeapDump();

      return _lastDump;
    }
  }

  @Override
  public void writeHeapDump(PrintWriter out)
    throws IOException
  {
    dump();

    HeapEntry []dump = _lastDump;

    if (dump == null)
      throw new IllegalStateException(L.l("unable to get heap dump"));

    HeapEntry []sortedDump = new HeapEntry[dump.length];
    System.arraycopy(dump, 0, sortedDump, 0, dump.length);
    Arrays.sort(sortedDump, new HeapEntryComparator());

    out.println("Heap Dump generated " + new Date(CurrentTime.currentTime()));
    out.println("self size  | child size | class name");

    int limit = 256;

    for (int i = 0; i < sortedDump.length && i < limit; i++) {
      HeapEntry entry = sortedDump[i];

      out.println(String.format("%10d | %10d | %s\n",
                                entry.getSelfSize(),
                                entry.getTotalSize(),
                                entry.getClassName()));
    }
    
    out.flush();
  }

  @Override
  public void writeExtendedHeapDump(PrintWriter out)
    throws IOException
  {
    dump();

    HeapEntry []dump = _lastDump;

    if (dump == null)
      throw new IllegalStateException(L.l("unable to get heap dump"));

    HeapEntry []sortedDump = new HeapEntry[dump.length];
    System.arraycopy(dump, 0, sortedDump, 0, dump.length);
    Arrays.sort(sortedDump, new HeapEntryComparator());

    out.println("Heap Dump generated " + new Date(CurrentTime.currentTime()));
    out.println("   count   |  self size | child size | class name");

    int limit = 256;

    for (int i = 0; i < sortedDump.length && i < limit; i++) {
      HeapEntry entry = sortedDump[i];

      out.println(String.format("%10d | %10d | %10d | %s",
                                entry.getCount(),
                                entry.getSelfSize(),
                                entry.getTotalSize(),
                                entry.getClassName()));
    }

    out.flush();
  }

  @Override
  public String jsonHeapDump()
  {
    dump();

    HeapEntry []dump = _lastDump;

    if (dump == null)
      throw new IllegalStateException(L.l("unable to get heap dump"));

    HeapEntry []sortedDump = new HeapEntry[dump.length];
    System.arraycopy(dump, 0, sortedDump, 0, dump.length);
    Arrays.sort(sortedDump, new HeapEntryComparator());
    
    StringBuilder sb = new StringBuilder();
    
    long timestamp = CurrentTime.currentTime();
    
    sb.append("{\n");
    sb.append("  \"create_time\": \"" + new Date(timestamp) + "\",\n");
    sb.append("  \"timestamp\": " + timestamp + ",\n");
    sb.append("  \"heap\" : {\n");

    for (int i = 0; i < sortedDump.length; i++) {
      HeapEntry entry = sortedDump[i];
      
      if (i > 0)
        sb.append(",\n");
      
      sb.append("\"" + entry.getClassName() + "\" : {");
      sb.append("\n  \"count\" : " + entry.getCount());
      sb.append(",\n  \"size\" : " + entry.getSelfSize());
      sb.append(",\n  \"descendant\" : " + entry.getTotalSize());
      sb.append("\n}");
    }
    
    sb.append("\n  }");
    sb.append("\n}");

    return sb.toString();
  }

  @Override
  public void logHeapDump(Logger log,
                          Level level)
  {
    dump();

    HeapEntry []dump = _lastDump;

    if (dump == null)
      throw new IllegalStateException(L.l("unable to get heap dump"));

    HeapEntry []sortedDump = new HeapEntry[dump.length];
    System.arraycopy(dump, 0, sortedDump, 0, dump.length);
    Arrays.sort(sortedDump, new HeapEntryComparator());

    log.log(level, "Heap Dump generated " + new Date(CurrentTime.currentTime()));
    log.log(level, " self size | child size |   count  | class name");
    log.log(level, "-----------+------------+----------+------------");

    int limit = 256;

    for (int i = 0; i < sortedDump.length && i < limit; i++) {
      HeapEntry entry = sortedDump[i];

      log.log(level, String.format("%10d | %10d | %7d | %s",
                                   entry.getSelfSize(),
                                   entry.getTotalSize(),
                                   entry.getCount(),
                                   entry.getClassName()));
    }
  }

  public static native Object nativeHeapDump();

  static class HeapEntryComparator implements Comparator<HeapEntry> {

    @Override
    public int compare(HeapEntry a, HeapEntry b)
    {
      if (a.getTotalSize() < b.getTotalSize())
        return 1;
      else if (a.getTotalSize() == b.getTotalSize())
        return 0;
      else
        return -1;
    }

  }

  static {
    _jniTroubleshoot
    = JniUtil.load(ProHeapDump.class,
                   new JniLoad() { 
                     public void load(String path) { System.load(path); }},
                   "baratine");
  }
}
