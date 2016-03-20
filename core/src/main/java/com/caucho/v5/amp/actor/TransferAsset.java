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

package com.caucho.v5.amp.actor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.amp.vault.VaultException;
import com.caucho.v5.convert.bean.FieldBean;
import com.caucho.v5.convert.bean.FieldBeanFactory;
import com.caucho.v5.util.L10N;

import io.baratine.service.Id;

/**
 * Copies to and from a transfer object.
 */
public class TransferAsset<T,S>
{
  private static final L10N L = new L10N(TransferAsset.class);
  
  private Class<T> _assetType;
  private Class<S> _transferType;
  
  private FieldCopy<T,S> []_toAsset;
  private FieldCopy<T,S> []_toTransfer;

  public TransferAsset(Class<T> beanType,
                         Class<S> transferType)
  {
    Objects.requireNonNull(beanType);
    Objects.requireNonNull(transferType);
    
    _assetType = beanType;
    _transferType = transferType;
    
    introspect();
  }
  
  @SuppressWarnings("unchecked")
  private void introspect()
  {
    validateConstructor();
    
    ArrayList<FieldCopy<T,S>> toAssetList = new ArrayList<>();
    ArrayList<FieldCopy<T,S>> toTransferList = new ArrayList<>();
    
    introspect(_assetType, _transferType, toAssetList, toTransferList);
    
    if (toTransferList.size() == 0) {
      throw error("'{0}' is an invalid transfer object for '{1}' because it has no matching fields",
                  _transferType.getSimpleName(),
                  _assetType.getSimpleName());
    }
    
    _toAsset = new FieldCopy[toAssetList.size()];
    toAssetList.toArray(_toAsset);
    
    _toTransfer = new FieldCopy[toTransferList.size()];
    toTransferList.toArray(_toTransfer);
  }
  
  private void validateConstructor()
  {
    if (Modifier.isAbstract(_transferType.getModifiers())
        && ! _transferType.equals(_assetType)) {
      throw error("'{0}' is an invalid transfer object for '{1}' because it is abstract",
                  _transferType.getSimpleName(),
                  _assetType.getSimpleName());
    }
    
    if (_transferType.isMemberClass() 
        && ! Modifier.isStatic(_transferType.getModifiers())) {
      throw error("'{0}' is an invalid transfer object for '{1}' because it is a non-static inner class",
                  _transferType.getSimpleName(),
                  _assetType.getSimpleName());
    }
  }
  
  private void introspect(Class<T> assetType, 
                          Class<?> transferType,
                          ArrayList<FieldCopy<T,S>> toAssetList,
                          ArrayList<FieldCopy<T,S>> toTransferList)
  {
    if (transferType == null) {
      return;
    }
    
    introspect(assetType,
               transferType.getSuperclass(), 
               toAssetList, 
               toTransferList);
    
    for (Field fieldTransfer : transferType.getDeclaredFields()) {
      if (Modifier.isStatic(fieldTransfer.getModifiers())) {
        continue;
      }
      
      if (Modifier.isTransient(fieldTransfer.getModifiers())) {
        continue;
      }
      
      Field fieldAsset = findField(assetType, fieldTransfer);
      
      if (fieldAsset == null) {
        throw error("Field '{0}' is unknown in asset '{1}' used by transfer object '{2}'",
                    fieldTransfer.getName(),
                    _assetType.getSimpleName(),
                    _transferType.getSimpleName());
      }
      
      FieldBean<T> fieldBeanAsset = FieldBeanFactory.get(fieldAsset);
      FieldBean<S> fieldBeanTransfer = FieldBeanFactory.get(fieldTransfer);
      
      FieldCopy<T,S> fieldCopy 
        = new FieldCopy<>(fieldBeanAsset, fieldBeanTransfer);
      
      toTransferList.add(fieldCopy);
      
      if (! isId(fieldAsset)) {
        toAssetList.add(fieldCopy);
      }
    }
  }
  
  private RuntimeException error(String msg, Object ...args)
  {
    return new VaultException(L.l(msg, args));
  }
  
  private boolean isId(Field field)
  {
    if (field.isAnnotationPresent(Id.class)) {
      return true;
    }
    else if (field.getName().equals("id")) {
      return true;
    }
    else if (field.getName().equals("_id")) {
      return true;
    }
    else {
      return false;
    }
  }
  
  private Field findField(Class<?> type, Field field)
  {
    if (type == null) {
      return null;
    }
    
    for (Field fieldType : type.getDeclaredFields()) {
      if (Modifier.isStatic(fieldType.getModifiers())) {
        continue;
      }
      
      if (Modifier.isTransient(fieldType.getModifiers())) {
        continue;
      }
      
      if (! fieldType.getName().equals(field.getName())) {
        continue;
      }
      
      if (fieldType.getType().equals(field.getType())) {
        return field;
      }
      
      throw error("Field '{0}' with type '{1}' in asset '{2}' does not match field in transfer object '{3}' with type '{4}'",
                  fieldType.getName(),
                  fieldType.getType().getSimpleName(),
                  _assetType.getSimpleName(),
                  _transferType.getSimpleName(),
                  field.getType().getSimpleName());
    }
    
    return findField(type.getSuperclass(), field);
  }
  
  public void toAsset(T asset, S transfer)
  {
    Objects.requireNonNull(asset);
    Objects.requireNonNull(transfer);
    
    for (FieldCopy<T,S> fieldCopy : _toAsset) {
      fieldCopy.toAsset(asset, transfer);
    }
  }
  
  public void toTransfer(T asset, S transfer)
  {
    Objects.requireNonNull(asset);
    Objects.requireNonNull(transfer);
    
    for (FieldCopy<T,S> fieldCopy : _toTransfer) {
      fieldCopy.toTransfer(asset, transfer);
    }
  }
  
  public S toTransfer(T asset)
  {
    try {
      S transfer = (S) _transferType.newInstance();
      
      toTransfer(asset, transfer);
      
      return transfer;
    } catch (Exception e) {
      throw new VaultException(e);
    }
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
           + "[" + _assetType.getSimpleName()
           + "," + _transferType.getSimpleName()
           + "]");
  }
  
  private static class FieldCopy<T,S>
  {
    private FieldBean<T> _fieldAsset;
    private FieldBean<S> _fieldTransfer;
    
    FieldCopy(FieldBean<T> fieldAsset,
              FieldBean<S> fieldTransfer)
    {
      _fieldAsset = fieldAsset;
      _fieldTransfer = fieldTransfer;
    }
    
    public void toAsset(T asset, S transfer)
    {
      _fieldAsset.setObject(asset, _fieldTransfer.getObject(transfer));
    }
    
    public void toTransfer(T asset, S transfer)
    {
      _fieldTransfer.setObject(transfer, _fieldAsset.getObject(asset));
    }
  }
}
