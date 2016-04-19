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

package io.baratine.vault;

/**
 * Asset load state. Used by assets to communicate with the Vault driver
 * about asset loading.
 * 
 * Assets with a "state" field and AssetState type are injected
 * with the correct state.
 */
public enum StateAsset
{
  UNKNOWN {
    
  },
  // after @OnLoad, but the load had no data
  UNLOADED {
    
  },
  LOADED {
    @Override
    public void requireLoad() {}
    
    @Override
    public StateAsset create()
    {
      throw new IllegalStateException(toString());
    }
  },
  DELETING,
  DELETED;
  
  /**
   * Require the asset to be loaded.
   */
  public void requireLoad()
  {
    throw new IllegalStateException(toString());
  }
  
  /**
   * Creates an asset.
   * 
   * Throws an IllegalStateException if the asset is already loaded.
   */
  public StateAsset create()
  {
    return LOADED;
  }

  /**
   * Marks the object at to be deleted in the next @OnState.
   */
  public StateAsset delete()
  {
    return DELETING;
  }
}
