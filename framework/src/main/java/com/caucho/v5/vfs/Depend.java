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

package com.caucho.v5.vfs;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.Dependency;

/**
 * Class for keeping track of modifications.
 */
public class Depend implements Dependency
{
  private static final Logger log
    = Logger.getLogger(Depend.class.getName());
  
  private final PathImpl _source;
  private final long _lastModified;
  private long _length;

  private boolean _requireSource = true;
  private boolean _isDigestModified;

  /**
   * Create a new dependency with an already known modified time and length.
   *
   * @param source the source file
   */
  public Depend(PathImpl source, long lastModified, long length)
  {
    _source = source;
    _lastModified = lastModified;
    _length = length;
  }

  /**
   * Create a new dependency.
   *
   * @param source the source file
   */
  public Depend(PathImpl source)
  {
    this(source, source.getLastModified(), source.length());
  }

  /**
   * Create a new dependency with a given digest.
   *
   * @param source the source file
   * @param digest the CRC64 digest
   */
  public Depend(PathImpl source, long digest)
  {
    this(source, digest, true);
  }

  /**
   * Create a new dependency with a given digest.
   *
   * @param source the source file
   * @param digest the CRC64 digest
   */
  public Depend(PathImpl source, long digest, boolean requireSource)
  {
    this(source);

    long newDigest = source.getCrc64();

    _requireSource = requireSource;

    if (newDigest == digest) {
    }
    else if (! requireSource && newDigest == 0) {
    }
    else if (newDigest == 0) {
      if (log.isLoggable(Level.FINE))
        log.fine(_source.getNativePath() + " source is deleted.");
      
      _isDigestModified = true;
    }
    else {
      /*
      if (log.isLoggable(Level.FINE))
        log.fine(_source.getNativePath() + " digest is modified.");
      */

      _isDigestModified = true;
    }
  }

  /**
   * Returns the underlying source path.
   */
  public PathImpl getPath()
  {
    return _source;
  }

  /**
   * Returns the current last-modified time of the file.
   */
  public long getLastModified()
  {
    return _source.getLastModified();
  }

  /**
   * Returns the current length time of the file.
   */
  public long getLength()
  {
    return _source.length();
  }

  /**
   * If true, deleting the source counts as a change.
   */
  public boolean getRequireSource()
  {
    return _requireSource;
  }

  /**
   * If true, deleting the source counts as a change.
   */
  public void setRequireSource(boolean requireSource)
  {
    _requireSource = requireSource;
  }

  /**
   * If the source modified date changes at all, treat it as a modification.
   * This protects against the case where multiple computers have
   * misaligned dates and a '<' comparison may fail.
   */
  @Override
  public boolean isModified()
  {
    if (_isDigestModified) {
      if (log.isLoggable(Level.FINE))
        log.fine(_source.getNativePath() + " digest is modified.");

      return true;
    }

    long sourceLastModified = _source.getLastModified();
    long sourceLength = _source.length();
    
    // if the source was deleted and we need the source
    if (! _requireSource && sourceLastModified == 0) {
      return false;
    }
    // if the length changed
    else if (sourceLength != _length) {
      if (log.isLoggable(Level.FINE)) {
        log.fine(_source.getNativePath() + " length is modified (" +
                 _length + " -> " + sourceLength + ")");
      }

      return true;
    }
    // if the source is newer than the old value
    else if (sourceLastModified != _lastModified) {
      if (log.isLoggable(Level.FINE))
        log.fine(_source.getNativePath() + " time is modified.");
      
      return true;
    }
    else
      return false;
  }

  /**
   * Log the reason for modification
   */
  public boolean logModified(Logger log)
  {
    if (_isDigestModified) {
      log.info(_source.getNativePath() + " digest is modified.");

      return true;
    }

    long sourceLastModified = _source.getLastModified();
    long sourceLength = _source.length();

    // if the source was deleted and we need the source
    if (! _requireSource && sourceLastModified == 0) {
      return false;
    }
    // if the length changed
    else if (sourceLength != _length) {
      log.info(_source.getNativePath() + " length is modified (" +
               _length + " -> " + sourceLength + ")");

      return true;
    }
    // if the source is newer than the old value
    else if (sourceLastModified != _lastModified) {
      log.info(_source.getNativePath() + " time is modified.");
      
      return true;
    }
    else
      return false;
  }

  /**
   * Returns the digest.
   */
  public long getDigest()
  {
    return _source.getCrc64();
  }
  
  /**
   * Returns true if the test Dependency has the same source path as
   * this dependency.
   */
  public boolean equals(Object obj)
  {
    if (! (obj instanceof Depend))
      return false;

    Depend depend = (Depend) obj;

    return _source.equals(depend._source);
  }

  /**
   * Returns the string to recreate the Dependency.
   */
  public String getJavaCreateString()
  {
    return ("new com.caucho.v5.vfs.Depend(com.caucho.v5.vfs.Vfs.lookup(\"" +
            _source.getPath() + "\"), " + _source.getCrc64() + "L)");
  }

  /**
   * Returns a printable version of the dependency.
   */
  public String toString()
  {
    return ("Depend[" + _source + ",time=" + _lastModified
            + ",time-ch=" + (_source.getLastModified() - _lastModified)
            + ",len-ch=" + (_source.length() - _length)
            + "]");
  }
}
