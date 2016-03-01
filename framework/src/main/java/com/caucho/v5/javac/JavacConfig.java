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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.javac;

import com.caucho.v5.config.types.Period;
import com.caucho.v5.loader.EnvironmentLocal;

import javax.annotation.PostConstruct;

public class JavacConfig {
  private static final EnvironmentLocal<JavacConfig> _localJavac =
  new EnvironmentLocal<JavacConfig>();

  // private String _compiler = "internal";
  private String _compiler = "tools";
  private String _args;
  private String _encoding;
  private int _maxBatch = 64;

  private long _startTimeout = 10 * 1000L;
  private long _maxCompileTime = 120 * 1000L;

  /**
   * Returns the environment configuration.
   */
  public static JavacConfig getLocalConfig()
  {
    JavacConfig config;

    config = _localJavac.get();

    if (config != null)
      return config;
    else
      return new JavacConfig();
  }

  /**
   * Sets the compiler.
   */
  public void setCompiler(String compiler)
  {
    _compiler = compiler;
  }

  /**
   * Gets the compiler.
   */
  public String getCompiler()
  {
    return _compiler;
  }

  /**
   * Sets the args.
   */
  public void setArgs(String args)
  {
    _args = args;
  }

  /**
   * Gets the args.
   */
  public String getArgs()
  {
    return _args;
  }

  /**
   * Sets the encoding.
   */
  public void setEncoding(String encoding)
  {
    _encoding = encoding;
  }

  /**
   * Gets the encoding.
   */
  public String getEncoding()
  {
    return _encoding;
  }

  /**
   * Sets the number of files to batch.
   */
  public void setMaxBatch(int max)
  {
    _maxBatch = max;
  }

  /**
   * Sets the number of files to batch.
   */
  public int getMaxBatch()
  {
    return _maxBatch;
  }

  /**
   * Sets the compiler args (backwards compat)
   */
  public void setCompilerArgs(String args)
  {
    setArgs(args);
  }

  public long getStartTimeout()
  {
    return _startTimeout ;
  }
  
  public void setStartTimeout(Period period)
  {
    _startTimeout = period.getPeriod();
  }

  public long getMaxCompileTime()
  {
    return _maxCompileTime;
  }
  
  public void setMaxCompileTime(Period period)
  {
    _maxCompileTime = period.getPeriod();
  }
  
  /**
   * Stores self.
   */
  @PostConstruct
  public void init()
  {
    _localJavac.set(this);
  }
}
