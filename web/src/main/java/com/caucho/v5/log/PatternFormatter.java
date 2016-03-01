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

package com.caucho.v5.log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.annotation.PostConstruct;

import com.caucho.v5.log.impl.MdcManager;
import com.caucho.v5.log.impl.MdcManager.MdcService;
import com.caucho.v5.util.CurrentTime;

/**
 * Formats a log entry.
 */
public class PatternFormatter extends Formatter
{
  private static final HashMap<String,String> _patternMap;
  
  public static final String DEFAULT_PATTERN = "[%d] %|{%t} %p %c{1}: %m";

  private static ZoneId _localZoneId;
  
  /*
  private static final FreeRing<ELContext> _freeContextList
    = new FreeRing<>(64);
    */
  
  private String _pattern;
  private FormatItem []_formatList;
  
  private boolean _isInitRequired;
  
  public PatternFormatter()
  {
    _pattern = DEFAULT_PATTERN;
    _isInitRequired = true;
  }
  
  public PatternFormatter(String pattern)
  {
    this(pattern, true);
  }
  
  public PatternFormatter(String pattern, boolean isTop)
  {
    _pattern = pattern;
    
    _formatList = parsePattern(pattern, isTop);
  }
  
  public void setPattern(String pattern)
  {
    _pattern = pattern;
  }
  
  @PostConstruct
  public void init()
  {
    if (_isInitRequired) {
      _formatList = parsePattern(_pattern);
    }
  }

  /**
   * Formats the record
   */
  @Override
  public String format(LogRecord log)
  {
    StringBuilder sb = new StringBuilder();
    
    int mark = 0;

    for (FormatItem item : _formatList) {
      item.format(sb, log, mark);
      
      if (item instanceof PrettyPrintMarkItem) {
        mark = sb.length();
      }
    }
    
    return sb.toString();
  }
   
  private FormatItem []parsePattern(String pattern)
  {
    return parsePattern(pattern, true);
  }
  
 private FormatItem []parseSubPattern(String pattern)
 {
   return parsePattern(pattern, false);
 }
  
  private FormatItem []parsePattern(String pattern, boolean isTop)
  {
    ArrayList<FormatItem> itemList = new ArrayList<FormatItem>();
    StringBuilder sb = new StringBuilder();
    
    boolean isException = false;

    for (int i = 0; i < pattern.length(); i++) {
      char ch = pattern.charAt(i);
      
      if (ch != '%') {
        sb.append(ch);
        continue;
      }
      
      String padString = parsePadString(pattern, i + 1);
      PadItem padItem = null;
      
      if (padString != null) {
        padItem = createPadItem(padString);
        i += padString.length();
      }

      String name = parsePatternName(pattern, i + 1);
      
      if (name == null) {
        sb.append(ch);
        continue;
      }
      
      i += name.length();
      
      String arg = parseArg(pattern, i + 1);
      if (arg != null) {
        i += arg.length() + 2;
      }
        
      switch (name) {
      case "c":
      case "logger":
        addText(itemList, sb);
        itemList.add(new CategoryItem(padItem, arg));
        break;
        
      case "C":
      case "class":
        addText(itemList, sb);
        itemList.add(new ClassItem(padItem, arg));
        break;
        
      case "d":
      case "date":
        addText(itemList, sb);
        itemList.add(new DateItem(padItem, arg));
        break;
        
      case "ex":
      case "exception":
      case "throwable":
        addText(itemList, sb);
        itemList.add(new ExceptionItem(padItem, arg));
        isException = true;
        break;
        
      case "highlight":
        addText(itemList, sb);
        itemList.add(new HighlightItem(padItem, arg));
        break;
        
      case "m":
      case "msg":
      case "message":
        addText(itemList, sb);
        itemList.add(new MessageItem(padItem));
        break;
        
      case "n":
        addText(itemList, sb);
        itemList.add(new NewlineItem(padItem));
        break;
        
      case "p":
      case "level":
        addText(itemList, sb);
        itemList.add(new LevelItem(padItem));
        break;

      case "r":
      case "relative":
        addText(itemList, sb);
        itemList.add(new RelativeTimeItem(padItem));
        break;

      case "rEx":
      case "rException":
        addText(itemList, sb);
        itemList.add(new ReverseExceptionItem(padItem, arg));
        isException = true;
        break;
        
      case "sn":
      case "sequenceNumber":
        addText(itemList, sb);
        itemList.add(new SequenceItem(padItem));
        break;

      case "t":
      case "thread":
        addText(itemList, sb);
        itemList.add(new ThreadItem(padItem));
        break;
        
      case "X":
      case "mdc":
        addText(itemList, sb);
        itemList.add(new MdcItem(padItem, arg));
        break;

      case "%":
        sb.append('%');
        break;

      case "|":
        addText(itemList, sb);
        itemList.add(new PrettyPrintMarkItem(padItem));
        if (arg != null) {
          i -= arg.length() + 2;
        }
        break;

        /*
      case "#":
        addText(itemList, sb);
        itemList.add(new ExprItem(padItem, arg));
        break;
        */

      default:
        sb.append('%');
        break;
      }
    }

    if (sb.length() > 0) {
      itemList.add(new TextItem(null, sb.toString()));
    }
    
    if (itemList.size() > 0
        && itemList.get(itemList.size() - 1) instanceof NewlineItem) {
      itemList.remove(itemList.size() - 1);
    }
    
    if (! isException && isTop) {
      itemList.add(new ExceptionItem(null, null));
    }
    
    FormatItem []formatList = new FormatItem[itemList.size()];
    itemList.toArray(formatList);
    
    return formatList;
  }
  
  private String parsePatternName(String pattern, int i)
  {
    String bestMatch = null;
    
    StringBuilder sb = new StringBuilder();
    
    for (; i < pattern.length(); i++) {
      sb.append(pattern.charAt(i));

      String match = _patternMap.get(sb.toString());

      if (match == null) {
        return bestMatch;
      }
      else if (match.length() == sb.length()) {
        bestMatch = match;
      }
    }
    
    return bestMatch;
  }
  
  private String parseArg(String pattern, int i)
  {
    if (pattern.length() <= i) {
      return null;
    }
    
    if (pattern.charAt(i) != '{') {
      return null;
    }
    
    int count = 0;
    
    for (int j = i; j < pattern.length(); j++) {
      char ch = pattern.charAt(j);
      
      if (ch == '}') {
        count--;
        
        if (count == 0) {
          return pattern.substring(i + 1, j);
        }
      }
      else if (ch == '{') {
        count++;
      }
    }
    
    return null;
  }
  
  private String parsePadString(String pattern, int head)
  {
    if (pattern.length() <= head) {
      return null;
    }
    
    int tail;
    for (tail = head; tail < pattern.length(); tail++) {
      switch (pattern.charAt(tail)) {
      case '0': case '1': case '2': case '3': case '4':
      case '5': case '6': case '7': case '8': case '9':
      case '.': case '-':
        break;
        
      default:
        if (head != tail) {
          return pattern.substring(head, tail);
        }
        else {
          return null;
        }
      }
    }
    
    return null;
  }
  
  private PadItem createPadItem(String format)
  {
    int padValue = 0;
    int padSign = 1;
    
    int truncateValue = 0;
    int truncateSign = 1;
    
    int i = 0;
    int length = format.length();
    
    if (length <= i) {
      return null;
    }
    
    if (format.charAt(i) == '-') {
      padSign = -1;
      i++;
    }
    
    int ch;
    
    for (;
        (i < length
         && '0' <= (ch = format.charAt(i))
         && ch <= '9');
        i++) {
      padValue = 10 * padValue + ch - '0';
    }
    
    if (i < length && format.charAt(i) == '.') {
      i++;
      
      if (i < length && format.charAt(i) == '-') {
        truncateSign = -1;
        i++;
      }
      
      for (;
          (i < length
           && '0' <= (ch = format.charAt(i))
           && ch <= '9');
          i++) {
        truncateValue = 10 * truncateValue + ch - '0';
      }
    }
        
    return new PadItem(padSign * padValue, truncateSign * truncateValue);
  }
  
  private void addText(ArrayList<FormatItem> itemList, StringBuilder sb)
  {
    if (sb.length() > 0) {
      itemList.add(new TextItem(null, sb.toString()));
      sb.setLength(0);
    }
  }
  
  private static void printDepth(StringBuilder sb, int depth)
  {
    for (int j = 0; j < depth; j++) {
      sb.append(' ');
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _pattern + "]";
  }
  
  private static class PadItem
  {
    private final int _padLeft;
    private final int _padRight;
    private final int _truncateLeft;
    private final int _truncateRight;
    
    PadItem(int pad, int truncate)
    {
      if (pad < 0) {
        _padRight = -pad;
        _padLeft = 0;
      }
      else {
        _padLeft = pad;
        _padRight= 0;
        
      }
      
      if (truncate < 0) {
        _truncateRight = -truncate;
        _truncateLeft = Integer.MAX_VALUE / 2;
      }
      else if (truncate > 0) {
        _truncateLeft = truncate;
        _truncateRight = Integer.MAX_VALUE / 2;
      }
      else {
        _truncateLeft = Integer.MAX_VALUE / 2;
        _truncateRight = Integer.MAX_VALUE / 2;
      }
    }
    
    public void format(StringBuilder sb, String value)
    {
      int length = value.length();
      
      for (int i = length; i < _padLeft; i++) {
        sb.append(' ');
      }
      
      int head = Math.max(0, length - _truncateLeft);
      int tail = Math.min(length, _truncateRight);
      
      sb.append(value, head, tail);
      
      for (int i = length; i < _padRight; i++) {
        sb.append(' ');
      }
    }
  }
  
  private static class ItemFilter
  {
    public String format(String item)
    {
      return item;
    }
  }
  
  private static class LastPackageFilter extends ItemFilter
  {
    private final int _count;
    
    LastPackageFilter(int count)
    {
      _count = Math.max(1, count);
    }
    
    @Override
    public String format(String string)
    {
      if (_count == 0 || string == null) {
        return "";
      }
      
      int count = 0;
      
      for (int i = string.length() - 1; i >= 0; i--) {
        char ch = string.charAt(i);
        
        if (ch == '.') {
          count++;
          
          if (_count <= count) {
            return string.substring(i + 1);
          }
        }
      }
      
      return string;
    }
  }
  
  /**
   * Package name compaction, like in LogBack. Names are compressed
   * to "c.f.b.ClassName", and expand packages if there's room.
   */
  private static class CompactPackageFilter extends ItemFilter
  {
    private final int _count;
    
    CompactPackageFilter(int count)
    {
      _count = count;
    }
    
    @Override
    public String format(String string)
    {
      if (_count == 0 || string == null) {
        return "";
      }
      
      int tail = 0;
      int length = string.length();
      int i = length - 1;
      
      for (; i >= 0; i--) {
        char ch = string.charAt(i);
        
        if (ch == '.') {
          if (length - i < _count || tail == 0) {
            tail = i;
          }
          else {
            break;
          }
        }
      }
      
      if (i <= 0) {
        return string;
      }

      StringBuilder sb = new StringBuilder();
      
      for (i = 0; i < tail; i++) {
        char ch = string.charAt(i);
        
        if (i == 0) {
          sb.append(ch);
          sb.append('.');
        }
        else if (ch == '.') {
          sb.append(string.charAt(i + 1));
          sb.append('.');
        }
      }
      
      sb.append(string, tail + 1, length);
      
      return sb.toString();
    }
  }
  
  private static class PrecisionItem
  {
    void append(StringBuilder sb, String value, int start, int end)
    {
      sb.append(value, start, end);
      sb.append('.');
    }
  }
  
  private static class TextPrecisionItem extends PrecisionItem
  {
    private String _text;
    
    TextPrecisionItem(String text)
    {
      _text = text;
      
    }
    
    @Override
    void append(StringBuilder sb, String value, int start, int end)
    {
      sb.append(_text);
      sb.append('.');
    }
  }
  
  private static class LengthPrecisionItem extends PrecisionItem
  {
    private int _length;
    
    LengthPrecisionItem(int length)
    {
      _length = length;
      
    }
    
    @Override
    void append(StringBuilder sb, String value, int start, int end)
    {
      sb.append(value, start, start + Math.min(_length, end - start));
      sb.append('.');
    }
  }

  private static class PatternPrecisionFilter extends ItemFilter
  {
    private final PrecisionItem []_items;
    
    PatternPrecisionFilter(String format)
    {
      String []formatPatterns = format.split("\\.");
      
      if (formatPatterns.length == 0) {
        formatPatterns = new String[] {""};
      }
      
      _items = new PrecisionItem[formatPatterns.length];
      
      for (int i = 0; i < formatPatterns.length; i++) {
        if (isInteger(formatPatterns[i], 0)) {
          _items[i] = new LengthPrecisionItem(Integer.parseInt(formatPatterns[i]));
        }
        else {
          _items[i] = new TextPrecisionItem(formatPatterns[i]);
        }
      }
    }
    
    @Override
    public String format(String string)
    {
      int count = 0;
      int head = 0;
      int tail = 0;
      int length = string.length();
      
      StringBuilder sb = new StringBuilder();
      
      for (; tail < length; tail++) {
        char ch = string.charAt(tail);
        
        if (ch == '.') {
          PrecisionItem item = _items[Math.min(count, _items.length - 1)];
          
          item.append(sb, string, head, tail);
          head = tail + 1;
          count++;
        }
      }
      
      sb.append(string, head, tail);
      
      return sb.toString();
    }
  }
  
  private static ItemFilter parsePrecisionFilter(String format)
  {
    if (format == null || format.equals("")) {
      return new ItemFilter();
    }
    else if (isInteger(format, 0)) {
      int value = Integer.parseInt(format);
      
      if (value < 5) {
        return new LastPackageFilter(value);
      }
      else {
        return new CompactPackageFilter(value);
      }
    }
    /*
    else if (format.charAt(0) == '-' && isInteger(format, 1)) {
      
    }
    */
    else {
      return new PatternPrecisionFilter(format);
    }
  }
  
  private static boolean isInteger(String format, int i)
  {
    if (i == format.length()) {
      return false;
    }
    
    for (; i < format.length(); i++) {
      char ch = format.charAt(i);
      
      if (! ('0' <= ch && ch <= '9')) {
        return false;
      }
    }
    
    return true;
  }

  private static class PatternEnv
    implements Function<String,Object>
  {
    public Object apply(String property)
    {
      switch ((String) property) {
      /*
      case "cookie": {
        HttpServletRequest req = ThreadRequestFactory.getCurrentHttpRequest();

        if (req != null) {
          Cookie []cookies = req.getCookies();

          if (cookies != null)
            return new CookieMap(cookies);
        }

        return null;
      }
      */
      
      /*
      case "request": {
        env.setPropertyResolved(base, property);

        return ThreadRequestFactory.getCurrentHttpRequest();
      }
      */
      
      /*
      case "session": {
        env.setPropertyResolved(base, property);

        HttpServletRequest req = ThreadRequestFactory.getCurrentHttpRequest();
        
        if (req != null) {
          HttpSession session = req.getSession(false);

          return session;
        }

        return null;
      }
      */
      
      case "thread": {
        return Thread.currentThread().getName();
      }
      }

      return null;
    }
  }

  //
  // formatting items
  //

  private static class FormatItem
  {
    private final PadItem _padItem;
    
    FormatItem(PadItem padItem)
    {
      _padItem = padItem;
    }
    
    protected boolean isPad()
    {
      return _padItem != null;
    }
    
    public void format(StringBuilder sb, 
                       LogRecord log,
                       int mark)
    {
      if (isPad()) {
        StringBuilder padBuilder = new StringBuilder();
        
        formatImpl(padBuilder, log);
        
        _padItem.format(sb, padBuilder.toString());
      }
      else {
        formatImpl(sb, log);
      }
    }
    
    protected void formatImpl(StringBuilder sb, LogRecord log)
    {
    }
  }

  private static class TextItem extends FormatItem
  {
    private final String _text;

    TextItem(PadItem padItem, String text)
    {
      super(padItem);
      
      _text = text;
    }
    
    @Override
    public void formatImpl(StringBuilder sb, LogRecord log)
    {
      sb.append(_text);
    }
  }

  /**
   * The %c code writes the log category
   *
   */
  private static class CategoryItem extends FormatItem
  {
    private final ItemFilter _filter;
    
    CategoryItem(PadItem padItem, String format)
    {
      super(padItem);
      
      _filter = parsePrecisionFilter(format);
    }
    
    @Override
    public void formatImpl(StringBuilder sb, LogRecord log)
    {
      String name = log.getLoggerName();
      
      sb.append(_filter.format(name));
    }
  }

  /**
   * The %C code writes the calling class
   *
   */
  private static class ClassItem extends FormatItem
  {
    private final ItemFilter _filter;
    
    ClassItem(PadItem padItem, String format)
    {
      super(padItem);
      
      _filter = parsePrecisionFilter(format);
    }
    
    @Override
    public void formatImpl(StringBuilder sb, LogRecord log)
    {
      String name = log.getSourceClassName();
      
      sb.append(_filter.format(name));
    }
  }

  /**
   * The %d code writes the timestamp
   *
   */
  private static class DateItem extends FormatItem
  {
    private DateFormatter _formatter;
    
    DateItem(PadItem padItem, String format)
    {
      super(padItem);
      
      if (format == null) {
        format = "yyyy-MM-dd HH:mm:ss,SSS";
      }
      else if ("ISO8601".equals(format)) {
        format = "yyyy-MM-dd HH:mm:ss,SSS";
      }
      else if ("ISO8601_BASIC".equals(format)) {
        format = "yyyyMMdd HHmmss,SSS";
      }
      else if ("ABSOLUTE".equals(format)) {
        format = "HH:mm:ss,SSS";
      }
      
      _formatter = new DateFormatter(format);
    }
    
    @Override
    public void formatImpl(StringBuilder sb, LogRecord log)
    {
      long now = log.getMillis();
      
      if (CurrentTime.isTest()) {
        now = CurrentTime.getCurrentTime();
      }
      
      /*
      LocalDateTime time = LocalDateTime.ofEpochSecond(now / 1000, 
                                                       (int) (now % 1000) * 1000000, 
                                                       ZoneOffset.UTC);
                                                       */
      Instant instant = Instant.ofEpochMilli(now);
      ZonedDateTime timeLocal = ZonedDateTime.ofInstant(instant, _localZoneId);
      
      _formatter.format(sb, timeLocal);
    }
  }
  
  private static final int ANSI_BRIGHT = 0x100;
  private static final int ANSI_DIM = 0x200;
  private static final int ANSI_ITALIC = 0x300;
  private static final int ANSI_UNDERLINE = 0x400;
  
  private static final int ANSI_BLACK = 30;
  private static final int ANSI_RED = 31;
  private static final int ANSI_GREEN = 32;
  private static final int ANSI_YELLOW = 33;
  private static final int ANSI_BLUE = 34;
  private static final int ANSI_MAGENTA = 35;
  private static final int ANSI_CYAN = 36;
  private static final int ANSI_WHITE = 37;
  
  /**
   * Ansi highlighting
   */
  private static class HighlightItem extends FormatItem
  {
    private final FormatItem []_format;
    private final HashMap<Level,Integer> _colorMap
      = new HashMap<Level,Integer>();
    
    HighlightItem(PadItem padItem, String arg)
    {
      super(padItem);
      
      if (arg == null) {
        _format = new FormatItem[0];
      }
      else {
        PatternFormatter subFormatter = new PatternFormatter(arg, false);
        
        _format = subFormatter._formatList;
      }
      
      _colorMap.put(Level.WARNING, ANSI_BRIGHT|ANSI_RED);
      _colorMap.put(Level.SEVERE, ANSI_BRIGHT|ANSI_RED);
      _colorMap.put(Level.INFO, ANSI_GREEN);
      _colorMap.put(Level.CONFIG, ANSI_GREEN);
      _colorMap.put(Level.FINE, ANSI_BLUE);
      _colorMap.put(Level.FINER, ANSI_CYAN);
      _colorMap.put(Level.FINEST, ANSI_BLACK|ANSI_DIM);
      _colorMap.put(Level.ALL, ANSI_BLACK|ANSI_DIM);
    }
    
    @Override
    public void formatImpl(StringBuilder sb, LogRecord log)
    {
      Integer color = _colorMap.get(log.getLevel());
      
      if (color != null) {
        highlight(sb, color);
      }
      
      int mark = 0;
      for (FormatItem format : _format) {
        format.format(sb, log, mark);
      }
      
      if (color != null) {
        highlight(sb, 0);
      }
    }
    
    private void highlight(StringBuilder sb, int code)
    {
      if ((code & 0xff00) != 0) {
        sb.append("\u001b[")
          .append(code & 0xff)
          .append(";")
          .append(code >> 8)
          .append("m");
      }
      else {
        sb.append("\u001b[").append(code).append("m");
      }
    }
  }

  /**
   * The %p code writes the logger level
   *
   */
  private static class LevelItem extends FormatItem
  {
    LevelItem(PadItem padItem)
    {
      super(padItem);
    }
    
    @Override
    public void formatImpl(StringBuilder sb, LogRecord log)
    {
      sb.append(log.getLevel());
    }
  }

  /**
   * The %m code writes the log message
   *
   */
  private class MessageItem extends FormatItem
  {
    MessageItem(PadItem padItem)
    {
      super(padItem);
      
    }
    @Override
    public void format(StringBuilder sb, 
                       LogRecord log,
                       int mark)
    {
      String msg = log.getMessage();
      
      if (log.getParameters() != null) {
        msg = formatMessage(log);
      }
      
      if (mark == 0) {
        sb.append(msg);
        return;
      }
      
      if (msg == null) {
        return;
      }
      
      int length = msg.length();
      for (int i = 0; i < length; i++) {
        char ch = msg.charAt(i);
        
        sb.append(ch);
        
        if (ch == '\n') {
          for (int j = 0; j < mark; j++) {
            sb.append(' ');
          }
        }
      }
    }
  }

  /**
   * The %n code creates a newline in the log.
   *
   */
  private static class NewlineItem extends FormatItem
  {
    NewlineItem(PadItem padItem)
    {
      super(padItem);
    }
    
    @Override
    public void format(StringBuilder sb, 
                       LogRecord log,
                       int mark)
    {
      sb.append("\n");
    }
  }

  private static class RelativeTimeItem extends FormatItem
  {
    private long _start = CurrentTime.getCurrentTime();
    
    RelativeTimeItem(PadItem padItem)
    {
      super(padItem);
    }
    
    @Override
    public void formatImpl(StringBuilder sb, LogRecord log)
    {
      long now = CurrentTime.getCurrentTime();
      
      sb.append(now - _start);
    }
  }

  private static class SequenceItem extends FormatItem
  {
    private AtomicLong _counter = new AtomicLong();
    
    SequenceItem(PadItem padItem)
    {
      super(padItem);
    }
    
    @Override
    public void formatImpl(StringBuilder sb, LogRecord log)
    {
      sb.append(_counter.incrementAndGet());
    }
  }

  private static class ThreadItem extends FormatItem
  {
    ThreadItem(PadItem padItem)
    {
      super(padItem);
    }
    
    @Override
    public void formatImpl(StringBuilder sb, LogRecord log)
    {
      Thread thread = Thread.currentThread();
      
      sb.append(thread.getName());
      sb.append('@');
      sb.append(thread.getId());
    }
  }

  private static class MdcItem extends FormatItem
  {
    private final MdcService _mdcEntry ;

    MdcItem(PadItem padItem, String key)
    {
      super(padItem);
      
      _mdcEntry  = MdcManager.get(key);
    }
    
    @Override
    public void formatImpl(StringBuilder sb, LogRecord log)
    {
      sb.append(_mdcEntry.get());
    }
  }

  /*
  private static class ExprItem extends FormatItem
  {
    private ExprCfg _expr;

    ExprItem(PadItem padItem, String expr)
    {
      super(padItem);
      
      try {
        _expr = ExprCfg.newParser(expr).parse();
      } catch (Exception ex) {
        throw ConfigException.wrap(ex);
      }
    }
    
    @Override
    public void formatImpl(StringBuilder sb, LogRecord log)
    {
      sb.append(_expr.evalString(new PatternEnv()));
    }
  }
  */

  private static class PrettyPrintMarkItem extends FormatItem
  {
    PrettyPrintMarkItem(PadItem padItem)
    {
      super(padItem);
    }
    
    @Override
    public void format(StringBuilder sb, LogRecord log, int mark)
    {
    }
  }
  
  private static Throwable getCause(Throwable exn)
  {
    for (; exn.getCause() != null; exn = exn.getCause()) {
    }
    
    return exn;
  }

  private static class ExceptionItem extends FormatItem
  {
    private int _max;
    
    ExceptionItem(PadItem padItem, String arg)
    {
      super(padItem);
      
      int max = Integer.MAX_VALUE;
      
      if (arg == null || "".equals(arg)) {
      }
      else if (isInteger(arg, 0)) {
        max = Integer.valueOf(arg);
        
        if (max == 0) {
          max = -1;
        }
      }
      else if ("none".equals(arg)) {
        max = -1;
      }
      else if ("short".equals(arg)) {
        max = 1;
      }
      
      _max = max;
    }
    
    @Override
    public void format(StringBuilder sb, LogRecord log, int mark)
    {
      Throwable exn = log.getThrown();
      
      if (exn == null || _max <= 0) {
        return;
      }
      
      StackTraceElement []prevStack = null;
      
      for (; exn != null; exn = exn.getCause()) {
        /*
        if (count != 0) {
          printDepth(sb, mark);
        }
        */

        sb.append("\n");
        printDepth(sb, mark);
        sb.append(exn.toString());
        sb.append("\n");

        StackTraceElement []stack = exn.getStackTrace();
        
        int tail = findTail(stack, prevStack);
        
        int max = Math.min(tail, _max);
      
        for (int i = 0; i < stack.length && i < max; i++) {
          StackTraceElement item = stack[i];
        
          printDepth(sb, mark);
        
          sb.append("\tat ").append(item).append("\n");
        }
        
        if (tail < stack.length && tail < _max) {
          printDepth(sb, mark);
          sb.append("... " + (stack.length - tail) + " more\n");
        }
        
        if (exn.getCause() != null) {
          printDepth(sb, mark);
          
          sb.append("Thrown by: ");
        }

        prevStack = stack;
      }
    }
  }

  private static class ReverseExceptionItem extends FormatItem
  {
    private int _max;
    
    ReverseExceptionItem(PadItem padItem, String arg)
    {
      super(padItem);
      
      int max = Integer.MAX_VALUE;
      
      if (arg == null || "".equals(arg)) {
      }
      else if (isInteger(arg, 0)) {
        max = Integer.valueOf(arg);
        
        if (max == 0) {
          max = -1;
        }
      }
      else if ("none".equals(arg)) {
        max = -1;
      }
      else if ("short".equals(arg)) {
        max = 1;
      }
      
      _max = max;
    }
    
    @Override
    public void format(StringBuilder sb, LogRecord log, int mark)
    {
      Throwable exn = log.getThrown();
      
      if (exn == null || _max <= 0) {
        return;
      }
      
      format(sb, exn, mark);
    }
    
    private StackTraceElement []format(StringBuilder sb,
                                       Throwable exn,
                                       int mark)
    {
      if (exn == null) {
        return null;
      }
      
      StackTraceElement []prevStack = format(sb, exn.getCause(), mark);
      
      StackTraceElement []stack = exn.getStackTrace();
      
      if (exn.getCause() != null) {
        printDepth(sb, mark);
        
        sb.append("Wrapped by: ");
      }
      
      sb.append(exn.toString());
      sb.append("\n");
        
      int tail = findTail(stack, prevStack);
        
      int max = Math.min(tail, _max);
      
      for (int i = 0; i < stack.length && i < max; i++) {
        StackTraceElement item = stack[i];
        
        printDepth(sb, mark);
        
        sb.append("  at ").append(item).append("\n");
      }
        
      if (tail < stack.length && tail < _max) {
        printDepth(sb, mark);
        sb.append("... " + (stack.length - tail) + " more\n");
      }

      return stack;
    }
  }

  private static int findTail(StackTraceElement []stack, 
                              StackTraceElement []prevStack)
  {
    if (prevStack == null) {
      return stack.length;
    }
    
    int max = Math.min(stack.length, prevStack.length);
    
    for (int i = 1; i <= max; i++) {
      if (! stack[stack.length - i].equals(prevStack[prevStack.length - i])) {
        return stack.length - i + 1;
      }
    }
    
    return stack.length;
  }
  
  private static void addPattern(String value)
  {
    for (int i = 1; i <= value.length(); i++) {
      String name = value.substring(0, i);
      
      String oldValue = _patternMap.get(name);
      
      if (oldValue == null || value.compareTo(oldValue) < 0) {
        _patternMap.put(name, value);
      }
    }
  }
  
  static {
    _patternMap = new HashMap<>();
    
    addPattern("c");
    addPattern("class");
    addPattern("C");    
    addPattern("d");    
    addPattern("date");    
    addPattern("ex");    
    addPattern("exception");    
    addPattern("highlight");
    addPattern("level");
    addPattern("lo");
    addPattern("logger");
    addPattern("m");
    addPattern("msg");
    addPattern("message");
    addPattern("n");
    addPattern("p");
    addPattern("r");
    addPattern("relative");
    addPattern("rEx");
    addPattern("sn");
    addPattern("sequenceNumber");
    addPattern("t");
    addPattern("throwable");
    addPattern("X");
    addPattern("mdc");
    addPattern("%");
    addPattern("|");
    addPattern("#");
    
    _localZoneId = ZoneOffset.systemDefault();
    //_localZone = ZoneOffset.UTC;
    //_localZone = ZoneOffset.of(localZoneId.getId());
  }
}
