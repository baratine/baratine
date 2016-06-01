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

package com.caucho.v5.h3.ser;

import java.util.Set;

/**
 * H3 set-typed serializer.
 */
public class SerializerH3SetPredef<T extends Set<?>> extends
  SerializerH3Collection<T>
{
  private int _typeSequence;

  SerializerH3SetPredef(Class<? extends T> type, int typeSequence)
  {
    super(type);
    
    _typeSequence = typeSequence;
  }
  
  @Override
  public int typeSequence()
  {
    return _typeSequence;
  }
}