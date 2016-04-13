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
 * @author Scott Ferguson
 */

package com.caucho.v5.cli.shell_old;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.cli.shell.BooleanCli;
import com.caucho.v5.cli.shell.JavaValueCli;
import com.caucho.v5.cli.shell.NullCli;
import com.caucho.v5.cli.shell.NumberCli;
import com.caucho.v5.cli.shell.StringCli;
import com.caucho.v5.cli.shell.TableCli;
import com.caucho.v5.cli.shell.ValueCli;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.io.Vfs;
import com.caucho.v5.log.impl.LogHandlerConfig;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.vfs.VfsOld;
import com.caucho.v5.vfs.WriteStreamOld;

/**
 * Runtime environment for the command-line
 */
public class EnvCliOld
{
  private TableCli _envTable = new TableCli();
  private WriteStreamOld _out;
  
  private boolean _isEmbedded;
  
  public WriteStreamOld getOut()
  {
    if (_out == null) {
      _out = VfsOld.openWrite(System.out);
      _out.setDisableClose(true);
      _out.setFlushOnNewline(true);
    }
    
    return _out;
  }
  
  
  public WriteStreamOld setOut(WriteStreamOld out)
  {
    WriteStreamOld oldOut = _out;
    
    _out = out;
    
    return oldOut;
  }
  
  public void flush()
  {
    if (_out != null) {
      try {
        _out.flush();
      } catch (IOException e) {
      }
    }
  }
  
  public void close()
  {
    flush();
  }
  
  public Object get(String name)
  {
    return _envTable.get(name);
  }
  
  public void put(String name, Object value)
  {
    if (value == null) {
      _envTable.put(name, NullCli.NULL);
    }
    else if (value instanceof ValueCli) {
      _envTable.put(name, (ValueCli) value);
    }
    else if (value instanceof Boolean) {
      _envTable.put(name, BooleanCli.create((Boolean) value));
    }
    else if (value instanceof String) {
      _envTable.put(name, new StringCli((String) value));
    }
    else if (value instanceof Number) {
      _envTable.put(name, new NumberCli(((Number) value).doubleValue()));
    }
    else {
      _envTable.put(name, new JavaValueCli(value));
    }
  }
  
  public void initLogging()
  {
    if (isEmbedded() || CurrentTime.isTest()) {
      return;
    }
    
    if (get("log.init") != null) {
      return;
    }
    
    put("log.init", true);
    
    removeLogging();
    
    Logger.getLogger("javax.management").setLevel(Level.INFO);
    
    if (_envTable.get("watchdog") == null) {
      LogHandlerConfig handler = new LogHandlerConfig();
      handler.setPath(Vfs.path("stdout:"));
      handler.init();
    }
  }
  
  protected void removeLogging()
  {
    if (! isEmbedded()) {
      Logger logger = Logger.getLogger("");

      for (Handler handler : logger.getHandlers()) {
        logger.removeHandler(handler);
      }
    }
  }

  public void setEmbedded(boolean isEmbedded)
  {
    _isEmbedded = isEmbedded;
  }

  public boolean isEmbedded()
  {
    return _isEmbedded;
  }

  public void exit(ExitCode code)
  {
    if (! _isEmbedded) {
      System.exit(code.ordinal());
    }
  }

  public void println(String msg)
  {
    try {
      getOut().println(msg);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
