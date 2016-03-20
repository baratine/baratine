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

package com.caucho.v5.amp.session;

import java.util.Objects;

import com.caucho.v5.amp.proxy.ActorAmpBean;
import com.caucho.v5.amp.proxy.StubClassSession;
import com.caucho.v5.amp.spi.ShutdownModeAmp;


/**
 * Baratine actor skeleton
 */
public class ActorSkeletonSession extends ActorAmpBean
{
  private ContextSession _context;
  private boolean _isModified;
  private String _keyString;
  private Object _key;
  private String _id;
  
  public ActorSkeletonSession(StubClassSession skel,
                               Object bean,
                               String key,
                               ContextSession context)
  {
    super(skel, bean, key, null);

    Objects.requireNonNull(context);

    _id = key;
    
    _key = key;
    _keyString = key;

    _context = context;
  }
  
  @Override
  public String getJournalKey()
  {
    return _keyString;
  }

  public String getId()
  {
    return _id;
  }
  
  /*
  private String getKeyTail(String path)
  {
    int p = path.indexOf("//");
    
    if (p > 0) {
      int q = path.indexOf('/', p + 2);
      
      if (q >= 0) {
        return path.substring(q);
      }
      else {
        return "";
      }
    }
    
    return path;
  }
  */
  
  /*
  private byte []generateKey(String path)
  {
    String keyPath = getKeyTail(path);
    
    int tailHash = (int) (Murmur64.generate(Murmur64.SEED, keyPath) & 0xffff);
    
    String keyString = String.valueOf(_key);
    
    Fnv128 fnv = new Fnv128();
    fnv.init();
    fnv.update(keyString);
    
    byte []key = new byte[32];
    
    // matches PodScheme
    key[key.length - 2] = (byte) (tailHash >> 8);
    key[key.length - 1] = (byte) (tailHash);
    
    int offset = key.length - 2 - 16;
    
    fnv.digest(key, offset, 16);
    
    int sublen = Math.min(keyString.length(), offset);
    
    for (int i = 0; i < sublen; i++) {
      key[i] = (byte) keyString.charAt(i);
    }
    
    return key;
  }
  */
  
  /*
  @Override
  protected SkeletonClassSession getSkeleton()
  {
    return (SkeletonClassSession) super.getSkeleton();
  }
  */
  
  protected StubClassSession getSkeletonSession()
  {
    return (StubClassSession) skeleton();
  }

  public Object getKey()
  {
    return _key;
  }
  
  @Override
  public boolean isLifecycleAware()
  {
    return true;
  }

  public void onModify()
  {
    if (! _isModified) {
      _isModified = true;
      
      _context.addDirty(this);
    }
  }
  
  public void afterBatch()
  {
    _context.flush();
  }
  
  public void afterFlush()
  {
    _isModified = false;
  }

  public void onCheckpoint()
  {
    _context.checkpoint();
  }
  
  public void onShutdown(ShutdownModeAmp mode)
  {
    _context.shutdown(mode);
  }

  public void load(Object value)
  {
    if (value != null) {
      getSkeletonSession().load(value, bean());
    }
  }

  public void delete()
  {
    _isModified = false;
    
    _context.delete(this);
    
    // XXX: possibly getSkeleton().clear()
    
  }
}
