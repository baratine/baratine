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

package com.caucho.v5.ramp.jamp;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.HeadersAmp;

/**
 * Broker for returning JAMP services.
 */
abstract class JampRestMessage
{
  private static final Logger log
    = Logger.getLogger(JampRestMessage.class.getName());
  
  abstract void write(OutJamp out)
    throws IOException;
  
  static class Send extends JampRestMessage
  {
    private final HeadersAmp _headers;
    private final String _address;
    private final String _method;
    private final Object []_args;
    
    Send(HeadersAmp headers, String address, String method, Object []args)
    {
      _headers = headers;
      _address = address;
      _method = method;
      _args = args;
    }
    
    @Override
    void write(OutJamp out)
      throws IOException
    {
      out.send(_headers, _address, _method, _args);
    }
  }
  
  static class Query extends JampRestMessage
  {
    private final HeadersAmp _headers;
    private final String _fromAddress;
    private final long _qid;
    private final String _address;
    private final String _method;
    private final Object []_args;
    
    Query(HeadersAmp headers,
          String fromAddress,
          long qid,
          String address, 
          String method, 
          Object []args)
    {
      _headers = headers;
      _fromAddress = fromAddress;
      _qid = qid; 
      _address = address;
      _method = method;
      _args = args;
    }
    
    @Override
    void write(OutJamp out)
      throws IOException
    {
      out.query(_headers, _fromAddress, _qid, _address, _method, _args);
    }
  }

  static class Reply extends JampRestMessage
  {
    private final HeadersAmp _headers;
    private final String _address;
    private final long _qid;
    private final Object _value;
    
    Reply(HeadersAmp headers,
          String address,
          long qid,
          Object value)
    {
      _headers = headers;
      _address = address;
      _qid = qid;
      _value = value;
    }
  
    @Override
    void write(OutJamp out)
      throws IOException
    {
      out.reply(_headers, _address, _qid, _value);
    }
  }
    
  static class Error extends JampRestMessage
  {
    private final HeadersAmp _headers;
    private final String _address;
    private final long _qid;
    private final Throwable _exn;
    
    Error(HeadersAmp headers,
          String address,
          long qid,
          Throwable exn)
    {
      _headers = headers;
      _address = address;
      _qid = qid;
      _exn = exn;
    }
  
    @Override
    void write(OutJamp out)
      throws IOException
    {
      out.queryError(_headers, _address, _qid, _exn);
    }
  }

  static class StreamResult extends JampRestMessage
  {
    private final HeadersAmp _headers;
    private final String _address;
    private final long _qid;
    private List<Object> _results;
    private boolean _isComplete;
    private Throwable _exn;
    
    StreamResult(HeadersAmp headers,
                 String address,
                 long qid,
                 List<Object> results,
                 boolean isComplete,
                 Throwable exn)
    {
      _headers = headers;
      _address = address;
      _qid = qid;
      _results = results;
      _isComplete = isComplete;
      _exn = exn;
    }
  
    @Override
    void write(OutJamp out)
      throws IOException
    {
      boolean isFirst = true;
      
      if (_results != null && _results.size() > 0) {
        out.streamResult(_headers, _address, _qid, _results);
        isFirst = false;
      }
      
      if (_isComplete) {
        if (! isFirst) {
          out.comma();
        }
        isFirst = false;
        out.streamComplete(_headers, _address, _qid);
        
      }
      // out.streamResult(_headers, _address, _qid, _isComplete);
    }
  }
}
