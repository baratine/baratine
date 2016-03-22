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

package com.caucho.v5.amp.vault;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.service.ActorFactoryAmp;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.stub.StubFactoryImpl;
import com.caucho.v5.amp.stub.ClassStub;
import com.caucho.v5.amp.stub.StubAmpBean;
import com.caucho.v5.amp.stub.StubGenerator;
import com.caucho.v5.config.Priority;
import com.caucho.v5.inject.impl.ServiceImpl;
import com.caucho.v5.inject.type.TypeRef;

import io.baratine.inject.Key;
import io.baratine.service.Asset;
import io.baratine.service.Vault;

/**
 * Creates an actor supplier based on a Resource and Store.
 */
@Priority(-100)
public class StubGeneratorVault implements StubGenerator
{
  private ArrayList<VaultDriver<?,?>> _drivers = new ArrayList<>();
  
  @Override
  public ActorFactoryAmp factory(Class<?> serviceClass,
                                 ServiceManagerAmp ampManager,
                                 ServiceConfig configService)
  {
    if (Vault.class.isAssignableFrom(serviceClass)) {
      return factoryResource(serviceClass, ampManager, configService);
    }
    else if (serviceClass.isAnnotationPresent(Asset.class)) {
      return factoryStore(serviceClass, ampManager, configService);
    }
    else {
      return null;
    }
  }

  private ActorFactoryAmp factoryResource(Class<?> serviceClass,
                                          ServiceManagerAmp ampManager,
                                          ServiceConfig configService)
  {
    if (! Vault.class.isAssignableFrom(serviceClass)) {
      throw new IllegalStateException();
    }
    
    TypeRef typeRef = TypeRef.of(serviceClass);
    TypeRef resourceRef = typeRef.to(Vault.class);
    TypeRef entityRef = resourceRef.param("T");
    TypeRef idRef = resourceRef.param("ID");
    
    VaultConfig configResource = new VaultConfig();
    configResource.entityType(entityRef.rawClass());
    configResource.idType(idRef.rawClass());
    
    VaultDriver<?,?> driver = driver(ampManager,
                                        serviceClass, 
                                        entityRef.rawClass(),
                                        idRef.rawClass(),
                                        configService.address());
    //driver must not be null because ActorAmpBean expects not null
    //for bean parameter
    //if (driver != null) {
    Objects.requireNonNull(driver);
    configResource.driver(driver);
    //}

    Object bean;
    
    if (Modifier.isAbstract(serviceClass.getModifiers())) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      
      bean = ClassGeneratorVault.create(serviceClass, 
                                             classLoader,
                                             driver);
      
      Consumer<Object> injector = 
        (Consumer) ampManager.inject().injector(bean.getClass());
      
      injector.accept(bean);
    }
    else {
      bean = ampManager.inject().instance(Key.of(serviceClass, ServiceImpl.class));
    }
    
    if (bean instanceof VaultBase && driver instanceof VaultStore) {
      VaultBase beanData = (VaultBase) bean;
      
      beanData.store((VaultStore) driver);
    }
        
    ClassStub skeleton;

    skeleton = new StubAssetStore(ampManager,
                                    serviceClass,
                                    configService,
                                    configResource);
    skeleton.introspect();

    StubAmpBean actor = new StubAmpBean(skeleton, 
                                          bean,
                                          configService);

    return new StubFactoryImpl(()->actor, configService);
  }

  private ActorFactoryAmp factoryStore(Class<?> serviceClass,
                                       ServiceManagerAmp ampManager,
                                       ServiceConfig configService)
  {
    if (! serviceClass.isAnnotationPresent(Asset.class)) {
      throw new IllegalStateException();
    }
    
    VaultConfig configResource = new VaultConfig();
    configResource.entityType(serviceClass);
    configResource.idType(Void.class);
    
    VaultDriver<?,?> driver = driver(ampManager, 
                                        serviceClass, 
                                        serviceClass,
                                        Void.class,
                                        null);
    //driver must not be null because ActorAmpBean expects not null
    //for bean parameter
    //if (driver != null) {
    Objects.requireNonNull(driver);
    configResource.driver(driver);
    //}
        
    ClassStub skeleton;

    skeleton = new StubAssetSolo(ampManager,
                                      serviceClass,
                                      configService,
                                      driver);
    
    skeleton.introspect();
      
    Key<?> key = Key.of(serviceClass, ServiceImpl.class);
    StubAmpBean actor = new StubAmpBean(skeleton, 
                                          ampManager.inject().instance(key),
                                          configService);

    return new StubFactoryImpl(()->actor, configService);
  }
  
  private VaultDriver<?,?> driver(ServiceManagerAmp ampManager,
                                     Class<?> serviceClass,
                                     Class<?> entityType,
                                     Class<?> idType,
                                     String address)
  {
    for (VaultDriver<?,?> driver : _drivers) {
      VaultDriver<?,?> driverMatch
        = ((VaultDriver) driver).driver(ampManager,
                                           serviceClass,
                                           entityType,
                                           idType,
                                           address);
      if (driverMatch != null) {
        return driverMatch;
      }
    }
    
    return new VaultDriverBase(ampManager, entityType, idType, null);
  }

  public void driver(VaultDriver<?,?> driver)
  {
    _drivers.add(driver);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + _drivers;
  }
}
