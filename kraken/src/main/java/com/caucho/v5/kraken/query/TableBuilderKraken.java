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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.kraken.query;

import io.baratine.service.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.caucho.v5.kelp.DatabaseKelp;
import com.caucho.v5.kelp.TableBuilderKelp;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kraken.table.KelpManager;
import com.caucho.v5.kraken.table.PodHashGenerator;
import com.caucho.v5.kraken.table.KrakenImpl;
import com.caucho.v5.util.L10N;

public class TableBuilderKraken
{
  private static final L10N L = new L10N(TableBuilderKraken.class);
  
  private final String _podName;
  private final String _name;
  
  private final String _sql;
  
  private final Map<String,Col> _columnMap = new LinkedHashMap<>();
  
  private ArrayList<String> _key;
  
  private ArrayList<Class<?>> _schema = new ArrayList<>();

  private PodHashGenerator _hashGen;
  private HashExprGenerator _hashBuilder;

  public TableBuilderKraken(String podName,
                            String name, 
                            String sql)
  {
    _podName = podName;
    _name = name;
    _sql = sql;
  }

  public String getPodName()
  {
    return _podName;
  }

  public String getName()
  {
    return _name;
  }

  public String getId()
  {
    return getPodName() + '.' + getName();
  }

  public String getSql()
  {
    return _sql;
  }

  public void addBool(String name)
  {
    _columnMap.put(name, new BoolCol(name));
  }

  public void addInt8(String name)
  {
    _columnMap.put(name, new Int8Col(name));
  }

  public void addInt16(String name)
  {
    _columnMap.put(name, new Int16Col(name));
  }

  public void addInt32(String name)
  {
    _columnMap.put(name, new Int32Col(name));
  }

  public void addInt64(String name)
  {
    _columnMap.put(name, new Int64Col(name));
  }

  public void addBytes(String name, int length)
  {
    _columnMap.put(name,  new BytesCol(name, length));
  }

  public void addVarchar(String name, int length)
  {
    _columnMap.put(name, new StringCol(name));
  }

  public void addString(String name)
  {
    _columnMap.put(name, new StringCol(name));
  }

  public void addObject(String name)
  {
    _columnMap.put(name, new ObjectCol(name));
  }

  public void addVersion(String name)
  {
    _columnMap.put(name, new VersionCol(name));
  }

  public void addVarbinary(String name, int length)
  {
  }

  public void addDateTime(String name)
  {
    _columnMap.put(name, new TimestampCol(name));
  }

  public void addIdentity(String name)
  {
    _columnMap.put(name, new IdentityCol(name));
  }

  public void setPrimaryKey(String name)
  {
    if (_key != null) {
      throw new RuntimeException(L.l("primary key is already defined {0}",
                                     name));
    }
    
    _key = new ArrayList<>();
    _key.add(name);
  }

  /**
   * @param parseColumnNames
   */
  public void addPrimaryKey(ArrayList<String> keys)
  {
    if (_key != null) {
      throw new RuntimeException(L.l("primary key is already defined {0}",
                                     keys));
    }
    
    _key = new ArrayList<>(keys);
  }

  /**
   * @param name
   */
  public void setNotNull(String name)
  {
    // TODO Auto-generated method stub
    
  }

  /**
   * @param name
   */
  public void addBlob(String name)
  {
    _columnMap.put(name, new BlobCol(name));
  }

  /**
   * float column
   */
  public void addFloat(String name)
  {
    _columnMap.put(name, new FloatCol(name));
  }

  /**
   * @param name
   */
  public void addDouble(String name)
  {
    _columnMap.put(name, new DoubleCol(name));
  }
  
  public void schema(Class<?> type)
  {
    Objects.requireNonNull(type);

    _schema.add(type);
  }

  public void setHashClass(Class<?> cl)
  {
    try {
      _hashGen = (PodHashGenerator) cl.newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void setHashGenerator(PodHashGenerator gen)
  {
    _hashGen = gen;
  }

  public void setHashBuilder(HashExprGenerator gen)
  {
    _hashBuilder = gen;
  }

  public PodHashGenerator buildHashGenerator(TableKelp tableKelp)
  {
    if (_hashGen != null) {
      return _hashGen;
    }
    else if (_hashBuilder != null) {
      return _hashBuilder.build(tableKelp);
    }
    else {
      return null;
    }
  }

  boolean isColumnPresent(String name)
  {
    return _columnMap.get(name) != null;
  }

  Col getColumn(String name)
  {
    return _columnMap.get(name);
  }

  public void create(KrakenImpl tableManager,
                     Result<Object> result)
  {
    Objects.requireNonNull(tableManager);
    Objects.requireNonNull(result);
    
    KelpManager kelpBacking = tableManager.getKelpBacking();
    DatabaseKelp db = kelpBacking.getDatabase();
    
    String tableName = _podName + '.' + _name;

    TableBuilderKelp builder = db.createTable(tableName);
    
    if (_key == null) {
      throw new RuntimeException(L.l("Create requires primary key"));
    }
    
    // int versionIndex = buildVersion();
    
    /*
    builder.startKey();
    for (String key : _key) {
      Col col = _columnMap.get(key);
      
      col.build(builder);
    }
    builder.endKey();
    */

    
    if (_key.size() == 0) {
      throw new IllegalStateException(L.l("No defined keys"));
    }
    
    ArrayList<String> directKeys = new ArrayList<>();
    
    builder.startKey();

    for (String key : _key) {
      Col col = _columnMap.get(key);

      if (col == null) {
        throw new IllegalStateException(L.l("'{0}' is an unknown key", key));
      }
      else if (col.isBlob()) {
        String name = ":key_" + col.getName();
        
        builder.columnBytes(name, 16); // synthetic key
      }
      else {
        directKeys.add(key);
      
        col.build(builder);
      }
    }
    
    if (_hashBuilder != null && _hashBuilder.isSyntheticHash(this)) {
      String name = ":hash";
      
      builder.columnInt16(name); // synthetic key
    }
    
    builder.endKey();
    
    for (Col col : _columnMap.values()) {
      if (directKeys.contains(col.getName())) {
        continue;
      }
      
      col.build(builder);
    }
    
    for (Class<?> schema : _schema) {
      builder.schema(schema);
    }
    
    /*
    if (_columnMap.get(":pod_hash") == null) {
      // synthetic hash column added after key end
      builder.columnInt16(":pod_hash");
    }
    */
    
    tableManager.getTableService().buildTable(_sql, this, builder, (Result) result);
    
    // builder.build(result.from((table,r)->addTable(table, kelpBacking, r)));
  }

  /*
  private void addTable(TableKelp table,
                        KelpBacking kelpBacking,
                        Result<Object> result)
  {
    if (table != null) {
      kelpBacking.addTable(table, _sql, this, (Result) result);
    }
    else {
      result.complete(null);
    }
  }
  */
  
  /*
  private int buildVersion()
  {
    int i = 1;
    for (Col col : _columnMap.values()) {
      if (col instanceof VersionCol) {
        return i;
      }

      i++;
    }
    
    _columnMap.put("$version", new VersionCol("$version"));
    
    return i;
  }
  */
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _name + "]";
  }
  
  static class Col {
    private String _name;
    
    private Col(String name)
    {
      _name = name;
    }

    protected String getName()
    {
      return _name;
    }
    
    public boolean isBlob()
    {
      return false;
    }

    public void build(TableBuilderKelp builder)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }
  }
  
  private static class BoolCol extends Col {
    private BoolCol(String name)
    {
      super(name);
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnBool(getName());
    }
  }
  
  private static class Int8Col extends Col {
    private Int8Col(String name)
    {
      super(name);
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnInt8(getName());
    }
  }
  
  private static class Int16Col extends Col {
    private Int16Col(String name)
    {
      super(name);
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnInt16(getName());
    }
  }
  
  private static class Int32Col extends Col {
    private Int32Col(String name)
    {
      super(name);
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnInt32(getName());
    }
  }
  
  /**
   * int64 column
   */
  private static class Int64Col extends Col {
    private Int64Col(String name)
    {
      super(name);
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnInt64(getName());
    }
  }

  /**
   * float column
   */
  private static class FloatCol extends Col {
    private FloatCol(String name)
    {
      super(name);
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnFloat(getName());
    }
  }

  /**
   * double column
   */
  private static class DoubleCol extends Col {
    private DoubleCol(String name)
    {
      super(name);
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnDouble(getName());
    }
  }
  
  /**
   * timestamp column
   */
  private static class TimestampCol extends Col {
    private TimestampCol(String name)
    {
      super(name);
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnTimestamp(getName());
    }
  }
  
  /**
   * identity column
   */
  private static class IdentityCol extends Col {
    private IdentityCol(String name)
    {
      super(name);
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnIdentity(getName());
    }
  }
  
  private static class VersionCol extends Col {
    private VersionCol(String name)
    {
      super(name);
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnInt64(getName());
    }
  }
  
  private static class BlobCol extends Col {
    private BlobCol(String name)
    {
      super(name);
    }
    
    @Override
    public boolean isBlob()
    {
      return true;
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnBlob(getName());
    }
  }
  
  private static class BytesCol extends Col {
    private int _len;
    
    private BytesCol(String name, int len)
    {
      super(name);
      
      _len = len;
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnBytes(getName(), _len);
    }
  }
  
  private static class StringCol extends Col {
    private StringCol(String name)
    {
      super(name);
    }
    
    @Override
    public boolean isBlob()
    {
      return true;
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnString(getName());
    }
  }
  
  private static class ObjectCol extends Col {
    private ObjectCol(String name)
    {
      super(name);
    }
    
    @Override
    public boolean isBlob()
    {
      return true;
    }

    @Override
    public void build(TableBuilderKelp builder)
    {
      builder.columnObject(getName());
    }
  }
  
  /*
  class TableResult extends Result.Wrapper<TableKelp,Object>
  {
    private KelpBacking _kelpBacking;
    
    TableResult(KelpBacking localBacking, 
                Result<Object> result)
    {
      super(result);
      
      _kelpBacking = localBacking;
    }
    
    @Override
    public void complete(TableKelp table)
    {
      try {
        TableBuilderKraken builder = TableBuilderKraken.this;
        
        _kelpBacking.addTable(table, _sql, builder, (Result) getNext());
      } catch (Throwable e) {
        fail(e);
      }
    }
    
    @Override
    public void fail(Throwable e)
    {
      // XXX: e.printStackTrace();
      
      super.fail(e);
      
    }
  }
  */
}
