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

import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.h3.context.ContextH3;
import com.caucho.v5.h3.query.QueryH3Amp;
import com.caucho.v5.h3.ser.SerializerH3Amp;

/**
 * H3 output interface
 */
class InH3Impl implements InH3Amp
{
  private ContextH3 _context;
  private InRawH3 _in;
  
  private ArrayList<SerializerH3Amp<?>> _serArray = new ArrayList<>();
  
  InH3Impl(ContextH3 context, InRawH3 in)
  {
    Objects.requireNonNull(context);
    Objects.requireNonNull(in);
    
    _context = context;
    _in = in;
    
    context.initSerializers(_serArray);
  }

  @Override
  public void readNull()
  {
    _in.readNull();
  }

  @Override
  public boolean readBoolean()
  {
    return _in.readBoolean();
  }

  @Override
  public long readLong()
  {
    return _in.readLong();
  }

  @Override
  public double readDouble()
  {
    return _in.readDouble();
  }

  @Override
  public String readString()
  {
    return _in.readString();
  }

  @Override
  public Object readObject()
  {
    return _in.readObject(this);
  }

  @Override
  public <T> T readObject(Class<T> type)
  {
    return (T) readObject();
  }

  @Override
  public SerializerH3Amp<?> serializer(int id)
  {
    SerializerH3Amp<?> ser = _serArray.get(id);
    
    Objects.requireNonNull(ser);
    
    return ser;
  }

  @Override
  public void define(int id, ClassInfoH3 info)
  {
    SerializerH3Amp<?> ser = _context.define(info);

    while (_serArray.size() <= id) {
      _serArray.add(null);
    }
    
    _serArray.add(id, ser);
  }

  public void query(QueryH3Amp queryAmp, Object[] values)
  {
    _in.scan(this, queryAmp.root(), values);
  }

  @Override
  public void close()
  {
    InRawH3 in = _in;
    
    if (in != null) {
      _in = null;
      in.close();
    }
  }
}
