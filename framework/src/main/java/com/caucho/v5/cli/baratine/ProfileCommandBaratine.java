/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Alex Rojkov
 */

package com.caucho.v5.cli.baratine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import com.caucho.v5.cli.spi.CommandArgumentException;
import com.caucho.v5.cli.spi.CommandBase;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.profile.Profile;
import com.caucho.v5.profile.ProfileEntry;
import com.caucho.v5.profile.ProfileReport;
import com.caucho.v5.profile.StackEntry;
import com.caucho.v5.util.L10N;

public class ProfileCommandBaratine extends CommandBase<ArgsCli>
{
  private static final L10N L = new L10N(ProfileCommandBaratine.class);

  @Override
  protected void initBootOptions()
  {
    super.initBootOptions();

    addValueOption("active-time", "time", "profiling time span, defualts to 5 sec");
    addValueOption("interval", "time", "the sample interval defaults to 10ms").tiny("i");
    addValueOption("depth", "count", "the stack trace depth (default 16)");
    addFlagOption("start", "start profiling");
    addFlagOption("stop", "stop profiling");
  }

  @Override
  public String getDescription()
  {
    return "gathers a CPU profile of a running server";
  }

  @Override
  protected ExitCode doCommandImpl(ArgsCli args)
  {
    long activeTime = 5 * 1000; // 5 seconds
    String activeTimeArg = args.getArg("-active-time");

    if (activeTimeArg != null)
      activeTime = Long.parseLong(activeTimeArg);

    double interval = args.getArgDouble("interval", 10);

    int depth = args.getArgInt("depth", 16);

    boolean isBegin = args.getArgFlag("start");
    boolean isEnd = args.getArgFlag("stop");

    Profile profile = Profile.create();

    if (profile == null) {
      throw new CommandArgumentException(L.l("profile is not available"));
    }

    profile.setPeriod(Math.max(1, (long) interval));

    if (isBegin) {
      profile.start();
      System.out.println("start");
    }
    else if (isEnd) {
      profile.stop();

      printProfile(profile);
    }

    return ExitCode.OK;
  }

  private void printProfile(Profile profile)
    throws ConfigException
  {
    StringWriter buffer = new StringWriter();
    PrintWriter out = new PrintWriter(buffer);

    ProfileReport report = profile.report();
    
    if (report == null) {
      System.out.println("No profile report generated");
      return;
    }

    ProfileEntry []entries = report.getEntries();

    if (entries == null || entries.length == 0) {
      out.println("Profile returned no entries.");
    }
    else {
      DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

      /*
      long cancelledTime = _cancelledTime.get();

      if (cancelledTime < 0) {
        out.print(L.l("Profile started at {0}. Active for a total of {1}ms.",
                      dateFormat.format(new Date(startedAt)),
                      activeTime));
      }
      else {

        long et = cancelledTime - startedAt;

        out.print(L.l("Profile started at {0}, cancelled at {1}. Active for a total of {2}ms.",
                      dateFormat.format(new Date(startedAt)),
                      dateFormat.format(new Date(cancelledTime)),
                      et));
      }
      */

      long samplingRate = report.getPeriod();
      int depth = report.getDepth();
      long gc = report.getGcTime();

      out.println(L.l(" Sampling rate {0}ms. Depth {1}. GC time {2}",
                      samplingRate,
                      depth,
                      gc));

      double totalTicks = 0;
      for (ProfileEntry entry : entries) {
        totalTicks += entry.getCount();
      }

      final double sampleTicks = report.getTicks();
      double totalPercent = 0;

      out.println(" ref# |   % time   |time self(s)|   % sum    | Method Call");
      for (int i = 0; i < entries.length; i++) {
        ProfileEntry entry = entries[i];

        totalPercent = printSummary(out, entry, i, sampleTicks, totalTicks,
                                    samplingRate, totalPercent);
      }

      totalPercent = 0;

      for (int i = 0; i < entries.length; i++) {
        ProfileEntry entry = entries[i];

        double timePercent = 100.0 * entry.getCount() / sampleTicks;

        out.println();
        out.print(String.format("%6.1f%% ", timePercent));
        out.println(" " + entry.getDescription());
        ArrayList<? extends StackEntry> stackEntries = entry.getStackTrace();
        for (StackEntry stackEntry : stackEntries) {
          out.println("         " + stackEntry.getDescription());
        }
      }
    }

    out.flush();

    System.out.println(buffer);
  }

  private double printSummary(PrintWriter out,
                              ProfileEntry entry,
                              int i,
                              double sampleTicks,
                              double totalTicks,
                              double samplingRate,
                              double totalPercent)
  {
    double timePercent = 100.0 * entry.getCount() / sampleTicks;
    double selfPercent = 100.0 * entry.getCount() / totalTicks;

    totalPercent += selfPercent;

    out.println(String.format(" %4d | %7.1f%% | %10.3fs | %7.1f%% | %s",
                              i,
                              timePercent,
                              (float) entry.getCount() * samplingRate * 0.001,
                              totalPercent,
                              entry.getDescription()));


    return totalPercent;
  }
}
