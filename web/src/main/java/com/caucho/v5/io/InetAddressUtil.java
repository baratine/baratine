/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.io;

import com.caucho.v5.util.ModulePrivate;

/**
 * Abstract socket to handle both normal sockets and bin/resin sockets.
 */
//@ModulePrivate
public final class InetAddressUtil {
  /**
   * Convert a system ip address to an actual address.
   */
  public static int createIpAddress(byte []address, char []buffer)
  {
    if (isIpv4(address)) {
      return createIpv4Address(address, 0, buffer, 0);
    }

    int offset = 0;
    boolean isZeroCompress = false;
    boolean isInZeroCompress = false;

    buffer[offset++] = '[';

    for (int i = 0; i < 16; i += 2) {
      int value = (address[i] & 0xff) * 256 + (address[i + 1] & 0xff);

      if (value == 0 && i != 14) {
        if (isInZeroCompress)
          continue;
        else if (! isZeroCompress) {
          isZeroCompress = true;
          isInZeroCompress = true;
          continue;
        }
      }

      if (isInZeroCompress) {
        isInZeroCompress = false;
        buffer[offset++] = ':';
        buffer[offset++] = ':';
      }
      else if (i != 0){
        buffer[offset++] = ':';
      }

      if (value == 0) {
        buffer[offset++] = '0';
        continue;
      }

      offset = writeHexDigit(buffer, offset, value >> 12);
      offset = writeHexDigit(buffer, offset, value >> 8);
      offset = writeHexDigit(buffer, offset, value >> 4);
      offset = writeHexDigit(buffer, offset, value);
    }

    buffer[offset++] = ']';

    return offset;
  }

  private static boolean isIpv4(byte []buffer)
  {
    if (buffer[10] != (byte) 0xff || buffer[11] != (byte) 0xff)
      return false;

    for (int i = 0; i < 10; i++) {
      if (buffer[i] != 0)
        return false;
    }

    return true;
  }

  private static int writeHexDigit(char []buffer, int offset, int value)
  {
    if (value == 0)
      return offset;

    value = value & 0xf;

    if (value < 10)
      buffer[offset++] = (char) ('0' + value);
    else
      buffer[offset++] = (char) ('a' + value - 10);

    return offset;
  }

  private static int createIpv4Address(byte []address, int addressOffset,
                                       char []buffer, int bufferOffset)
  {
    int tailOffset = bufferOffset;

    for (int i = 12; i < 16; i++) {
      if (i > 12)
        buffer[tailOffset++] = '.';

      int digit = address[addressOffset + i] & 0xff;
      int d1 = digit / 100;
      int d2 = digit / 10 % 10;
      int d3 = digit % 10;

      if (digit >= 100) {
        buffer[tailOffset++] = (char) ('0' + d1);
      }

      if (digit >= 10) {
        buffer[tailOffset++] = (char) ('0' + d2);
      }

      buffer[tailOffset++] = (char) ('0' + d3);
    }

    return tailOffset - bufferOffset;
  }
}

