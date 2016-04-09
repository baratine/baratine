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

import java.lang.reflect.Type;
import java.util.ArrayList;

import com.caucho.v5.h3.SerializerH3;
import com.caucho.v5.h3.context.ContextH3;

/**
 * H3 serializer factory.
 */
public interface SerializerFactoryH3
{
  <T> SerializerH3Amp<T> serializer(Class<T> type, ContextH3 context);
  
  <T> SerializerH3<T> get(Type type);

  void initSerializers(ArrayList<SerializerH3Amp<?>> serArray);
}
