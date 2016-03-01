/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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
 * @author Alex Rojkov
 */

package com.caucho.v5.cli.server;

import static io.baratine.files.Status.FileType.DIRECTORY;
import static io.baratine.files.Status.FileType.FILE;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Locale;

import com.caucho.v5.baratine.client.ServiceManagerClient;
import com.caucho.v5.bartender.files.FilesDeployService;
import com.caucho.v5.cli.baratine.ArgsCli;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.util.L10N;

import io.baratine.files.Status;

public class BfsLsCommand extends BfsCommand
{
  private static final L10N L = new L10N(BfsLsCommand.class);

  private boolean _isLong;

  private boolean _isRecursive;

  private FilesDeployService _files;

  @Override
  public String name()
  {
    return "ls";
  }

  @Override
  public String getDescription()
  {
    return "list bfs files";
  }

  @Override
  public String getUsageTailArgs()
  {
    return " file";
  }

  @Override
  public int getTailArgsMinCount()
  {
    return 1;
  }

  @Override
  protected void initBootOptions()
  {
    addFlagOption("long", "lists in long format").tiny("l");
    addFlagOption("recursive",
                  "recursively list subdirectories encountered").tiny("R");

    super.initBootOptions();
  }

  @Override
  protected ExitCode doCommandImpl(ArgsCli args,
                               ServiceManagerClient client)
  {
    _isLong = args.getArgFlag("long");

    _isRecursive = args.getArgFlag("recursive");

    _files = client.service("remote:///bartender-files")
                   .as(FilesDeployService.class);

    ArrayList<String> argList = args.getTailArgs();

    String path = argList.get(0);

    if (path.length() > 1 && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    list(path);

    return ExitCode.OK;
  }

  private void list(String path)
  {
    String[] files = _files.list(path);

    try {
      LinkedList<String> pendingDirectories = new LinkedList<>();

      for (int i = 0; i < files.length; i++) {
        String file = files[i];

        Status status = null;

        if (_isLong || _isRecursive)
          status = _files.getStatus(path + "/" + file);

        String desc = generateDesc(file, status);

        if (i > 0 && _isRecursive && ! _isLong)
          System.out.print("  ");

        System.out.print(desc);

        if (_isLong)
          System.out.println();

        if (! _isRecursive && ! _isLong)
          System.out.println();

        if (_isRecursive && i == files.length -1 && ! _isLong)
          System.out.println();

        if (_isRecursive
            && status != null
            && DIRECTORY == status.getType()) {
          pendingDirectories.push(file);
        }
      }

      if (! pendingDirectories.isEmpty()) {
        System.out.println();
      }

      while (! pendingDirectories.isEmpty()) {
        String file = pendingDirectories.removeFirst();
        System.out.println(path + '/' + file + ':');
        list(path + '/' + file);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();

      throw e;
    }
  }

  private String generateDesc(String file, Status status)
  {
    StringBuilder sb = new StringBuilder();

    if (_isLong) {
      appendLongDesc(sb, file, status);
    }

    sb.append(file);

    if (status != null && DIRECTORY == status.getType() && ! _isLong)
      sb.append('/');

    return sb.toString();
  }

  private void appendLongDesc(StringBuilder sb, String file, Status status)
  {
    Status.FileType type = FILE;
    long length = 0;
    long modifiedTime = 0;

    if (status != null) {
      type = status.getType();
      length = status.getLength();
      modifiedTime = status.getLastModifiedTime();
    }
    else {
      System.out.println("XXX: status is null: " + file);
    }

    if (type == DIRECTORY) {
      sb.append('d');
    }
    else {
      sb.append('-');
    }

    // permissions
    sb.append("rwx");
    sb.append("rwx");
    sb.append("rwx");
    sb.append("  ");

    // ???
    sb.append('-');
    sb.append("  ");

    // user
    sb.append('-');
    sb.append("  ");

    // group
    sb.append('-');
    sb.append("  ");

    // size
    appendSize(sb, length, 12);
    sb.append(' ');

    // date
    appendDate(sb, modifiedTime);
    sb.append(" ");
  }

  private void appendSize(StringBuilder sb, long size, int width)
  {
    String str = String.valueOf(size);

    for (int i = str.length(); i < width; i++) {
      sb.append(' ');
    }

    sb.append(str);
  }

  private void appendDate(StringBuilder sb, long timeMs)
  {
    Instant instant = Instant.ofEpochMilli(timeMs);
    ZoneId zoneId = ZoneId.systemDefault();
    Locale locale = Locale.getDefault();

    LocalDateTime date = LocalDateTime.ofInstant(instant, zoneId);

    sb.append(date.getYear());
    sb.append(" ");

    sb.append(date.getMonth().getDisplayName(TextStyle.SHORT, locale));
    sb.append(" ");

    sb.append(date.getDayOfMonth());
    sb.append(" ");

    if (date.getHour() < 10) {
      sb.append("0");
    }

    sb.append(date.getHour());
    sb.append(":");

    if (date.getMinute() < 10) {
      sb.append("0");
    }

    sb.append(date.getMinute());
  }
}
