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

package com.caucho.v5.cli.baratine;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Objects;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import com.caucho.v5.cli.daemon.ArgsDaemon;
import com.caucho.v5.cli.shell_old.EnvCliOld;
import com.caucho.v5.vfs.TempStream;
import com.caucho.v5.vfs.WriteStream;

/**
 * Script engine for baratine server.
 */
public class ScriptEngineBaratine extends AbstractScriptEngine
{
  private ScriptEngineFactoryBaratine _factory;
  private ArgsCliMainBaratine _argsParent;
  private EnvCliOld _env;
  
  public ScriptEngineBaratine(ScriptEngineFactoryBaratine factory)
  {
    _factory = factory;
    
    _argsParent = new ArgsCliMainBaratine();
    _env = _argsParent.envCli();
  }

  @Override
  public Object eval(String script, ScriptContext context)
      throws ScriptException
  {
    Objects.requireNonNull(script);
    Objects.requireNonNull(context);
    
    String []args = script.trim().split("[\\s]+");

    try {
      ArgsDaemon argsCommand = _argsParent.createChild(args);
      
      TempStream tos = new TempStream();
      
      try (WriteStream sOut = new WriteStream(tos)) {
        WriteStream oldOut = _env.setOut(sOut);
      
        try {
          argsCommand.doCommand();
        } finally {
          _env.setOut(oldOut);
        }
      }
      
      InputStream is = tos.getInputStream();
      
      StringBuilder sb = new StringBuilder();
      
      int ch;
      
      while ((ch = is.read()) >= 0) {
        sb.append((char) ch);
      }
      
      return sb.toString();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new ScriptException(e);
    }
  }

  @Override
  public Object eval(Reader reader, ScriptContext context)
    throws ScriptException
  {
    Objects.requireNonNull(reader);
    Objects.requireNonNull(context);
    
    StringBuilder sb = new StringBuilder();
    
    int ch;

    try {
      while ((ch = reader.read()) >= 0) {
        sb.append(ch);
      }
    } catch (IOException e) {
      throw new ScriptException(e);
    }

    return eval(sb.toString(), context);
  }

  @Override
  public Bindings createBindings()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public ScriptEngineFactory getFactory()
  {
    return _factory;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
