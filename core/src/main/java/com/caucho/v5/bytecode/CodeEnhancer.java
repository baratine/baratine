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

package com.caucho.v5.bytecode;

import com.caucho.v5.bytecode.attr.Attribute;
import com.caucho.v5.bytecode.attr.CodeAttribute;
import com.caucho.v5.util.ByteArrayBuffer;
import com.caucho.v5.util.IntArray;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Visitor for travelling the code.
 */
public class CodeEnhancer extends CodeVisitor {
  static private final Logger log = Logger.getLogger(CodeEnhancer.class.getName());
  private ByteArrayBuffer _code;

  private ArrayList<Jump> _jumps;
  private ArrayList<Switch> _switches;
  private boolean _changeLength;

  // already visited targets
  private IntArray _pendingTargets;
  private IntArray _completedTargets;

  public CodeEnhancer()
  {
  }

  public CodeEnhancer(JavaClass javaClass, CodeAttribute code)
  {
    init(javaClass, code);
  }

  public void init(JavaClass javaClass, CodeAttribute codeAttr)
  {
    super.init(javaClass, codeAttr);

    _code = new ByteArrayBuffer();
    
    byte []codeBuffer = codeAttr.getCode();
    
    _code.add(codeBuffer, 0, codeBuffer.length);

    _changeLength = false;
  }

  /**
   * Analyzes the code for a method
   */
  public void analyze(Analyzer analyzer, boolean allowFlow)
    throws Exception
  {
    _pendingTargets = new IntArray();
    _completedTargets = new IntArray();
    
    analyzeImpl(analyzer, allowFlow, _pendingTargets, _completedTargets);
  }

  /**
   * Returns the code buffer.
   */
  public byte []getCode()
  {
    return _code.getBuffer();
  }

  /**
   * Returns the length.
   */
  public int getLength()
  {
    return _code.getLength();
  }

  /**
   * Adds a byte to the code.
   */
  public void addByte(int offset, int value)
  {
    insertCode(offset, 1);
    
    _code.set(offset, value);
  }

  /**
   * Adds a byte to the code.
   */
  public void setByte(int offset, int value)
  {
    _code.set(offset, value);
  }

  /**
   * Adds a short to the code.
   */
  public void addShort(int offset, int value)
  {
    insertCode(offset, 2);
    
    _code.set(offset + 0, value >> 8);
    _code.set(offset + 1, value);
  }

  /**
   * Adds a byte to the code.
   */
  public void add(int offset, byte []buffer, int bufOffset, int length)
  {
    insertCode(offset, length);
    
    _code.set(offset, buffer, bufOffset, length);
  }

  /**
   * Removes a range from the code.
   */
  public void remove(int offset, int count)
  {
    removeCode(offset, count);
  }

  /**
   * Adds a byte to the code.
   */
  public void addNulls(int offset, int count)
  {
    insertCode(offset, count);
  }

  /**
   * Updates indices when adding a chunk of code.  The operation at
   * the given offset moves, e.g. adding 6 bytes to the beginning of
   * the program moves the initial byte down by 6 and therefore needs
   * to update the links as well.
   *
   * Therefore, enhancers which expand an opcode from 2 bytes to 3 bytes
   * must insert the new bytes after the initial opcode.
   */
  protected void insertCode(int offset, int count)
  {
    if (_jumps == null)
      analyzeJumps();

    // XXX: revisits the new code
    if (offset <= _offset) {
      _offset += count;
    }

    for (int i = 0; i < _jumps.size(); i++) {
      Jump jump = _jumps.get(i);

      jump.insert(this, offset, count);
    }
    
    ArrayList<CodeAttribute.ExceptionItem> exns = getExceptions();

    for (int i = 0; i < exns.size(); i++) {
      CodeAttribute.ExceptionItem exn = exns.get(i);

      if (offset <= exn.getStart())
        exn.setStart(exn.getStart() + count);
      
      if (offset <= exn.getEnd())
        exn.setEnd(exn.getEnd() + count);
      
      if (offset <= exn.getHandler())
        exn.setHandler(exn.getHandler() + count);
    }

    if (_pendingTargets != null) {
      for (int i = _pendingTargets.size() - 1; i >= 0; i--) {
        int target = _pendingTargets.get(i);

        if (offset <= target)
          _pendingTargets.set(i, target + count);
      }
      
      for (int i = _completedTargets.size() - 1; i >= 0; i--) {
        int target = _completedTargets.get(i);

        if (offset <= target)
          _completedTargets.set(i, target + count);
      }
    }

    for (int i = 0; i < _switches.size(); i++) {
      Branch branch = _switches.get(i);

      branch.insert(this, offset, count);
    }

    for (int i = 0; i < count; i++)
      _code.add(offset, 0);

    for (int i = 0; i < _switches.size(); i++) {
      Switch branch = _switches.get(i);

      branch.insertPad(this, offset, count);
    }
  }
  
  protected void removeCode(int offset, int count)
  {
    if (_jumps == null)
      analyzeJumps();

    if (offset + count < _offset)
      _offset -= count;
    else if (offset <= _offset)
      _offset = offset;

    for (int i = 0; i < _jumps.size(); i++) {
      Branch jump = _jumps.get(i);

      jump.remove(this, offset, count);
    }
    
    ArrayList<CodeAttribute.ExceptionItem> exns = getExceptions();

    for (int i = 0; i < exns.size(); i++) {
      CodeAttribute.ExceptionItem exn = exns.get(i);

      exn.setStart(remove(exn.getStart(), offset, count));
      exn.setEnd(remove(exn.getEnd(), offset, count));
      exn.setHandler(remove(exn.getHandler(), offset, count));
    }

    if (_pendingTargets != null) {
      for (int i = _pendingTargets.size() - 1; i >= 0; i--) {
        int target = _pendingTargets.get(i);

        _pendingTargets.set(i, remove(target, offset, count));
      }
      
      for (int i = _completedTargets.size() - 1; i >= 0; i--) {
        int target = _completedTargets.get(i);

        _completedTargets.set(i, remove(target, offset, count));
      }
    }

    for (int i = 0; i < _switches.size(); i++) {
      Branch branch = _switches.get(i);

      branch.remove(this, offset, count);
    }

    _code.remove(offset, count);

    for (int i = 0; i < _switches.size(); i++) {
      Switch branch = _switches.get(i);

      branch.removePad(this, offset, count);
    }
  }

  protected void analyzeJumps()
  {
    _jumps = new ArrayList<Jump>();
    _switches = new ArrayList<Switch>();

    _changeLength = true;

    JumpAnalyzer analyzer = new JumpAnalyzer();

    CodeVisitor visitor = new CodeVisitor(getJavaClass(), getCodeAttribute());

    try {
      visitor.analyze(analyzer);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  /**
   * Updates the code.
   */
  public void update()
  {
    byte []code = new byte[_code.size()];

    System.arraycopy(_code.getBuffer(), 0, code, 0, _code.size());

    _codeAttr.setCode(code);

    if (_changeLength) {
      // XXX: really need more sophisticated solution
      ArrayList<Attribute> attrList = getCodeAttribute().getAttributes();
      for (int i = attrList.size() - 1; i >= 0; i--) {
        Attribute attr = attrList.get(i);

        if (attr.getName().equals("LineNumberTable"))
          attrList.remove(i);
      }
    }
  }

  private int remove(int pc, int offset, int count)
  {
    if (pc < offset)
      return pc;
    else if (pc < offset + count)
      return offset;
    else
      return pc - count;
  }

  abstract static class Branch {
    abstract void insert(CodeEnhancer enhancer, int offset, int count);
    abstract void remove(CodeEnhancer enhancer, int offset, int count);
  }
    
  static class Jump extends Branch {
    private int _src;
    private int _delta;

    Jump(int src, int delta)
    {
      _src = src;
      _delta = delta;
    }

    void insert(CodeEnhancer enhancer, int offset, int count)
    {
      // offset is before the jump
      if (offset <= _src && offset <= _src + _delta) {
        _src += count;
      }
      // offset is inside a forward jump
      else if (_src < offset && offset < _src + _delta) {
        _delta += count;
        enhancer.setShort(_src + 1, _delta);
      }
      // offset is inside a backward jump
      else if (_src + _delta <= offset && offset <= _src) {
        _delta -= count;
        enhancer.setShort(_src + 1, _delta);
        _src += count;
      }
    }

    void remove(CodeEnhancer enhancer, int offset, int count)
    {
      // offset is before the jump
      if (offset <= _src && offset <= _src + _delta) {
        _src -= count;
      }
      // offset is inside a forward jump
      else if (_src < offset && offset < _src + _delta) {
        _delta -= count;
        enhancer.setShort(_src + 1, _delta);
      }
      // offset is inside a backward jump
      else if (_src + _delta <= offset && offset <= _src) {
        _delta += count;
        enhancer.setShort(_src + 1, _delta);
        _src -= count;
      }
    }
  }

  static class Switch extends Branch {
    private int _oldSrc;
    private int _src;
    private int []_offsets;
    
    Switch(int src)
    {
      _src = src;
      _oldSrc = src;
    }

    protected void setOffsets(int []offsets)
    {
      _offsets = offsets;
    }

    void insert(CodeEnhancer enhancer, int offset, int count)
    {
      for (int i = 0; i < _offsets.length; i++) {
        int delta = enhancer.getInt(_offsets[i]);

        if (offset <= _src && _src + delta <= offset)
          enhancer.setInt(_offsets[i], delta - count);
        else if (_src < offset && offset < _src + delta)
          enhancer.setInt(_offsets[i], delta + count);

        if (offset <= _src + 1)
          _offsets[i] += count;
      }
      
      if (offset < _src)
        _src += count;
    }

    void remove(CodeEnhancer enhancer, int offset, int count)
    {
      for (int i = 0; i < _offsets.length; i++) {
        int delta = enhancer.getInt(_offsets[i]);

        if (offset <= _src && _src + delta <= offset)
          enhancer.setInt(_offsets[i], delta + count);
        else if (_src < offset && offset < _src + delta)
          enhancer.setInt(_offsets[i], delta - count);

        if (offset <= _src + 1)
          _offsets[i] -= count;
      }
      
      if (offset < _src)
        _src -= count;
    }

    void insertPad(CodeEnhancer enhancer, int offset, int count)
    {
      // offset is before the jump
      if (_oldSrc != _src) {
        int oldPad = (4 - (_oldSrc + 1) % 4) % 4;
        int newPad = (4 - (_src + 1) % 4) % 4;

        _oldSrc = _src;

        if (newPad < oldPad)
          enhancer.remove(_src + 1, oldPad - newPad);
        else if (oldPad < newPad)
          enhancer.addNulls(_src + 1, newPad - oldPad);
      }
    }

    void removePad(CodeEnhancer enhancer, int offset, int count)
    {
      // offset is before the jump
      if (_oldSrc != _src) {
        int oldPad = (4 - (_oldSrc + 1) % 4) % 4;
        int newPad = (4 - (_src + 1) % 4) % 4;

        _oldSrc = _src;

        if (newPad < oldPad)
          enhancer.remove(_src + 1, oldPad - newPad);
        else if (oldPad < newPad)
          enhancer.addNulls(_src + 1, newPad - oldPad);
      }
    }

    public boolean equals(Object v)
    {
      if (! (v instanceof Switch))
        return false;
      
      Switch s = (Switch) v;

      return _src == s._src;
    }
  }

  static class TableSwitch extends Switch {
    TableSwitch(int src, CodeVisitor visitor)
    {
      super(src);
      
      int arg = src + 1;
      arg += (4 - arg % 4) % 4;

      int low = visitor.getInt(arg + 4);
      int high = visitor.getInt(arg + 8);

      int []offsets = new int[high - low + 2];

      offsets[0] = arg;

      for (int i = 0; i <= high - low; i++) {
        offsets[i + 1] = arg + 12 + i * 4;
      }

      setOffsets(offsets);
    }
  }

  static class LookupSwitch extends Switch {
    LookupSwitch(int src, CodeVisitor visitor)
    {
      super(src);
      
      int arg = src + 1;
      arg += (4 - arg % 4) % 4;

      int n = visitor.getInt(arg + 4);

      int []offsets = new int[n + 1];
      offsets[0] = arg;

      for (int i = 0; i < n; i++) {
        offsets[i + 1] = arg + 8 + i * 8 + 4;
      }

      setOffsets(offsets);
    }
  }

  class JumpAnalyzer extends Analyzer {
    public void analyze(CodeVisitor visitor)
      throws Exception
    {
      if (visitor.isSwitch()) {
        int src = visitor.getOffset();

        switch (visitor.getOpcode()) {
        case TABLESWITCH:
          {
            TableSwitch branch = new TableSwitch(src, visitor);
            if (! _switches.contains(branch))
              _switches.add(branch);
            break;
          }

        case LOOKUPSWITCH:
          {
            LookupSwitch branch = new LookupSwitch(src, visitor);
            if (! _switches.contains(branch))
              _switches.add(branch);
            break;
          }
        }
      }
      else if (visitor.isBranch()) {
        int src = visitor.getOffset();
        int offset = visitor.getShortArg(1);

        _jumps.add(new Jump(src, offset));
      }
    }
  }
}
