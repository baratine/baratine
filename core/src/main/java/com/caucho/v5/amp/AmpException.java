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

package com.caucho.v5.amp;

import io.baratine.service.ServiceException;

/**
 * General AMP exception
 */
@SuppressWarnings("serial")
public class AmpException extends ServiceException {
  private ErrorAmp _ampError;
  
  public AmpException()
  {
  }

  public AmpException(ErrorCodeAmp code, String msg)
  {
    super(msg);
  }

  public AmpException(String msg)
  {
    super(msg);
  }

  public AmpException(Throwable e)
  {
    super(e);
  }

  public AmpException(String msg, Throwable e)
  {
    super(msg, e);
  }

  public AmpException(ErrorAmp ampError)
  {
    super(ampError.getMessage());
    
    _ampError = ampError;
  }

  public AmpException(ErrorAmp ampError, Throwable cause)
  {
    super(ampError.getMessage(), cause);
    
    _ampError = ampError;
  }

  public ErrorAmp getActorError()
  {
    return _ampError;
  }

  public ErrorAmp createActorError()
  {
    /*
    return new AmpError(AmpError.TYPE_CANCEL,
                          AmpError.INTERNAL_SERVER_ERROR,
                          toString());
                          */
    return null;
  }

  /**
   * Rethrow the exception to include the proper stack trace.
   */
  @Override
  public AmpException rethrow(String msg)
  {
    return new AmpException(msg, this);
  }

  public static ServiceException createAndRethrow(Throwable exn)
  {
    if (exn instanceof ServiceException) {
      return ((ServiceException) exn).rethrow();
    }
    else {
      return new AmpException(exn);
    }
  }
}
