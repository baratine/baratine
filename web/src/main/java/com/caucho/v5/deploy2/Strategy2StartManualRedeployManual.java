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

import com.caucho.v5.lifecycle.LifecycleState;

import io.baratine.service.Result;

/**
 * The start-mode="manual", redeploy-model="manual" controller strategy.
 *
 * initial state = stop
 *
 * <table>
 * <tr><th>input  <th>stopped  <th>active  <th>modified   <th>error
 * <tr><td>start  <td>startImpl<td>-       <td>restartImpl<td>restartImpl
 * <tr><td>update <td>startImpl<td>-       <td>restartImpl<td>restartImpl
 * <tr><td>stop   <td>-        <td>stopImpl<td>stopImpl   <td>stopImpl
 * <tr><td>request<td>-        <td>-       <td>-          <td>-
 * <tr><td>include<td>-        <td>-       <td>-          <td>-
 * <tr><td>alarm  <td>-        <td>-       <td>-          <td>-
 * </table>
 */
class Strategy2StartManualRedeployManual<I extends DeployInstance2>
  extends Strategy2Base<I>
{
  private final static Strategy2StartManualRedeployManual<?> STRATEGY
    = new Strategy2StartManualRedeployManual<>();

  protected Strategy2StartManualRedeployManual()
  {
  }
  
  @Override
  public DeployMode redeployMode()
  {
    return DeployMode.MANUAL;
  }
  
  /**
   * Returns the start="lazy" redeploy="automatic" strategy
   *
   * @return the singleton strategy
   */
  @SuppressWarnings("unchecked")
  public static <I extends DeployInstance2> DeployStrategy2<I> strategy()
  {
    return (DeployStrategy2<I>) STRATEGY;
  }
  
  /**
   * Called at initialization time for automatic start.
   *
   * @param deploy the owning controller
   */
  @Override
  public void startOnInit(DeployService2Impl<I> deploy, Result<I> result)
  {
    deploy.startImpl(result);
  }

  /**
   * Checks for updates from an admin command.  The target state will be the
   * initial state, i.e. update will not start a lazy instance.
   *
   * @param deploy the owning controller
   */
  @Override
  public void update(DeployService2Impl<I> deploy, Result<I> result)
  {
    LifecycleState state = deploy.getState();
    
    if (state.isStopped()) {
      deploy.startImpl(result);
    }
    else if (state.isError()) {
      deploy.restartImpl(result);
    }
    else if (deploy.isModifiedNow()) {
      deploy.restartImpl(result);
    }
    else { /* active */
      result.ok(deploy.get());
    }
  }


  /**
   * Returns the current instance.  This strategy does not lazily restart
   * the instance.
   *
   * @param deploy the owning controller
   * @return the current deploy instance
   */
  @Override
  public void request(DeployService2Impl<I> deploy, 
                      Result<I> result)
  {
    result.ok(deploy.get());
  }

  /**
   * Returns the current instance.  This strategy does not lazily restart
   * the instance.
   *
   * @param deploy the owning controller
   */
  @Override
  public void alarm(DeployService2Impl<I> deploy, Result<I> result)
  {
    result.ok(deploy.get());
  }
}
