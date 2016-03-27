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

package com.caucho.v5.amp.stub;

import java.lang.reflect.AnnotatedType;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.spi.LoadState;
import com.caucho.v5.amp.spi.LoadStateLoadBase;
import com.caucho.v5.amp.spi.MessageAmp;

/**
 * amp disruptor method
 */
public class StubAmpJournal extends StubAmpBase
{
  private final StubClass _stubClass;
  private final JournalAmp _journal;
  private final StubAmp _stubMain;
  private final String _path;
  
  public StubAmpJournal(StubClass stubClass,
                 JournalAmp journal,
                 StubAmp actorMain,
                 String path)
  {
    _stubClass = stubClass;
    _journal = journal;
    _stubMain = actorMain;
    _path = path;
  }
  
  @Override
  public String name()
  {
    return _path;
  }
  
  @Override
  public AnnotatedType api()
  {
    return _stubClass.api();
  }
  
  @Override
  public boolean isPublic()
  {
    return _stubClass.isPublic();
  }
  
  @Override
  public JournalAmp journal()
  {
    return _journal;
  }
  
  @Override
  public LoadState load(MessageAmp msg)
  {
    // return _actor.load(msg, actor);
    StubAmp actorMain = _stubMain;
    
    if (actorMain != null) {
      return _stubMain.load(msg);
    }
    else {
      return LoadStateLoadBase.LOAD;
    }
  }

  @Override
  public void onModify()
  {
    _stubMain.onModify();
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
    if (_stubMain != null) {
      return _stubMain.bean();
    }
    
    return super.bean();
  }
  
  @Override
  public Object onLookup(String path, ServiceRefAmp parentRef)
  {
    if (_stubMain != null) {
      return _stubMain.onLookup(path, parentRef);
    }
    
    return super.onLookup(path, parentRef);
  }
  
  @Override
  public MethodAmp []getMethods()
  {
    return _stubClass.getMethods();
    
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
    return _stubClass.getMethod(this, name);
    
    /*
    MethodAmp method = _skel.getMethod(this, name);
    
    return new MethodAmpDisruptor(method);
    */
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _stubClass + "]";
  }
}
