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

package javax.json;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.SoftReference;
import java.util.ServiceLoader;
import java.util.WeakHashMap;

import javax.json.spi.JsonProvider;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParserFactory;

public class Json
{
  private static WeakHashMap<ClassLoader,SoftReference<JsonProvider>>
   _providerMap = new WeakHashMap<>();
   
  private Json() {}
  
  public static JsonGenerator createGenerator(OutputStream os)
  {
    return getProvider().createGenerator(os);
  }
  
  public static JsonGenerator createGenerator(Writer out)
  {
    return getProvider().createGenerator(out);
  }
  
  public static JsonGeneratorFactory createGeneratorFactory()
  {
    return getProvider().createGeneratorFactory();
  }
  
  public static JsonGeneratorFactory createGeneratorFactory(JsonConfiguration config)
  {
    return getProvider().createGeneratorFactory(config);
  }
  
  public static JsonParser createParser(InputStream is)
  {
    return getProvider().createParser(is);
  }
  
  public static JsonParser createParser(Reader reader)
  {
    return getProvider().createParser(reader);
  }
  
  public static JsonParserFactory createParserFactory()
  {
    return getProvider().createParserFactory();
  }
  
  public static JsonParserFactory createParserFactory(JsonConfiguration config)
  {
    return getProvider().createParserFactory(config);
  }
  
  private static JsonProvider getProvider()
  {
    Thread thread = Thread.currentThread();
    ClassLoader loader = thread.getContextClassLoader();
    
    synchronized (_providerMap) {
      SoftReference<JsonProvider> providerRef = _providerMap.get(loader);
    
      JsonProvider provider = null;
    
      if (providerRef != null) {
        provider = providerRef.get();
      }
    
      if (provider == null) {
        provider = createProvider();
        
        _providerMap.put(loader, new SoftReference<>(provider));
      }
      
      return provider;
    }
  }
  
  private static JsonProvider createProvider()
  {
    for (JsonProvider provider : ServiceLoader.load(JsonProvider.class)) {
      return provider;
    }
    
    throw new UnsupportedOperationException("Cannot find JsonProvider");
  }
}