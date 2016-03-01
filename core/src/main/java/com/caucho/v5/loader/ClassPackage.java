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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.loader;

import com.caucho.v5.util.ModulePrivate;

/**
 * Information about a particular package.
 */
@ModulePrivate
public class ClassPackage {
  private String _prefix;
  
  private String _specName;
  private String _specVersion;
  private String _specVendor;
  
  private String _implName;
  private String _implVersion;
  private String _implVendor;
  
  public ClassPackage(String name)
  {
    if (! name.equals("") && ! name.endsWith("/"))
      name = name + "/";
    
    _prefix = name;
  }

  /**
   * Returns the prefix for this package.
   */
  public String getPrefix()
  {
    return _prefix;
  }

  /**
   * Returns the specification name.
   */
  public String getSpecificationTitle()
  {
    return _specName;
  }

  /**
   * Sets the specification name.
   */
  public void setSpecificationTitle(String specName)
  {
    _specName = specName;
  }

  /**
   * Returns the specification vendor.
   */
  public String getSpecificationVendor()
  {
    return _specVendor;
  }

  /**
   * Sets the specification vendor.
   */
  public void setSpecificationVendor(String specVendor)
  {
    _specVendor = specVendor;
  }

  /**
   * Returns the specification version.
   */
  public String getSpecificationVersion()
  {
    return _specVersion;
  }

  /**
   * Sets the specification version.
   */
  public void setSpecificationVersion(String specVersion)
  {
    _specVersion = specVersion;
  }

  /**
   * Returns the implementation name.
   */
  public String getImplementationTitle()
  {
    return _implName;
  }

  /**
   * Sets the implementation name.
   */
  public void setImplementationTitle(String implName)
  {
    _implName = implName;
  }

  /**
   * Returns the implementation vendor.
   */
  public String getImplementationVendor()
  {
    return _implVendor;
  }

  /**
   * Sets the implementation vendor.
   */
  public void setImplementationVendor(String implVendor)
  {
    _implVendor = implVendor;
  }

  /**
   * Returns the implementation version.
   */
  public String getImplementationVersion()
  {
    return _implVersion;
  }

  /**
   * Sets the implementation version.
   */
  public void setImplementationVersion(String implVersion)
  {
    _implVersion = implVersion;
  }
}
