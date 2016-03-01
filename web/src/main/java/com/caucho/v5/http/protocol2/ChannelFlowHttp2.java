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

package com.caucho.v5.http.protocol2;


/**
 * ChannelFlowHttp2 manages the flow for a single HTTP stream.
 */
public class ChannelFlowHttp2
{
  private long _receiveLength;
  private long _receiveCredit;

  private long _sendLength;
  private long _sendCredit;

  void init()
  {
    _receiveLength = 0;
    _receiveCredit = 0;
    
    _sendLength = 0;
    _sendCredit = 0;
  }

  public int getReceiveCredit()
  {
    return (int) (_receiveCredit - _receiveLength);
  }

  public void addReceive(int length)
  {
    _receiveLength += length;
  }

  public void addReceiveCredit(int length)
  {
    _receiveCredit += length;
  }

  public int getSendCredit()
  {
    return (int) (_sendCredit - _sendLength);
  }

  public void addSend(long length)
  {
    _sendLength += length; 
  }

  public void addSendCredit(int credit)
  {
    _sendCredit += credit;
  }

  public void onData(int length, ConnectionHttp2 conn, int streamId)
  {
    addReceive(length);
    
    int credit = getReceiveCredit();
    
    int window = conn.getInHttp().getSettings().getInitialWindowSize();
    int delta = window - credit;
    
    if (delta >= 8192 || window <= 2 * delta) {
      addReceiveCredit(delta);
      
      conn.getOutHttp().flow(streamId, delta);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
