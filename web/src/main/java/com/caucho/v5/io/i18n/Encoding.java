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

package com.caucho.v5.io.i18n;

import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.util.CharBuffer;

/**
 * Converts between the mime encoding names and Java encoding names.
 */
public class Encoding {
  private static ConcurrentHashMap<String,String> _javaName;
  private static ConcurrentHashMap<String,String> _mimeName;
  private static ConcurrentHashMap<String,String> _localeName;

  // map from an encoding name to its EncodingReader factory.
  static final ConcurrentHashMap<String,EncodingReader> _readEncodingFactories
    = new ConcurrentHashMap<String,EncodingReader>();

  // map from an encoding name to its EncodingWriter factory.
  static final ConcurrentHashMap<String,EncodingWriter> _writeEncodingFactories
    = new ConcurrentHashMap<String,EncodingWriter>();

  static final EncodingWriter _latin1Writer = new ISO8859_1Writer();

  /**
   * Can't create an instance of the encoding class.
   */
  private Encoding() {}

  /**
   * Returns the canonical mime name for the given character encoding.
   *
   * @param encoding character encoding name, possibly an alias
   *
   * @return canonical mime name for the encoding.
   */
  public static String getMimeName(String encoding)
  {
    if (encoding == null)
      return null;

    String value = _mimeName.get(encoding);
    if (value != null)
      return value;

    String upper = normalize(encoding);

    String lookup = _mimeName.get(upper);

    value = lookup == null ? upper : lookup;

    _mimeName.put(encoding, value);

    return value;
  }

  /**
   * Returns the canonical mime name for the given locale.
   *
   * @param locale locale to use.
   *
   * @return canonical mime name for the encoding.
   */
  public static String getMimeName(Locale locale)
  {
    if (locale == null)
      return "utf-8";

    String mimeName = _localeName.get(locale.toString());
    if (mimeName == null)
      mimeName = _localeName.get(locale.getLanguage());

    if (mimeName == null)
      return "utf-8";
    else
      return mimeName;
  }

  /**
   * Returns a Reader to translate bytes to characters.  If a specialized
   * reader exists in com.caucho.v5.vfs.i18n, use it.
   *
   * @param is the input stream.
   * @param encoding the encoding name.
   *
   * @return a reader for the translation
   */
  public static Reader getReadEncoding(InputStream is, String encoding)
    throws UnsupportedEncodingException
  {
    return getReadFactory(encoding).create(is);
  }

  /**
   * Returns a Reader to translate bytes to characters.  If a specialized
   * reader exists in com.caucho.v5.vfs.i18n, use it.
   *
   * @param is the input stream.
   * @param encoding the encoding name.
   *
   * @return a reader for the translation
   */
  public static EncodingReader getReadFactory(final String encoding)
    throws UnsupportedEncodingException
  {
    String encKey = encoding == null ? "iso-8859-1" : encoding;

    EncodingReader factory = _readEncodingFactories.get(encKey);

    if (factory == null) {
      try {
        String javaEncoding = Encoding.getJavaName(encoding);

        if (javaEncoding == null)
          javaEncoding = "ISO8859_1";

        String className = "com.caucho.v5.vfs.i18n." + javaEncoding + "Reader";

        Class<?> cl = Class.forName(className);

        factory = (EncodingReader) cl.newInstance();
        factory.setJavaEncoding(javaEncoding);
      } catch (Throwable e) {
      }

      if (factory == null) {
        String javaEncoding = Encoding.getJavaName(encoding);

        if (javaEncoding == null)
          javaEncoding = "ISO8859_1";

        factory = new JDKReader();
        factory.setJavaEncoding(javaEncoding);
      }

      _readEncodingFactories.put(encKey, factory);
    }

    return factory;
  }

  /**
   * Returns an EncodingWriter to translate characters to bytes.
   *
   * @param encoding the encoding name.
   *
   * @return a writer for the translation
   */
  public static EncodingWriter getWriteEncoding(String encoding)
  {
    if (encoding == null)
      encoding = "iso-8859-1";

    EncodingWriter factory = _writeEncodingFactories.get(encoding);

    if (factory != null)
      return factory.create();

    factory = _writeEncodingFactories.get(encoding);

    if (factory == null) {
      try {
        String javaEncoding = Encoding.getJavaName(encoding);

        if (javaEncoding == null)
          javaEncoding = "ISO8859_1";

        String className = "com.caucho.v5.vfs.i18n." + javaEncoding + "Writer";

        Class<?> cl = Class.forName(className);

        factory = (EncodingWriter) cl.newInstance();
        factory.setJavaEncoding(javaEncoding);
      } catch (Throwable e) {
      }

      if (factory == null) {
        factory = new JDKWriter();
        String javaEncoding = Encoding.getJavaName(encoding);

        if (javaEncoding == null)
          javaEncoding = "ISO8859_1";
        factory.setJavaEncoding(javaEncoding);
      }

      _writeEncodingFactories.put(encoding, factory);
    }

    // return factory.create(factory.getJavaEncoding());
    // charset uses the original encoding, not the java encoding
    return factory.create(encoding);
  }

  /**
   * Returns the latin 1 writer.
   */
  public static EncodingWriter getLatin1Writer()
  {
    return _latin1Writer;
  }

  /**
   * Returns the Java name for the given encoding.
   *
   * @param encoding character encoding name
   *
   * @return Java encoding name
   */
  public static String getJavaName(String encoding)
  {
    if (encoding == null)
      return null;
    
    String javaName = _javaName.get(encoding);
    
    if (javaName != null)
      return javaName;

    String upper = normalize(encoding);

    javaName = _javaName.get(upper);
    if (javaName == null) {
      String lookup = _mimeName.get(upper);

      if (lookup != null)
        javaName = _javaName.get(lookup);
    }
    
    if (javaName == null)
      javaName = upper;
    
    _javaName.put(encoding, javaName);

    return javaName;
  }

  /**
   * Returns the Java name for the given locale.
   *
   * @param locale the locale to use
   *
   * @return Java encoding name
   */
  public static String getJavaName(Locale locale)
  {
    if (locale == null)
      return null;

    return getJavaName(getMimeName(locale));
  }

  /**
   * Normalize the user's encoding name to avoid case issues.
   */
  private static String normalize(String name)
  {
    CharBuffer cb = new CharBuffer();

    int len = name.length();
    for (int i = 0; i < len; i++) {
      char ch = name.charAt(i);

      if (Character.isLowerCase(ch))
        cb.append(Character.toUpperCase(ch));
      else if (ch == '_')
        cb.append('-');
      else
        cb.append(ch);
    }

    return cb.close();
  }


  static {
    _javaName = new ConcurrentHashMap<String,String>();
    _mimeName = new ConcurrentHashMap<String,String>();
    _localeName = new ConcurrentHashMap<String,String>();

    _mimeName.put("ANSI-X3.4-1968", "US-ASCII");
    _mimeName.put("ISO-IR-6", "US-ASCII");
    _mimeName.put("ISO-646.IRV:1991", "US-ASCII");
    _mimeName.put("ASCII", "US-ASCII");
    _mimeName.put("ISO646-US", "US-ASCII");
    _mimeName.put("US-ASCII", "US-ASCII");
    _mimeName.put("us", "US-ASCII");
    _mimeName.put("IBM367", "US-ASCII");
    _mimeName.put("CP367", "US-ASCII");
    _mimeName.put("CSASCII", "US-ASCII");
    _javaName.put("US-ASCII", "ISO8859_1");

    _mimeName.put("ISO-2022-KR", "ISO-2022-KR");
    _mimeName.put("CSISO2022KR", "ISO-2022-KR");
    _mimeName.put("ISO2022-KR", "ISO-2022-KR");
    _javaName.put("ISO-2022-KR", "ISO2022_KR");

    _mimeName.put("EUC-KR", "EUC-KR");
    _mimeName.put("CSEUCKR", "EUC-KR");
    _javaName.put("EUC-KR", "EUC_KR");

    _mimeName.put("ISO-2022-JP", "ISO-2022-JP");
    _mimeName.put("CSISO2022JP", "ISO-2022-JP");
    _mimeName.put("ISO2022-JP", "ISO-2022-JP");
    _javaName.put("ISO-2022-JP", "ISO2022JP");

    _mimeName.put("ISO-2022-JP-2", "ISO-2022-JP-2");
    _mimeName.put("CSISO2022JP2", "ISO-2022-JP-2");
    _mimeName.put("ISO2022-JP2", "ISO-2022-JP-2");
    _javaName.put("ISO-2022-JP-2", "ISO2022_JP2");

    _mimeName.put("ISO_8859-1:1987", "ISO-8859-1");
    _mimeName.put("ISO-IR-100", "ISO-8859-1");
    _mimeName.put("ISO-8859-1", "ISO-8859-1");
    _mimeName.put("LATIN1", "ISO-8859-1");
    _mimeName.put("LATIN-1", "ISO-8859-1");
    _mimeName.put("L1", "ISO-8859-1");
    _mimeName.put("IBM819", "ISO-8859-1");
    _mimeName.put("CP819", "ISO-8859-1");
    _mimeName.put("CSISOLATIN1", "ISO-8859-1");
    _mimeName.put("ISO8859-1", "ISO-8859-1");
    _mimeName.put("8859-1", "ISO-8859-1");
    _mimeName.put("8859_1", "ISO-8859-1");
    _javaName.put("ISO-8859-1", "ISO8859_1");

    _mimeName.put("ISO-8859-2:1987", "ISO-8859-2");
    _mimeName.put("ISO-IR-101", "ISO-8859-2");
    _mimeName.put("ISO-8859-2", "ISO-8859-2");
    _mimeName.put("LATIN2", "ISO-8859-2");
    _mimeName.put("LATIN-2", "ISO-8859-2");
    _mimeName.put("L2", "ISO-8859-2");
    _mimeName.put("CSISOLATIN2", "ISO-8859-2");
    _mimeName.put("ISO8859-2", "ISO-8859-2");
    _javaName.put("ISO-8859-2", "ISO8859_2");

    _mimeName.put("ISO-8859-3:1988", "ISO-8859-3");
    _mimeName.put("ISO-IR-109", "ISO-8859-3");
    _mimeName.put("ISO-8859-3", "ISO-8859-3");
    _mimeName.put("ISO-8859-3", "ISO-8859-3");
    _mimeName.put("LATIN3", "ISO-8859-3");
    _mimeName.put("LATIN-3", "ISO-8859-3");
    _mimeName.put("L3", "ISO-8859-3");
    _mimeName.put("CSISOLATIN3", "ISO-8859-3");
    _mimeName.put("ISO8859-3", "ISO-8859-3");
    _javaName.put("ISO-8859-3", "ISO8859_3");

    _mimeName.put("ISO-8859-4:1988", "ISO-8859-4");
    _mimeName.put("ISO-IR-110", "ISO-8859-4");
    _mimeName.put("ISO-8859-4", "ISO-8859-4");
    _mimeName.put("ISO-8859-4", "ISO-8859-4");
    _mimeName.put("LATIN4", "ISO-8859-4");
    _mimeName.put("LATIN-4", "ISO-8859-4");
    _mimeName.put("L4", "ISO-8859-4");
    _mimeName.put("CSISOLATIN4", "ISO-8859-4");
    _mimeName.put("ISO8859-4", "ISO-8859-4");
    _javaName.put("ISO-8859-4", "ISO8859_4");

    _mimeName.put("ISO-8859-5:1988", "ISO-8859-5");
    _mimeName.put("ISO-IR-144", "ISO-8859-5");
    _mimeName.put("ISO-8859-5", "ISO-8859-5");
    _mimeName.put("ISO-8859-5", "ISO-8859-5");
    _mimeName.put("CYRILLIC", "ISO-8859-5");
    _mimeName.put("CSISOLATINCYRILLIC", "ISO-8859-5");
    _mimeName.put("ISO8859-5", "ISO-8859-5");
    _javaName.put("ISO-8859-5", "ISO8859_5");

    _mimeName.put("ISO-8859-6:1987", "ISO-8859-6");
    _mimeName.put("ISO-IR-127", "ISO-8859-6");
    _mimeName.put("ISO-8859-6", "ISO-8859-6");
    _mimeName.put("ISO-8859-6", "ISO-8859-6");
    _mimeName.put("ECMA-114", "ISO-8859-6");
    _mimeName.put("ASMO-708", "ISO-8859-6");
    _mimeName.put("ARABIC", "ISO-8859-6");
    _mimeName.put("CSISOLATINARABIC", "ISO-8859-6");
    _mimeName.put("ISO8859-6", "ISO-8859-6");
    _javaName.put("ISO-8859-6", "ISO8859_6");

    _mimeName.put("ISO-8859-7:1987", "ISO-8859-7");
    _mimeName.put("ISO-IR-126", "ISO-8859-7");
    _mimeName.put("ISO-8859-7", "ISO-8859-7");
    _mimeName.put("ISO-8859-7", "ISO-8859-7");
    _mimeName.put("ELOT-928", "ISO-8859-7");
    _mimeName.put("ECMA-118", "ISO-8859-7");
    _mimeName.put("GREEK", "ISO-8859-7");
    _mimeName.put("GREEK8", "ISO-8859-7");
    _mimeName.put("CSISOLATINGREEN", "ISO-8859-7");
    _mimeName.put("ISO8859-7", "ISO-8859-7");
    _javaName.put("ISO-8859-7", "ISO8859_7");

    _mimeName.put("ISO-8859-8:1988", "ISO-8859-8");
    _mimeName.put("ISO-IR-138", "ISO-8859-8");
    _mimeName.put("ISO-8859-8", "ISO-8859-8");
    _mimeName.put("ISO-8859-8", "ISO-8859-8");
    _mimeName.put("HEBREW", "ISO-8859-8");
    _mimeName.put("CSISOLATINHEBREW", "ISO-8859-8");
    _mimeName.put("ISO8859-8", "ISO-8859-8");
    _javaName.put("ISO-8859-8", "ISO8859_8");

    _mimeName.put("ISO-8859-9:1989", "ISO-8859-9");
    _mimeName.put("ISO-IR-148", "ISO-8859-9");
    _mimeName.put("ISO-8859-9", "ISO-8859-9");
    _mimeName.put("ISO-8859-9", "ISO-8859-9");
    _mimeName.put("LATIN5", "ISO-8859-9");
    _mimeName.put("LATIN-5", "ISO-8859-9");
    _mimeName.put("L5", "ISO-8859-9");
    _mimeName.put("CSISOLATIN5", "ISO-8859-9");
    _mimeName.put("ISO8859-9", "ISO-8859-9");
    _javaName.put("ISO-8859-9", "ISO8859_9");

    /* unsupported by java
    _mimeName.put("ISO_8859-10:1992", "ISO-8859-10");
    _mimeName.put("iso-ir-157", "ISO-8859-10");
    _mimeName.put("I6", "ISO-8859-10");
    _mimeName.put("cslSOLatin6", "ISO-8859-10");
    _mimeName.put("latin6", "ISO-8859-10");
    _javaName.put("ISO-8859-10", "ISO8859_10");
    */

    _mimeName.put("UTF-7", "UTF-7");
    _mimeName.put("UTF7", "UTF-7");
    _javaName.put("UTF-7", "UTF7");

    _mimeName.put("UTF-8", "utf-8");
    _mimeName.put("UTF8", "utf-8");
    _javaName.put("UTF-8", "UTF8");

    _mimeName.put("UTF-16", "utf-16");
    _mimeName.put("UTF16", "utf-16");
    _javaName.put("UTF-16", "UTF16");

    _mimeName.put("UTF-16-REV", "utf-16-rev");
    _mimeName.put("UTF16-REV", "utf-16-rev");
    _javaName.put("utf-16-rev", "UTF16_REV");

    _mimeName.put("JIS-ENCODING", "JIS_Encoding");
    _mimeName.put("JIS-ENCODING", "JIS_Encoding");
    _mimeName.put("CSJISENCODING", "JIS_Encoding");
    _javaName.put("JIS_Encoding", "JIS_ENCODING");

    _mimeName.put("SHIFT-JIS", "Shift_JIS");
    _mimeName.put("SHIFT_JIS", "Shift_JIS");
    _mimeName.put("CSSHIFTJIS", "Shift_JIS");
    _mimeName.put("SJIS", "Shift_JIS");
    _javaName.put("Shift_JIS", "SJIS");

    _mimeName.put("EUC-JP", "EUC-JP");
    _mimeName.put("EUC-JP", "EUC-JP");
    _mimeName.put("EUCJP", "EUC-JP");
    _mimeName.put("EUC-JP-LINUX", "EUC-JP");
    _javaName.put("EUC-JP", "EUC_JP");

    _mimeName.put("GB2312", "GB2312");
    _mimeName.put("CSGB2312", "GB2312");
    _javaName.put("GB2312", "GB2312");

    _mimeName.put("GBK", "GBK");
    _javaName.put("GBK", "GBK");

    _mimeName.put("BIG5", "Big5");
    _mimeName.put("BIG-5", "Big5");
    _mimeName.put("CSBIG5", "Big5");
    _javaName.put("Big5", "BIG5");

    _mimeName.put("KOI8-R", "KOI8-R");
    _mimeName.put("KOI-8-R", "KOI8-R");
    _mimeName.put("KOI8-R", "KOI8-R");
    _javaName.put("KOI8-R", "KOI8-R");

    _mimeName.put("MS950", "ms950");
    _javaName.put("ms950", "MS950");

    _javaName.put("JAVA", "JAVA");

    _mimeName.put("windows-hack", "ISO-8859-1");
    _mimeName.put("WINDOWS-HACK", "ISO-8859-1");
    _javaName.put("WINDOWS-HACK", "WindowsHack");

    // #4180
    _mimeName.put("MACROMAN", "utf-8");
    _javaName.put("MacRoman", "utf-8");

    _mimeName.put("KS_C_5601-1987", "ks_c_5601-1987");
    _javaName.put("ks_c_5601-1987", "Cp949");

    _javaName.put("IBM500", "Cp500");

    String []cp = new String[] {
      "037", "1006", "1025", "1026", "1046", "1097",
      "1098", "1112", "1122", "1123", "1124", "1250",
      "1251", "1252", "1253", "1254", "1255", "1256",
      "1257", "1258", "1381", "273", "277", "278", "280", "284",
      "285", "297", "33722", "420", "424", "437", "500", "737",
      "775", "838", "850", "852", "855", "857", "860", "861", "862",
      "863", "864", "865", "866", "868", "869", "870", "871", "874",
      "875", "918", "921", "922", "930", "933", "935", "937", "939",
      "942", "948", "949", "964", "970"
    };

    for (int i = 0; i < cp.length; i++) {
      _mimeName.put("CP" + cp[i], "windows-" + cp[i]);
      _mimeName.put("WINDOWS-" + cp[i], "windows-" + cp[i]);
      _javaName.put("windows-" + cp[i], "Cp" + cp[i]);
    }

    // from http://www.w3c.org/International/O-charset-lang.html
    _localeName = new ConcurrentHashMap<String,String>();
    _localeName.put("af", "ISO-8859-1");
    _localeName.put("sq", "ISO-8859-1");
    _localeName.put("ar", "ISO-8859-6");
    _localeName.put("eu", "ISO-8859-1");
    _localeName.put("bg", "ISO-8859-5");
    _localeName.put("be", "ISO-8859-5");
    _localeName.put("ca", "ISO-8859-1");
    _localeName.put("hr", "ISO-8859-2");
    _localeName.put("cs", "ISO-8859-2");
    _localeName.put("da", "ISO-8859-1");
    _localeName.put("nl", "ISO-8859-1");
    _localeName.put("en", "ISO-8859-1");
    _localeName.put("eo", "ISO-8859-3");
    _localeName.put("et", "ISO-8859-4");
    _localeName.put("fo", "ISO-8859-1");
    _localeName.put("fi", "ISO-8859-1");
    _localeName.put("fr", "ISO-8859-1");
    _localeName.put("gl", "ISO-8859-1");
    _localeName.put("de", "ISO-8859-1");
    _localeName.put("el", "ISO-8859-7");
    _localeName.put("iw", "ISO-8859-8");
    _localeName.put("hu", "ISO-8859-2");
    _localeName.put("is", "ISO-8859-1");
    _localeName.put("ga", "ISO-8859-1");
    _localeName.put("it", "ISO-8859-1");
    _localeName.put("ja", "Shift_JIS");
    _localeName.put("lv", "ISO-8859-4");
    _localeName.put("lt", "ISO-8859-4");
    _localeName.put("mk", "ISO-8859-5");
    _localeName.put("mt", "ISO-8859-3");
    _localeName.put("no", "ISO-8859-1");
    _localeName.put("pl", "ISO-8859-2");
    _localeName.put("pt", "ISO-8859-1");
    _localeName.put("ro", "ISO-8859-2");
    // _localeName.put("ru", "KOI8-R");
    _localeName.put("ru", "ISO-8859-5");
    _localeName.put("gd", "ISO-8859-1");
    _localeName.put("sr", "ISO-8859-5");
    _localeName.put("sk", "ISO-8859-2");
    _localeName.put("sl", "ISO-8859-2");
    _localeName.put("es", "ISO-8859-1");
    _localeName.put("sv", "ISO-8859-1");
    _localeName.put("tr", "ISO-8859-9");
    _localeName.put("uk", "ISO-8859-5");

    _localeName.put("ko", "EUC-KR");
    _localeName.put("zh", "GB2312");
    _localeName.put("zh_TW", "Big5");
  }
}
