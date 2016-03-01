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

package com.caucho.junit;

import com.caucho.v5.bartender.pod.PodBartender;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(value = ConfigurationsBaratine.class)
public @interface ConfigurationBaratine
{
  /**
   * Specifies host for Baratine
   *
   * @return Baratine host
   */
  String host() default "localhost";

  /**
   * Baratine Root directory base. The actual directory used by Baratine will
   * default to rootDirectoryBase() + '/' + "junit-baratine-runner"
   */
  String rootDirectoryBase() default "/tmp";

  /**
   * Specifies port for Baratine
   *
   * @return Baratine port
   */
  int port() default -1;

  /**
   * Specifies applications to deploy
   *
   * @return archives to deploy
   */
  String[] deploy() default {};

  /**
   * Specifies services to deploy
   */
  Class<?>[] services() default {};

  /**
   * Specifies log level to set to the specified loggers
   *
   * @return loglevel to use
   */
  @Deprecated
  String logLevel() default "OFF";

  /**
   * Specifies loggers to set to a specified log level
   *
   * @return names of loggers
   */
  @Deprecated
  String[] logNames() default "";

  /**
   * Specifies loggers
   * @return
   */
  Log[] logs() default {};

  /**
   * Specifies pod
   * @return
   */
  String pod() default "pod";

  PodBartender.PodType podType() default PodBartender.PodType.solo;

  /**
   * Journal delay - default journal delay for testing replay
   */
  long journalDelay() default -1;

  /**
   * If >= 0, use artificial test time. 
   */
  long testTime() default -1;

  static public @interface Log
  {
    String name() default "";

    String level() default "INFO";
  }
}
