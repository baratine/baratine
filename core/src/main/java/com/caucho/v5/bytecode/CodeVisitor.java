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

import com.caucho.v5.bytecode.attr.CodeAttribute;
import com.caucho.v5.bytecode.cpool.ConstantPoolEntry;
import com.caucho.v5.util.IntArray;
import com.caucho.v5.util.L10N;

import java.util.ArrayList;

/**
 * Visitor for travelling the code.
 */
public class CodeVisitor {
  static private final L10N L = new L10N(CodeVisitor.class);

  private JavaClass _javaClass;
  protected CodeAttribute _codeAttr;
  private byte []_code;
  protected int _offset;

  public CodeVisitor()
  {
  }

  public CodeVisitor(JavaClass javaClass, CodeAttribute codeAttr)
  {
    init(javaClass, codeAttr);
  }

  public void init(JavaClass javaClass, CodeAttribute codeAttr)
  {
    _javaClass = javaClass;
    _codeAttr = codeAttr;
    _code = codeAttr.getCode();
    _offset = 0;
  }

  /**
   * Returns the java class.
   */
  public JavaClass getJavaClass()
  {
    return _javaClass;
  }
  
  /**
   * Returns the code attribute.
   */
  public CodeAttribute getCodeAttribute()
  {
    return _codeAttr;
  }

  /**
   * Returns the offset.
   */
  public int getOffset()
  {
    return _offset;
  }

  /**
   * Sets the offset.
   */
  public void setOffset(int offset)
  {
    _offset = offset;
  }

  /**
   * Returns the code buffer.
   */
  public byte []getCode()
  {
    return _code;
  }

  /**
   * Returns the exceptions.
   */
  public ArrayList<CodeAttribute.ExceptionItem> getExceptions()
  {
    return _codeAttr.getExceptions();
  }

  /**
   * Returns the opcode at the cursor.
   */
  public int getOpcode()
  {
    int op = getCode()[_offset] & 0xff;

    if (op == WIDE)
      return getCode()[_offset + 1] & 0xff;
    else
      return op; 
  }

  /**
   * Goes to the next opcode.
   */
  public boolean next()
  {
    _offset = nextOffset();

    return _offset >= 0;
  }

  /**
   * Goes to the next opcode.
   */
  protected int nextOffset()
  {
    int opcode = getCode()[_offset] & 0xff;
    
    int length = OP_LEN[opcode];

    switch (opcode) {
    case GOTO:
    case GOTO_W:
    case RET:
    case IRETURN:
    case LRETURN:
    case FRETURN:
    case DRETURN:
    case ARETURN:
    case RETURN:
    case ATHROW:
      return -1;

    case TABLESWITCH:
      {
        int arg = _offset + 1;
        arg += (4 - arg % 4) % 4;

        int low = getInt(arg + 4);
        int high = getInt(arg + 8);

        return arg + 12 + (high - low + 1) * 4;
      }

    case LOOKUPSWITCH:
      {
        return -1;

        /*
        int arg = _offset + 1;
        arg += (4 - arg % 4) % 4;

        int n = getInt(arg + 4);

        int next = arg + 12 + n * 8;

        return next;
        */
      }

    case WIDE:
      {
        int op2 = getCode()[_offset + 1] & 0xff;

        if (op2 == IINC)
          length = 5;
        else
          length = 3;
        break;
      }
    }
    
    if (length < 0 || length > 0x10)
      throw new UnsupportedOperationException(L.l("{0}: can't handle opcode {1}",
                                                  "" + _offset,
                                                  "" + getOpcode()));

    return _offset + length + 1;
  }

  /**
   * Returns true for a simple branch, i.e. a branch with a simple target.
   */
  public boolean isBranch()
  {
    switch (getOpcode()) {
    case IFNULL:
    case IFNONNULL:
    case IFNE:
    case IFEQ:
    case IFLT:
    case IFGE:
    case IFGT:
    case IFLE:
    case IF_ICMPEQ:
    case IF_ICMPNE:
    case IF_ICMPLT:
    case IF_ICMPGE:
    case IF_ICMPGT:
    case IF_ICMPLE:
    case IF_ACMPEQ:
    case IF_ACMPNE:
    case JSR:
    case JSR_W:
    case GOTO:
    case GOTO_W:
      return true;
    }

    return false;
  }

  /**
   * Returns the branch target.
   */
  public int getBranchTarget()
  {
    switch (getOpcode()) {
    case IFNULL:
    case IFNONNULL:
    case IFNE:
    case IFEQ:
    case IFLT:
    case IFGE:
    case IFGT:
    case IFLE:
    case IF_ICMPEQ:
    case IF_ICMPNE:
    case IF_ICMPLT:
    case IF_ICMPLE:
    case IF_ICMPGE:
    case IF_ICMPGT:
    case IF_ACMPEQ:
    case IF_ACMPNE:
    case GOTO:
    case JSR:
      return _offset + getShortArg();
      
    case GOTO_W:
    case JSR_W:
      return _offset + getIntArg();

    default:
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Returns true for a switch.
   */
  public boolean isSwitch()
  {
    switch (getOpcode()) {
    case TABLESWITCH:
    case LOOKUPSWITCH:
      return true;
    default:
      return false;
    }
  }

  /**
   * Returns the branch target.
   */
  public int []getSwitchTargets()
  {
    switch (getOpcode()) {
    case TABLESWITCH:
      {
        int arg = _offset + 1;
        arg += (4 - arg % 4) % 4;

        int low = getInt(arg + 4);
        int high = getInt(arg + 8);

        int []targets = new int[high - low + 2];
        targets[0] = getInt(arg) + _offset;

        for (int i = 0; i <= high - low; i++) {
          targets[i + 1] = getInt(arg + 12 + i * 4) + _offset;
        }

        return targets;
      }
      
    case LOOKUPSWITCH:
      {
        int arg = _offset + 1;
        arg += (4 - arg % 4) % 4;

        int n = getInt(arg + 4);

        int []targets = new int[n + 1];
        targets[0] = getInt(arg) + _offset;

        for (int i = 0; i < n; i++) {
          int off = arg + 8 + i * 8 + 4;

          targets[i + 1] = getInt(off) + _offset;
        }

        return targets;
      }

    default:
      throw new UnsupportedOperationException("getSwitchTargets");
    }
  }

  /**
   * Returns a constant pool item.
   */
  public ConstantPoolEntry getConstantArg()
  {
    switch (getOpcode()) {
    case LDC:
      return _javaClass.getConstantPool().getEntry(getByteArg());
      
    case LDC_W:
      return _javaClass.getConstantPool().getEntry(getShortArg());

    default:
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Reads a byte argument.
   */
  public int getByteArg()
  {
    return getCode()[_offset + 1];
  }

  /**
   * Gets a byte arg
   */
  public int getByteArg(int offset)
  {
    return getByte(_offset + offset);
  }

  /**
   * Sets a byte value.
   */
  public void setByteArg(int offset, int value)
  {
    getCode()[_offset + offset + 0] = (byte) value;
  }

  /**
   * Reads a short argument.
   */
  public int getShortArg()
  {
    return getShort(_offset + 1);
  }

  /**
   * Sets a short value.
   */
  public int getShortArg(int offset)
  {
    return getShort(_offset + offset);
  }

  /**
   * Sets a short value.
   */
  public void setShortArg(int offset, int value)
  {
    setShort(_offset + offset, value);
  }

  /**
   * Sets a short value.
   */
  public void setShort(int offset, int value)
  {
    byte []code = getCode();
    
    code[offset + 0]  = (byte) (value >> 8);
    code[offset + 1]  = (byte) (value);
  }

  /**
   * Reads an integer.
   */
  public int getIntArg()
  {
    return getInt(_offset + 1);
  }

  /**
   * Sets a short value.
   */
  public void setInt(int offset, int value)
  {
    byte []code = getCode();
    
    code[offset + 0]  = (byte) (value >> 24);
    code[offset + 1]  = (byte) (value >> 16);
    code[offset + 2]  = (byte) (value >> 8);
    code[offset + 3]  = (byte) (value);
  }

  /**
   * Reads a byte value.
   */
  public int getByte(int offset)
  {
    return getCode()[offset + 0] & 0xff;
  }

  /**
   * Reads a short value.
   */
  public int getShort(int offset)
  {
    int b0 = getCode()[offset + 0] & 0xff;
    int b1 = getCode()[offset + 1] & 0xff;
    
    return (short) ((b0 << 8) + b1);
  }

  /**
   * Reads an int argument.
   */
  public int getInt(int offset)
  {
    byte []code = getCode();
    
    int b0 = code[offset + 0] & 0xff;
    int b1 = code[offset + 1] & 0xff;
    int b2 = code[offset + 2] & 0xff;
    int b3 = code[offset + 3] & 0xff;
    
    return (b0 << 24) + (b1 << 16) + (b2 << 8) + b3;
  }

  /**
   * Analyzes the code for a method
   */
  public void analyze(Analyzer analyzer)
    throws Exception
  {
    analyze(analyzer, true);
  }

  /**
   * Analyzes the code for a method
   */
  public void analyze(Analyzer analyzer, boolean allowFlow)
    throws Exception
  {
    analyzeImpl(analyzer, allowFlow, new IntArray(), new IntArray());
  }

  /**
   * Analyzes the code for a method
   */
  protected void analyzeImpl(Analyzer analyzer, boolean allowFlow,
                             IntArray pendingTargets,
                             IntArray completedTargets)
    throws Exception
  {
    setOffset(0);
    
    pendingTargets.add(0);

    ArrayList<CodeAttribute.ExceptionItem> exns;
    exns = getExceptions();

    for (int i = 0; i < exns.size(); i++) {
      CodeAttribute.ExceptionItem exn = exns.get(i);

      pendingTargets.add(exn.getHandler());
    }
    
    analyze(analyzer, allowFlow, pendingTargets, completedTargets);
  }
  
  /**
   * Analyzes the code for a basic block.
   */
  private void analyze(Analyzer analyzer,
                       boolean allowFlow,
                       IntArray pendingTargets,
                       IntArray completedTargets)
    throws Exception
  {
    pending:
    while (pendingTargets.size() > 0) {
      int pc = pendingTargets.pop();

      if (allowFlow) {
        if (completedTargets.contains(pc))
          continue pending;

        completedTargets.add(pc);
      }

      setOffset(pc);

      flow:
      do {
        pc = getOffset();

        if (pc < 0)
          throw new IllegalStateException();
    
        if (! allowFlow) {
          if (completedTargets.contains(pc))
            break flow;

          completedTargets.add(pc);
        }

        if (isBranch()) {
          int targetPC = getBranchTarget();

          if (! pendingTargets.contains(targetPC))
            pendingTargets.add(targetPC);
        }
        else if (isSwitch()) {
          int []switchTargets = getSwitchTargets();

          for (int i = 0; i < switchTargets.length; i++) {
            if (! pendingTargets.contains(switchTargets[i]))
              pendingTargets.add(switchTargets[i]);
          }
        }

        analyzer.analyze(this);
      } while (next());
    }
  }

  public static final int NOP             = 0x00;
  public static final int ACONST_NULL     = 0x01;
  public static final int ICONST_M1       = 0x02;
  public static final int ICONST_0        = 0x03;
  public static final int ICONST_1        = 0x04;
  public static final int ICONST_2        = 0x05;
  public static final int ICONST_3        = 0x06;
  public static final int ICONST_4        = 0x07;
  public static final int ICONST_5        = 0x08;
  public static final int LCONST_0        = 0x09;
  public static final int LCONST_1        = 0x0a;
  public static final int FCONST_0        = 0x0b;
  public static final int FCONST_1        = 0x0c;
  public static final int FCONST_2        = 0x0d;
  public static final int DCONST_0        = 0x0e;
  public static final int DCONST_1        = 0x0f;
  
  public static final int BIPUSH          = 0x10;
  public static final int SIPUSH          = 0x11;
  public static final int LDC             = 0x12;
  public static final int LDC_W           = 0x13;
  public static final int LDC2_W          = 0x14;
  public static final int ILOAD           = 0x15;
  public static final int LLOAD           = 0x16;
  public static final int FLOAD           = 0x17;
  public static final int DLOAD           = 0x18;
  public static final int ALOAD           = 0x19;
  public static final int ILOAD_0         = 0x1a;
  public static final int ILOAD_1         = 0x1b;
  public static final int ILOAD_2         = 0x1c;
  public static final int ILOAD_3         = 0x1d;
  public static final int LLOAD_0         = 0x1e;
  public static final int LLOAD_1         = 0x1f;
  
  public static final int LLOAD_2         = 0x20;
  public static final int LLOAD_3         = 0x21;
  public static final int FLOAD_0         = 0x22;
  public static final int FLOAD_1         = 0x23;
  public static final int FLOAD_2         = 0x24;
  public static final int FLOAD_3         = 0x25;
  public static final int DLOAD_0         = 0x26;
  public static final int DLOAD_1         = 0x27;
  public static final int DLOAD_2         = 0x28;
  public static final int DLOAD_3         = 0x29;
  public static final int ALOAD_0         = 0x2a;
  public static final int ALOAD_1         = 0x2b;
  public static final int ALOAD_2         = 0x2c;
  public static final int ALOAD_3         = 0x2d;
  public static final int IALOAD          = 0x2e;
  public static final int LALOAD          = 0x2f;
  
  public static final int FALOAD          = 0x30;
  public static final int DALOAD          = 0x31;
  public static final int AALOAD          = 0x32;
  public static final int BALOAD          = 0x33;
  public static final int CALOAD          = 0x34;
  public static final int SALOAD          = 0x35;
  public static final int ISTORE          = 0x36;
  public static final int LSTORE          = 0x37;
  public static final int FSTORE          = 0x38;
  public static final int DSTORE          = 0x39;
  public static final int ASTORE          = 0x3a;
  public static final int ISTORE_0        = 0x3b;
  public static final int ISTORE_1        = 0x3c;
  public static final int ISTORE_2        = 0x3d;
  public static final int ISTORE_3        = 0x3e;
  public static final int LSTORE_0        = 0x3f;
  
  public static final int LSTORE_1        = 0x40;
  public static final int LSTORE_2        = 0x41;
  public static final int LSTORE_3        = 0x42;
  public static final int FSTORE_0        = 0x43;
  public static final int FSTORE_1        = 0x44;
  public static final int FSTORE_2        = 0x45;
  public static final int FSTORE_3        = 0x46;
  public static final int DSTORE_0        = 0x47;
  public static final int DSTORE_1        = 0x48;
  public static final int DSTORE_2        = 0x49;
  public static final int DSTORE_3        = 0x4a;
  public static final int ASTORE_0        = 0x4b;
  public static final int ASTORE_1        = 0x4c;
  public static final int ASTORE_2        = 0x4d;
  public static final int ASTORE_3        = 0x4e;
  public static final int IASTORE         = 0x4f;
  
  public static final int LASTORE         = 0x50;
  public static final int FASTORE         = 0x51;
  public static final int DASTORE         = 0x52;
  public static final int AASTORE         = 0x53;
  public static final int BASTORE         = 0x54;
  public static final int CASTORE         = 0x55;
  public static final int SASTORE         = 0x56;
  public static final int POP             = 0x57;
  public static final int POP2            = 0x58;
  public static final int DUP             = 0x59;
  public static final int DUP_X1          = 0x5a;
  public static final int DUP_X2          = 0x5b;
  public static final int DUP2            = 0x5c;
  public static final int DUP2_X1         = 0x5d;
  public static final int DUP2_X2         = 0x5e;
  public static final int SWAP            = 0x5f;
  
  public static final int IADD            = 0x60;
  public static final int LADD            = 0x61;
  public static final int FADD            = 0x62;
  public static final int DADD            = 0x63;
  public static final int ISUB            = 0x64;
  public static final int LSUB            = 0x65;
  public static final int FSUB            = 0x66;
  public static final int DSUB            = 0x67;
  public static final int IMUL            = 0x68;
  public static final int LMUL            = 0x69;
  public static final int FMUL            = 0x6a;
  public static final int DMUL            = 0x6b;
  public static final int IDIV            = 0x6c;
  public static final int LDIV            = 0x6d;
  public static final int FDIV            = 0x6e;
  public static final int DDIV            = 0x6f;
  
  public static final int IREM            = 0x70;
  public static final int LREM            = 0x71;
  public static final int FREM            = 0x72;
  public static final int DREM            = 0x73;
  public static final int INEG            = 0x74;
  public static final int LNEG            = 0x75;
  public static final int FNEG            = 0x76;
  public static final int DNEG            = 0x77;
  public static final int ISHL            = 0x78;
  public static final int LSHL            = 0x79;
  public static final int ISHR            = 0x7a;
  public static final int LSHR            = 0x7b;
  public static final int IUSHR           = 0x7c;
  public static final int LUSHR           = 0x7d;
  public static final int IAND            = 0x7e;
  public static final int LAND            = 0x7f;
  
  public static final int IOR             = 0x80;
  public static final int LOR             = 0x81;
  public static final int IXOR            = 0x82;
  public static final int LXOR            = 0x83;
  public static final int IINC            = 0x84;
  public static final int I2L             = 0x85;
  public static final int I2F             = 0x86;
  public static final int I2D             = 0x87;
  public static final int L2I             = 0x88;
  public static final int L2F             = 0x89;
  public static final int L2D             = 0x8a;
  public static final int F2I             = 0x8b;
  public static final int F2L             = 0x8c;
  public static final int F2D             = 0x8d;
  public static final int D2I             = 0x8e;
  public static final int D2L             = 0x8f;
  
  public static final int D2F             = 0x90;
  public static final int I2B             = 0x91;
  public static final int I2C             = 0x92;
  public static final int I2S             = 0x93;
  public static final int LCMP            = 0x94;
  public static final int FCMPL           = 0x95;
  public static final int FCMPG           = 0x96;
  public static final int DCMPL           = 0x97;
  public static final int DCMPG           = 0x98;
  public static final int IFEQ            = 0x99;
  public static final int IFNE            = 0x9a;
  public static final int IFLT            = 0x9b;
  public static final int IFGE            = 0x9c;
  public static final int IFGT            = 0x9d;
  public static final int IFLE            = 0x9e;
  public static final int IF_ICMPEQ       = 0x9f;
  
  public static final int IF_ICMPNE       = 0xa0;
  public static final int IF_ICMPLT       = 0xa1;
  public static final int IF_ICMPGE       = 0xa2;
  public static final int IF_ICMPGT       = 0xa3;
  public static final int IF_ICMPLE       = 0xa4;
  public static final int IF_ACMPEQ       = 0xa5;
  public static final int IF_ACMPNE       = 0xa6;
  public static final int GOTO            = 0xa7;
  public static final int JSR             = 0xa8;
  public static final int RET             = 0xa9;
  public static final int TABLESWITCH     = 0xaa;
  public static final int LOOKUPSWITCH    = 0xab;
  public static final int IRETURN         = 0xac;
  public static final int LRETURN         = 0xad;
  public static final int FRETURN         = 0xae;
  public static final int DRETURN         = 0xaf;
  
  public static final int ARETURN         = 0xb0;
  public static final int RETURN          = 0xb1;
  public static final int GETSTATIC       = 0xb2;
  public static final int PUTSTATIC       = 0xb3;
  public static final int GETFIELD        = 0xb4;
  public static final int PUTFIELD        = 0xb5;
  public static final int INVOKEVIRTUAL   = 0xb6;
  public static final int INVOKESPECIAL   = 0xb7;
  public static final int INVOKESTATIC    = 0xb8;
  public static final int INVOKEINTERFACE = 0xb9;
  public static final int INVOKEDYNAMIC   = 0xba;
  public static final int NEW             = 0xbb;
  public static final int NEWARRAY        = 0xbc;
  public static final int ANEWARRAY       = 0xbd;
  public static final int ARRAYLENGTH     = 0xbe;
  public static final int ATHROW          = 0xbf;
  
  public static final int CHECKCAST       = 0xc0;
  public static final int INSTANCEOF      = 0xc1;
  public static final int MONITORENTER    = 0xc2;
  public static final int MONITOREXIT     = 0xc3;
  public static final int WIDE            = 0xc4;
  public static final int MULTIANEWARRAY  = 0xc5;
  public static final int IFNULL          = 0xc6;
  public static final int IFNONNULL       = 0xc7;
  public static final int GOTO_W          = 0xc8;
  public static final int JSR_W           = 0xc9;

  // the reset are reserved

  private static final int OP_LEN[] = {
     0,  0,  0,  0,  0,  0,  0,  0,    0,  0,  0,  0,  0,  0,  0,  0, /* 00 */
     1,  2,  1,  2,  2,  1,  1,  1,    1,  1,  0,  0,  0,  0,  0,  0, /* 10 */
     0,  0,  0,  0,  0,  0,  0,  0,    0,  0,  0,  0,  0,  0,  0,  0, /* 20 */
     0,  0,  0,  0,  0,  0,  1,  1,    1,  1,  1,  0,  0,  0,  0,  0, /* 30 */
    
     0,  0,  0,  0,  0,  0,  0,  0,    0,  0,  0,  0,  0,  0,  0,  0, /* 40 */
     0,  0,  0,  0,  0,  0,  0,  0,    0,  0,  0,  0,  0,  0,  0,  0, /* 50 */
     0,  0,  0,  0,  0,  0,  0,  0,    0,  0,  0,  0,  0,  0,  0,  0, /* 60 */
     0,  0,  0,  0,  0,  0,  0,  0,    0,  0,  0,  0,  0,  0,  0,  0, /* 70 */
    
     0,  0,  0,  0,  2,  0,  0,  0,    0,  0,  0,  0,  0,  0,  0,  0, /* 80 */
     0,  0,  0,  0,  0,  0,  0,  0,    0,  2,  2,  2,  2,  2,  2,  2, /* 90 */
     2,  2,  2,  2,  2,  2,  2,  2,    2,  1, 99, 99,  0,  0,  0,  0, /* a0 */
     0,  0,  2,  2,  2,  2,  2,  2,    2,  4,  5,  2,  1,  2,  0,  0, /* b0 */
    
     2,  2,  0,  0, 99,  3,  2,  2,    4,  4, -1, -1, -1, -1, -1, -1, /* c0 */
    -1, -1, -1, -1, -1, -1, -1, -1,   -1, -1, -1, -1, -1, -1, -1, -1, /* d0 */
    -1, -1, -1, -1, -1, -1, -1, -1,   -1, -1, -1, -1, -1, -1, -1, -1, /* e0 */
    -1, -1, -1, -1, -1, -1, -1, -1,   -1, -1, -1, -1, -1, -1, -1, -1, /* f0 */
  };
}
