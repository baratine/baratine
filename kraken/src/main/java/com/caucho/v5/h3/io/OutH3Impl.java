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

package com.caucho.v5.h3.io;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Objects;

import com.caucho.v5.h3.OutH3;
import com.caucho.v5.h3.SerializerH3;
import com.caucho.v5.h3.context.ContextH3;
import com.caucho.v5.h3.ser.SerializerH3Amp;

/**
 * H3 output interface
 */
class OutH3Impl implements OutH3
{
  private ContextH3 _context;
  private OutRawH3 _out;
  
  private HashMap<SerializerH3<?>,Integer> _objMap = new HashMap<>();
  private int _typeSequence;
  
  OutH3Impl(ContextH3 context, OutRawH3 out)
  {
    Objects.requireNonNull(context);
    Objects.requireNonNull(out);
    
    _context = context;
    _out = out;
    
    _typeSequence = _context.typeSequence();
  }
  
  protected OutRawH3 out()
  {
    return _out;
  }
  
  @Override
  public void writeNull()
  {
    _out.writeNull();
  }

  @Override
  public void writeBoolean(boolean value)
  {
    _out.writeBoolean(value);
  }
  
  @Override
  public void writeLong(long value)
  {
    _out.writeLong(value);
  }
  
  @Override
  public void writeDouble(double value)
  {
    _out.writeDouble(value);
  }
  
  @Override
  public void writeString(String value)
  {
    _out.writeString(value);
  }
  
  @Override
  public void writeBinary(byte []buffer, int offset, int length)
  {
    _out.writeBinary(buffer, offset, length);
  }
 
  @Override
  public void writeStringPart(String value)
  {
    //_out.writeStringPart(value);
  }
  
  @Override
  public void writeBinaryPart(byte []buffer, int offset, int length)
  {
    //_out.writeBinaryPart(buffer, offset, length);
  }

  @Override
  public <T> SerializerH3<T> serializer(Class<T> type)
  {
    return _context.serializer(type);
  }

  @Override
  public <T> SerializerH3<T> serializer(Type type)
  {
    return _context.serializer(type);
  }

  @Override
  public <T> void writeObject(T object)
  {
    if (object == null) {
      writeNull();
    }
    else {
      SerializerH3<T> ser = (SerializerH3<T>) serializer(object.getClass());
      
      writeObject(object, ser);
    }
  }

  @Override
  public <T> void writeObject(T object, SerializerH3<T> serializer)
  {
    Objects.requireNonNull(serializer);
    
    if (object == null) {
      _out.writeNull();
      return;
    }
    
    Integer objIndex = _objMap.get(serializer);
    
    if (objIndex == null) {
      SerializerH3Amp<T> serializerAmp = (SerializerH3Amp<T>) serializer;
      
      int typeSequence = serializerAmp.typeSequence();
      
      if (typeSequence > 0) {
        objIndex = typeSequence;
      }
      else {
        objIndex = ++_typeSequence;
      }
      
      _objMap.put(serializer, objIndex);
    
      if (typeSequence == 0) {
        serializer.writeDefinition(_out, objIndex);
      }
    }
    
    serializer.writeObject(_out, objIndex, object, this);
  }
  
  public void flush()
  {
    //_out.flush();
  }
  
  @Override
  public void close()
  {
    OutRawH3 out = _out;
    
    if (out != null) {
      _out = null;
      out.close();
    }
  }
}
