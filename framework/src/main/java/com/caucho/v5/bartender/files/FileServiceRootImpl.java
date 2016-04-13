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

package com.caucho.v5.bartender.files;

import io.baratine.db.BlobReader;
import io.baratine.db.Cursor;
import io.baratine.db.CursorPrepareSync;
import io.baratine.db.DatabaseWatch;
import io.baratine.files.BfsFile;
import io.baratine.files.BfsFileSync;
import io.baratine.files.Status;
import io.baratine.files.Watch;
import io.baratine.files.WriteOption;
import io.baratine.service.Cancel;
import io.baratine.service.OnInit;
import io.baratine.service.OnLookup;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;
import io.baratine.service.Service;
import io.baratine.service.Services;
import io.baratine.service.ServiceRef;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.Direct;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.io.TempOutputStream;
import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.kelp.TableListener;
import com.caucho.v5.kraken.KrakenSystem;
import com.caucho.v5.kraken.query.QueryKraken;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.KrakenImpl;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.Crc64;
import com.caucho.v5.util.Fnv128;
import com.caucho.v5.util.HashKey;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.LruCache;
import com.caucho.v5.util.Murmur32;
import com.caucho.v5.vfs.Crc64OutputStream;
import com.caucho.v5.vfs.ReadStreamOld;
import com.caucho.v5.vfs.VfsOld;
import com.caucho.v5.vfs.WriteStreamOld;

/**
 * Root of a filesystem. Each filesystem belongs to a pod and has a 
 * consistent hashing function and its own kraken table.
 */
public class FileServiceRootImpl
{
  private static final Logger log
    = Logger.getLogger(FileServiceRootImpl.class.getName());
  
  private static final int KEY_SIZE = 4 + 16 + 2;
  
  private static final int DIR_LENGTH_CODE = -2;
  
  private final BartenderFileSystem _system;

  private final String _podName;
  
  private final LruCache<HashKey,FileEntry> _fileEntryMap
    = new LruCache<>(16 * 1024);

  private final ConcurrentHashMap<String,FileServiceBind> _bindMap
    = new ConcurrentHashMap<>();

  private final FileHash _hash;

  private ServiceRefAmp _serviceRef;
  private TableKraken _fileTable;
  private FileServiceRoot _rootService;

  private QueryKraken _fileInsert;

  private QueryKraken _fileDelete;

  private QueryKraken _fileRead;

  private QueryKraken _dirInsert;

  private QueryKraken _dirList;

  private QueryKraken _fileWatch;

  private QueryKraken _fileStatus;

  private PodBartender _pod;

  private ServerBartender _serverSelf;

  // private QueryKraken _dirNotify;

  private BfsFileImpl _rootNode;

  private final String _address;
  private final String _prefix;

  private String _authority;

  private QueryKraken _fileName;

  FileServiceRootImpl(FileServiceBuilder builder)
  {
    _system = builder.getSystem();

    _address = builder.getAddress();
    _prefix = builder.getPrefix();
    _authority = _address.substring(0, _address.length() - _prefix.length());
    _podName = builder.getPodName();
    
    _hash = builder.getHash();
    
    BartenderSystem bartenderSystem = BartenderSystem.current();
    
    _pod = bartenderSystem.findPod(_podName);
    _serverSelf = bartenderSystem.serverSelf();

    _fileTable = createFileTable();
    // _dirTable = createDirTable();

    Objects.requireNonNull(_fileTable);
    // Objects.requireNonNull(_dirTable);

    createFileQuery();
    // createDirQuery();
    
    ServicesAmp manager = builder.getManager();
    String address = builder.getAddress();

    _serviceRef = manager.newService(this).address(address).ref();
    _rootService = _serviceRef.as(FileServiceRoot.class);
    
    _rootNode = new BfsFileImpl(this, "", "");
  }

  FileServiceRootImpl(String address,
                      String podName,
                      ServicesAmp manager)
  {
    this(new FileServiceBuilder().address(address).pod(podName));
  }

  public String getPodName()
  {
    if (_podName != null) {
      return _podName;
    }
    else {
      return "";
    }
  }

  public String getAddress()
  {
    return _address;
  }
  
  public PodBartender getPod()
  {
    return _pod;
  }

  public boolean isPrimary(int hash)
  {
    return _pod.getNode(hash).isServerPrimary(_serverSelf);
  }

  public boolean isOwner(int hash)
  {
    return _pod.getNode(hash).isServerOwner(_serverSelf);
  }

  private boolean isOwner(String path)
  {
    return isOwner(hash(path));
  }

  @OnLookup
  public Object onLookup(String path)
  {
    String fullPath = _prefix + path;
    
    // check for a static binding first (e.g. /proc)
    BfsFile file = lookupBind(fullPath);

    if (file != null) {
      return file;
    }

    // binding not found, look in database
    //return lookupImpl(path).getService();
    //return lookupImpl(path);
    
    return new BfsFileImpl(this, fullPath, path);
  }

  @OnInit
  public void onActive()
  {
    FileChangeService listener = new FileChangeService();
    Services manager = Services.current();
    
    _fileTable.addListener(manager.newService(listener).as(TableListener.class));
    // _dirTable.addListener(new DirChangeListener());
  }

  FileServiceRoot getService()
  {
    return _rootService;
  }
  
  //
  // cache entry methods
  //

  public FileStatusImpl getStatusEntry(String path)
  {
    FileEntry entry = getOwnerEntry(path);
    
    if (entry != null) {
      return entry.getStatus();
    }
    else {
      return null;
    }
  }

  public void setStatusEntry(String path, FileStatusImpl status)
  {
    FileEntry entry = getOwnerEntry(path);
    
    if (entry != null) {
      entry.setStatus(status);
    }
  }
  
  FileEntry getOwnerEntry(String path)
  {
    if (isOwner(path)) {
      return createEntry(path);
    }
    else {
      return null;
    }
  }
  
  private FileEntry createEntry(String path)
  {
    HashKey key = HashKey.create(getFileKey(path));

    FileEntry entry = _fileEntryMap.get(key);
    
    if (entry == null) {
      entry = new FileEntry(this, path, key);
      
      _fileEntryMap.putIfNew(key, entry);
      
      entry = _fileEntryMap.get(key);
    }
    
    return entry;
  }
  
  private FileEntry getEntry(String path)
  {
    HashKey key = HashKey.create(getFileKey(path));

    return _fileEntryMap.get(key);
  }
  
  public void openReadFile(String path,
                           Result<InputStream> result)
  {
    BfsFile fileBind = lookupBind(path);

    if (fileBind != null) {
      fileBind.openRead(result);
      return;
    }
    
    int parentHash = getParentHash(path);
    byte []key = getPathKey(path);
    int hash = hash(path);
    
    FileEntry entry = getOwnerEntry(path);

    if (entry == null) {
      _fileRead.findOne(result.of(cursor->openReadStream(cursor)), 
                        parentHash, key, hash);
    }
    else if (entry.isReadClean()) {
      _fileRead.findOneDirect(result.of(cursor->openReadStream(cursor)), 
                              parentHash, key, hash);
    }
    else {
      long writeCount = entry.getWriteCount();
      
      _fileRead.findOne(result.of(cursor->openReadStream(cursor, entry, writeCount)), 
                        parentHash, key, hash);
    }
  }

  private void openReadCursor(String path,
                              Result<Cursor> result)
  {
    /*
    BfsFile fileBind = lookupBind(path);

    if (fileBind != null) {
      fileBind.openRead(result);
      return;
    }
    */
    
    int parentHash = getParentHash(path);
    byte []key = getPathKey(path);
    int hash = hash(path);
    
    FileEntry entry = getOwnerEntry(path);

    if (entry == null) {
      _fileRead.findOne(result, parentHash, key, hash);
    }
    else if (entry.isReadClean()) {
      _fileRead.findOneDirect(result, parentHash, key, hash);
    }
    else {
      long writeCount = entry.getWriteCount();
      
      _fileRead.findOne(result.of(cursor->openReadCursor(cursor, entry, writeCount)), 
                        parentHash, key, hash);
    }
  }

  private InputStream openReadStream(Cursor cursor)
  {
    if (cursor != null) {
      InputStream is = cursor.getInputStream(1);
      
      return is;
    }
    else {
      return null;
    }
  }

  private InputStream openReadStream(Cursor cursor, 
                                     FileEntry entry,
                                     long writeCount)
  {
    entry.setReadCount(writeCount);
    
    if (cursor != null) {
      return cursor.getInputStream(1);
    }
    else {
      return null;
    }
  }
  
  public void openReadBlob(String path,
                           Result<BlobReader> result)
  {
    BfsFile fileBind = lookupBind(path);

    if (fileBind != null) {
      fileBind.openReadBlob(result);
      return;
    }
    
    int parentHash = getParentHash(path);
    byte []key = getPathKey(path);
    int hash = hash(path);
    
    FileEntry entry = getOwnerEntry(path);

    if (entry == null) {
      _fileRead.findOne(result.of(cursor->openReadBlobStream(cursor, entry)), 
                        parentHash, key, hash);
    }
    else if (entry.isReadClean()) {
      _fileRead.findOneDirect(result.of(cursor->openReadBlobStream(cursor, entry)), 
                              parentHash, key, hash);
    }
    else {
      long writeCount = entry.getWriteCount();
      
      _fileRead.findOne(result.of(cursor->openReadBlobStream(cursor, entry, writeCount)), 
                        parentHash, key, hash);
    }
  }

  private BlobReader openReadBlobStream(Cursor cursor, FileEntry entry)
  {
    if (cursor == null) {
      return null;
    }
      
    BlobReader dbBlobReader = cursor.getBlobReader(1);
      
    if (dbBlobReader == null) {
      return null;
    }

    return new BlobReaderFile(dbBlobReader, entry);
  }

  private BlobReader openReadBlobStream(Cursor cursor, 
                                        FileEntry entry,
                                        long writeCount)
  {
    entry.setReadCount(writeCount);
    
    if (cursor != null) {
      return cursor.getBlobReader(1);
    }
    else {
      return null;
    }
  }

  private Cursor openReadCursor(Cursor cursor, 
                                FileEntry entry,
                                long writeCount)
  {
    entry.setReadCount(writeCount);
    
    return cursor;
  }

  @Direct
  public void copyTo(String srcPath, 
                     String dstPath, 
                     Result<Boolean> result,
                     WriteOption[] options)
  {
    boolean isWaitForPut = isWaitForPut(options);
    
    openReadCursor(srcPath, 
                   result.of((c,r)->copyAfterRead(c, dstPath, r, isWaitForPut)));
  }

  @Direct
  public void renameTo(String srcPath, 
                       String dstPath, 
                       Result<Boolean> result,
                       WriteOption[] options)
  {
    boolean isWaitForPut = isWaitForPut(options);
    
    openReadCursor(srcPath, 
                   result.of((c,r)->renameAfterRead(c, srcPath, dstPath, r, isWaitForPut)));
  }
  
  private void renameAfterRead(Cursor cursor,
                               String srcPath,
                               String dstPath,
                               Result<Boolean> result,
                               boolean isWaitForPut)
  {
    if (cursor == null) {
      result.ok(false);
      return;
    }
    
    copyAfterRead(cursor, dstPath, result.of((x,r)->remove(srcPath, r)), true);
  }
  
  private void copyAfterRead(Cursor cursor,
                             String path,
                             Result<Boolean> result,
                             boolean isWaitForPut)
  {
    if (cursor == null) {
      result.ok(false);
      return;
    }
    
    Result<Boolean> putResult;
    
    if (isWaitForPut) {
      putResult = result;
    }
    else {
      putResult = Result.ignore();
    }
    
    _fileInsert.exec(putResult.of(v->afterWriteClose(path)),
                     getParentHash(path), getPathKey(path), hash(path),
                     getParent(path), getName(path),
                     cursor.getInputStream(1), 
                     cursor.getLong(2),
                     cursor.getLong(3));
    
    ServiceRef.flushOutbox();
    
    if (! isWaitForPut) {
      result.ok(true);
    }
  }

  private boolean isEphemeral(WriteOption ...options)
  {
    if (options == null) {
      return false;
    }

    for (WriteOption opt : options) {
      if (opt == WriteOption.Standard.EPHEMERAL) {
        return true;
      }
    }

    return false;
  }

  public void remove(String path, Result<Boolean> result)
  {
    _fileDelete.exec(result.of((x,r)->deleteResult(path, r)),
                     getParentHash(path), 
                     getPathKey(path),
                     hash(path));

    //fileTableKelp.remove(cursor, backup, result);

    /*
    FileServiceImpl file = _fileMap.get(path);

    if (file != null) {
      file.onChange();
    }
    */
  }

  public void removeAll(String path, Result<Boolean> result)
  {
    remove(path, result);
    /*
    _fileDelete.exec(x->deleteResult(path, result),
                     path);
                     */
    //result.complete(false);
  }

  private void deleteResult(String path, Result<Boolean> result)
  {
    notifyChange(path);
    
    result.ok(Boolean.TRUE);
  }
  
  public void addDirectory(String path,
                           Result<Object> result)
  {
    result.ok(null);
  }

  /**
   * Updates the directory with the new file list.
   */
  public void removeDirectory(String path,
                              String tail,
                              Result<Object> result)
  {
    // _dirRemove.exec(result, path, tail);
    result.ok(null);
  }
  
  public void list(Result<String[]> result)
  {
    _rootNode.list(result);
  }

  /**
   * Asynchronously returns a list of files in the directory.
   */
  public void listImpl(String path,
                       Result<List<String>> result)
  {
    if (isOwner(path)) {
      listQueryImpl(path, result);
    }
    else {
      openReadFile(path, result.of((is,r)->readList(path, is, r)));
    }

    //_dirList.findAll(result.from(cursorIter->listResult(cursorIter, path)), 
    //                 calculateParentHash(path), path);
  }

  /**
   * Asynchronously returns a list of files in the directory.
   */
  public void listQueryImpl(String path,
                            Result<List<String>> result)
  {
    _dirList.findAll(result.of(cursorIter->listResult(cursorIter, path)), 
                     calculateParentHash(path), path);
  }
  
  private void readList(String path, 
                        InputStream is, 
                        Result<List<String>> result)
  {
    if (is != null) {
      result.ok(readDirFile(is));
    }
    else {
      listQueryImpl(path, result);
    }
  }

  private ArrayList<String> listResult(Iterable<Cursor> cursorIter, String path)
  {
    ArrayList<String> nameList = getBindList(path);

    if (cursorIter == null) {
      return nameList;
    }

    for (Cursor cursor : cursorIter) {
      if (cursor == null) {
        continue;
      }
      
      String name = cursor.getString(1);
      
      if (name.endsWith("/")) {
        // directory names are saved with "/"
        name = name.substring(0, name.length() - 1);
      }

      if (! nameList.contains(name)) {
        nameList.add(name);
      }
    }

    Collections.sort(nameList);

    return nameList;
  }

  public void watch(String path, Watch watch, 
                    Result<Cancel> result)
  {
    FileEntry fileEntry = createEntry(path);
    
    DatabaseWatch watchDatabase = fileEntry.watch(watch);
    
    _fileWatch.watch(watchDatabase, 
                     result,
                     getParentHash(path),
                     getPathKey(path),
                     hash(path));
  }

  /*
  public void unregisterWatch(String path, Watch watch)
  {
    FileEntry fileEntry = createEntry(path);
    
    fileEntry.unregisterWatch(watch);
  }
  */

  /*
  public void addWatchOld(String path, DatabaseWatch watchDatabase)
  {
    _fileWatch.watch(watchDatabase, path);
    //_dirWatch.watch(watchDatabase, path);
  }
  */

  /*
  public void addWatch(String path, DatabaseWatch watch)
  {
    _fileWatch.watch(watch, 
                     getParentHash(path),
                     getPathKey(path),
                     hash(path));
    
    //_dirWatch.watch(watchDatabase, path);
  }
  */

  /*
  public void removeWatch(String path, DatabaseWatch watchDatabase)
  {
    _fileWatch.unwatch(watchDatabase, path);
    //_dirWatch.watch(watchDatabase, path);
  }
  */

  private ArrayList<String> getBindList(String path)
  {
    if (! path.endsWith("/")) {
      path = path + "/";
    }

    ArrayList<String> bindList = new ArrayList<>();

    for (String bindPath : _bindMap.keySet()) {
      if (bindPath.startsWith(path)) {
        int p = bindPath.indexOf('/', path.length());

        String subpath = null;

        if (p > 0) {
          subpath = bindPath.substring(path.length(), p);
        }
        else {
          subpath = bindPath.substring(path.length());
        }

        if (! bindList.contains(subpath)) {
          bindList.add(subpath);
        }
      }
    }

    return bindList;
  }
  
  public void getStatus(String path, Result<? super FileStatusImpl> result)
  {
    _fileStatus.findOne(result.of(cursor->statusResult(cursor, path)),
                        getParentHash(path),
                        getPathKey(path),
                        hash(path));
  }

  private FileStatusImpl statusResult(Cursor cursor, String path)
  {
    int hash = hash(path);
    NodePodAmp node = _pod.getNode(hash);
    
    if (cursor == null) {
      // return null;
      return new FileStatusImpl(_authority + path, Status.FileType.NULL,
                                -1, -1, -1, 0,
                                node);
    }

    long length = cursor.getInt(1);
    long checksum = cursor.getInt(2);
    long modifiedTime = cursor.getUpdateTime();
    long version = cursor.getVersion();

    Status.FileType type;

    if (length >= 0) {
      type = Status.FileType.FILE;
    }
    else {
      type = Status.FileType.DIRECTORY;
    }
    
    return new FileStatusImpl(_authority + path, type,
                              version, length, modifiedTime, checksum, node);
  }

  @Direct
  public BfsFileSync lookup(String path)
  {
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    
    ServiceRef fileRef;
    
    if (path.isEmpty() || path.equals("/")) {
      fileRef = getServiceRef().service("");
    }
    else if (path.startsWith("/")) {
      // XXX:
      fileRef = getServiceRef().service(path);
    }
    else {
      fileRef = getServiceRef().service("/" + path);
    }
    
    return fileRef.as(BfsFileSync.class);
  }

  private BfsFile lookupBind(String path)
  {
    return lookupBind(path, "");
  }

  private BfsFile lookupBind(String path, String tail)
  {
    if ("//".equals(path) || "".equals(path)) {
      return null;
    }

    BfsFileSync bindRoot = _bindMap.get(path);

    if (bindRoot != null) {
      if ("".equals(tail)) {
        return bindRoot;
      }
      else {
        return bindRoot.lookup(tail);
      }
    }

    int p = path.lastIndexOf('/');

    if (p <= 0) {
      return null;
    }

    String head = path.substring(0, p);
    String newTail = path.substring(p) + tail;

    return lookupBind(head, newTail);
  }

  public void setServiceRef(ServiceRefAmp currentService)
  {
    _serviceRef = currentService;
  }

  public ServiceRefAmp  getServiceRef()
  {
    return _serviceRef;
  }

  /*
  public void addFile(String dir, String tail)
  {
    boolean isEphemeral = false;

    if (isEphemeral) {
      // String metaKey = getMetaKey(dir + "/" + tail);

      //_metaStore.put(metaKey, _selfServer);
    }

    BfsFileImpl dirService = lookupImpl(dir);
    dirService.addFile(tail, Result.ignore());
  }
  */

  public void bind(String address, @Service FileServiceBind service)
  {
    _bindMap.put(address, service);
  }

  @Direct
  public OutputStream openWriteFile(String path, WriteOption ...options)
  {
    CursorPrepareSync cursor = _fileInsert.prepare();
    
    cursor.setInt(1, getParentHash(path));
    cursor.setBytes(2, getPathKey(path));
    cursor.setInt(3, hash(path));
    cursor.setString(4, getParent(path));
    cursor.setString(5, getName(path));
    
    return new OutputStreamFile(path, cursor, options);
  }

  private void writePut(String path,
                       CursorPrepareSync cursor,
                       Result<Boolean> result)
  {
    /*
    Result<Boolean> resultCursor;
    
    if (isWaitForPut) {
      resultCursor = result;
    }
    else {
      resultCursor = Result.ignore();
    }
    */
    
    cursor.exec(result.of(v->afterWriteClose(path)));

    /*
    FileEntry entry = getEntry(path);
    
    if (entry != null) {
      entry.onChange();
    }
    */
    
    /*
    if (! isWaitForPut) {
      result.complete(true);
    }
    */
  }
  
  boolean afterWriteClose(String path)
  {
    addParent(path);
    notifyChange(path);
    
    return true;
  }
  
  private Object addParent(String path)
  {
    String parent = getParent(path);
    
    if (parent.isEmpty()) {
      return null;
    }
    
    //String dirPath = parent + "/";
    String dirPath = parent;
    
    
    FileEntry entry = getEntry(dirPath);
    
    if (entry != null) {
      entry.onChange();
    }

    /*
    _fileStatus.findOne(x->onDirAdd(dirPath, x),
                        getParentHash(dirPath), getPathKey(dirPath), hash(parent));
                        */

    return null;
  }
  
  private void onDirAdd(String dirPath, Object cursor)
  {
    if (cursor != null) {
      return;
    }
    
    /*
    String dirBase = dirPath;
    
    String parent = getParent(dirBase);
    String name = getName(dirBase); //  + "/";
   

    _dirInsert.exec(x->notifyChange(dirBase),
                    getParentHash(dirPath), getPathKey(dirPath), hash(dirBase),
                    parent, name, -1);
    
    addParent(dirBase);
    */
  }
  
  //
  // update/notify methods
  //

  Object notifyChange(String path)
  {
    /*
    String parent = getParent(path);

    _dirNotify.exec(Result.ignore(), 
                    getParentHash(parent), getPathKey(parent), hash(parent));
                    */
    
    return null;
  }
  
  void updateStatus(String path)
  {
    FileEntry entry = getOwnerEntry(path);
  
    if (entry != null) {
      entry.onChange();
    }
  }
  
  private void onFileChange(Cursor cursor)
  {
    if (cursor == null) {
      return;
    }
    
    String parentName = cursor.getString(1);

    // String name = cursor.getString(2);
    
    // FileEntry parentFile = getEntry(parentName);
    
    /*
    if (parentFile != null) {
      parentFile.onWatchNotify();
    }
    */
    
    if (isOwner(parentName)) {
      // XXX: s/b notification instead?
      updateDir(parentName);
    }
  }
  
  private void updateDir(String dirName)
  {
    listQueryImpl(dirName, Result.onOk(list->updateDirList(dirName, list)));
  }
  
  private void updateDirList(String dirName, List<String> list)
  {
    openReadFile(dirName, Result.onOk(is->updateDirFile(dirName, list, is)));
  }
  
  private void updateDirFile(String dirName, 
                             List<String> list,
                             InputStream is)
  {
    if (is == null) {
      if (list.size() > 0) {
        writeDirFile(dirName, list);
      }
    }
    else {
      List<String> dirFileList = readDirFile(is);

      if (dirFileList != null && ! dirFileList.equals(list)) {
        writeDirFile(dirName, list);
      }
      else if (dirFileList == null && list.size() > 0) {
        writeDirFile(dirName, list);
      }
    }
  }
  
  private List<String> readDirFile(InputStream is)
  {
    try (ReadStreamOld in = VfsOld.openRead(is)) {
      JsonReader jIn = new JsonReader(in.getReader());
      
      Object value = jIn.readObject();
      
      if (value instanceof List || value == null) {
        return (List) value;
      }
      else {
        log.warning("Unexpected value: " + value);
      
        return null;
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
      return null;
    }
  }
  
  private void writeDirFile(String dirName, List<String> list)
  {
    TempOutputStream tOs = new TempOutputStream();
    Crc64OutputStream crcOut = new Crc64OutputStream(tOs);
    
    try (WriteStreamOld out = VfsOld.openWrite(crcOut)) {
      out.setEncoding("utf-8");
      
      out.print("[");
      
      for (int i = 0; i < list.size(); i++) {
        String name = list.get(i);
        
        if (i != 0) {
          out.print(", ");
        }
        
        out.print("\"");
        out.print(name);
        out.print("\"");
      }
      
      out.print("]");
    } catch (IOException e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    
    // crcOut.close();
    
    long checksum = crcOut.getDigest();

    try {
      _fileInsert.exec(Result.ignore(),
                       getParentHash(dirName), getPathKey(dirName), hash(dirName),
                       getParent(dirName), getName(dirName),
                       tOs.getInputStream(), DIR_LENGTH_CODE, checksum);
        
      // ServiceRef.flushOutbox();
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }
  
  //
  // init methods
  //

  private TableKraken createFileTable()
  {
    KrakenImpl kraken = KrakenSystem.current().getTableManager();

    String name = getFileTable();

    String sql = ("create table " + name + " ("
                   + " parent_hash int32,"
                   + " path_key bytes(16),"
                   + " hash int16,"
                   + " parent string,"
                   + " name string,"
                   + " length int64,"
                   + " checksum int64,"
                   + " data blob,"
                   + " attr object,"
                   + " primary key (parent_hash, path_key, hash)"
                   + ")");

    return kraken.createTable(name, sql);
  }

  private String getFileTable()
  {
    String name = "caucho_bfs_file";

    String podName = _podName;

    if (podName == null) {
      return name;
    }
    
    int p = podName.indexOf('.');
    
    if (p >= 0) {
      podName = podName.substring(0, p);
    }

    return podName.replace('-', '_') + "." + name;
  }

  private void createFileQuery()
  {
    KrakenImpl kraken = KrakenSystem.current().getTableManager();
    String name = getFileTable();

    _fileInsert = kraken.query("INSERT INTO " + name
                               + " (parent_hash, path_key, hash, parent, name, data, length, checksum)"
                               + " VALUES (?,?,?,?,?,?,?,?)");

    _fileRead = kraken.query("SELECT data, length, checksum FROM " + name
                               + " WHERE parent_hash=? AND path_key=? AND hash=?");

    _fileName = kraken.query("SELECT parent, name FROM " + name
                               + " WHERE parent_hash=? AND path_key=? AND hash=?");

    _fileStatus = kraken.query("SELECT length, checksum FROM " + name
                               + " WHERE parent_hash=? AND path_key=? AND hash=?");

    _fileDelete = kraken.query("DELETE FROM " + name
                               + " WHERE parent_hash=? AND path_key=? AND hash=?");

    _fileWatch = kraken.query("WATCH " + name
                               + " WHERE parent_hash=? AND path_key=? AND hash=?");
    
    _dirInsert = kraken.query("INSERT INTO " + name
                               + " (parent_hash, path_key, hash, parent, name, length, checksum)"
                               + " VALUES (?,?,?,?,?,?,0)");

    _dirList = kraken.query("SELECT name FROM " + name
                             + " WHERE parent_hash=? AND parent=?");

    /*
    _dirNotify = kraken.query("NOTIFY " + name
                             + " WHERE parent_hash=? AND path_key=? AND hash=?");
                             */
  }

  //
  // utilities
  //
  
  /**
   * Returns the pod hash for a given path. 
   */
  public int hash(String path)
  {
    return _hash.hash(path);
  }

  private byte []getFileKey(String path)
  {
    if (path.endsWith("/")) {
      throw new IllegalArgumentException(path);
      
    }
    int p = path.lastIndexOf('/');
    
    if (p >= 0) {
      return getPrimaryKey(path, path.substring(0, p), path.substring(p + 1));
    }
    else {
      return getPrimaryKey(path, "", path);
    }
  }
  
  private int getParentHash(String path)
  {
    return calculateParentHash(getParent(path));
  }
  
  private int getParentHash(byte []key)
  {
    return BitsUtil.readInt(key, 0);
  }
  
  private String getParent(String path)
  {
    int p;
    
    if (path.endsWith("/")) {
      p = path.lastIndexOf('/', path.length() - 2);
    }
    else {
      p = path.lastIndexOf('/');
    }
    
    String parentPath;
    
    if (p >= 0) {
      parentPath = path.substring(0, p);
    }
    else {
      parentPath = "";
    }

    return parentPath;
  }
  
  private String getName(String path)
  {
    int p = path.lastIndexOf('/');
    
    String name;
    
    if (p >= 0) {
      name = path.substring(p + 1);
    }
    else {
      name = "";
    }

    return name;
  }
  
  private int calculateParentHash(String parentPath)
  {
    return Murmur32.generate(Murmur32.SEED, parentPath);
  }

  private byte []getPrimaryKey(String path, String parentPath, String name)
  {
    if (! parentPath.startsWith("/") && ! parentPath.isEmpty()) {
      throw new IllegalArgumentException(parentPath);
    }
    
    byte []key = new byte[KEY_SIZE];
    
    int parentHash = Murmur32.generate(Murmur32.SEED, parentPath);
    
    BitsUtil.writeInt(key, 0, parentHash);
    
    Fnv128 fnv = new Fnv128();
    fnv.update(path);
    fnv.digest(key, 4, 16);
    
    int hash = hash(path);
    
    key[20] = (byte) (hash >> 8);
    key[21] = (byte) (hash);

    return key;
  }

  private byte []getPathKey(String path)
  {
    Fnv128 fnv = new Fnv128();
    fnv.update(path);
    
    return fnv.getDigest();
  }
  
  private byte []getPathKey(byte []key)
  {
    byte []pathKey = new byte[16];
    
    System.arraycopy(key, 4, pathKey, 0, 16);
    
    return pathKey;
  }
  
  private int getHash(byte []key)
  {
    return ((key[20] & 0xff) << 8) + (key[21] & 0xff);
  }

  private boolean isWaitForPut(WriteOption []options)
  {
    if (options == null) {
      return false;
    }

    for (WriteOption option : options) {
      if (WriteOption.Standard.CLOSE_WAIT_FOR_PUT == option) {
        return true;
      }
    }

    return false;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _podName + "]";
  }

  @Service
  private class FileChangeService implements TableListener
  {
    @Override
    public void onPut(byte[] key, TypePut type)
    {
      FileEntry file = _fileEntryMap.get(HashKey.create(key));

      if (file != null) {
        file.onChange();
      }
      
      _fileName.findOneDirect(Result.onOk(c->onFileChange(c)),
                              getParentHash(key),
                              getPathKey(key),
                              getHash(key));
    }

    @Override
    public void onRemove(byte[] key, TypePut type)
    {
      FileEntry file = _fileEntryMap.get(HashKey.create(key));

      if (file != null) {
        file.onChange();
      }
      
      _fileName.findOneDirect(Result.onOk(c->onFileChange(c)),
                              getParentHash(key),
                              getPathKey(key),
                              getHash(key));
    }
  }

  private class OutputStreamFile extends OutputStream
  {
    private String _path;
    private WriteOption []_options;
    private OutputStream _out;
    private CursorPrepareSync _cursor;
    private long _length;
    private long _crc;

    OutputStreamFile(String path,
                     CursorPrepareSync cursor,
                     WriteOption []options)
    {
      Objects.requireNonNull(cursor);
      
      _path = path;
      _cursor = cursor;
      _options = options;
      
      _out = cursor.openOutputStream(6);
    }

    private boolean isWaitForPut()
    {
      if (_options == null) {
        return false;
      }

      for (WriteOption option : _options) {
        if (WriteOption.Standard.CLOSE_WAIT_FOR_PUT == option) {
          return true;
        }
      }

      return false;
    }

    @Override
    public void write(int ch)
      throws IOException
    {
      _out.write(ch);

      _length++;
      _crc = Crc64.generate(_crc, ch);
    }

    @Override
    public void write(byte []buffer, int offset, int length)
      throws IOException
    {
      _out.write(buffer, offset, length);

      _length += length;
      _crc = Crc64.generate(_crc, buffer, offset, length);
    }

    @Override
    public void close()
        throws IOException
    {
      OutputStream out = _out;
      _out = null;

      if (out != null) {
        out.close();
        
        _cursor.setLong(7, _length);
        _cursor.setLong(8, _crc);

        ResultFuture<Boolean> resultFuture = null;
        Result<Boolean> result;
        
        boolean isWaitForPut = isWaitForPut();
        
        if (isWaitForPut) {
          resultFuture = new ResultFuture<>();
          result = resultFuture;
        }
        else {
          result = Result.ignore();
        }
        
        updateStatus(_path);

        //_rootService.writePut(_path, _cursor, isWaitForPut(), result);
        //_cursor.exec(result.from(v->afterClose(_path)));
        if (isWaitForPut) {
          writePut(_path, _cursor, result);
        }
        else {
          _rootService.writePut(_path, _cursor, result);
        }
        
        //ServiceRef.flushOutbox();

        // insertClose(result, _path, is.getInputStream(), _length);
        
        updateStatus(_path);

        if (isWaitForPut) {
          resultFuture.get(10, TimeUnit.SECONDS);
          
          //onNotify(_path);
          //afterClose(_path);
        }

        /*
        int p = _path.lastIndexOf('/');

        if (p >= 0) {
          String dir = _path.substring(0, p);
          String tail = _path.substring(p + 1);

          if (! "".equals(tail)) {
            addFile(dir, tail);
          }
        }
        */
      }
    }
  }
  
  /*
  private class WatchHandleImpl implements CancelHandle {
    private String _path;
    private Watch _watch;
    
    WatchHandleImpl(String path, Watch watch)
    {
      _path = path;
      _watch = watch;
    }
    
    @Override
    public void cancel()
    {
      unregisterWatch(_path, _watch);
    }
  }
  */
}
