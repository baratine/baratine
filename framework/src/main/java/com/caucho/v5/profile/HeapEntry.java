/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.profile;

/**
 * A heap entry
 */
public class HeapEntry {
  private final String _className;
  private final long _count;
  
  private final long _selfSize;
  private final long _childSize;

  public HeapEntry(String className,
		   long count,
		   long selfSize,
		   long childSize)
  {
    _className = toUserClassName(className);
    _count = count;
    _selfSize = selfSize;
    _childSize = childSize;
  }

  public String getClassName()
  {
    return _className;
  }
  
  public long getCount()
  {
    return _count;
  }
  
  public long getSelfSize()
  {
    return _selfSize;
  }
  
  public long getChildSize()
  {
    return _childSize;
  }
  
  public long getTotalSize()
  {
    return _selfSize + _childSize;
  }

  private static String toUserClassName(String rawName)
  {
    if (rawName == null || rawName.length() == 0)
      return rawName;
    else if (rawName.charAt(0) == '[')
      return toUserClassName(rawName.substring(1)) + "[]";
    else if (rawName.length() > 1)
      return rawName;
    else {
      switch (rawName.charAt(0)) {
      case 'B': return "byte";
      case 'C': return "char";
      case 'D': return "double";
      case 'F': return "float";
      case 'I': return "int";
      case 'J': return "long";
      case 'S': return "short";
      case 'Z': return "boolean";
	
      default:
	return rawName;
      }
    }
  }

  public String toString()
  {
    return "HeapEntry[" + _className + "," + _count + "," + getTotalSize() + "]";
  }
}
