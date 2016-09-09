/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.convert;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.baratine.convert.Convert;
import io.baratine.inject.Priority;
import io.baratine.service.Result;

/**
 * Default string converter.
 * read-only properties map.
 */
@Priority(-100)
public class ConvertStringDefault extends ConvertFromBase<String>
{
  private static final Logger log
    = Logger.getLogger(ConvertStringDefault.class.getName());
  
  private static final Map<Class<?>,Convert<String,?>> _converterMap
    = new HashMap<>();
  
  private ConvertStringDefault()
  {
    super(String.class, _converterMap);
  }
  
  public static ConvertStringDefault get()
  {
    return new ConvertStringDefault();
  }
  
  public static Map<Class<?>,Convert<String,?>> getDefaultMap()
  {
    return _converterMap;
  }

  @Override
  public <T> T convert(Class<T> targetType, String source)
  {
    Convert<String,T> convert = converter(targetType);
    
    if (convert != null) {
      return convert.convert(source);
    }
    else {
      return null;
    }
  }

  @Override
  public <T> void convert(Class<T> targetType, String source, Result<T> result)
  {
    result.ok(convert(targetType, source));
  }
  
  @Override
  @SuppressWarnings({"unchecked"})
  public <T> Convert<String,T> converter(Class<T> targetType)
  {
    Convert<String,T> convert = (Convert<String,T>) _converterMap.get(targetType);
    
    if (convert != null) {
      return convert;
    }
    
    return autoConverter(targetType);
  }
  
  @SuppressWarnings({"unchecked","rawtypes"})
  private <T> Convert<String,T> autoConverter(Class<T> type)
  {
    if (Enum.class.isAssignableFrom(type)) {
      return new ConvertStringToEnum(type);
    }
    
    Convert<String,T> converter;
    
    if ((converter = staticMethod(type, "valueOf", String.class)) != null) {
      return converter;
    }
    
    if ((converter = staticMethod(type, "valueOf", CharSequence.class)) != null) {
      return converter;
    }
    
    if ((converter = staticMethod(type, "parse", String.class)) != null) {
      return converter;
    }
    
    if ((converter = staticMethod(type, "parse", CharSequence.class)) != null) {
      return converter;
    }
    
    return null;
  }
  
  private <T> Convert<String,T> staticMethod(Class<T> type, 
                                             String name,
                                             Class<?> argType)
  {
    try {
      Method method = type.getMethod(name, argType);
    
      if (method != null && Modifier.isStatic(method.getModifiers())) {
        return new ConvertStringToValueOf(method);
      }
    } catch (Exception e) {
      log.log(Level.FINEST, e.toString(), e);
    }
    
    return null;
  }
  
  public static ConvertFromBuilder<String> build()
  {
    ConvertFromBuilderImpl<String> builder
      = new ConvertFromBuilderImpl<>(String.class);
    
    builder.add(get());
    
    return builder;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  /**
   * String to boolean
   */
  private static class ConvertStringToBoolean implements Convert<String,Boolean>
  {
    @Override
    public Boolean convert(String source)
    {
      if (source == null) {
        return null;
      }
      else if (source.isEmpty()) {
        return Boolean.FALSE;
      }
      else if (source.equals("false")
               || source.equals("no")
               || source.equals("0")) {
        return Boolean.FALSE;
      }
      else {
        return Boolean.TRUE;
      }
    }
  }
  
  /**
   * String to boolean
   */
  private static class ConvertStringToBooleanPrim implements Convert<String,Boolean>
  {
    @Override
    public Boolean convert(String source)
    {
      if (source == null) {
        return Boolean.FALSE;
      }
      else if (source.isEmpty()) {
        return Boolean.FALSE;
      }
      else if (source.equals("false")
               || source.equals("no")
               || source.equals("0")) {
        return Boolean.FALSE;
      }
      else {
        return Boolean.TRUE;
      }
    }
  }
  
  /**
   * String to char
   */
  private static class ConvertStringToCharPrim implements Convert<String,Character>
  {
    @Override
    public Character convert(String source)
    {
      if (source == null) {
        return (char) 0;
      }
      else if (source.isEmpty()) {
        return (char) 0;
      }
      else {
        return source.charAt(0);
      }
    }
  }
  
  /**
   * String to char
   */
  private static class ConvertStringToChar implements Convert<String,Character>
  {
    @Override
    public Character convert(String source)
    {
      if (source == null) {
        return null;
      }
      else if (source.isEmpty()) {
        return null;
      }
      else {
        return source.charAt(0);
      }
    }
  }
  
  /**
   * String to byte
   */
  private static class ConvertStringToBytePrim implements Convert<String,Byte>
  {
    @Override
    public Byte convert(String source)
    {
      if (source == null || source.isEmpty()) {
        return new Byte((byte) 0);
      }
      else {
        return Byte.decode(source);
      }
    }
  }
  
  /**
   * String to byte
   */
  private static class ConvertStringToByte implements Convert<String,Byte>
  {
    @Override
    public Byte convert(String source)
    {
      if (source == null) {
        return null;
      }
      else if (source.isEmpty()) {
        return new Byte((byte) 0);
      }
      else {
        return Byte.decode(source);
      }
    }
  }
  
  /**
   * String to short
   */
  private static class ConvertStringToShortPrim implements Convert<String,Short>
  {
    @Override
    public Short convert(String source)
    {
      if (source == null || source.isEmpty()) {
        return new Short((short) 0);
      }
      else {
        return Short.decode(source);
      }
    }
  }
  
  /**
   * String to short
   */
  private static class ConvertStringToShort implements Convert<String,Short>
  {
    @Override
    public Short convert(String source)
    {
      if (source == null) {
        return null;
      }
      else if (source.isEmpty()) {
        return new Short((short) 0);
      }
      else {
        return Short.decode(source);
      }
    }
  }
  
  /**
   * String to int
   */
  private static class ConvertStringToIntPrim implements Convert<String,Integer>
  {
    @Override
    public Integer convert(String source)
    {
      if (source == null || source.isEmpty()) {
        return new Integer(0);
      }
      else {
        return Integer.decode(source);
      }
    }
  }
  
  /**
   * String to int
   */
  private static class ConvertStringToInt implements Convert<String,Integer>
  {
    @Override
    public Integer convert(String source)
    {
      if (source == null) {
        return null;
      }
      else if (source.isEmpty()) {
        return new Integer(0);
      }
      else {
        return Integer.decode(source);
      }
    }
  }
  
  /**
   * String to long
   */
  private static class ConvertStringToLongPrim implements Convert<String,Long>
  {
    @Override
    public Long convert(String source)
    {
      if (source == null || source.isEmpty()) {
        return new Long(0);
      }
      else {
        return Long.decode(source);
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[]";
    }
  }
    
  /**
   * String to long
   */
  private static class ConvertStringToLong implements Convert<String,Long>
  {
    @Override
    public Long convert(String source)
    {
      if (source == null) {
        return null;
      }
      else if (source.isEmpty()) {
        return new Long(0);
      }
      else {
        return Long.decode(source);
      }
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[]";
    }
  }
  
  /**
   * String to float
   */
  private static class ConvertStringToFloatPrim implements Convert<String,Float>
  {
    @Override
    public Float convert(String source)
    {
      if (source == null || source.isEmpty()) {
        return new Float(0);
      }
      else {
        return Float.valueOf(source);
      }
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[]";
    }
  }
  
  /**
   * String to float
   */
  private static class ConvertStringToFloat implements Convert<String,Float>
  {
    @Override
    public Float convert(String source)
    {
      if (source == null) {
        return null;
      }
      else if (source.isEmpty()) {
        return new Float(0);
      }
      else {
        return Float.valueOf(source);
      }
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[]";
    }
  }
    
  /**
   * String to double
   */
  private static class ConvertStringToDoublePrim implements Convert<String,Double>
  {
    @Override
    public Double convert(String source)
    {
      if (source == null || source.isEmpty()) {
        return new Double(0);
      }
      else {
        return Double.valueOf(source);
      }
    }
      
    public String toString()
    {
      return getClass().getSimpleName() + "[]";
    }
  }
  
/**
 * String to double
 */
private static class ConvertStringToDouble implements Convert<String,Double>
{
  @Override
  public Double convert(String source)
  {
    if (source == null) {
      return null;
    }
    else if (source.isEmpty()) {
      return new Double(0);
    }
    else {
      return Double.valueOf(source);
    }
  }
    
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
  
  private static class ConvertStringToPath implements Convert<String,Path>
  {
    @Override
    public Path convert(String source)
    {
      if (source.startsWith("file:")) {
        return FileSystems.getDefault().getPath(source);
      }
      else if (source.startsWith("/")) {
        return FileSystems.getDefault().getPath(source);
      }
      else {
        return Paths.get(source);
      }
    }
  }
  
  /**
   * String to enum
   */
  private static class ConvertStringToEnum<T extends Enum<T>>
    implements Convert<String,T>
  {
    private Class<T> _type;
    
    ConvertStringToEnum(Class<T> type)
    {
      Objects.requireNonNull(type);
      
      _type = type;
    }
    
    @Override
    public T convert(String source)
    {
      if (source == null || source.isEmpty()) {
        return null;
      }
      else {
        return Enum.valueOf(_type, source);
      }
    }
  }
  
  /**
   * String to enum
   */
  private static class ConvertStringToValueOf<T>
    implements Convert<String,T>
  {
    private Method _method;
    
    ConvertStringToValueOf(Method method)
    {
      Objects.requireNonNull(method);
      
      _method = method;
      _method.setAccessible(true);
    }
    
    @Override
    public T convert(String source)
    {
      if (source == null || source.isEmpty()) {
        return null;
      }
      
      try {
        return (T) _method.invoke(null, source);
      } catch (Exception e) {
        throw new ConvertException(e);
      }
    }
  }
  
  static {
    _converterMap.put(Boolean.class, new ConvertStringToBoolean());
    _converterMap.put(boolean.class, new ConvertStringToBooleanPrim());
    
    _converterMap.put(Character.class, new ConvertStringToChar());
    _converterMap.put(char.class, new ConvertStringToCharPrim());
    
    _converterMap.put(Byte.class, new ConvertStringToByte());
    _converterMap.put(byte.class, new ConvertStringToBytePrim());
    
    _converterMap.put(Short.class, new ConvertStringToShort());
    _converterMap.put(short.class, new ConvertStringToShortPrim());
    
    _converterMap.put(Integer.class, new ConvertStringToInt());
    _converterMap.put(int.class, new ConvertStringToIntPrim());
    
    _converterMap.put(Long.class, new ConvertStringToLong());
    _converterMap.put(long.class, new ConvertStringToLongPrim());
    
    _converterMap.put(Float.class, new ConvertStringToFloat());
    _converterMap.put(float.class, new ConvertStringToFloatPrim());
    
    _converterMap.put(Double.class, new ConvertStringToDouble());
    _converterMap.put(double.class, new ConvertStringToDoublePrim());
    
    _converterMap.put(Path.class, new ConvertStringToPath());
    
    //DEFAULT = new ConvertStringDefault();
  }
}
