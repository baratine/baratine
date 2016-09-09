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
import java.util.function.Function;
import java.util.function.Supplier;

import io.baratine.inject.Key;
import io.baratine.inject.Priority;
import io.baratine.vault.Asset;
import io.baratine.vault.Vault;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.service.StubFactoryAmp;
import com.caucho.v5.amp.spi.StubContainerAmp;
import com.caucho.v5.amp.stub.StubAmpBean;
import com.caucho.v5.amp.stub.StubClass;
import com.caucho.v5.amp.stub.StubFactoryImpl;
import com.caucho.v5.amp.stub.StubGenerator;
import com.caucho.v5.inject.impl.ServiceImpl;
import com.caucho.v5.inject.type.TypeRef;

/**
 * Creates an actor supplier based on a Resource and Store.
 */
@Priority(-100)
public class StubGeneratorVault implements StubGenerator
{
  private ArrayList<VaultDriver<?,?>> _drivers = new ArrayList<>();
  
  @Override
  public <T> StubFactoryAmp factory(Class<T> serviceClass,
                                 ServicesAmp ampManager,
                                 Supplier<? extends T> supplier,
                                 ServiceConfig configService)
  {
    if (Vault.class.isAssignableFrom(serviceClass)) {
      return factoryVault(serviceClass, ampManager, configService);
    }
    else if (serviceClass.isAnnotationPresent(Asset.class)) {
      return factoryStore(serviceClass, ampManager, configService);
    }
    else {
      return null;
    }
  }

  private StubFactoryAmp factoryVault(Class<?> vaultClass,
                                          ServicesAmp services,
                                          ServiceConfig configService)
  {
    if (! Vault.class.isAssignableFrom(vaultClass)) {
      throw new IllegalStateException();
    }
    
    TypeRef typeRef = TypeRef.of(vaultClass);
    TypeRef resourceRef = typeRef.to(Vault.class);
    TypeRef assetRef = resourceRef.param("T");
    TypeRef idRef = resourceRef.param("ID");
    
    VaultConfig configResource = new VaultConfig();
    configResource.assetType(assetRef.rawClass());
    configResource.idType(idRef.rawClass());
    
    VaultDriver<?,?> driver = driver(services,
                                     vaultClass, 
                                     assetRef.rawClass(),
                                     idRef.rawClass(),
                                     configService.address());
    
    // driver must not be null because ActorAmpBean expects not null
    // for bean parameter
    //if (driver != null) {
    Objects.requireNonNull(driver);
    configResource.driver(driver);
    //}

    Object bean;
    
    if (Modifier.isAbstract(vaultClass.getModifiers())) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      
      bean = ClassGeneratorVault.create(vaultClass, 
                                        classLoader,
                                        driver);

      Consumer<Object> injector = 
        (Consumer) services.injector().injector(bean.getClass());
      
      injector.accept(bean);
    }
    else {
      bean = services.injector().instance(Key.of(vaultClass, ServiceImpl.class));
    }
    
    if (bean instanceof VaultBase && driver instanceof VaultStore) {
      VaultBase beanData = (VaultBase) bean;
      
      beanData.store((VaultStore) driver);
    }
        
    StubClass stubClassVault;

    stubClassVault = new StubVault(services,
                             vaultClass,
                             configService,
                             configResource);
    stubClassVault.introspect();
    
    Function<StubAmpBean,StubContainerAmp> factory;
    
    StubAmpBean stub = new StubAmpVault(stubClassVault,
                                        driver.stubClassAsset(),
                                        bean,
                                        configService.name(),
                                        configService);

    return new StubFactoryImpl(()->stub, configService);
  }

  private StubFactoryAmp factoryStore(Class<?> serviceClass,
                                       ServicesAmp ampManager,
                                       ServiceConfig configService)
  {
    if (! serviceClass.isAnnotationPresent(Asset.class)) {
      throw new IllegalStateException();
    }
    
    VaultConfig configResource = new VaultConfig();
    configResource.assetType(serviceClass);
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
        
    StubClass stubClass;

    stubClass = new StubAssetSolo(ampManager,
                                      serviceClass,
                                      configService,
                                      driver);
    
    stubClass.introspect();
      
    Key<?> key = Key.of(serviceClass, ServiceImpl.class);
    StubAmpBean stub = new StubAmpBean(stubClass, 
                                       ampManager.injector().instance(key),
                                       configService.name(),
                                       null,
                                       configService);

    return new StubFactoryImpl(()->stub, configService);
  }
  
  private VaultDriver<?,?> driver(ServicesAmp ampManager,
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
    
    return new VaultDriverBase(ampManager, entityType, idType, address);
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
