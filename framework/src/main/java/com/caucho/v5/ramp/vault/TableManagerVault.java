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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.io.Vfs;
import com.caucho.v5.json.Json;
import com.caucho.v5.json.io.InJson.Event;
import com.caucho.v5.json.io.JsonReader;
import com.caucho.v5.kraken.info.TableInfo;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;
import io.baratine.db.DatabaseServiceSync;

public class TableManagerVault<ID,T>
{
  private final static Logger log
    = java.util.logging.Logger.getLogger(TableManagerVault.class.getName());
  private final static L10N L = new L10N(TableManagerVault.class);

  private DatabaseServiceSync _db;
  private AssetInfo<ID,T> _entityInfo;
  private Config _config;

  public TableManagerVault(DatabaseServiceSync db,
                           AssetInfo<ID,T> entityDesc)
  {
    Objects.requireNonNull(db);
    
    _db = db;
    _entityInfo = entityDesc;
    
    _config = InjectorAmp.current().instance(Config.class);
    Objects.requireNonNull(_config);
  }

  public TableInfo initializeSchema()
  {
    TableInfo tableInfo = tableInfo();

    if (tableInfo != null) {
      return tableInfo;
    }
    else if ((tableInfo = createTableSql(_entityInfo.type())) != null) {
      initTableData();
      
      return tableInfo;
    }
    else if ((tableInfo = createTableSql(_entityInfo.type().getPackage())) != null) {
      initTableData();
      
      return tableInfo;
    }
    else if ((tableInfo = createTable()) != null) {
      initTableData();
      
      return tableInfo;
    }
    else {
      throw new RuntimeException(L.l("Unable to create table {0}",
                                     _entityInfo));
    }
  }

  private TableInfo createTableSql(Class<?> type)
  {
    final String path = initLocation() + "/" + type.getSimpleName() + ".ddl";

    return execSql(path);
  }

  private TableInfo createTableSql(Package pkg)
  {
    final String path = initLocation() + "/schema.sql";

    return execSql(path);
  }

  public TableInfo execSql(String pathName)
  {
    //ClassLoader cl = Thread.currentThread().getContextClassLoader();

    StringBuilder builder = null;

    Path path = Vfs.path(pathName);
    
    if (! Files.exists(path)) {
      return null;
    }
    
    int ch;

    try (InputStream in = Files.newInputStream(path)) {
      if (in == null) {
        return null;
      }

      while ((ch = in.read()) > 0) {
        if (ch != ';') {
          if (builder == null && (ch == '\n' || ch == '\r'))
            continue;

          if (builder == null)
            builder = new StringBuilder();

          builder.append((char) ch);

          continue;
        }

        if (builder.length() == 0) {
          continue;
        }

        _db.exec(builder.toString());

        if (log.isLoggable(Level.FINER))
          log.finer(builder.toString());

        builder = null;
      }
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
    
    return tableInfo();
  }

  private TableInfo createTable()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("create table " + tableName() + " (");
    sb.append("id " + _entityInfo.id().sqlType() + " primary key");
    sb.append(", __doc object");
    sb.append(")");

    String sqlCreate = sb.toString();
    
    _db.exec(sqlCreate);

    return tableInfo();
  }
  
  private void initTableData()
  {
    Class<?> type = _entityInfo.type();
    
    String location = initLocation();
    
    String pathName = location + "/" + type.getSimpleName() + ".json";

    Json json = Json.newSerializer().build();
    
    Path path = Vfs.path(pathName);
    
    if (! Files.exists(path)) {
      return;
    }
    
    try (Reader is = Files.newBufferedReader(path,
                                             Charset.forName("utf-8"))) {
      if (is == null) {
        return;
      }
    
      if (log.isLoggable(Level.FINE)) {
        log.fine(L.l("Loading {0} from {1}",
                     type.getSimpleName(), pathName));
      }
      
      try (JsonReader in = json.in(is)) {
        initTableData(in);
      }
    } catch (FileNotFoundException e) {
      log.log(Level.ALL, e.toString(), e);
    } catch (IOException e) {
      log.log(Level.FINEST, e.toString(), e);
    } catch (Exception e) {
      e.printStackTrace();;
    }
  }
  
  private String initLocation()
  {
    String location = _config.get("baratine.vault.init.location",
                                  "classpath:");
    
    if (location.indexOf(':') > 0 || location.indexOf("/") > 0) {
      return location;
    }
    
    return "classpath:" + location.replace('.', '/');
  }

  
  private void initTableData(JsonReader in)
  {
    Event token = in.next();
    
    if (token != Event.START_ARRAY) {
      return;
    }
    
    String sql = ("insert into " + _entityInfo.tableName()
                  + " (id,__doc) values (?,?)");
    
    while ((token = in.next()) == Event.START_OBJECT) {
      Map<String,Object> map = new HashMap<>();
      
      while ((token = in.next()) == Event.KEY_NAME) {
        String key = in.getString();
        Object value = in.readObject();
        
        map.put(key, value);
      }
      
      if (token != Event.END_OBJECT) {
        System.out.println("Expected end");
        return;
      }

      if (map.size() > 0) {
        ID id = _entityInfo.nextId();
        
        Objects.requireNonNull(id);

        _db.exec(sql, _entityInfo.id().toParam(id), map);
      }
    }
  }
  
  private TableInfo tableInfo()
  {
    String tableInfoSql = "show tableinfo " + tableName();

    try {
      TableInfo tableInfo = (TableInfo) _db.exec(tableInfoSql);

      return tableInfo;
    } catch (Exception e) {
      e.printStackTrace();
      log.log(Level.FINEST, e.toString(), e);
      
      return null;
    }
  }
  
  private String tableName()
  {
    return _entityInfo.tableName();
  }
}
