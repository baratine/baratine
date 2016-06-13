/*
 * Copyright (c) 2001-2016 Caucho Technology, Inc.  All rights reserved.
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.h3.io;

import com.caucho.v5.h3.H3.OutFactoryBuilderH3;
import com.caucho.v5.h3.OutFactoryH3;

/**
 * Factory for H3 output.
 */
public class OutFactoryBuilderH3Impl implements OutFactoryBuilderH3
{
  private boolean _isGraph;
  
  @Override
  public OutFactoryBuilderH3 graph(boolean isGraph)
  {
    _isGraph = isGraph;
    
    return this;
  }
  
  public boolean isGraph()
  {
    return _isGraph;
  }
  
  @Override
  public OutFactoryH3 get()
  {
    return new OutFactoryH3Impl(this);
  }
}
