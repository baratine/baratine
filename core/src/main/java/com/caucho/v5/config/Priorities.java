package com.caucho.v5.config;

/**
 * Static methods for the &#64;Priority annotation.
 */
public class Priorities
{
  public static int compare(Object a, Object b)
  {
    return compare(a.getClass(), b.getClass());
  }
  
  public static int compare(Class<?> a, Class<?> b)
  {
    Priority aPriority = a.getAnnotation(Priority.class);
    Priority bPriority = b.getAnnotation(Priority.class);
    
    if (aPriority != null && bPriority == null) {
      return -1;
    }
    
    if (aPriority == null && bPriority != null) {
      return 1;
    }
    
    if (aPriority == null && bPriority == null) {
      return a.getName().compareTo(b.getName());
    }
    
    int cmp = aPriority.value() - bPriority.value();
    
    if (cmp != 0) {
      return cmp;
    }
    
    return a.getName().compareTo(b.getName());
  }
}
