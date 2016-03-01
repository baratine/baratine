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

package com.caucho.v5.config.reflect;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import com.caucho.v5.util.ModulePrivate;

/**
 * type matching the web bean
 */
@ModulePrivate
abstract public class BaseType
{
  private static final BaseType []NULL_PARAM = new BaseType[0];
  
  private LinkedHashSet<Type> _typeSet;
  
  private LinkedHashSet<BaseType> _typeClosureSet;
  
  public static BaseType createForTarget(Type type, 
                                         HashMap<String,BaseType> paramMap,
                                         String paramDeclName)
  {
    return create(type, paramMap, paramDeclName, ClassFill.TARGET);
  }
  
  public static BaseType createForSource(Type type, 
                                         HashMap<String,BaseType> paramMap,
                                         String paramDeclName)
  {
//  return create(type, paramMap, false);
    return create(type, paramMap, paramDeclName, ClassFill.SOURCE);
  }
  
  public static BaseType create(Type type, 
                                HashMap<String,BaseType> paramMap,
                                String paramDeclName,
                                ClassFill classFill)
  {
    return create(type, paramMap, paramDeclName, null, classFill);
  }
    
  public static BaseType create(Type type, 
                                HashMap<String,BaseType> paramMap,
                                String paramDeclName,
                                Type parentType,
                                ClassFill classFill)
  {
    if (type instanceof Class<?>) {
      Class<?> cl = (Class<?>) type;
      
      TypeVariable<?> []typeParam = cl.getTypeParameters();
      
      if (typeParam == null || typeParam.length == 0)
        return ClassType.create(cl);

      if (classFill == ClassFill.PLAIN)
        return ClassType.create(cl);
      else if (classFill == ClassFill.SOURCE)
        return createGenericClass(cl);
      
      // ioc/0p80 vs ioc/1238
      /*
      if (true)
        return ClassType.create(cl);
        */
      
      BaseType []args = new BaseType[typeParam.length];

      HashMap<String,BaseType> newParamMap = new HashMap<String,BaseType>();

      for (int i = 0; i < args.length; i++) {
        BaseType value = null;
        
        if (paramMap != null)
          value = paramMap.get(typeParam[i].getName());
        
        // ioc/0246
        if (value != null)
          args[i] = value;
        else
          args[i] = TargetObjectType.OBJECT_TYPE;
        
        if (args[i] == null) {
          throw new NullPointerException("unsupported BaseType: " + type);
        }

        newParamMap.put(typeParam[i].getName(), args[i]);
      }

      return new GenericParamType(cl, args, newParamMap);
    }
    else if (type instanceof ParameterizedType) {
      ParameterizedType pType = (ParameterizedType) type;

      Class<?> rawType = (Class<?>) pType.getRawType();

      Type []typeArgs = pType.getActualTypeArguments();
      
      BaseType []args = new BaseType[typeArgs.length];

      for (int i = 0; i < args.length; i++) {
        args[i] = create(typeArgs[i], paramMap, paramDeclName, 
                         type, ClassFill.TARGET);

        if (args[i] == null) {
          throw new NullPointerException("unsupported BaseType: " + type);
        }
      }
      
      HashMap<String,BaseType> newParamMap = new HashMap<String,BaseType>();
      
      TypeVariable<?> []typeVars = rawType.getTypeParameters();

      for (int i = 0; i < typeVars.length; i++) {
        newParamMap.put(typeVars[i].getName(), args[i]);
      }

      ParamType paramType = new ParamType(rawType, args, newParamMap);
      
      return paramType;
    }
    else if (type instanceof GenericArrayType) {
      GenericArrayType aType = (GenericArrayType) type;

      BaseType baseType = create(aType.getGenericComponentType(), 
                                 paramMap, paramDeclName, 
                                 classFill);
      Class<?> rawType = Array.newInstance(baseType.getRawClass(), 0).getClass();
      
      return new ArrayType(baseType, rawType);
    }
    else if (type instanceof TypeVariable<?>) {
      TypeVariable<?> aType = (TypeVariable<?>) type;
      
      return createVar(aType, paramMap, paramDeclName, parentType, classFill);
    }
    else if (type instanceof WildcardType) {
      WildcardType aType = (WildcardType) type;

      BaseType []lowerBounds = toBaseType(aType.getLowerBounds(), 
                                          paramMap, paramDeclName);
      BaseType []upperBounds = toBaseType(aType.getUpperBounds(),
                                          paramMap, paramDeclName);
      
      return new WildcardTypeImpl(lowerBounds, upperBounds);
    }
    
    else {
      throw new IllegalStateException("unsupported BaseType: " + type
                                      + " " + (type != null ? type.getClass() : null));
    }
    
  }
  
  private static BaseType createVar(Type type,
                                    HashMap<String,BaseType> paramMap,
                                    String paramDeclName,
                                    Type parentType,
                                    ClassFill classFill)
  {
    TypeVariable aType = (TypeVariable) type;

    BaseType actualType = null;
    
    String aTypeName = aType.getName();

    if (paramMap != null) {
      actualType = (BaseType) paramMap.get(aTypeName);

      if (actualType != null)
        return actualType;
      
      if (paramDeclName != null) {
        aTypeName = paramDeclName + "_" + aType.getName();

        actualType = (BaseType) paramMap.get(aTypeName);
      }

      if (actualType != null)
        return actualType;
    }
    
    String varName;
    
    if (paramMap != null)
      varName = createVarName(paramMap, paramDeclName);
    else
      varName = aType.getName();
    
    BaseType []baseBounds;

    if (aType.getBounds() != null) {
      Type []bounds = aType.getBounds();

      baseBounds = new BaseType[bounds.length];

      for (int i = 0; i < bounds.length; i++) {
        // ejb/1243 - Enum
        if (bounds[i] != parentType)
          baseBounds[i] = create(bounds[i], 
                                 paramMap, paramDeclName, 
                                 type, ClassFill.TARGET);
        else
          baseBounds[i] = ObjectType.OBJECT_TYPE;
      }
    }
    else
      baseBounds = new BaseType[0];
    
    VarType<?> varType = new VarType(varName, baseBounds);
    
    if (paramMap != null)
      paramMap.put(aTypeName, varType);
    
    return varType;
  }
  
  private static String createVarName(HashMap<String,BaseType> paramMap,
                                      String paramDeclName)
  {
    for (int i = 0; true; i++) {
      String name = "T_" + i;
      
      if (paramDeclName != null)
        name = paramDeclName + "_" + name;
      
      if (! paramMap.containsKey(name))
        return name;
    }
  }
  
  /**
   * Create a class-based type, where any parameters are filled with the
   * variables, not Object.
   */
  public static BaseType createClass(Class<?> type)
  {
    // ioc/1238
    // ioc/07f2

    return ClassType.create(type);
  }

  /**
   * Create a class-based type, where any parameters are filled with the
   * variables, not Object.
   */
  public static BaseType createGenericClass(Class<?> type)
  {
    TypeVariable<?> []typeParam = type.getTypeParameters();
      
    if (typeParam == null || typeParam.length == 0)
      return ClassType.create(type);

    BaseType []args = new BaseType[typeParam.length];

    HashMap<String,BaseType> newParamMap = new HashMap<String,BaseType>();
    String paramDeclName = null;

    for (int i = 0; i < args.length; i++) {
      args[i] = create(typeParam[i], 
                       newParamMap, paramDeclName, 
                       ClassFill.TARGET);

      if (args[i] == null) {
        throw new NullPointerException("unsupported BaseType: " + type);
      }

      newParamMap.put(typeParam[i].getName(), args[i]);
    }
    
    // ioc/07f2

    return new GenericParamType(type, args, newParamMap);
  }

  private static BaseType []toBaseType(Type []types,
                                       HashMap<String,BaseType> paramMap,
                                       String paramDeclName)
  {
    if (types == null)
      return NULL_PARAM;
    
    BaseType []baseTypes = new BaseType[types.length];

    for (int i = 0; i < types.length; i++) {
      baseTypes[i] = create(types[i], 
                            paramMap, paramDeclName, 
                            ClassFill.TARGET);
    }

    return baseTypes;
  }

  abstract public Class<?> getRawClass();

  public HashMap<String,BaseType> getParamMap()
  {
    return null;
  }

  public BaseType []getParameters()
  {
    return NULL_PARAM;
  }

  public boolean isWildcard()
  {
    return false;
  }
  
  /**
   * Returns true for a generic type like MyBean<X> or MyBean<?>
   */
  public boolean isGeneric()
  {
    return false;
  }
  
  /**
   * Returns true for a generic variable type like MyBean<X>, but not MyBean<?>
   */
  public boolean isGenericVariable()
  {
    return isVariable();
  }
  
  /**
   * Returns true for a variable type like X
   */
  public boolean isVariable()
  {
    return false;
  }
  
  /**
   * Returns true for a raw type like MyBean where the class definition 
   * is MyBean<X>.
   */
  public boolean isGenericRaw()
  {
    return false;
  }
  
  public boolean isPrimitive()
  {
    return false;
  }
  
  public boolean isObject()
  {
    return false;
  }
  
  protected BaseType []getWildcardBounds()
  {
    return NULL_PARAM;
  }

  public boolean isAssignableFrom(BaseType type)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Assignable as a parameter.
   */
  public boolean isParamAssignableFrom(BaseType type)
  {
    return equals(type);
  }

  public Type toType()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Fills in a parameter with a given name.
   */
  public BaseType fill(BaseType ... baseType)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Returns the type closure of the base type.
   */
  public final Set<Type> getTypeClosure(BaseTypeFactory typeFactory)
  {
    if (_typeSet == null) {
      LinkedHashSet<Type> typeSet = new LinkedHashSet<Type>();
      
      fillTypeClosure(typeFactory, typeSet);
      
      _typeSet = typeSet;
    }
    
    return _typeSet;
  }

  /**
   * Returns the type closure of the base type.
   */
  public final Set<BaseType> getBaseTypeClosure(BaseTypeFactory typeFactory)
  {
    if (_typeClosureSet == null) {
      LinkedHashSet<BaseType> baseTypeSet = new LinkedHashSet<BaseType>();
    
      for (Type type : getTypeClosure(typeFactory)) {
        baseTypeSet.add(typeFactory.createForSource(type));
      }
      
      _typeClosureSet = baseTypeSet;
    }
    
    return _typeClosureSet;
  }
    
  protected void fillTypeClosure(BaseTypeFactory typeFactory, Set<Type> typeSet)
  {
    typeSet.add(toType());
  }
  
  public void fillSyntheticTypes(Set<VarType<?>> varTypeList)
  {
  }

  public BaseType extendGenericType()
  {
    return this;
  }

  public String getSimpleName()
  {
    return getRawClass().getSimpleName();
  }
  
  @Override
  public String toString()
  {
    return getRawClass().getName();
  }
  
  public enum ClassFill {
    PLAIN,
    SOURCE,
    TARGET;
  }
}
