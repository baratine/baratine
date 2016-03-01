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

package com.caucho.v5.bartender.pod;

import io.baratine.service.Result;


public interface PodLocalService
{
  public static final String PATH = "/bartender-pod";

  void start();
  
  PodBartender findPod(String podName);
  
  PodBartender findActivePod(String podName);
  
  ServerPod findActiveServer(String podName);
  
  Iterable<PodBartender> getPods();
  
  /*
  void createPod(String name, 
                 PodBartender.Type type, 
                 int size, 
                 Result<PodBartender> result);
  PodBartender createPod(String name, 
                         PodBartender.Type type, 
                         int size);
                         */

  //void createPodByBuilder(PodBuilder builder, Result<PodBartender> result);
  // PodBartender createPodByBuilder(PodBuilder builder);
  
  void createPodByUpdate(UpdatePod createPodUpdate);

  /**
   * Initialize pods after the join completes.
   */
  void onJoinStart(Result<Void> result);

  UpdatePod getInitPod(String id);

  void stop();

  //void createPodStub(String name, Result<PodBartender> result);
  //PodBartender createPodStub(String name);
  
  /*
  void createPodManager(String name, 
                        PodBartender.Type type,
                        int size, 
                        Result<UpdatePod> result);
                        */

  /*
  void createPodManagerByBuilder(String name, 
                                 PodBuilder builder,
                                 Result<UpdatePod> chain);
                                 */

  //void createPodBuilder(UpdatePod updatePod);

  //void updatePod(UpdatePod updatePod, Result<UpdatePod> result);
}
