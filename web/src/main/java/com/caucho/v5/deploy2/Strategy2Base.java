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

package com.caucho.v5.deploy2;

import io.baratine.service.Result;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.lifecycle.LifecycleState;
import com.caucho.v5.util.ModulePrivate;

/**
 * The abstract strategy implements the start, update, and stop commands which
 * are common to all strategies.
 *
 * <table>
 * <tr><th>input  <th>stopped  <th>active  <th>modified   <th>error
 * <tr><td>start  <td>startImpl<td>-       <td>restartImpl<td>restartImpl
 * <tr><td>update <td>startImpl<td>-       <td>restartImpl<td>restartImpl
 * <tr><td>stop   <td>-        <td>stopImpl<td>stopImpl   <td>stopImpl
 * </table>
 */
@ModulePrivate
abstract class Strategy2Base<I extends DeployInstance2>
  implements DeployStrategy2<I>
{
  /**
   * Starts the instance.  Called from an admin start.
   *
   * @param deploy the owning controller
   */
  @Override
  public void start(DeployService2Impl<I> deploy, Result<I> result)
  {
    LifecycleState state = deploy.getState();
    
    if (state.isStopped()) {
      // server/1d03
      deploy.startImpl(result);
    }
    else if (state.isError()) {
      deploy.restartImpl(result);
    }
    else if (deploy.isModifiedNow()) {
      // server/1d0p
      deploy.restartImpl(result);
    }
    else { /* active */
      result.ok(deploy.get());
    }
  }

  /**
   * Stops the instance from an admin command.
   *
   * @param deploy the owning controller
   */
  @Override
  public void shutdown(DeployService2Impl<I> deploy,
                       ShutdownModeAmp mode,
                       Result<Boolean> result)
  {
    deploy.shutdownImpl(mode, result);
  }
}
