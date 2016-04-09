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

package com.caucho.v5.kelp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.logging.Logger;

import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.L10N;

import io.baratine.db.BlobReader;

/**
 * A column for the log store.
 */
public class ColumnBlob extends Column
{
  private static final Logger log = Logger.getLogger(ColumnBlob.class.getName());
  
  private static final L10N L = new L10N(ColumnBlob.class);
  
  static final int LARGE_BLOB_MASK = 0x8000;
  
  static final int BLOB_CONT_MASK = 0x4000_0000;
  
  public ColumnBlob(int index,
                     String name,
                     int offset)
  {
    super(index, name, ColumnType.BLOB, offset);
  }
  
  void init(DatabaseKelp db)
  {
    //_inode = new KelpInode(db, _inodeSize);
  }

  @Override
  public final int getLength()
  {
    return 4;
  }
  
  @Override
  public final BlobOutputStream openOutputStream(RowCursor cursor)
  {
    return new BlobOutputStream(cursor, this);
  }
  
  @Override
  public final InputStream openInputStream(RowCursor cursor,
                                           byte []rowBuffer,
                                           int rowOffset,
                                           byte []pageBuffer)
  {
    int offset = rowOffset + getOffset();
    
    int blobOffset = BitsUtil.readInt16(rowBuffer, offset);
    int blobLength = BitsUtil.readInt16(rowBuffer, offset + 2);

    if (isLargeBlob(blobLength)) {
      int pid = BitsUtil.readInt(pageBuffer, blobOffset);
      
      PageBlob blobPage = cursor.getTableService().getBlobPage(pid);
      
      //System.out.println("PID: " + pid + " " + blobPage);

      // System.out.println("BLOB-OIS: " + cursor + " " + blobPage);
      if (blobPage != null) {
        InputStream is = new PageBlobInputStream(cursor, this, blobPage);
        
        //return Vfs.openRead(is);
        return is;
      }
      else {
        return null;
      }
    }
    else if (blobLength > 0) {
      return new BlobInputStream(cursor, this, 
                                 blobOffset, blobLength,
                                 pageBuffer);
    }
    else {
      return null;
    }
  }
  
  @Override
  public final BlobReader openBlobReader(RowCursor cursor,
                                           byte []rowBuffer,
                                           int rowOffset,
                                           byte []pageBuffer)
  {
    int offset = rowOffset + getOffset();
    
    int blobOffset = BitsUtil.readInt16(rowBuffer, offset);
    int blobLength = BitsUtil.readInt16(rowBuffer, offset + 2);

    if (isLargeBlob(blobLength)) {
      int pid = BitsUtil.readInt(pageBuffer, blobOffset);

      PageBlob blobPage = cursor.getTableService().getBlobPage(pid);
      
      if (blobPage != null) {
        return new BlobReaderPageImpl(cursor, this, blobPage);
      }
      else {
        return null;
      }
    }
    else if (blobLength > 0) {
      return new BlobReaderImpl(cursor, this, 
                                 blobOffset, blobLength,
                                 pageBuffer);
    }
    else {
      return null;
    }
  }

  public void read(byte []rowBuffer, int rowOffset,
                   byte []buffer, int offset)
  {
    System.arraycopy(rowBuffer, rowOffset + getOffset(),
                     buffer, offset, getLength());
  }
  
  public void write(byte []rowBuffer, int rowOffset, 
                    byte []buffer, int offset)
  {
    System.arraycopy(buffer, offset,
                     rowBuffer, rowOffset + getOffset(),
                     getLength());
  }

  @Override
  void setBlob(byte []rowBuffer, int rowOffset, int blobOffset, int length)
  {
    int offset = rowOffset + getOffset();
    
    BitsUtil.writeInt16(rowBuffer, offset, blobOffset);
    BitsUtil.writeInt16(rowBuffer, offset + 2, length);
  }

  /**
   * Copies the inline blob from a source block to a destination block.
   * If the inline blob is too large for the target block, return -1, 
   * otherwise return the new blobTail.
   */
  @Override
  int insertBlob(byte[] srcBuffer, int srcRowOffset,
                 byte[] dstBuffer, int dstRowOffset, int dstBlobTail)
  {
    int srcColumnOffset = srcRowOffset + getOffset();
    int dstColumnOffset = dstRowOffset + getOffset();
    
    int blobLen = BitsUtil.readInt16(srcBuffer, srcColumnOffset + 2);

    if (blobLen == 0) {
      return dstBlobTail;
    }
    
    blobLen &= ~LARGE_BLOB_MASK;

    if (dstRowOffset < dstBlobTail + blobLen) {
      return -1;
    }

    int blobOffset = BitsUtil.readInt16(srcBuffer, srcColumnOffset);

    try {
      System.arraycopy(srcBuffer, blobOffset, dstBuffer, dstBlobTail,
                       blobLen);
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new ArrayIndexOutOfBoundsException("srcOff: " + blobOffset
                                               + " dstOff: " + dstBlobTail
                                               + " len: " + blobLen
                                               + " " + this);
    }

    BitsUtil.writeInt16(dstBuffer, dstColumnOffset, dstBlobTail);
    
    /*
    System.out.println("SET: " + Hex.toHex(dstBuffer, dstRowOffset, 5) 
                       + " " + Hex.toHex(dstBuffer, dstBlobTail, 4));
                       */
    
    return dstBlobTail + blobLen;
  }

  @Override
  public void remove(PageServiceImpl pageActor, byte[] buffer, int rowOffset)
  {
    int offset = rowOffset + getOffset();
    
    int blobOffset = BitsUtil.readInt16(buffer, offset);
    int blobLen = BitsUtil.readInt16(buffer, offset + 2);
    
    if (! isLargeBlob(blobLen)) {
      return;
    }
    
    blobLen &= ~LARGE_BLOB_MASK;
    
    int blobId = BitsUtil.readInt(buffer, blobOffset);
    
    while (blobId > 0) {
      PageBlob blob = pageActor.getBlobPage(blobId);
      
      int nextId = blob.getNextId();
    
      pageActor.freeBlob(blobId);
      
      blobId = nextId;
    }
  }

  //
  // journal persistence
  //

  /**
   * Writes the blob column to the journal. Large blobs are written to the
   * page store and the reference is written to the journal.
   */
  @Override
  void writeJournal(OutputStream os, 
                    byte[] buffer, int offset,
                    BlobOutputStream blob)
    throws IOException
  {
    if (blob == null) {
      BitsUtil.writeInt16(os, 0);
      return;
    }

    if (blob.isLargeBlob()) {
      blob.flushBlob();
      
      BitsUtil.writeInt16(os, LARGE_BLOB_MASK | 4);
      BitsUtil.writeInt(os, blob.getBlobId());
      return;
    }
    
    int len = blob.getSize();
    
    if (len >= 32 * 1024) {
      throw new IllegalStateException("TOO_BIG: " + len);
    }
    
    BitsUtil.writeInt16(os, len);
    
    blob.writeToStream(os);
  }

  /**
   * Reads the blob from the journal into the working cursor.
   */
  @Override
  void readJournal(PageServiceImpl pageActor,
                   ReadBuffer is, 
                   byte[] buffer, int offset, RowCursor cursor)
    throws IOException
  {
    int len = BitsUtil.readInt16(is);

    if (len == 0) {
      BitsUtil.writeInt(buffer, offset + getOffset(), 0);
      return;
    }
    
    if ((len & LARGE_BLOB_MASK) != 0) {
      len = len & ~LARGE_BLOB_MASK;
      
      if (len != 4) {
        throw new IllegalStateException();
      }
      
      int id = BitsUtil.readInt(is);
      
      PageBlob pageBlob = pageActor.getBlobPage(id);

      if (pageBlob != null) {
        BlobOutputStream blob = new BlobOutputStream(cursor, this, id);
      
        cursor.setBlob(getIndex(), blob);
      }

      return;
    }
    
    TempBuffer tBuf = TempBuffer.allocate();
    byte []tempBuffer = tBuf.buffer();

    is.readAll(tempBuffer, 0, len);
      
    BlobOutputStream blob = new BlobOutputStream(cursor,
                                                 this,
                                                 tBuf,
                                                 len);
    
    cursor.setBlob(getIndex(), blob);
  }

  /**
   * Reads the blob from the stream into the working cursor.
   */
  @Override
  void readStream(InputStream is, 
                  byte[] buffer, int offset, RowCursor cursor)
    throws IOException
  {
  }
  
  /**
   * Reads a stream blob from a stream from the serialized row.
   */
  @Override
  void readStreamBlob(InputStream is, 
                      byte[] buffer, int offset, RowCursor cursor)
    throws IOException
  {
    int len = BitsUtil.readInt(is);

    if (len == 0) {
      BitsUtil.writeInt(buffer, offset + getOffset(), 0);
      return;
    }
    
    try (BlobOutputStream blob = openOutputStream(cursor)) {
      while (len > 0) {
        boolean isNext = (len & BLOB_CONT_MASK) != 0;
        len &= ~BLOB_CONT_MASK;
        
        blob.writeFromStream(is, len);
        
        if (isNext) {
          len = BitsUtil.readInt(is);
        }
        else {
          len = 0;
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
      
      throw e;
    }
    
    if (len < 0) {
      throw new IOException(L.l("Unexpected end of file while reading {0}", this));
    }

    //cursor.setBlob(getIndex(), blob);
  }
  
  /**
   * Writes the column data to the output stream.
   * 
   * Blobs write nothing in this phase, writing the blobs after all
   * the fixed columns.
   */
  @Override
  void writeStream(OutputStream os, 
                   byte[] buffer, int rowOffset)
    throws IOException
  {
  }
  
  /**
   * Writes blob data to the output stream.
   */
  @Override
  void writeStreamBlob(OutputStream os, 
                       byte[] buffer, int rowOffset,
                       byte []blobBuffer,
                       PageServiceImpl tableService)
    throws IOException
  {
    int offset = rowOffset + getOffset();
    
    int blobLen = BitsUtil.readInt16(buffer, offset + 2);
    int blobOffset = BitsUtil.readInt16(buffer, offset);
    
    if (blobLen == 0) {
      BitsUtil.writeInt(os, 0);
      return;
    }
    
    if (isLargeBlob(blobLen)) {
      int blobId = BitsUtil.readInt(blobBuffer, blobOffset);
      
      ArrayList<PageBlob> blobList = new ArrayList<>();

      while (blobId > 0) {
        PageBlob page = tableService.getBlobPage(blobId);
        
        blobList.add(page);
        
        blobId = page.getNextId();
      }
        
      for (int i = 0; i < blobList.size(); i++) {
        PageBlob page = blobList.get(i);
        int length = page.getLength();
        
        if ((length & 0xc000_0000) != 0) {
          throw new IllegalStateException(L.l("Unexpected blob length {0} for {1}", 
                                              length, page));
        }
        
        if (i + 1 < blobList.size()) {
          length |= BLOB_CONT_MASK;
        }
        
        BitsUtil.writeInt(os, length);
        
        page.writeToStream(os);
      }
    }
    else {
      BitsUtil.writeInt(os, blobLen);
      
      os.write(blobBuffer, blobOffset, blobLen);
    }
  }

  @Override
  public long getLength(long length, byte[] buffer, int rowOffset,
                        PageServiceImpl pageService)
  {
    int offset = rowOffset + getOffset();
    
    int blobLen = BitsUtil.readInt16(buffer, offset + 2);
    int blobOffset = BitsUtil.readInt16(buffer, offset);
    
    if (blobLen == 0) {
      return length + 4;
    }
    
    if (isLargeBlob(blobLen)) {
      int blobId = BitsUtil.readInt(buffer, blobOffset);
      
      while (blobId > 0) {
        PageBlob page = pageService.getBlobPage(blobId);
        
        length += 4 + page.getLength();
        
        blobId = page.getNextId();
      }
      
      return length;
    }
    else {
      return length + 4 + blobLen;
    }
  }
  
  private boolean isLargeBlob(int len)
  {
    return (len & LARGE_BLOB_MASK) != 0;
  }
  
  //
  // checkpoint persistence
  //

  @Override
  void writeCheckpoint(WriteBuffer os, byte[] rowBuffer, int rowOffset)
    throws IOException
  {
    int colOffset = rowOffset + getOffset();
    
    int blobOffset = BitsUtil.readInt16(rowBuffer, colOffset);
    int blobLength = BitsUtil.readInt16(rowBuffer, colOffset + 2);
    
    BitsUtil.writeInt16(os, blobLength);

    if (blobLength != 0) {
      int sublen = blobLength & ~LARGE_BLOB_MASK;
      
      os.write(rowBuffer, blobOffset, sublen);
      
      /*
      System.out.println("WRITE: " + Hex.toHex(rowBuffer, rowOffset, 5) + " "
        + Hex.toHex(rowBuffer, blobOffset, sublen));
        */
    }
  }

  /**
   * Reads checkpoint blob data from the stream into the current block.
   * 
   * If the block is full, returns -1.
   * 
   * @param is the source stream for the checkpoint
   * @param blockBuffer the target block to be filled
   * @param rowOffset the offset of the row being filled
   * @param rowLength the rowLength
   * @param blobTail the inline-blob's tail pointer.
   * 
   * @return the new blob tail pointer or -1 if the blob doesn't fit.
   */
  @Override
  int readCheckpoint(ReadBuffer is,
                     byte[] blockBuffer, int rowOffset, int rowLength,
                     int blobTail)
    throws IOException
  {
    int blobLen = BitsUtil.readInt16(is);
    int colOffset = rowOffset + getOffset();

    if (blobLen != 0) {
      int sublen = blobLen & ~LARGE_BLOB_MASK;
      
      int newBlobTail = blobTail + sublen;
      
      if (rowOffset <= newBlobTail) {
        return -1;
      }
      
      is.readAll(blockBuffer, blobTail, sublen);
      
      BitsUtil.writeInt16(blockBuffer, colOffset, blobTail);
      BitsUtil.writeInt16(blockBuffer, colOffset + 2, blobLen);
      
      // System.out.println("READ: " + Hex.toHex(blockBuffer, rowOffset, 12) + " " + Hex.toHex(blockBuffer, blobTail, sublen));
      
      return newBlobTail;
    }
    else {
      BitsUtil.writeInt16(blockBuffer, colOffset, 0);
      BitsUtil.writeInt16(blockBuffer, colOffset + 2, 0);
      
      return blobTail;
    }
  }
  
  //
  // validation
  //

  /**
   * Validates the column, checking for corruption.
   */
  @Override
  public void validate(byte[] blockBuffer, int rowOffset, int rowHead, int blobTail)
  {
    int offset = rowOffset + getOffset();
    
    int blobLen = BitsUtil.readInt16(blockBuffer, offset + 2);
    int blobOffset = BitsUtil.readInt16(blockBuffer, offset);
    
    if (blobLen == 0) {
      return;
    }
    
    if (blobOffset < 0 || blobTail < blobOffset) {
      throw new IllegalStateException(L.l("{0}: corrupted blob offset {1} with blobTail={2}",
                                          this, blobOffset, blobTail));
    }
    
    if ((blobLen & LARGE_BLOB_MASK) != 0) {
      blobLen &= ~LARGE_BLOB_MASK;
      
      if (blobLen != 4) {
        throw new IllegalStateException(L.l("{0}: corrupted blob len {1} for large blob.",
                                            this, blobOffset));
      }
    }
    
    
    if (blobLen < 0 || blobTail < blobLen + blobOffset) {
      throw new IllegalStateException(L.l("{0}: corrupted blob len {1} with blobOffset={2} blobTail={3}",
                                          this, blobLen, blobOffset, blobTail));
    }
  }
}
