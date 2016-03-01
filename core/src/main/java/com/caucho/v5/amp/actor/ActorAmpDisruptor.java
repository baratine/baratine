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

package com.caucho.v5.amp.actor;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.proxy.SkeletonClass;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.LoadState;
import com.caucho.v5.amp.spi.LoadStateLoadBase;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodAmp;

/**
 * amp disruptor method
 */
public class ActorAmpDisruptor extends ActorAmpBase
{
  private final SkeletonClass _skel;
  private final JournalAmp _journal;
  private final ActorAmp _actorMain;
  private final String _path;
  
  public ActorAmpDisruptor(SkeletonClass skel,
                 JournalAmp journal,
                 ActorAmp actorMain,
                 String path)
  {
    _skel = skel;
    _journal = journal;
    _actorMain = actorMain;
    _path = path;
  }
  
  public String getName()
  {
    return _path;
  }
  
  @Override
  public Class<?> getApiClass()
  {
    return _skel.getApiClass();
  }
  
  @Override
  public boolean isExported()
  {
    return _skel.isExported();
  }
  
  @Override
  public JournalAmp getJournal()
  {
    return _journal;
  }
  
  @Override
  public LoadState load(MessageAmp msg)
  {
    // return _actor.load(msg, actor);
    ActorAmp actorMain = _actorMain;
    
    if (actorMain != null) {
      return _actorMain.load(msg);
    }
    else {
      return LoadStateLoadBase.LOAD;
    }
  }

  @Override
  public void onModify()
  {
    _actorMain.onModify();
  }

  /*
  @Override
  public ActorAmp getActor(ActorAmp actorMessage)
  {
    if (_actorMain != null) {
      return this;
    }
    else {
      return actorMessage;
    }
  }
  */

  @Override
  public Object bean()
  {
    if (_actorMain != null) {
      return _actorMain.bean();
    }
    
    return super.bean();
  }
  
  @Override
  public Object onLookup(String path, ServiceRefAmp parentRef)
  {
    if (_actorMain != null) {
      return _actorMain.onLookup(path, parentRef);
    }
    
    return super.onLookup(path, parentRef);
  }
  
  @Override
  public MethodAmp []getMethods()
  {
    return _skel.getMethods();
    
    /*
    MethodAmp []baseMethods = _skel.getMethods();
    
    MethodAmp []methods = new MethodAmp[baseMethods.length];
    
    for (int i = 0; i < baseMethods.length; i++) {
      methods[i] = new MethodAmpDisruptor(baseMethods[i]);
    }
    
    return methods;
    */
  }
  
  @Override
  public MethodAmp getMethod(String name)
  {
    return _skel.getMethod(this, name);
    
    /*
    MethodAmp method = _skel.getMethod(this, name);
    
    return new MethodAmpDisruptor(method);
    */
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _skel + "]";
  }
}
