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

package com.caucho.v5.cli.shell;

import java.io.PrintStream;
import java.util.Objects;

import com.caucho.v5.health.shutdown.ExitCode;

import io.baratine.web.WebServer;

/**
 * Runtime environment for the command-line
 */
public class EnvCli
{
  //private TableCli _envTable = new TableCli();
  private PrintStream _out;
  
  private boolean _isEmbedded;
  
  private TableCli _envTable = new TableCli();
  
  public PrintStream out()
  {
    if (_out == null) {
      _out = System.out;
    }
    
    return _out;
  }
  
  
  public PrintStream out(PrintStream out)
  {
    Objects.requireNonNull(out);
    
    PrintStream oldOut = _out;
    
    _out = out;
    
    return oldOut;
  }
  
  public void flush()
  {
    if (_out != null) {
      _out.flush();
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
  
  public <T> T get(Class<T> type)
  {
    return (T) get(type.getSimpleName());
  }
  
  public void put(Object value)
  {
    put(value.getClass(), value);
  }
  
  public void put(Class<?> type, Object value)
  {
    put(type.getSimpleName(), value);
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

  public void remove(Class<?> type)
  {
    _envTable.remove(type);
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
    out().println(msg);
  }
}
