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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.query.QueryBuilderKelp;
import com.caucho.v5.kraken.table.KrakenImpl;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.ModulePrivate;

@ModulePrivate
public class QueryParserKraken {
  private static final Logger log
    = Logger.getLogger(QueryParserKraken.class.getName());
  private static final L10N L = new L10N(QueryParserKraken.class);

  // private final static IntMap _reserved;
  private final static HashMap<String,Token> _reserved;

  private final String _sql;
  private final char []_sqlChars;
  private final int _sqlLength;

  private int _parseIndex;

  private final CharBuffer _cb = new CharBuffer();

  private String _lexeme;
  private Token _token;
  
  private String _podName;

  private ArrayList<ParamExpr> _params = new ArrayList<>();

  private QueryBuilderKraken _query;
  //private AndExpr _andExpr;
  
  private QueryBuilderKelp _queryBuilderKelp;
  private KrakenImpl _tableManager;
  
  //private SerializerFactory _serializerFactory;
  //private OutFactoryH3 _serializerFactory;

  public QueryParserKraken(KrakenImpl tableManager,
                            String sql)
  {
    _tableManager = tableManager;
    _sql = sql;
    _sqlLength = _sql.length();
    _sqlChars = new char[_sqlLength];
    _sql.getChars(0, _sqlLength, _sqlChars, 0);
    
    /*
    // XXX: needs to be in an owner
    _serializerFactory = new SerializerFactory();
    _serializerFactory.setAllowNonSerializable(true);
    */
  }
  
  public static QueryBuilderKraken parse(KrakenImpl tableManager,
                                         String tableName,
                                         String sql)
  {
    QueryParserKraken parser = new QueryParserKraken(tableManager, sql);
    
    String podName = getPodName(tableName);
    
    parser.setPodName(podName);

    return parser.parse();
  }

  public static QueryBuilderKraken parse(KrakenImpl tableManager,
                                         String sql)
  {
    QueryParserKraken parser = new QueryParserKraken(tableManager, sql);

    return parser.parse();

    /*
    queryBuilder.bind();
    
    return queryBuilder.build(tableManager, db);
    */
  }

  public static String parse(KrakenImpl tableManager,
                             String tableName, 
                             String columnName, 
                             String query)
  {
    QueryParserKraken parser = new QueryParserKraken(tableManager, query);
    
    ExprKraken expr = parser.parseSubQuery();
    
    String subExpr = expr.toObjectExpr(columnName);

    return subExpr;
  }

  public void setPodName(String podName)
  {
    _podName = podName;
  }

  public KrakenImpl tableManager()
  {
    return _tableManager;
  }
  
  /*
  public SerializerFactory serializerFactory()
  {
    return _serializerFactory;
  }
  */
  /*
  public OutFactoryH3 serializerFactory()
  {
    return _serializerFactory;
  }
  */

  /**
   * Parses the query.
   */
  public QueryBuilderKraken parse()
  {
    Token token = scanToken();

    switch (token) {
    case CREATE:
      return parseCreate();
      
    case EXPLAIN:
      return parseExplain();

    case INSERT:
      return parseInsert();

    case REPLACE:
      return parseReplace();
      
    case SELECT:
      return parseSelect();
      
    case SELECT_LOCAL:
      return parseSelectLocal();
      
    case MAP:
      return parseMap();
      
    case SHOW:
      return parseShow();
      
    case UPDATE:
      return parseUpdate();
      
    case DELETE:
      return parseDelete();
      
    case WATCH:
      return parseWatch();
      
      /*
    case NOTIFY:
      return parseNotify();
      */

      /*
    case VALIDATE:
      return parseValidate();

    case DROP:
      return parseDrop();
*/
      
    case IDENTIFIER:
      if (_lexeme.equalsIgnoreCase("checkpoint")) {
        return parseCheckpoint();
      }
      
    default:
      throw error("unknown query at {0}", token);
    }
  }

  /**
   * Parses the query.
   */
  private ExprKraken parseSubQuery()
  {
    return parseExpr();
  }

  /**
   * Parses the select.
   */
  private QueryBuilderKraken parseExplain()
  {
    Token token = scanToken();
    
    if (token != Token.SELECT) {
      throw error("Explain requires SELECT at {0}", token);
    }
    
    return parseSelect(new QueryBuilderExplain(_tableManager, _sql));
  }

  /**
   * Parses the select.
   */
  private QueryBuilderKraken parseSelect()
  {
    SelectQueryBuilder selectBuilder
      = new SelectQueryBuilder(_tableManager, _sql);
    
    return parseSelect(selectBuilder);
  }
  
  private QueryBuilderKraken parseSelectLocal()
  {
    SelectQueryBuilder selectBuilder
      = new SelectQueryBuilder(_tableManager, _sql);
    
    selectBuilder.setLocal(true);
    
    return parseSelect(selectBuilder);
  }

  /**
   * Parses the map.
   */
  private QueryBuilderKraken parseMap()
  {
    return parseMap(new MapQueryBuilder(_tableManager, _sql));
  }

  /**
   * Parses the select.
   */
  private QueryBuilderKraken parseMap(QueryBuilderKraken query)
  {
    boolean distinct = false;

    Token token = scanToken();

    if (token == Token.DISTINCT)
      distinct = true;
    else
      _token = token;

    ArrayList<ExprKraken> resultItems = new ArrayList<>();

    Token startToken = scanToken();
    String startLexeme = _lexeme;
    int startOffset = _parseIndex;

    while ((token = scanToken()) != null
        && token != Token.EOF 
        && token != Token.FROM) {
    }

    if (token != Token.FROM) { 
      throw error("expected FROM at '{0}'", token);
    }

    _query = query;

    String tableName = parseFrom();

    query.setTableName(tableName);
    
    token = scanToken();
    
    Token tailToken = token;
    int tailOffset = _parseIndex;

    _token = startToken;
    _parseIndex = startOffset;
    _lexeme = startLexeme;

    ExprKraken expr = parseSelectExpr();

    resultItems.add(expr);

    while ((token = scanToken()) == Token.COMMA) {
      expr = parseSelectExpr();

      resultItems.add(expr);
    }

    _token = tailToken;
    _parseIndex = tailOffset;

    token = scanToken();
    
    ExprKraken whereExpr = null;

    if (token == Token.WHERE) {
      // _andExpr.add(parseExpr());
      whereExpr = parseExpr();
    }
    else {
      _token = token;
    }

    ParamExpr []params = _params.toArray(new ParamExpr[_params.size()]);
    
    query.setWhereExpr(whereExpr);
    // query.setKelpExpr(whereKelp);
    query.setParams(params);

    for (int i = resultItems.size() - 1; i >= 0; i--) {
      ExprKraken subExpr = resultItems.get(i);
    }

    ExprKraken []resultArray
      = resultItems.toArray(new ExprKraken[resultItems.size()]);

    query.setResults(resultArray);
    
    token = scanToken();
    
    if (token != null 
        && token != Token.EOF
        && token != Token.LIMIT 
        && token != Token.OFFSET) {
      throw error("unexpected token at end '{0}'", token);
    }

    return query;
  }

  /**
   * Parses the select.
   */
  private QueryBuilderKraken parseSelect(QueryBuilderKraken query)
  {
    boolean distinct = false;

    Token token = scanToken();

    if (token == Token.DISTINCT)
      distinct = true;
    else
      _token = token;

    ArrayList<ExprKraken> resultItems = new ArrayList<>();

    Token startToken = scanToken();
    String startLexeme = _lexeme;
    int startOffset = _parseIndex;

    while ((token = scanToken()) != null
        && token != Token.EOF 
        && token != Token.FROM) {
    }

    if (token != Token.FROM) { 
      throw error("expected FROM at '{0}'", token);
    }

    _query = query;

    String tableName = parseFrom();

    query.setTableName(tableName);
    
    token = scanToken();
    
    Token tailToken = token;
    int tailOffset = _parseIndex;

    _token = startToken;
    _parseIndex = startOffset;
    _lexeme = startLexeme;

    ExprKraken expr = parseSelectExpr();

    resultItems.add(expr);

    while ((token = scanToken()) == Token.COMMA) {
      expr = parseSelectExpr();

      resultItems.add(expr);
    }

    _token = tailToken;
    _parseIndex = tailOffset;

    token = scanToken();
    
    ExprKraken whereExpr = null;

    if (token == Token.WHERE) {
      // _andExpr.add(parseExpr());
      whereExpr = parseExpr();
    }
    else {
      _token = token;
    }
    
    /*
    QueryKelp whereKelp = null;
    
    if (whereExpr != null) {
      QueryBuilderKelp queryBuilderKelp = new QueryBuilderKelp(table);
      
      ExprBuilderKelp whereKelpBuilder = whereExpr.buildKelp(queryBuilderKelp);
      
      if (whereKelpBuilder != null) {
        whereKelp = queryBuilderKelp.build(whereKelpBuilder);
      }
    }
    */

    ParamExpr []params = _params.toArray(new ParamExpr[_params.size()]);

    //Expr whereExpr = _andExpr.getSingleExpr();
    //_andExpr = null;
    
    query.setWhereExpr(whereExpr);
    // query.setKelpExpr(whereKelp);
    query.setParams(params);

    for (int i = resultItems.size() - 1; i >= 0; i--) {
      ExprKraken subExpr = resultItems.get(i);

      /*
      if (subExpr instanceof UnboundStarExpr) {
        UnboundStarExpr unboundExpr = (UnboundStarExpr) subExpr;
        ArrayList<Expr> exprList = unboundExpr.expand(query.getFromItems());

        resultItems.remove(i);
        resultItems.addAll(i, exprList);
      }
      */
    }

    /*
    ArrayList<Expr> groupItems = null;
    token = scanToken();
    if (token == GROUP) {
      token = scanToken();

      if (token != BY)
        throw error(L.l("expected BY at `{0}'", tokenName(token)));

      groupItems = parseGroup(query);
    }
    else
      _token = token;
      */

    /*
    token = scanToken();
    if (token == ORDER) {
      token = scanToken();

      if (token != BY)
        throw error(L.l("expected BY at `{0}'", tokenName(token)));

      Order order = parseOrder(query, resultItems);
    }
    else
      _token = token;
      */

    ExprKraken []resultArray
      = resultItems.toArray(new ExprKraken[resultItems.size()]);

    query.setResults(resultArray);

    /*
    if (query.isGroup()) {
      Expr []resultList = query.getResults();

      bindGroup(query, groupItems);

      for (int i = 0; i < resultList.length; i++) {
        Expr subExpr = resultList[i];

        if (! (subExpr instanceof GroupExpr)) {
          resultList[i] = new GroupResultExpr(i, subExpr);
        }
      }
    }
    */
    
    /*
    token = scanToken();
    if (token == Token.LIMIT) {
      parseLimit(query);
    }
    else {
      _token = token;
    }
    */
    
    token = scanToken();
    
    if (token != null 
        && token != Token.EOF
        && token != Token.LIMIT 
        && token != Token.OFFSET) {
      throw error("unexpected token at end '{0}'", token);
    }

    // _query = query.getParent();
    // _andExpr = oldAnd;

    return query;
  }

  /**
   * Parses the show.
   */
  private ShowQueryBuilder parseShow()
  {
    return parseShow(new ShowQueryBuilder(_tableManager, _sql));
  }

  /**
   * Parses the show.
   */
  private ShowQueryBuilder parseShow(ShowQueryBuilder query)
  {
    Token token = scanToken();

    if (token == Token.TABLE) {
    }
    else if (token == Token.IDENTIFIER
             && _lexeme.equalsIgnoreCase("tableinfo")) {
      query.method("tableinfo");
    }
    else {
      throw error("Expected TABLE at {0}", token);
    }
    
    token = scanToken();

    if (token != Token.IDENTIFIER) {
      throw error("Expected IDENTIFIER at {0}", token);
    }
    
    String pod = _lexeme;
    String name;
    
    if (peekToken() == Token.DOT) {
      scanToken();

      if ((token = scanToken()) != Token.IDENTIFIER) {
        throw error("Expected IDENTIFIER at {0}", token);
      }
      
      name = _lexeme; 
    }
    else {
      name = pod;
      pod = getPodName();
    }
    
    query.setTableName(pod + '.' + name);

    return query;
  }

  /**
   * Checkpoint parsing
   */
  private CheckpointQueryBuilder parseCheckpoint()
  {
    return parseCheckpoint(new CheckpointQueryBuilder(_tableManager, _sql));
  }

  /**
   * Parses the show.
   */
  private CheckpointQueryBuilder parseCheckpoint(CheckpointQueryBuilder query)
  {
    Token token = scanToken();

    if (token != Token.IDENTIFIER) {
      throw error("Expected IDENTIFIER at {0}", token);
    }
    
    String pod = _lexeme;
    String name;
    
    if (peekToken() == Token.DOT) {
      scanToken();

      if ((token = scanToken()) != Token.IDENTIFIER) {
        throw error("Expected IDENTIFIER at {0}", token);
      }
      
      name = _lexeme; 
    }
    else {
      name = pod;
      pod = getPodName();
    }
    
    query.setTableName(pod + '.' + name);

    return query;
  }

  /*
  private ArrayList<FromItem> parseFromItems()
    throws SQLException
  {
    ArrayList<FromItem> fromItems = new ArrayList<FromItem>();

    int token;

    // XXX: somewhat hacked syntax
    while ((token = scanToken()) == '(') {
    }
    _token = token;
    
    FromItem fromItem = parseFromItem();

    if (fromItem != null)
      fromItems.add(fromItem);
    
    int parenCount = 0;

    while (true) {
      token = scanToken();

      boolean isNatural = false;
      boolean isOuter = false;
      boolean isLeft = true;
      boolean isRight = true;

      if (token == ',') {
        fromItem = parseFromItem();
        fromItems.add(fromItem);
        continue;
      }
      else if (token == '(') {
        parenCount++;
        continue;
      }
      else if (token == ')') {
        if (--parenCount < 0) {
          _token = token;
          break;
        }
        else
          continue;
      }
      else if (token != IDENTIFIER) {
        _token = token;
        break;
      }
      else if ("join".equalsIgnoreCase(_lexeme)) {
      }
      else if ("inner".equalsIgnoreCase(_lexeme)) {
        String join = parseIdentifier();

        if (! "join".equalsIgnoreCase(join))
          throw error(L.l("expected JOIN at '{0}'", join));
      }
      else if ("left".equalsIgnoreCase(_lexeme)) {
        String name = parseIdentifier();

        if ("outer".equalsIgnoreCase(name))
          name = parseIdentifier();

        if (! "join".equalsIgnoreCase(name))
          throw error(L.l("expected JOIN at '{0}'", name));

        isOuter = true;
      }
      else if ("right".equalsIgnoreCase(_lexeme)) {
        String name = parseIdentifier();

        if ("outer".equalsIgnoreCase(name))
          name = parseIdentifier();

        if (! "join".equalsIgnoreCase(name))
          throw error(L.l("expected JOIN at '{0}'", name));

        isRight = true;
        isOuter = true;

        throw error(L.l("right outer joins are not supported"));
      }
      else if ("natural".equalsIgnoreCase(_lexeme)) {
        String name = parseIdentifier();

        isNatural = true;

        if ("left".equalsIgnoreCase(name)) {
          name = parseIdentifier();

          if ("outer".equalsIgnoreCase(name))
            name = parseIdentifier();

          isOuter = true;
        }
        else if ("right".equalsIgnoreCase(name)) {
          name = parseIdentifier();

          if ("outer".equalsIgnoreCase(name))
            name = parseIdentifier();

          isRight = true;
          isOuter = true;

          throw error(L.l("right outer joins are not supported"));
        }

        if (! "join".equalsIgnoreCase(name))
          throw error(L.l("expected JOIN at '{0}'", name));
      }
      else {
        _token = token;
        break;
      }

      fromItem = parseFromItem();
      fromItems.add(fromItem);

      _query.setFromItems(fromItems);

      token = scanToken();
      if (token == IDENTIFIER && "on".equalsIgnoreCase(_lexeme)) {
        Expr onExpr = parseExpr();

        if (isOuter) {
          FromItem leftItem = fromItems.get(fromItems.size() - 2);
          FromItem rightItem = fromItems.get(fromItems.size() - 1);

          onExpr = new LeftOuterJoinExpr(rightItem, onExpr);

          rightItem.setDependTable(leftItem);
        }

        _andExpr.add(onExpr);
      }
      else
        _token = token;
    }

    return fromItems;
  }
  */

  /**
   * Parses a select expression.
   */
  private ExprKraken parseSelectExpr()
  {
    Token token = scanToken();

    if (token == Token.STAR) {
      // return new UnboundStarExpr();
      
      throw new UnsupportedOperationException(getClass().getName());
    }
    else {
      _token = token;

      return parseExpr();
    }
  }

  /**
   * Parses a from item
   */

  private String parseFrom()
  {
    return parseTableName();
    
    // String tableName = parseIdentifier();
    
    // return tableName;
    /*
    if (tableName.equalsIgnoreCase("DUAL"))
      return null;
      */

    /*
    TableKelp table = _db.getTable(tableName);

    if (table == null) {
      throw error("'{0}' is an unknown table.  'FROM table' requires an existing table.", tableName);
    }
    */

    /*
    String name = table.getName();

    Token token = scanToken();
    if (token == Token.AS)
      name = parseIdentifier();
    else if (token == IDENTIFIER)
      name = _lexeme;
    else
      _token = token;

    return new FromItem(table, name);
    */
    
    // return table;
  }

  /**
   * Parses the ORDER BY
   */
  /*
  private Order parseOrder(SelectQueryBuilder query,
                           ArrayList<Expr> resultList)
    throws SQLException
  {
    int token;

    Order order = null;

    do {
      Expr expr = parseExpr();

      expr = expr.bind(query);

      token = scanToken();
      boolean isAsc = true;
      if (token == ASC)
        isAsc = true;
      else if (token == DESC)
        isAsc = false;
      else
        _token = token;

      int index;
      for (index = 0; index < resultList.size(); index++) {
        Expr resultExpr = resultList.get(index);

        if (expr.equals(resultExpr))
          break;
      }

      if (resultList.size() <= index) {
        resultList.add(expr);
      }

      Order tailOrder = expr.createOrder(index);
      tailOrder.setAscending(isAsc);

      order = Order.append(order, tailOrder);

      // ascList.add(isAsc ? Boolean.TRUE : Boolean.FALSE);
    } while ((token = scanToken()) == ',');

    query.setOrder(order);

    _token = token;

    return order;
  }
  */

  /**
   * Parses the GROUP BY
   */
  /*
  private ArrayList<Expr> parseGroup(SelectQueryBuilder query)
    throws SQLException
  {
    query.setGroup(true);
    int token;

    ArrayList<Expr> groupList = new ArrayList<Expr>();

    do {
      groupList.add(parseExpr());
    } while ((token = scanToken()) == ',');

    _token = token;

    return groupList;
  }
  */

  /**
   * Parses the GROUP BY
   */
  /*
  private void bindGroup(SelectQueryBuilder query, 
                         ArrayList<Expr> groupList)
    throws SQLException
  {
    query.setGroup(true);

    Expr []resultList = query.getResults();

    for (int i = 0; i < groupList.size(); i++) {
      Expr expr = groupList.get(i);

      expr = expr.bind(query);

      int index;
      for (index = 0; index < resultList.length; index++) {
        Expr resultExpr = resultList[index];

        if (expr.equals(resultExpr)) {
          resultList[index] = new GroupResultExpr(index, resultExpr);

          break;
        }
      }

      if (resultList.length <= index) {
        throw error(L.l("GROUP BY field '{0}' must refer to a result field.",
                        expr));
      }

      query.setGroupResult(index);
    }
  }
  */

  /**
   * Parses the LIMIT
   */
  /*
  private void parseLimit(SelectQueryBuilder query)
    throws SQLException
  {
    int token = scanToken();

    if (token == INTEGER) {
      query.setLimit(Integer.valueOf(_lexeme));
      _token = scanToken();
    }
    else
      throw error(L.l("LIMIT expected LIMIT int"));
  }
  */

  /**
   * Parses the create.
   */
  private QueryBuilderKraken parseCreate()
  {
    Token token;

    // TableBuilderKraken factory = null;// = _database.createTableFactory();

    if ((token = scanToken()) != Token.TABLE)
      throw error("expected TABLE at '{0}'", token);

    if ((token = scanToken()) != Token.IDENTIFIER)
      throw error("expected identifier at '{0}'", token);
    
    String name = _lexeme;
    String pod = null;
    
    while (peekToken() == Token.DOT) {
      scanToken();
      
      if ((token = scanToken()) != Token.IDENTIFIER) {
        throw error("expected identifier at '{0}'", token);
      }
    
      if (pod == null) {
        pod = name;
      }
      else {
        pod = pod + '.' + name;
      }
      
      name = _lexeme;
    }
    
    if (pod == null) {
      pod = getPodName();
    }

    TableBuilderKraken factory = new TableBuilderKraken(pod, name, _sql);
    // factory.startTable(_lexeme);

    if ((token = scanToken()) != Token.LPAREN) {
      throw error("expected '(' at '{0}'", token);
    }

    do {
      token = scanToken();

      switch (token) {
      case IDENTIFIER:
        parseCreateColumn(factory, _lexeme);
        break;

        /*
      case UNIQUE:
        token = scanToken();
        
        if (token != KEY) {
          _token = token;
        }
        
        factory.addUnique(parseColumnNames());
        break;
        */

      case PRIMARY:
        token = scanToken();
        if (token != Token.KEY)
          throw error("expected 'key' at {0}", token);

        factory.addPrimaryKey(parseColumnNames());
        break;

      case KEY:
        //String key = parseIdentifier();

        factory.addPrimaryKey(parseColumnNames()); // factory.addPrimaryKey(parseColumnNames());
        break;

        /*
      case CHECK:
        if ((token = scanToken()) != '(')
          throw error(L.l("Expected '(' at '{0}'", tokenName(token)));

        parseExpr();

        if ((token = scanToken()) != ')')
          throw error(L.l("Expected ')' at '{0}'", tokenName(token)));
        break;
        */

      default:
        throw error("unexpected token '{0}'", token);
      }

      token = scanToken();
    } while (token == Token.COMMA);

    if (token != Token.RPAREN) {
      throw error("expected ')' at '{0}'", token);
    }
    
    token = scanToken();
    
    HashMap<String,String> propMap = new HashMap<>();
    
    if (token == Token.WITH) {
      do {
        String key = parseIdentifier();
        ExprKraken expr = parseExpr();

        if (! (expr instanceof LiteralExpr)) {
          throw error("WITH expression must be a literal at '{0}'", expr);
        }
        
        String value = expr.evalString(null);
        
        propMap.put(key, value);
      } while ((token = scanToken()) == Token.COMMA);
    }
    
    if (token != Token.EOF) {
      throw error("Expected end of file at '{0}'", token);
    }

    return new CreateQueryBuilder(_tableManager, factory, _sql, propMap);
  }
  
  public String getPodName()
  {
    if (_podName != null) {
      return _podName;
    }
    else {
      return getCurrentPodName();
    }
  }
  
  public static String getCurrentPodName()
  {
    // XXX:
    /*
    String podName = BartenderSystem.getCurrentPod().name();
    String clusterId = BartenderSystem.getCurrentSelfServer().getClusterId();
    
    podName = podName.replace('-', '_');
    podName = podName.replace('.', '_');
    
    if (podName.equals(clusterId)) {
      podName = podName + "_hub";
    }
    
    return podName;
    */
    return "pod";
  }
  
  public static String getPodName(String tableName)
  {
    int p = tableName.indexOf('.');
    
    if (p > 0) {
      return tableName.substring(0, p);
    }
    
    return getCurrentPodName();
  }

  /**
   * Parses a column declaration.
   */
  private void parseCreateColumn(TableBuilderKraken factory, String name)
  {
    Token token;

    if ((token = scanToken()) != Token.IDENTIFIER) {
      throw error(L.l("expected column type at {0}", token));
    }

    String type = _lexeme;
    int length = -1;
    int scale = -1;

    if (type.equalsIgnoreCase("double")) {
      if ((token = peekToken()) == Token.IDENTIFIER) {
        scanToken();
        
        if (_lexeme.equalsIgnoreCase("precision")) {
        }
        else
          throw error(L.l("unexpected double type at {0}", _lexeme));
      }
    }

    if ((token = peekToken()) == Token.LPAREN) {
      scanToken();
      
      if ((token = scanToken()) != Token.INTEGER) {
        throw error("expected column width at '{0}'", token);
      }

      length = Integer.parseInt(_lexeme);

      if ((token = scanToken()) == Token.COMMA) {
        if ((token = scanToken()) != Token.INTEGER) {
          throw error("expected column scale at '{0}'", token);
        }

        scale = Integer.parseInt(_lexeme);

        token = scanToken();
      }

      if (token != Token.RPAREN) {
        throw error("expected ')' at '{0}'", token);
      }
    }

    if (type.equalsIgnoreCase("varchar")) {
      /*
      if (length < 0) {
        throw error(L.l("VARCHAR needs a defined length"));
      }
      */

      // factory.addVarchar(name, length);
      factory.addVarchar(name, length);
    }
    else if (type.equalsIgnoreCase("blob")) {
      factory.addBlob(name);
    }
    else if (type.equalsIgnoreCase("string")) {
      factory.addString(name);
    }
    else if (type.equalsIgnoreCase("object")) {
      factory.addObject(name);
    }
    else if (type.equalsIgnoreCase("char")) {
      length = Math.max(length, 1);

      factory.addVarchar(name, length);
    }
    else if (type.equalsIgnoreCase("varbinary")) {
      if (length < 0) {
        throw error("VARBINARY needs a defined length");
      }

      factory.addVarbinary(name, length);
    }
    else if (type.equalsIgnoreCase("binary")
        || type.equalsIgnoreCase("bytes")) {
      if (length < 0)
        throw error(L.l("BINARY needs a defined length"));

      factory.addBytes(name, length);
    }
    /*
    else if (type.equalsIgnoreCase("tinytext")) {
      factory.addTinytext(name);
    }
    */
    else if (type.equalsIgnoreCase("mediumtext")) {
      factory.addVarchar(name, 256);
    }
    else if (type.equalsIgnoreCase("longtext")) {
      factory.addVarchar(name, 512);
    }
    else if (type.equalsIgnoreCase("bit")
             || type.equalsIgnoreCase("bool")) {
      factory.addBool(name);
    }
    else if (type.equalsIgnoreCase("tinyint")
        || type.equalsIgnoreCase("bit")
        || type.equalsIgnoreCase("int8")) {
      factory.addInt8(name);
    }
    else if (type.equalsIgnoreCase("smallint")
             || type.equalsIgnoreCase("int16")) {
      factory.addInt16(name);
    }
    else if (type.equalsIgnoreCase("integer")
             || type.equalsIgnoreCase("int")
             || type.equalsIgnoreCase("mediumint")
             || type.equalsIgnoreCase("int32")) {
      factory.addInt32(name);
    }
    else if (type.equalsIgnoreCase("bigint")
             || type.equalsIgnoreCase("long")
             || type.equalsIgnoreCase("int64")) {
      factory.addInt64(name);
    }
    else if (type.equalsIgnoreCase("double")
            || type.equalsIgnoreCase("float64")
            || type.equalsIgnoreCase("real")) {
      factory.addDouble(name);
    }
    else if (type.equalsIgnoreCase("float")
             || type.equalsIgnoreCase("float32")) {
      factory.addFloat(name);
    }
    else if (type.equalsIgnoreCase("datetime")
             || type.equalsIgnoreCase("timestamp")) {
      factory.addDateTime(name);
    }
    else if (type.equalsIgnoreCase("text")
             || type.equalsIgnoreCase("clob")) {
      factory.addVarchar(name, 255);
    }
    else if (type.equalsIgnoreCase("identity")) {
      factory.addIdentity(name);
    }
    else
      throw error(L.l("Unknown type {0}", type));

    /*
    token = scanToken();
    if (token == IDENTIFIER && _lexeme.equalsIgnoreCase("default")) {
      Expr defaultExpr = parseExpr();

      factory.setDefault(name, defaultExpr);
    }
    else {
      _token = token;
    }
    */

    while (true) {
      token = scanToken();

      // XXX: stuff like NOT NULL

      switch (token) {
      case RPAREN:
      case COMMA:
        _token = token;
        return;

        /*
      case UNIQUE:
        factory.setUnique(name);
        break;
        */

      case PRIMARY:
        token = scanToken();
        if (token != Token.KEY)
          throw error("expected key at {0}", token);

        factory.setPrimaryKey(name);
        break;

        /*
      case CHECK:
        if ((token = scanToken()) != '(')
          throw error(L.l("Expected '(' at '{0}'", tokenName(token)));

        parseExpr();

        if ((token = scanToken()) != ')')
          throw error(L.l("Expected ')' at '{0}'", tokenName(token)));
        break;
        */

        /*
      case IDENTIFIER:
        String id = _lexeme;
        if (id.equalsIgnoreCase("references")) {
          ArrayList<String> foreignKey = new ArrayList<String>();
          foreignKey.add(name);
          parseReferences(foreignKey);
        }
        else if (id.equalsIgnoreCase("default")) {
          Expr expr = parseExpr();
        }
        else if (id.equalsIgnoreCase("auto_increment")) {
          factory.setAutoIncrement(name, 1);
        }
        else if (id.equalsIgnoreCase("unsigned")) {
        }
        else if (id.equalsIgnoreCase("binary")) {
        }
        else
          throw error(L.l("unexpected token '{0}'", tokenName(token)));
        break;
        */

      case NULL:
        break;

      case NOT:
        if ((token = scanToken()) == Token.NULL)
          factory.setNotNull(name);
        else
          throw error("unexpected token '{0}'", token);
        break;

      default:
        throw error("unexpected token '{0}'", token);
      }
    }
  }
  
  /**
   * Parses a key constraint declaration.
   */
  /*
  private void parseKeyConstraint(TableFactoryKelp factory)
  {
    String key = parseIdentifier();

    Token token = scanToken();

    if (token == Token.LPAREN) {
      parseIdentifier();

      token = scanToken();
      if (token != Token.RPAREN) {
        throw error("expected ')' at {0}", token);
      }
    }
    else {
      _token = token;
    }
  }
  */

  /**
   * Parses the references clause.
   */
  public void parseReferences(ArrayList<String> name)
  {
    String foreignTable = parseIdentifier();

    Token token = scanToken();

    ArrayList<String> foreignColumns = new ArrayList<String>();

    if (token == Token.LPAREN) {
      _token = token;

      foreignColumns = parseColumnNames();
    }
    else {
      _token = token;
    }
  }

  /**
   * Parses a list of column names
   */
  public ArrayList<String> parseColumnNames()
  {
    ArrayList<String> columns = new ArrayList<String>();

    Token token = scanToken();
    if (token == Token.LPAREN) {
      do {
        columns.add(parseIdentifier());

        token = scanToken();
      } while (token == Token.COMMA);

      if (token != Token.RPAREN) {
        throw error("expected ')' at '{0}'", token);
      }
    }
    else if (token == Token.IDENTIFIER) {
      columns.add(_lexeme);

      _token = token;
    }
    else {
      throw error("expected '(' at '{0}'", token);
    }

    return columns;
  }

  /**
   * Parses the insert.
   */
  private QueryBuilderKraken parseInsert()
  {
    Token token;

    if ((token = scanToken()) != Token.INTO) {
      throw error("expected INTO at '{0}'", token);
    }

    TableKraken table = parseTable();

    Objects.requireNonNull(table);
    
    TableKelp tableKelp = table.getTableKelp();

    ArrayList<Column> columns = new ArrayList<>();
    
    boolean isKeyHash = false;

    if ((token = scanToken()) == Token.LPAREN) {
      do {
        String columnName = parseIdentifier();

        Column column = tableKelp.getColumn(columnName);

        if (column == null) {
          throw error("'{0}' is not a valid column in {1}",
                      columnName, table.getName());
        }
        
        columns.add(column);
      } while ((token = scanToken()) == Token.COMMA);

      if (token != Token.RPAREN) {
        throw error("expected ')' at '{0}'", token);
      }

      token = scanToken();
    }
    else {
      for (Column column : tableKelp.getColumns()) {
        if (column.name().startsWith(":")) {
          continue;
        }
        
        columns.add(column);
      }
    }

    if (token != Token.VALUES)
      throw error("expected VALUES at '{0}'", token);

    if ((token = scanToken()) != Token.LPAREN) {
      throw error("expected '(' at '{0}'", token);
    }

    ArrayList<ExprKraken> values = new ArrayList<>();

    InsertQueryBuilder query;
    
    query = new InsertQueryBuilder(this, _sql, table, columns);
    _query = query;

    do {
      ExprKraken expr = parseExpr();

      //expr = expr.bind(new TempQueryBuilder(table));

      values.add(expr);
    } while ((token = scanToken()) == Token.COMMA);

    if (token != Token.RPAREN) {
      throw error("expected ')' at '{0}'", token);
    }

    if (columns.size() != values.size()) {
      throw error("number of columns does not match number of values");
    }

    ParamExpr []params = _params.toArray(new ParamExpr[_params.size()]);

    query.setParams(params);
    query.setValues(values);
    // query.init();

    return query;
  }

  /**
   * Parses replace.
   */
  private QueryBuilderKraken parseReplace()
  {
    Token token;

    TableKraken table = parseTable();

    Objects.requireNonNull(table);
    
    TableKelp tableKelp = table.getTableKelp();

    ArrayList<Column> columns = new ArrayList<>();
    
    if ((token = scanToken()) == Token.LPAREN) {
      do {
        String columnName = parseIdentifier();

        Column column = tableKelp.getColumn(columnName);

        if (column == null) {
          throw error("'{0}' is not a valid column in {1}",
                      columnName, table.getName());
        }
        
        columns.add(column);
      } while ((token = scanToken()) == Token.COMMA);

      if (token != Token.RPAREN) {
        throw error("expected ')' at '{0}'", token);
      }

      token = scanToken();
    }
    else {
      for (Column column : tableKelp.getColumns()) {
        if (column.name().startsWith(":")) {
          continue;
        }
        
        columns.add(column);
      }
    }

    if (token != Token.VALUES)
      throw error("expected VALUES at '{0}'", token);

    if ((token = scanToken()) != Token.LPAREN) {
      throw error("expected '(' at '{0}'", token);
    }

    ArrayList<ExprKraken> values = new ArrayList<>();

    ReplaceQueryBuilder query;
    
    query = new ReplaceQueryBuilder(_tableManager, _sql, table, columns);
    _query = query;

    do {
      ExprKraken expr = parseExpr();

      //expr = expr.bind(new TempQueryBuilder(table));

      values.add(expr);
    } while ((token = scanToken()) == Token.COMMA);

    if (token != Token.RPAREN) {
      throw error("expected ')' at '{0}'", token);
    }

    if (columns.size() != values.size()) {
      throw error("number of columns does not match number of values");
    }

    ParamExpr []params = _params.toArray(new ParamExpr[_params.size()]);

    query.setParams(params);
    query.setValues(values);
    // query.init();

    return query;
  }

  private TableKraken parseTable()
  {
    String tableName = parseTableName();
    
    TableKraken table = _tableManager.loadTable(tableName);
    
    if (table == null) {
      throw error("unknown table '{0}'", tableName);
    }
  
    return table;
  }
  
  private String parseTableName()
  {
    Token token;
    
    String tableName = null;
    
    while (true) {
      if ((token = scanToken()) != Token.IDENTIFIER) {
        throw error("expected identifier at '{0}'", token);
      }
      
      if (tableName == null) {
        tableName = _lexeme;
      }
      else {
        tableName = tableName + '.' + _lexeme;
      }
      
      if (peekToken() != Token.DOT) {
        return fullTableName(tableName);
      }
      
      scanToken();
    }
  }
  
  private String fullTableName(String tableName)
  {
    if (tableName.indexOf('.') >= 0) {
      return tableName;
    }
    else {
      return getPodName() + '.' + tableName;
    }
  }

  /**
   * Parses the delete.
   */
  private QueryBuilderKraken parseDelete()
  {
    DeleteQueryBuilder query
    = new DeleteQueryBuilder(_tableManager, _sql);
    
    Token token;

    if ((token = scanToken()) != Token.FROM) {
      throw error("expected FROM at '{0}'", token);
    }
    
    String tableName = parseTableName();

    query.setTableName(tableName);

    /*
    Table table = _database.getTable(_lexeme);

    if (table == null)
      throw error("unknown table '{0}'", token);

    DeleteQueryBuilder query = new DeleteQueryBuilder(_database, _sql, table);
    */
    
    _query = query;

    ExprKraken whereExpr = null;

    token = scanToken();
    
    if (token == Token.WHERE) { 
      whereExpr = parseExpr();
    }
    else if (token != null) {
      throw error("expected WHERE at '{0}'", token);
    }

    ParamExpr []params = _params.toArray(new ParamExpr[_params.size()]);

    query.setParams(params);
    query.setWhereExpr(whereExpr);

    return query;
  }

  /**
   * watch: adding and removing listeners.
   */
  private QueryBuilderKraken parseWatch()
  {
    WatchQueryBuilder query
      = new WatchQueryBuilder(_tableManager, _sql);
    
    Token token;
    
    String tableName = parseTableName();

    query.setTableName(tableName);
    
    _query = query;

    ExprKraken whereExpr = null;

    token = scanToken();
    
    if (token == Token.WHERE) {
      whereExpr = parseExpr();
    }
    else if (token != null) {
      throw error("expected WHERE at '{0}'", token);
    }

    ParamExpr []params = _params.toArray(new ParamExpr[_params.size()]);

    query.setParams(params);
    query.setWhereExpr(whereExpr);

    return query;
  }

  /**
   * notify: signals to watch without updating data
   */
  private QueryBuilderKraken parseNotify()
  {
    NotifyQueryBuilder query
      = new NotifyQueryBuilder(_tableManager, _sql);
    
    Token token;
    
    String tableName = parseTableName();

    query.setTableName(tableName);
    
    _query = query;

    ExprKraken whereExpr = null;

    token = scanToken();
    
    if (token == Token.WHERE) {
      whereExpr = parseExpr();
    }
    else if (token != null) {
      throw error("expected WHERE at '{0}'", token);
    }

    ParamExpr []params = _params.toArray(new ParamExpr[_params.size()]);

    query.setParams(params);
    query.setWhereExpr(whereExpr);

    return query;
  }

  /**
   * Parses the delete.
   */
  /*
  private QueryBuilder parseValidate()
  {
    int token;

    if ((token = scanToken()) != IDENTIFIER)
      throw error(L.l("expected identifier at '{0}'", tokenName(token)));

    Table table = _database.getTable(_lexeme);

    if (table == null)
      throw error(L.l("unknown table '{0}'", tokenName(token)));

    ValidateQueryBuilder query
      = new ValidateQueryBuilder(_database, _sql, table);

    return query;
  }
  */

  /**
   * Parses the insert.
   */
  /*
  private QueryBuilder parseDrop()
  {
    int token;

    if ((token = scanToken()) != TABLE)
      throw error(L.l("expected TABLE at `{0}'", tokenName(token)));

    if ((token = scanToken()) != IDENTIFIER)
      throw error(L.l("expected identifier at `{0}'", tokenName(token)));

    String table = _lexeme;

    if ((token = scanToken()) >= 0)
      throw error(L.l("expected end of query at `{0}'", tokenName(token)));

    return new DropQueryBuilder(_sql, _database, table);
  }
  */

  /**
   * Parses the update.
   * 
   * UPDATE table_name SET a=?,b=? WHERE expr
   */
  private QueryBuilderKraken parseUpdate()
  {
    Token token;
    
    UpdateQueryBuilder query = new UpdateQueryBuilder(_tableManager, _sql);
    
    String tableName = parseTableName();

    query.setTableName(tableName);

    _query = query;

    if ((token = scanToken()) != Token.SET) {
      throw error(L.l("expected SET at {0}", token));
    }

    do {
      parseSetItem(query);
    } while ((token = scanToken()) == Token.COMMA);
    
    _token = token;

    //query.setUpdateBuilder(new SetUpdateBuilder(setItems));
    
    ExprKraken whereExpr = null;

    token = scanToken();
    
    if (token == Token.WHERE) {
      whereExpr = parseExpr();
    }
    else if (token != null && token != Token.EOF) {
      throw error("expected WHERE at '{0}'", token);
    }

    ParamExpr []params = _params.toArray(new ParamExpr[_params.size()]);

    query.setParams(params);
    query.setWhereExpr(whereExpr);

    return query;
  }
  
  /*
  private Query parseShow()
  {
    int token;

    if ((token = scanToken()) != IDENTIFIER)
      throw error(L.l("expected identifier at `{0}'", tokenName(token)));

    String name = _lexeme;
    
    if (name.equalsIgnoreCase("tables"))
      return new ShowTablesQuery();
    else if (name.equalsIgnoreCase("databases"))
      return new ShowDatabasesQuery();
    else
      throw error(L.l("`{0}' is an unknown type in SHOW.", name));
  }
  */

  /**
   * Parses a set item.
   */
  private void parseSetItem(UpdateQueryBuilder query)
  {
    Token token;

    if ((token = scanToken()) != Token.IDENTIFIER) {
      throw error(L.l("expected identifier at '{0}'", token));
    }

    String columnName = _lexeme;

    if ((token = scanToken()) != Token.EQ) {
      throw error("expected '=' at {0}", token);
    }

    ExprKraken expr = parseExpr();

    query.addItem(columnName, expr);
  }

  /**
   * Parses an expression.
   */

  private ExprKraken parseExpr()
  {
    return parseOrExpr();
  }

  /**
   * Parses a +/- expression.
   */
  private ExprKraken parseOrExpr()
  {
    ExprKraken left = parseAndExpr();

    while (true) {
      Token token = scanToken();

      switch (token) {
      case OR:
        left = new BinaryExpr(BinaryOp.OR, left, parseAndExpr());
        break;

      default:
        _token = token;
        return left;
      }
    }
  }

  /**
   * Parses an AND expression.
   */
  private ExprKraken parseAndExpr()
  {
    // AndExpr oldAndExpr = _andExpr;
    AndExpr andExpr = new AndExpr();
    // _andExpr = andExpr;

    andExpr.add(parseNotExpr());

    while (true) {
      Token token = scanToken();

      switch (token) {
      case AND:
        andExpr.add(parseNotExpr());
        break;

      default:
        _token = token;

        // _andExpr = oldAndExpr;

        return andExpr.getSingleExpr();
      }
    }
  }

  private ExprKraken parseNotExpr()
  {
    return parseCmpExpr();
  }

  /**
   * Parses a term.
   */
  /*
  private Expr parseNotExpr()
  {
    int token = scanToken();

    switch (token) {
    case NOT:
      return new NotExpr(parseNotExpr());

    default:
      _token = token;
      return parseCmpExpr();
    }
  }
  */

  /**
   * Parses a CMP expression.
   */

  private ExprKraken parseCmpExpr()
  {
    // Expr left = parseConcatExpr();
    ExprKraken left = parseAddExpr();

    Token token = scanToken();
    
    boolean isNot = false;
    
    /*

    if (token == NOT) {
      isNot = true;

      token = scanToken();

      if (token != BETWEEN && token != LIKE && token != IN) {
        _token = token;

        return left;
      }
    }
    */

    switch (token) {
    case EQ:
      return new BinaryExpr(BinaryOp.EQ, left, parseAddExpr());
      
    case NE:
      return new BinaryExpr(BinaryOp.NE, left, parseAddExpr());
      
    case LT:
      return new BinaryExpr(BinaryOp.LT, left, parseAddExpr());
      
    case LE:
      return new BinaryExpr(BinaryOp.LE, left, parseAddExpr());
      
    case GT:
      return new BinaryExpr(BinaryOp.GT, left, parseAddExpr());
      
    case GE:
      return new BinaryExpr(BinaryOp.GE, left, parseAddExpr());

    case BETWEEN:
      {
        ExprKraken min = parseAddExpr();

        token = scanToken();
        if (token != Token.AND)
          throw error(L.l("expected AND at '{0}'", token));

        ExprKraken max = parseAddExpr();

        return new BetweenExpr(left, min, max, isNot);
      }
/*
    case LT:
    case LE:
    case GT:
    case GE:
    case NE:
      return new CmpExpr(left, parseConcatExpr(), token);

    case BETWEEN:
      {
        Expr min = parseConcatExpr();

        token = scanToken();
        if (token != AND)
          throw error(L.l("expected AND at '{0}'", tokenName(token)));

        Expr max = parseConcatExpr();

        return new BetweenExpr(left, min, max, isNot);
      }

    case IS:
      {
        token = scanToken();
        isNot = false;
        if (token == NOT) {
          token = scanToken();
          isNot = true;
        }

        if (token == NULL)
          return new IsNullExpr(left, isNot);
        else
          throw error(L.l("expected NULL at '{0}'", tokenName(token)));
      }

    case LIKE:
      {
        token = scanToken();

        if (token == STRING)
          return new LikeExpr(left, _lexeme, isNot);
        else
          throw error(L.l("expected string at '{0}'", tokenName(token)));
      }

    case IN:
      {
        HashSet<String> values = parseInValues();

        return new InExpr(left, values, isNot);
      }
*/
    default:
      _token = token;
      return left;
    }
  }

  /**
   * Parses the IN values.
   */
  /*
  private HashSet<String> parseInValues()
  {
    int token = scanToken();

    if (token != '(')
      throw error(L.l("Expected '('"));

    HashSet<String> values = new HashSet<String>();

    while ((token = scanToken()) != ')') {
      if (token == STRING) {
        values.add(_lexeme);
      }
      else
        throw error(L.l("expected STRING at {0}", tokenName(token)));

      if ((token = scanToken()) != ',')
        break;
    }

    if (token != ')')
        throw error(L.l("expected ')' at {0}", tokenName(token)));

    return values;
  }
  */

  /**
   * Parses a concat expression.
   */
  /*
  private Expr parseConcatExpr()
  {
    Expr left = parseAddExpr();

    while (true) {
      int token = scanToken();

      switch (token) {
      case CONCAT:
        left = new ConcatExpr(left, parseAddExpr());
        break;

      default:
        _token = token;
        return left;
      }
    }
  }
  */

  /**
   * Parses a +/- expression.
   */
  private ExprKraken parseAddExpr()
  {
    ExprKraken left = parseMulExpr();

    while (true) {
      Token token = scanToken();

      switch (token) {
      case PLUS:
        left = new BinaryExpr(BinaryOp.ADD, left, parseSimpleTerm());
        break;
      case MINUS:
        left = new BinaryExpr(BinaryOp.SUB, left, parseSimpleTerm());
        break;

      default:
        _token = token;
        return left;
      }
    }
  }

  /**
   * Parses a mul/div expression
   */
  private ExprKraken parseMulExpr()
  {
    ExprKraken left = parseSimpleTerm();

    while (true) {
      Token token = scanToken();

      switch (token) {
      case STAR:
        left = new BinaryExpr(BinaryOp.MUL, left, parseSimpleTerm());
        break;
      case DIV:
        left = new BinaryExpr(BinaryOp.DIV, left, parseSimpleTerm());
        break;
      case MOD:
        left = new BinaryExpr(BinaryOp.MOD, left, parseSimpleTerm());
        break;

      default:
        _token = token;
        return left;
      }
    }
  }

  /**
   * Parses a term.
   */
  /*
  private Expr parseTerm()
  {
    int token = scanToken();

    switch (token) {
    case '+':
      return parseTerm();

    case '-':
      return new UnaryExpr(parseTerm(), token);

    case '(':
      Expr expr = parseExpr();
      int peekToken;
      if ((peekToken = scanToken()) != ')')
        throw error(L.l("expected ')' at {0}", tokenName(peekToken)));
      return expr;

    default:
      _token = token;
      return parseSimpleTerm();
    }
  }
  */

  /**
   * Parses a simple term.
   */

  private ExprKraken parseSimpleTerm()
  {
    Token token = scanToken();

    switch (token) {
    case MINUS:
      return new UnaryExpr(UnaryOp.MINUS, parseSimpleTerm());
      
    case LPAREN:
    {
      ExprKraken expr = parseExpr();
      
      if ((token = scanToken()) != Token.RPAREN) {
        throw error("Expected ')' at '{0}'", token);
      }
      
      return expr;
    }
      
      
    case IDENTIFIER:
      {
        String name = _lexeme;

        if ((token = peekToken()) == Token.DOT) {
          return parsePath(name);
        }
        else if (token == Token.LPAREN) {
          FunExpr fun = null;
          
          /*
          if (name.equalsIgnoreCase("max"))
            fun = new MaxExpr();
          else if (name.equalsIgnoreCase("min"))
            fun = new MinExpr();
          else if (name.equalsIgnoreCase("sum"))
            fun = new SumExpr();
          else if (name.equalsIgnoreCase("avg"))
            fun = new AvgExpr();
          else if (name.equalsIgnoreCase("count")) {
            fun = new CountExpr();

            token = scanToken();
            if (token == '*') {
              fun.addArg(new UnboundStarExpr());
            }
            else
              _token = token;
          }
          else */
          {
            String funName = (Character.toUpperCase(name.charAt(0)) +
                              name.substring(1).toLowerCase(Locale.ENGLISH));

            funName = "com.caucho.v5.kraken.fun." + funName + "Expr";

            try {
              ClassLoader loader = Thread.currentThread().getContextClassLoader();
              Class<?> cl = Class.forName(funName, false, loader);

              fun = (FunExpr) cl.newInstance();
            } catch (ClassNotFoundException e) {
              log.finer(e.toString());
            } catch (Exception e) {
              log.log(Level.FINER, e.toString(), e);
              
              throw error(e.toString());
            }

            if (fun == null) {
              throw error(L.l("'{0}' is an unknown function.", name));
            }
          }

          scanToken();
          token = peekToken();
          while (token != null && token != Token.RPAREN) {
            ExprKraken arg = parseExpr();

            fun.addArg(arg);

            token = peekToken();

            if (token == Token.COMMA) {
              scanToken();
              token = peekToken();
            }
          }

          scanToken();

          return fun;
        }
        else {
          return new IdExprBuilder(name);
        }
      }

    case STRING:
      return new LiteralExpr(_lexeme);

    case DOUBLE:
      return new LiteralExpr(Double.parseDouble(_lexeme));
      
    case INTEGER:
      return new LiteralExpr(Long.parseLong(_lexeme));
      
    case LONG:
      return new LiteralExpr(Long.parseLong(_lexeme));

    case NULL:
      return new NullExpr();

    case TRUE:
      return new LiteralExpr(true);

    case FALSE:
      return new LiteralExpr(false);

    case QUESTION_MARK:
      ParamExpr param = new ParamExpr(_params.size());
      _params.add(param);
      return param;

    default:
      throw error("unexpected term {0}", token);
    }
  }

  private ExprKraken parsePath(String name)
  {
    ExprKraken term = new IdExprBuilder(name);
    Token token;
    
    while (true) {
      switch (peekToken()) {
      case DOT: {
        scanToken();

        if ((token = scanToken()) != Token.IDENTIFIER) {
          throw error("expected IDENTIFIER at {0}", token);
        }
        
        
        term = term.field(_lexeme);
        break;
      }
      
      default:
        return term;
      }
    }
  }


  /**
   * Parses an identifier.
   */
  private String parseIdentifier()
  {
    Token token = scanToken();

    if (token != Token.IDENTIFIER) { 
      throw error("expected identifier at {0}", token);
    }

    return _lexeme;
  }
  
  private Token peekToken()
  {
    Token token = scanToken();
    
    _token = token;
    
    return token;
  }

  /**
   * Scan the next token.  If the lexeme is a string, its string
   * representation is in "lexeme".
   *
   * @return integer code for the token
   */
  private Token scanToken()
  {
    Token token = _token;
    
    if (token != null) {
      _token = null;
      
      return token;
    }

    int sign = 1;
    int ch;

    for (ch = read(); Character.isWhitespace((char) ch); ch = read()) {
    }

    switch (ch) {
    case -1:
      return Token.EOF;
      
    case '(':
      return Token.LPAREN;
    case ')':
      return Token.RPAREN;
    case ',':
      return Token.COMMA;
    case '*':
      return Token.STAR;
    case '-':
      return Token.MINUS;
    case '+':
      return Token.PLUS;
    case '/':
      return Token.DIV;
    case '%':
      return Token.MOD;
    case '.':
      return Token.DOT;
    case '?':
      return Token.QUESTION_MARK;
      /*
    case '/':
    case '%':
    case '?':
      return ch;
      */

      /*
    case '+':
      if ((ch = read()) >= '0' && ch <= '9')
        break;
      else {
        unread(ch);
        return '+';
      }

    case '-':
      if ((ch = read()) >= '0' && ch <= '9') {
        sign = -1;
        break;
      }
      else {
        unread(ch);
        return '-';
      }
      */

    case '=':
      return Token.EQ;

    case '<':
      if ((ch = read()) == '=')
        return Token.LE;
      else if (ch == '>')
        return Token.NE;
      else {
        unread(ch);
        return Token.LT;
      }

    case '>':
      if ((ch = read()) == '=')
        return Token.GE;
      else {
        unread(ch);
        return Token.GT;
      }

      /*
    case '|':
      if ((ch = read()) == '|')
        return CONCAT;
      else {
        throw error(L.l("'|' expected at {0}", charName(ch)));
      }

      // @@ is useless?
    case '@':
      if ((ch = read()) != '@')
        throw error(L.l("`@' expected at {0}", charName(ch)));
      return scanToken();
    */
    }

    if (Character.isJavaIdentifierStart((char) ch) || ch == ':') {
      CharBuffer cb = _cb;
      cb.clear();

      for (; ch > 0 && isIdentifierPart((char) ch); ch = read()) {
        cb.append((char) ch);
      }

      unread(ch);

      _lexeme = cb.toString();
      String lower = _lexeme.toLowerCase(Locale.ENGLISH);

      token = _reserved.get(lower);

      if (token != null)
        return token;
      else
        return Token.IDENTIFIER;
    }
    else if (ch >= '0' && ch <= '9') {
      CharBuffer cb = _cb;
      cb.clear();

      Token type = Token.INTEGER;

      if (sign < 0)
        cb.append('-');

      for (; ch >= '0' && ch <= '9'; ch = read())
        cb.append((char) ch);

      if (ch == '.') {
        type = Token.DOUBLE;

        cb.append('.');
        for (ch = read(); ch >= '0' && ch <= '9'; ch = read())
          cb.append((char) ch);
      }

      if (ch == 'e' || ch == 'E') {
        type = Token.DOUBLE;

        cb.append('e');
        if ((ch = read()) == '+' || ch == '-') {
          cb.append((char) ch);
          ch = read();
        }

        if (! (ch >= '0' && ch <= '9'))
          throw error(L.l("exponent needs digits at {0}",
                          charName(ch)));

        for (; ch >= '0' && ch <= '9'; ch = read())
          cb.append((char) ch);
      }

      if (ch == 'F' || ch == 'D')
        type = Token.DOUBLE;
      else if (ch == 'L') {
        type = Token.LONG;
      }
      else
        unread(ch);

      _lexeme = cb.toString();

      return type;
    }
    else if (ch == '\'') {
      CharBuffer cb = _cb;
      cb.clear();

      for (ch = read(); ch >= 0; ch = read()) {
        if (ch == '\'') {
          if ((ch = read()) == '\'')
            cb.append('\'');
          else {
            unread(ch);
            break;
          }
        }
        else if (ch == '\\') {
          ch = read();

          if (ch >= 0)
            cb.append(ch);
        }
        else
          cb.append((char) ch);
      }

      _lexeme = cb.toString();

      return Token.STRING;
    }
    else if (ch == '#') {
      // skip comment
      while ((ch = read()) >= 0 && ch != '\n' && ch != '\r') {
      }

      // XXX: cleanup to avoid recursion
      return scanToken();
    }

    throw error(L.l("unexpected char at {0} ({1})", "" + (char) ch,
                    String.valueOf(ch)));
  }
  
  private boolean isIdentifierPart(char ch)
  {
    return Character.isJavaIdentifierPart(ch) || ch == ':';
  }

  /**
   * Returns the next character.
   */
  private int read()
  {
    if (_parseIndex < _sqlLength)
      return _sqlChars[_parseIndex++];
    else
      return -1;
  }

  /**
   * Unread the last character.
   */
  private void unread(int ch)
  {
    if (ch >= 0)
      _parseIndex--;
  }

  /**
   * Returns the name for a character
   */
  private String charName(int ch)
  {
    if (ch < 0)
      return L.l("end of query");
    else
      return String.valueOf((char) ch);
  }

  private RuntimeException error(String msg, Object ...args)
  {
    return new QueryException(L.l(msg, args) + "\n" + _sql);
  }
  
  enum Token {
    EOF("end of file"),
    
    COMMA(","),
    DOT("."),
    LPAREN("("),
    RPAREN(")"),
    QUESTION_MARK("?"),
    SEMICOLON(";"),
    
    PLUS("+"),
    MINUS("-"),
    STAR("*"),
    DIV("/"),
    MOD("%"),
    
    EQ("="),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    NE("<>"),
    
    IDENTIFIER,
    INTEGER,
    DOUBLE,
    LONG,
    STRING,
    
    AND,
    BETWEEN,
    CREATE,
    DELETE,
    DISTINCT,
    EXPLAIN,
    FALSE,
    FROM,
    INSERT,
    INTO,
    KEY,
    LIMIT,
    MAP,
    NOT,
    NOTIFY,
    NULL,
    OFFSET,
    OR,
    PRIMARY,
    
    REPLACE,
    
    SELECT,
    SELECT_LOCAL,
    SET,
    SHOW,
    
    TABLE,
    TRUE,
    
    UNWATCH,
    UPDATE,
    
    VALUES,
    
    WATCH,
    WHERE,
    WITH,
    ;
    
    private String _displayName;
    
    Token()
    {
      _displayName = name();
    }
    
    Token(String name)
    {
      _displayName = name;
    }
    
    public String toString()
    {
      return _displayName;
    }
  }

  static {
    _reserved = new HashMap<>();
    
    _reserved.put("and", Token.AND);
    
    _reserved.put("between", Token.BETWEEN);
    
    _reserved.put("create", Token.CREATE);
    
    _reserved.put("delete", Token.DELETE);
    _reserved.put("distinct", Token.DISTINCT);
    
    _reserved.put("eq", Token.EQ);
    _reserved.put("ne", Token.NE);
    _reserved.put("lt", Token.LT);
    _reserved.put("le", Token.LE);
    _reserved.put("gt", Token.GT);
    _reserved.put("ge", Token.GE);
    
    _reserved.put("explain", Token.EXPLAIN);
    
    _reserved.put("false", Token.FALSE);
    _reserved.put("from", Token.FROM);
    
    _reserved.put("insert", Token.INSERT);
    _reserved.put("into", Token.INTO);
    
    _reserved.put("key", Token.KEY);
    
    _reserved.put("limit", Token.LIMIT);
    
    _reserved.put("map", Token.MAP);
    _reserved.put("notify", Token.NOTIFY);
    
    _reserved.put("offset", Token.OFFSET);
    _reserved.put("or", Token.OR);
    
    _reserved.put("primary", Token.PRIMARY);
    
    _reserved.put("replace", Token.REPLACE);
    
    _reserved.put("select", Token.SELECT);
    _reserved.put("select_local", Token.SELECT_LOCAL);
    _reserved.put("set", Token.SET);
    _reserved.put("show", Token.SHOW);
    
    _reserved.put("table", Token.TABLE);
    _reserved.put("true", Token.TRUE);
    
    _reserved.put("unwatch", Token.UNWATCH);
    _reserved.put("update", Token.UPDATE);
    
    _reserved.put("values", Token.VALUES);
    
    _reserved.put("watch", Token.WATCH);
    _reserved.put("where", Token.WHERE);
    _reserved.put("with", Token.WITH);
    
    /*
    _reserved = new IntMap();
    _reserved.put("as", AS);
    _reserved.put("from", FROM);
    _reserved.put("in", IN);
    _reserved.put("select", SELECT);
    _reserved.put("distinct", DISTINCT);
    _reserved.put("where", WHERE);
    _reserved.put("order", ORDER);
    _reserved.put("group", GROUP);
    _reserved.put("by", BY);
    _reserved.put("asc", ASC);
    _reserved.put("desc", DESC);
    _reserved.put("limit", LIMIT);
    _reserved.put("offset", OFFSET);

    _reserved.put("or", OR);
    _reserved.put("and", AND);
    _reserved.put("not", NOT);

    _reserved.put("between", BETWEEN);
    _reserved.put("like", LIKE);
    _reserved.put("escape", ESCAPE);
    _reserved.put("is", IS);

    _reserved.put("true", TRUE);
    _reserved.put("false", FALSE);
    _reserved.put("unknown", UNKNOWN);
    _reserved.put("null", NULL);

    _reserved.put("create", CREATE);
    _reserved.put("table", TABLE);
    _reserved.put("insert", INSERT);
    _reserved.put("into", INTO);
    _reserved.put("values", VALUES);
    _reserved.put("drop", DROP);
    _reserved.put("update", UPDATE);
    _reserved.put("set", SET);
    _reserved.put("delete", DELETE);
    _reserved.put("validate", VALIDATE);

    _reserved.put("constraint", CONSTRAINT);
    _reserved.put("unique", UNIQUE);
    _reserved.put("check", CHECK);
    _reserved.put("primary", PRIMARY);
    _reserved.put("key", KEY);
    _reserved.put("foreign", FOREIGN);
    */
  }
}
