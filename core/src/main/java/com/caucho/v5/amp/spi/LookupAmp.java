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

package com.caucho.v5.amp.spi;

import com.caucho.v5.amp.ServiceRefAmp;

/**
 * AmpRouter routes messages to mailboxes.
 */
public interface LookupAmp
{
  /**
   * Returns a mailbox for the given address, 
   * or null if the mailbox does not exist.
   * 
   * @param address the address of the mailbox
   * 
   * @return the mailbox with the given address or null
   */
  ServiceRefAmp service(String address);

  default MethodRefAmp method(String address, String methodName)
  {
    return service(address).methodByName(methodName);
  }
}
