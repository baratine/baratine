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
 * @author Alex Rojkov
 */

package com.caucho.v5.ramp.vault;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.vault.MethodVault;
import com.caucho.v5.amp.vault.VaultDriver;
import com.caucho.v5.amp.vault.VaultDriverBase;
import com.caucho.v5.amp.vault.VaultStore;
import com.caucho.v5.kraken.info.TableInfo;
import com.caucho.v5.util.L10N;

import io.baratine.db.Cursor;
import io.baratine.db.DatabaseServiceSync;
import io.baratine.service.Asset;
import io.baratine.service.IdAsset;
import io.baratine.service.Result;
import io.baratine.service.ServiceException;
import io.baratine.service.ServiceRef;
import io.baratine.stream.ResultStream;

public class VaultDriverDataImpl<ID, T>
  extends VaultDriverBase<ID,T>
  implements VaultDriver<ID,T>, VaultStore<ID,T>
{
  private static final L10N L = new L10N(VaultDriverDataImpl.class);
  private static final Logger log
    = Logger.getLogger(VaultDriverDataImpl.class.getName());

  private final static Map<Class<?>,String> _typeMap = new HashMap<>();

  private Class<ID> _idClass;
  private Class<T> _entityClass;

  private EntityInfo<ID,T> _entityInfo;

  private DatabaseServiceSync _db;

  @Inject
  private TableManagerVault _schemaManager;

  private String _saveSql;
  private String _loadSql;

  private IdReader<ID> _idReader;
  private ServiceManagerAmp _ampManager;
  private TableInfo _tableInfo;

  public VaultDriverDataImpl(ServiceManagerAmp ampManager,
                                Class<T> entityClass,
                                Class<ID> idClass,
                                String address)
  {
    super(ampManager, entityClass, idClass, address);

    Objects.requireNonNull(entityClass);
    Objects.requireNonNull(idClass);

    _entityClass = entityClass;
    _idClass = idClass;

    _ampManager = ServiceManagerAmp.current();

    _db = _ampManager.service("bardb:///")
                     .as(DatabaseServiceSync.class);

    Objects.requireNonNull(_db);

    introspect();
  }

  private void introspect()
  {
    Asset table = _entityClass.getAnnotation(Asset.class);

    _entityInfo = new EntityInfo<>(_entityClass, _idClass, table);

    TableManagerVault<ID,T> schemaManager = new TableManagerVault<>(_db, _entityInfo);

    _tableInfo = schemaManager.initializeSchema();
    
    _entityInfo.fillColumns(_tableInfo);

    /*
                                    (b, e) -> {
                                      if (e instanceof RuntimeException)
                                        throw (RuntimeException) e;
                                      else if (e != null)
                                        throw new RuntimeException(e);
                                      else if (!b) createTable();
                                    });
                                    */

    _saveSql = _entityInfo.saveSql();
    _loadSql = _entityInfo.loadSql();
  }
  
  EntityInfo<ID,T> entityInfo()
  {
    return _entityInfo;
  }

  @Override
  public boolean isPersistent()
  {
    return true;
  }

  @Override
  public void load(ID id, T entity, Result<Boolean> result)
  {
    if (log.isLoggable(Level.FINER)) {
      log.finer(L.l("loading entity {0} for id {1}",
                    _entityInfo,
                    id));
    }
    
    if (_entityInfo.isSolo()) {
      id = (ID) new Integer(1);
    }

    _db.findOne(_loadSql,
                result.of(c -> onLoad(c, entity)),
                _entityInfo.id().toParam(id));
  }

  /**
   * Fills the entity on load complete.
   */
  private boolean onLoad(Cursor cursor, T entity)
  {
    if (cursor == null) {
      if (log.isLoggable(Level.FINEST)) {
        log.finest(L.l("{0} cursor is null", _entityInfo));
      }

      return false;
    }
    else {
      try {
        // T t = _entityDesc.readObject(c, false);
        _entityInfo.load(cursor, entity);
        // _entityDesc.setPk(t, id);

        if (log.isLoggable(Level.FINER)) {
          log.finer("loaded " + entity);
        }

        return true;
      } catch (ReflectiveOperationException e) {
        e.printStackTrace();
        //TODO log.log()
        throw ServiceException.createAndRethrow(e);
      }
    }
  }

  @Override
  public void save(ID id, T entity, Result<Void> result)
  {
    if (log.isLoggable(Level.FINER)) {
      log.finer("saving entity " + entity);
    }

    Object[] values = _entityInfo.saveValues(entity);
    
    _db.exec(_saveSql, result.of(o -> { return null; }), values);
  }

  @Override
  public void findOne(String[] fields, Object[] values, Result<ID> result)
  {
    String sql = getSelectPkSql(fields);

    _db.findOne(sql, result.of(c -> readIdFromCursor(c)), values);
  }

  @Override
  public void findOneCursor(String sql,
                            Object[] values, 
                            Result<Cursor> result)
  {
    _db.findOne(sql, result, values);
  }

  @Override
  public void findCursor(String sql,
                         Object[] values, 
                         Result<Iterable<Cursor>> result)
  {
    _db.findAll(sql, result, values);
  }

  public void findOne(String where, Object[] values, Result<ID> result)
  {
    String sql = getSelectPkSql(where);

    _db.findOne(sql, result.of(c -> readIdFromCursor(c)), values);
  }

  @Override
  public <X> void findValue(String sql, Object[] values, Result<X> result)
  {
    _db.findOne(sql,
                result.of(c -> c == null ? null : (X) c.getObject(1)),
                values);
  }


  @Override
  public void findAllIds(String where, Object[] values, Result<List<ID>> result)
  {
    String sql = getSelectPkSql(where);

    _db.findAll(sql, result.of(iter -> readIdListFromCursor(iter)), values);
  }

  @Override
  public <X> void findValueList(String sql,
                                Object[] values,
                                Result<List<X>> result)
  {
    Iterable<Cursor> rows = _db.findAll(sql, values);

    List<X> list = new ArrayList<>();

    for (Cursor row : rows) {
      list.add((X) row.getObject(1));
    }

    result.ok(list);
  }

  @Override
  public T toProxy(Object id)
  {
    if (id == null) {
      return null;
    }

    ServiceRef ref = _ampManager.service(toAddress(id));

    return ref.as(_entityClass);
  }

  @Override
  public ServiceRefAmp lookup(ID id)
  {
    if (id == null) {
      return null;
    }

    ServiceRefAmp ref = (ServiceRefAmp) _ampManager.service(toAddress(id));

    return ref;
  }
  
  private String toAddress(Object id)
  {
    if (id == null) {
      return null;
    }
    else if (id instanceof Long) {
      return getAddress() + '/' + IdAsset.encode((Long) id);
    }
    else {
      return getAddress() + '/' + id;
    }
  }
  @Override
  public void findAllIds(ResultStream<ID> ids)
  {
    String sql = getSelectIds();

    _db.findAll(sql, ids.of((i, r) -> relayIds(i, r)));
  }

  public List<ID> findIdsList()
  {
    String sql = getSelectIds();

    Iterable<Cursor> cursor = _db.findAll(sql);
    List<ID> list = new ArrayList<>();

    for (Cursor id : cursor) {
      list.add(readIdFromCursor(id));
    }

    return list;
  }

  public List<ID> readIdListFromCursor(Iterable<Cursor> cursor)
  {
    List<ID> list = new ArrayList<>();

    for (Cursor id : cursor) {
      list.add(readIdFromCursor(id));
    }

    return list;
  }

  private void relayIds(Iterable<Cursor> from, ResultStream<ID> to)
  {
    try {
      for (Cursor c : from) {
        ID id = readIdFromCursor(c);

        to.accept(id);
      }

      to.ok();
    } catch (Throwable t) {
      to.fail(t);
      //t.printStackTrace();
    }
  }

  private ID readIdFromCursor(Cursor c)
  {
    return getIdReader().read(c);
  }

  private IdReader<ID> getIdReader()
  {
    if (_idReader != null)
      return _idReader;

    Class idType = _entityInfo.id().getJavaType();

    if (isInteger(idType)) {
      _idReader = (IdReader<ID>) new IntIdReader();
    }
    else if (long.class == idType || Long.class == idType) {
      _idReader = (IdReader<ID>) new LongIdReader();
    }
    else if (IdAsset.class == idType) {
      _idReader = (IdReader<ID>) new IdAssetReader();
    }
    else if (String.class == idType) {
      _idReader = (IdReader<ID>) new StringIdReader();
    }
    else {
      throw new IllegalStateException(String.valueOf(idType));
    }

    return _idReader;
  }

  private boolean isInteger(Class t)
  {
    return int.class == t
           || Integer.class == t
           || byte.class == t
           || Byte.class == t
           || short.class == t
           || Short.class == t;
  }

  @Override
  public void create(T bean, Result<T> result)
  {
    try {
      _entityInfo.nextId(bean);

      result.ok(bean);
    } catch (Throwable e) {
      result.fail(e);
    }
  }

  @Override
  public T service(ID id)
  {
    String address = _entityInfo.generateAddress(id);

    return _ampManager.service(address).as(_entityClass);
  }

  /**
   * @param columns
   * @param values
   * @param stream
   */
  public void findMatch(String[] columns,
                        Object[] values,
                        ResultStream<T> stream)
  {
    if (columns.length != values.length)
      throw new IllegalArgumentException();

    StringBuilder sql = getWildSelect();

    sql.append(" where ");

    for (int i = 0; i < columns.length; i++) {
      String column = columns[i];
      sql.append(column).append("=?");

      if ((i + 1) < columns.length)
        sql.append(" and ");
    }

    _db.findAll(sql.toString(),
                stream.of((i, r) -> readObjects(i, r)),
                values);
  }

  private void readObjects(Iterable<Cursor> it, ResultStream<T> r)
  {
    try {
      for (Cursor cursor : it) {
        T bean = _entityInfo.readObject(cursor, true);

        r.accept(bean);
      }

      r.ok();
    } catch (ReflectiveOperationException e) {
      //TODO log
      r.fail(e);
    }
  }

  public void find(Iterable<ID> ids, ResultStream<T> stream)
  {
    //TODO
  }

  public void findAll(ResultStream<T> stream)
  {
    StringBuilder sql = getWildSelect();

    _db.findAll(sql.toString(), stream.of(this::readObjects));
  }

  private TableInfo createTable()
  {
    String tableInfoSql = "show tableinfo " + _entityInfo.tableName();

    try {
      TableInfo tableInfo = (TableInfo) _db.exec(tableInfoSql);

      if (tableInfo != null) {
        return tableInfo;
      }
    } catch (Exception e) {
      log.log(Level.FINEST, e.toString(), e);
    }

    StringBuilder sb = new StringBuilder();
    sb.append("create table " + _entityInfo.tableName() + " (");
    sb.append("id " + _entityInfo.id().sqlType() + " primary key");
    sb.append(", __id");
    sb.append(")");

    String sqlCreate = sb.toString();

    _db.exec(sqlCreate);

    return (TableInfo) _db.exec(tableInfoSql);
  }

  public String getSelectPkSql(String[] where)
  {
    StringBuilder sql = new StringBuilder("select ")
      .append(_entityInfo.id().columnName())
      .append(" FROM ").append(_entityInfo.tableName())
      .append(" where ");

    for (int i = 0; i < where.length; i++) {
      sql.append(where[i]).append('=').append('?');

      if ((i + 1) < where.length)
        sql.append(" AND ");
    }

    return sql.toString();
  }

  public String getSelectPkSql(String where)
  {
    StringBuilder sql = new StringBuilder("select ")
      .append(_entityInfo.id().columnName())
      .append(" FROM ").append(_entityInfo.tableName())
      .append(' ').append(where);

    return sql.toString();
  }

  public StringBuilder getWildSelect()
  {
    StringBuilder sql = new StringBuilder("select ");

    FieldInfo[] fields = _entityInfo.getFields();

    for (int i = 0; i < fields.length; i++) {
      FieldInfo field = fields[i];
      sql.append(field.columnName());

      if ((i + 1) < fields.length)
        sql.append(", ");
    }

    sql.append(" FROM ").append(_entityInfo.tableName());

    return sql;
  }

  public String getSelectIds()
  {
    StringBuilder sql = new StringBuilder("select ")
      .append(_entityInfo.id().columnName())
      .append(" FROM ").append(_entityInfo.tableName());

    return sql.toString();
  }

  public String getDeleteSql()
  {
    StringBuilder sql = new StringBuilder("delete from ")
      .append(_entityInfo.tableName())
      .append(" where ");

    for (FieldInfo field : _entityInfo.getFields()) {
      if (field.isId()) {
        sql.append(field.columnName());
        break;
      }

    }

    sql.append(" = ?");

    return sql.toString();
  }

  public String createDdl()
  {
    StringBuilder createDdl = new StringBuilder("create table ")
      .append(_entityInfo.tableName()).append('(');

    FieldInfo[] fields = _entityInfo.getFields();

    for (int i = 0; i < fields.length; i++) {
      FieldInfo field = _entityInfo.getFields()[i];

      createDdl.append(field.columnName())
               .append(' ')
               .append(field.sqlType());

      if (field.isId()) {
        createDdl.append(" primary key");
      }

      if ((i + 1) < fields.length) {
        createDdl.append(", ");
      }
    }

    createDdl.append(')');

    return createDdl.toString();
  }

  @Override
  public <V> MethodVault<V> newMethod(Method method)
  {
    MethodParserVault parser
      = new MethodParserVault(this, _entityInfo, method);

    FindQueryVault<?,?,V> query = parser.parse();
    
    if (query != null) {
      return query;
    }
    else {
      return super.newMethod(method);
    }
    
    /*
    System.out.println("RQ: " + query);

    switch (query.getMode()) {
    case ALL: {
      return createFindAllMethod(query);
    }
    case ONE: {
      return createFindOneMethod(query);
    }
    
    default: {
      return super.newMethod(method);
    }
    }
    */
  }

  /*
  private <V> ResourceMethod<V> createFindOneMethod(ResourceQuery query)
  {
    return new FindOneMethod(this, _ampManager, query);
  }

  <V> ResourceMethod<V> createFindAllMethod(ResourceQuery query)
  {
    return new FindAllMethod(this, _ampManager, query);
  }
  */

  class NullResourceMethod implements MethodVault
  {
    @Override
    public void invoke(Result result, Object[] args)
    {
      throw new IllegalStateException();
    }
  }

  public String toString()
  {
    return this.getClass().getSimpleName()
           + "["
           + _db
           + ", "
           + _entityClass
           + "]";
  }

  public static String getColumnType(Class type)
  {
    String sqlType = _typeMap.get(type);

    if (sqlType == null)
      sqlType = "object";

    return sqlType;
  }

  private interface IdReader<ID>
  {
    ID read(Cursor c);
  }

  private static class StringIdReader implements IdReader<String>
  {
    @Override
    public String read(Cursor c)
    {
      if (c == null)
        return null;

      return c.getString(1);
    }
  }

  private static class IntIdReader implements IdReader<Integer>
  {
    @Override
    public Integer read(Cursor c)
    {
      if (c == null)
        return null;

      return c.getInt(1);
    }
  }

  private static class LongIdReader implements IdReader<Long>
  {
    @Override
    public Long read(Cursor c)
    {
      if (c == null)
        return null;

      return c.getLong(1);
    }
  }

  private static class IdAssetReader implements IdReader<IdAsset>
  {
    @Override
    public IdAsset read(Cursor c)
    {
      if (c == null) {
        return null;
      }

      return new IdAsset(c.getLong(1));
    }
  }

  static {
    _typeMap.put(byte.class, "integer");
    _typeMap.put(Byte.class, "integer");

    _typeMap.put(short.class, "integer");
    _typeMap.put(Short.class, "integer");

    _typeMap.put(char.class, "char");
    _typeMap.put(Character.class, "char");

    _typeMap.put(int.class, "integer");
    _typeMap.put(Integer.class, "integer");

    _typeMap.put(long.class, "long");
    _typeMap.put(Long.class, "long");

    _typeMap.put(float.class, "float");
    _typeMap.put(Float.class, "float");

    _typeMap.put(double.class, "double");
    _typeMap.put(Double.class, "double");

    _typeMap.put(String.class, "varchar");
  }
}
