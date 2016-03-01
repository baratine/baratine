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

package com.caucho.v5.websocket.io;

/**
 * Represents a reason for a websocket session.
 */
public class CloseReason {
  private final CloseCode _code;
  private final String _reason;
  
  public CloseReason(CloseCode code, String reason)
  {
    _code = code;
    _reason = reason;
  }
  
  public CloseCode getCloseCode()
  {
    return _code;
  }
  
  public String getReasonPhrase()
  {
    return _reason;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _code + "," + _reason + "]";
  }
  
  public interface CloseCode {
    int getCode();
  }
  
  public static enum CloseCodes implements CloseCode {
    CANNOT_ACCEPT {
      public int getCode() { return 1003; }
    },
    CLOSED_ABNORMALLY {
      public int getCode() { return 1006; }
    },
    GOING_AWAY {
      public int getCode() { return 1001; }
    },
    NO_EXTENSION {
      public int getCode() { return 1010; }
    },
    NO_STATUS_CODE {
      public int getCode() { return 1005; }
    },
    NORMAL_CLOSURE {
      public int getCode() { return 1000; }
    },
    NOT_CONSISTENT {
      public int getCode() { return 1007; }
    },
    PROTOCOL_ERROR {
      public int getCode() { return 1002; }
    },
    RESERVED {
      public int getCode() { return 1004; }
    },
    SERVICE_RESTART {
      public int getCode() { return 1012; }
    },
    TLS_HANDSHAKE_FAILURE {
      public int getCode() { return 1015; }
    },
    TOO_BIG {
      public int getCode() { return 1009; }
    },
    TRY_AGAIN_LATER {
      public int getCode() { return 1013; }
    },
    UNEXPECTED_CONDITION {
      public int getCode() { return 1011; }
    },
    VIOLATED_POLICY {
      public int getCode() { return 1008; }
    },
    ;
    
    abstract public int getCode();
    
    public static CloseCode getCloseCode(int code)
    {
      switch (code) {
      case 1000: 
        return NORMAL_CLOSURE;
        
      case 1001: 
        return GOING_AWAY;
        
      case 1002:
        return PROTOCOL_ERROR;
        
      case 1003:
        return CANNOT_ACCEPT;
        
      case 1004:
        return RESERVED;
        
      case 1005:
        return NO_STATUS_CODE;
        
      case 1006:
        return CLOSED_ABNORMALLY;
        
      case 1007:
        return NOT_CONSISTENT;
        
      case 1008:
        return VIOLATED_POLICY;
        
      case 1009:
        return TOO_BIG;
        
      case 1010:
        return NO_EXTENSION;
        
      case 1011:
        return UNEXPECTED_CONDITION;
        
      case 1012:
        return SERVICE_RESTART;
        
      case 1013:
        return TRY_AGAIN_LATER;
        
      case 1015:
        return TLS_HANDSHAKE_FAILURE;
        
      default:
        throw new IllegalArgumentException();
      }
      
    }
  }
}
