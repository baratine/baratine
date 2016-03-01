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

package com.caucho.v5.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import com.caucho.v5.util.L10N;

import io.baratine.db.Cursor;
import io.baratine.db.DatabaseService;
import io.baratine.service.Data;
import io.baratine.service.OnInit;
import io.baratine.service.Result;
import io.baratine.service.ResultStream;
import io.baratine.service.ServiceManager;
import io.baratine.stream.ResultStreamBuilder;

public class RepositoryImpl<ID, T>
  implements Repository<ID,T>
{
  private static final L10N L = new L10N(RepositoryImpl.class);
  private static final Logger log
    = Logger.getLogger(RepositoryImpl.class.getName());

  private Class<ID> _idClass;
  private Class<T> _entityClass;

  private EntityInfo<ID,T> _entityDesc;

  private DatabaseService _db;

  @Inject
  private TableManagerVault _schemaManager;

  public RepositoryImpl(Class<T> entityClass,
                        Class<ID> idClass,
                        DatabaseService db)
  {
    Objects.requireNonNull(entityClass);
    Objects.requireNonNull(idClass);
    _entityClass = entityClass;
    _idClass = idClass;

    if (db == null) {
      db = ServiceManager.current()
                         .lookup("bardb:///")
                         .as(DatabaseService.class);
    }

    _db = db;
  }

  @OnInit
  public void init()
  {
    Data table = _entityClass.getAnnotation(Data.class);

    _entityDesc = new EntityInfo<>(_entityClass, _idClass, table);

    /*
    //xxx: inect hack
    _schemaManager = new SchemaManager();

    _schemaManager.initializeSchema(_entityClass,
                                    _entityClass.getPackage(),
                                    _db,
                                    Result.ignore());
                                    */
  }

  @Override
  public <S extends T> void save(S entity, Result<Boolean> result)
  {
    if (log.isLoggable(Level.FINER)) {
      log.finer("saving entity " + entity);
    }

    Object[] values = new Object[_entityDesc.getSize()];

    for (int i = 0; i < values.length; i++) {
      Object value = _entityDesc.getValue(i, entity);

      values[i] = value;
    }

    _db.exec(insertSql(), result.of(o -> {
      log.log(Level.FINER, String.format("entity %1$s is saved", entity));
      return true;
    }), values);
  }

  @Override
  public void findOne(ID id, Result<T> result)
  {
    log.log(Level.FINER, String.format("loading entity %1$s with for id %2$s",
                                       _entityDesc,
                                       id));

    _db.findOne(getSelectOneSql(),
                result.of((c, r) -> readObject(c, id, r)),
                id);
  }

  @Override
  public ResultStreamBuilder<T> findMatch(String[] columns, Object[] values)
  {
    throw new UnsupportedOperationException();
  }

  /**
   * @param columns
   * @param values
   * @param stream
   * @see #findMatch(String[], Object[])
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
      for (Cursor c : it) {
        T t = _entityDesc.readObject(c, true);

        r.accept(t);
      }

      r.ok();
    } catch (ReflectiveOperationException e) {
      //TODO log
      r.fail(e);
    }
  }

  private void readObject(Cursor c, ID id, Result<T> result)
  {
    if (c == null) {
      log.log(Level.FINER, L.l("{0} cursor is null", _entityDesc));

      result.ok(null);
    }
    else {
      try {
        T t = _entityDesc.readObject(c, false);

        _entityDesc.setPk(t, id);

        log.log(Level.FINER, String.format("loaded %1$s", t));

        result.ok(t);
      } catch (ReflectiveOperationException e) {
        e.printStackTrace();
        //TODO log.log()
        result.fail(e);
      }
    }
  }

  @Override
  public ResultStreamBuilder<T> find(Iterable<ID> ids)
  {
    throw new UnsupportedOperationException();
  }

  public void find(Iterable<ID> ids, ResultStream<T> stream)
  {
    //TODO
  }

  @Override
  public ResultStreamBuilder<T> findAll()
  {
    throw new UnsupportedOperationException();
  }

  public void findAll(ResultStream<T> stream)
  {
    StringBuilder sql = getWildSelect();

    _db.findAll(sql.toString(), stream.of(this::readObjects));
  }

  @Override
  public void delete(ID id, Result<Boolean> result)
  {
    _db.exec(getDeleteSql(), result.of(x -> (Boolean) x), id);
  }

  @Override
  public void deleteIds(Iterable<ID> ids, Result<Boolean> result)
  {
    for (ID entity : ids) {
      delete(entity, null);
    }
  }

  public String insertSql()
  {
    StringBuilder head = new StringBuilder("insert into ")
      .append(_entityDesc.tableName())
      .append('(');

    StringBuilder tail = new StringBuilder(") values (");

    FieldInfo[] fields = _entityDesc.getFields();
    for (int i = 0; i < fields.length; i++) {
      FieldInfo field = _entityDesc.getFields()[i];

      head.append(field.columnName());

      tail.append('?');
      if ((i + 1) < fields.length) {
        head.append(", ");
        tail.append(", ");
      }
    }

    tail.append(')');
    head.append(tail);

    return head.toString();
  }

  public String getSelectOneSql()
  {
    StringBuilder head = new StringBuilder("select ");
    StringBuilder where = new StringBuilder(" where ");

    FieldInfo[] fields = _entityDesc.getFields();

    for (int i = 0; i < fields.length; i++) {
      FieldInfo field = fields[i];

      if (field.isId()) {
        where.append(field.columnName()).append(" = ?");
        continue;
      }

      head.append(field.columnName());

      if ((i + 1) < fields.length && !fields[i + 1].isId())
        head.append(", ");
    }

    head.append(" from ").append(_entityDesc.tableName());

    head.append(where);

    return head.toString();
  }

  public StringBuilder getWildSelect()
  {
    StringBuilder sql = new StringBuilder("select ");

    FieldInfo[] fields = _entityDesc.getFields();

    for (int i = 0; i < fields.length; i++) {
      FieldInfo field = fields[i];
      sql.append(field.columnName());

      if ((i + 1) < fields.length)
        sql.append(", ");
    }

    sql.append(" FROM ").append(_entityDesc.tableName());

    return sql;
  }

  public String getDeleteSql()
  {
    StringBuilder sql = new StringBuilder("delete from ")
      .append(_entityDesc.tableName())
      .append(" where ");

    for (FieldInfo field : _entityDesc.getFields()) {
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
      .append(_entityDesc.tableName()).append('(');

    FieldInfo[] fields = _entityDesc.getFields();

    for (int i = 0; i < fields.length; i++) {
      FieldInfo field = _entityDesc.getFields()[i];

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
    String sqlType = typeMap.get(type);

    if (sqlType == null)
      sqlType = "object";

    return sqlType;
  }

  private final static Map<Class,String> typeMap = new HashMap<>();

  static {
    typeMap.put(byte.class, "integer");
    typeMap.put(Byte.class, "integer");

    typeMap.put(short.class, "integer");
    typeMap.put(Short.class, "integer");

    typeMap.put(char.class, "char");
    typeMap.put(Character.class, "char");

    typeMap.put(int.class, "integer");
    typeMap.put(Integer.class, "integer");

    typeMap.put(long.class, "long");
    typeMap.put(Long.class, "long");

    typeMap.put(float.class, "float");
    typeMap.put(Float.class, "float");

    typeMap.put(double.class, "double");
    typeMap.put(Double.class, "double");

    typeMap.put(String.class, "varchar");
  }
}
