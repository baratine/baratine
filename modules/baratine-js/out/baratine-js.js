var Jamp = {};

Jamp.BaratineClient = function (url, rpc)
{
  this.client = new Jamp.Client(url, rpc);

  this.onSession;

  var _this = this;

  this.client.onOpen = function() {
    if (_this.onSession !== undefined)
      _this.onSession();
  };

  return this;
};

Jamp.BaratineClient.prototype.send = function (service, method, args, headers)
{
  this.client.send(service, method, args, headers);
};

Jamp.BaratineClient.prototype.query = function (service,
                                                method,
                                                args,
                                                callback,
                                                headers)
{
  this.client.query(service, method, args, callback, headers);
};

Jamp.BaratineClient.prototype.lookup = function (path)
{
  var target = new Jamp.BaratineClientProxy(this, path);

  try {
    if (Proxy !== undefined)
      try {
        target = Proxy.create(handlerMaker(target));
      } catch (err) {
        console.log(err);
      }
  } catch (err) {
  }

  return target;
};

Jamp.BaratineClient.prototype.createListener = function ()
{
  return new Jamp.ServiceListener();
};

Jamp.BaratineClient.prototype.close = function () {
  this.client.close();
};

Jamp.BaratineClient.prototype.toString = function ()
{
  return "BaratineClient[" + this.client + "]";
};

Jamp.BaratineClientProxy = function (client, path)
{
  this.client = client;
  this.path = path;

  return this;
};

Jamp.BaratineClientProxy.prototype.$send = function (method, args, headers)
{
  this.client.send(this.path, method, args, headers);
};

Jamp.BaratineClientProxy.prototype.$query = function (method,
                                                      args,
                                                      callback,
                                                      headers)
{
  this.client.query(this.path, method, args, callback, headers);
};

Jamp.BaratineClientProxy.prototype.$lookup = function (path)
{
  var target = new Jamp.BaratineClientProxy(this.client, this.path + path);

  try {
    if (Proxy !== undefined)
      try {
        target = Proxy.create(handlerMaker(target));
      } catch (err) {
        console.log(err);
      }
  } catch (err) {
  }

  return target;
};

Jamp.BaratineClientProxy.prototype.toString = function ()
{
  return "Jamp.BaratineClientProxy[" + this.path + "]";
};

Jamp.ServiceListener = function ()
{
  this.___isListener = true;

  return this;
};
Jamp.unserialize = function (json)
{
  var array = JSON.parse(json);

  return Jamp.unserializeArray(array);
};

Jamp.unserializeArray = function (array)
{
  var type = array[0];

  switch (type) {
  case "reply":
    if (array.length < 5) {
      throw new Error("incomplete message for JAMP type: " + type);
    }

    var headers = array[1];
    var fromAddress = array[2];
    var queryId = array[3];
    var result = array[4];

    var msg = new Jamp.ReplyMessage(headers, fromAddress, queryId, result);

    return msg;

  case "error":
    if (array.length < 5) {
      throw new Error("incomplete message for JAMP type: " + type);
    }

    var headers = array[1];
    var toAddress = array[2];
    var queryId = array[3];
    var result = array[4];

    if (array.length > 5) {
      var resultArray = new Array();

      for (var i = 4; i < array.length; i++) {
        resultArray.push(array[i]);
      }

      result = resultArray;
    }

    var msg = new Jamp.ErrorMessage(headers, toAddress, queryId, result);

    return msg;

  case "query":
    if (array.length < 6) {
      throw new Error("incomplete message for JAMP type: " + type);
    }

    var headers = array[1];
    var fromAddress = array[2];
    var queryId = array[3];
    var toAddress = array[4];
    var methodName = array[5];

    var args = null;

    if (array.length > 6) {
      args = new Array();

      for (var i = 6; i < array.length; i++) {
        args.push(array[i]);
      }
    }

    var msg = new Jamp.QueryMessage(headers,
                                    fromAddress,
                                    queryId,
                                    toAddress,
                                    methodName,
                                    args);

    return msg;

  case "send":
    if (array.length < 4) {
      throw new Error("incomplete message for JAMP type: " + type);
    }

    var headers = array[1];
    var toAddress = array[2];
    var methodName = array[3];

    var parameters = null;

    if (array.length > 4) {
      parameters = new Array();

      for (var i = 4; i < array.length; i++) {
        parameters.push(array[i]);
      }
    }

    var msg = new Jamp.SendMessage(headers,
                                   toAddress,
                                   methodName,
                                   parameters);

    return msg;

  default:
    throw new Error("unknown JAMP type: " + type);
  }
};

Jamp.Message = function (headers)
{
  if (headers == null) {
    headers = {};
  }

  this.headers = headers;

};

Jamp.Message.prototype.serialize = function ()
{
  var array = new Array();

  this.serializeImpl(array);

  var json = JSON.stringify(array);

  return json;
};

Jamp.Message.prototype.serializeImpl;

Jamp.SendMessage = function (headers, address, method, parameters)
{
  Jamp.Message.call(this, headers);

  this.address = address;
  this.method = method;

  this.parameters = parameters;
};

Jamp.SendMessage.prototype = Object.create(Jamp.Message.prototype);

Jamp.SendMessage.prototype.serializeImpl = function (array)
{
  array.push("send");
  array.push(this.headers);
  array.push(this.address);
  array.push(this.method);

  if (this.parameters != null) {
    for (var i = 0; i < this.parameters.length; i++) {
      array.push(this.parameters[i]);
    }
  }
};

Jamp.QueryMessage = function (headers,
                              fromAddress,
                              queryId,
                              address,
                              method,
                              args)
{
  Jamp.Message.call(this, headers);

  this.fromAddress = fromAddress
  this.queryId = queryId;

  this.address = address;
  this.method = method;

  this.args;

  this.listenerAddresses;
  this.listeners;

  if (args !== undefined) {
    this.args = new Array();

    for (var i = 0; i < args.length; i++) {
      var arg = args[i];
      if (arg["___isListener"]) {
        this.args.push(this.addListener(arg, queryId));
      } else {
        this.args.push(arg);
      }
    }
  }

  if (fromAddress == null) {
    this.fromAddress = "me";
  }
};

Jamp.QueryMessage.prototype = Object.create(Jamp.Message.prototype);

Jamp.QueryMessage.prototype.serializeImpl = function (array)
{
  array.push("query");
  array.push(this.headers);
  array.push(this.fromAddress);
  array.push(this.queryId);
  array.push(this.address);
  array.push(this.method);

  if (this.args !== undefined) {
    for (var i = 0; i < this.args.length; i++) {
      array.push(this.args[i]);
    }
  }
};

Jamp.QueryMessage.prototype.addListener = function (listener, queryId)
{
  if (this.listeners === undefined) {
    this.listeners = new Array();
    this.listenerAddresses = new Array();
  }

  this.listeners.push(listener);

  var callbackAddress = "/callback-" + queryId;
  this.listenerAddresses.push(callbackAddress);

  return callbackAddress;
};

Jamp.ReplyMessage = function (headers, fromAddress, queryId, result)
{
  Jamp.Message.call(this, headers);

  this.fromAddress = fromAddress;
  this.queryId = queryId;

  this.result = result;
};

Jamp.ReplyMessage.prototype = Object.create(Jamp.Message.prototype);

Jamp.ReplyMessage.prototype.serializeImpl = function (array)
{
  array.push("reply");
  array.push(this.headers);
  array.push(this.fromAddress);
  array.push(this.queryId);
  array.push(this.result);
};

Jamp.ErrorMessage = function (headers, toAddress, queryId, result)
{
  Jamp.Message.call(this, headers);

  this.address = toAddress;
  this.queryId = queryId;

  this.result = result;
};

Jamp.ErrorMessage.prototype = Object.create(Jamp.Message.prototype);

Jamp.ErrorMessage.prototype.serializeImpl = function (array)
{
  array.push("error");
  array.push(this.headers);
  array.push(this.address);
  array.push(this.queryId);
  array.push(this.result);
};

function handlerMaker(obj)
{
  return {
    getOwnPropertyDescriptor: function (name)
    {
      var desc = Object.getOwnPropertyDescriptor(obj, name);
      if (desc !== undefined) {
        desc.configurable = true;
      }
      return desc;
    },
    getPropertyDescriptor: function (name)
    {
      var desc = Object.getPropertyDescriptor(obj, name);

      if (desc !== undefined) {
        desc.configurable = true;
      }
      return desc;
    },
    getOwnPropertyNames: function ()
    {
      return Object.getOwnPropertyNames(obj);
    },
    getPropertyNames: function ()
    {
      return Object.getPropertyNames(obj);
    },
    defineProperty: function (name, desc)
    {
      Object.defineProperty(obj, name, desc);
    },
    delete: function (name)
    {
      return delete obj[name];
    },
    fix: function ()
    {
      if (Object.isFrozen(obj)) {
        var result = {};
        Object.getOwnPropertyNames(obj).forEach(function (name)
                                                {
                                                  result[name]
                                                    = Object.getOwnPropertyDescriptor(obj,
                                                                                      name);
                                                });
        return result;
      }
      return undefined;
    },

    has: function (name)
    {
      return name in obj;
    },
    hasOwn: function (name)
    {
      return ({}).hasOwnProperty.call(obj, name);
    },
    get: function (receiver, name)
    {
      try {
        if (obj[name] !== undefined)
          return obj[name];
      } catch (err) {
        console.log("get (" + name + "): " + err);
      }

      return function ()
      {
        var args = new Array();

        var method = name;
        var callback;
        var headers;

        for (var i = 0; i < arguments.length; i++) {
          var arg = arguments[i];
          if ((typeof arg) === "function" && callback === undefined) {
            callback = arg;
          }
          else if ((typeof arg) === "function") {
            throw "function expected at " + arg;
          }
          else if (callback !== undefined) {
            headers = arg;

            break;
          }
          else {
            args.push(arg);
          }
        }

        if (callback === undefined) {
          callback = function(data) {
            console.log(data);
          };
        };

        receiver.$query(method, args, callback, headers);
      };
    },
    set: function (receiver, name, val)
    {
      obj[name] = val;
      return true;
    }, // bad behavior when set fails in non-strict mode
    enumerate: function ()
    {
      var result = [];
      for (var name in obj) {
        result.push(name);
      }
      ;
      return result;
    },
    keys: function ()
    {
      return Object.keys(obj);
    }
  };
};

Jamp.Client = function (url, rpc)
{
  this.transport;

  url = url.trim();

  if (true === rpc) {
    this.transport = new Jamp.HttpRpcTransport(url, this);
  }
  else if (url.indexOf("http:") == 0 || url.indexOf("https:") == 0) {
    this.transport = new Jamp.HttpTransport(url, this);
  }
  else if (url.indexOf("ws:") == 0 || url.indexOf("wss:") == 0) {
    this.transport = new Jamp.WsTransport(url, this);
  }
  else {
    throw "Invalid url: " + url;
  }

  this.requestMap = {};
  this.listenerMap = {};
  this.queryCount = 0;
};

Jamp.Client.prototype.onMessage = function (msg)
{
  if (msg instanceof Jamp.ReplyMessage) {
    var queryId = msg.queryId;
    var request = this.removeRequest(queryId);

    if (request != null) {
      request.completed(this, msg.result);
    }
    else {
      console.log("cannot find request for query id: " + queryId);
    }
  }
  else if (msg instanceof Jamp.ErrorMessage) {
    var queryId = msg.queryId;
    var request = this.removeRequest(queryId);

    if (request != null) {
      request.error(this, msg.result);
    }
    else {
      console.log("cannot find request for query id: " + queryId);
    }
  }
  else if (msg instanceof Jamp.SendMessage) {
    var listener = this.getListener(msg.address);
    listener[msg.method].apply(listener, msg.parameters);
  }
  else {
    throw new Error("unexpected jamp message type: " + msg);
  }
};

Jamp.Client.prototype.onMessageArray = function (list)
{
  for (var i = 0; i < list.length; i++) {
    var msg = Jamp.unserializeArray(list[i]);
    this.onMessage(msg);
  }
};

Jamp.Client.prototype.expireRequests = function ()
{
  var expiredRequests = new Array();

  for (var queryId in this.requestMap) {
    var request = this.requestMap[queryId];

    expiredRequests.push(request);
  }

  for (var i = 0; i < expiredRequests.length; i++) {
    var request = expiredRequests[i];

    this.removeRequest(request.queryId);

    request.error(this, "request expired");
  }
};

Jamp.Client.prototype.removeRequest = function (queryId)
{
  var request = this.requestMap[queryId];

  delete this.requestMap[queryId];

  return request;
};

Jamp.Client.prototype.close = function ()
{
  this.transport.close();
};

Jamp.Client.prototype.reconnect = function ()
{
  this.transport.reconnect();
};

Jamp.Client.prototype.submitRequest = function (request)
{
  this.transport.submitRequest(request);
};

Jamp.Client.prototype.onMessageJson = function (json, client)
{
  var msg = Jamp.unserialize(json);

  client.onMessage(msg);
};

Jamp.Client.prototype.getListener = function (listenerAddress)
{
  return this.listenerMap[listenerAddress];
}

Jamp.Client.prototype.send = function (service,
                                       method,
                                       args,
                                       headerMap)
{
  var queryId = this.queryCount++;

  var msg = new Jamp.SendMessage(headerMap, service, method, args);

  var request = this.createSendRequest(queryId, msg);

  this.submitRequest(request);
};

Jamp.Client.prototype.query = function (service,
                                        method,
                                        args,
                                        callback,
                                        headerMap)
{
  var queryId = this.queryCount++;

  var msg = new Jamp.QueryMessage(headerMap,
                                  "/client",
                                  queryId,
                                  service,
                                  method,
                                  args);

  if (msg.listeners !== undefined) {
    for (var i = 0; i < msg.listeners.length; i++) {
      var address = msg.listenerAddresses[i];
      var listener = msg.listeners[i];
      this.listenerMap[address] = listener;
    }
  }

  var request = this.createQueryRequest(queryId, msg, callback);

  this.submitRequest(request);
};

Jamp.Client.prototype.onfail = function (error)
{
  console.log("error: " + JSON.stringify(error));
};

Jamp.Client.prototype.createQueryRequest = function (queryId, msg, callback)
{
  var request = new Jamp.QueryRequest(queryId, msg, callback);

  this.requestMap[queryId] = request;

  return request;
};

Jamp.Client.prototype.createSendRequest = function (queryId, msg)
{
  var request = new Jamp.SendRequest(queryId, msg);

  this.requestMap[queryId] = request;

  return request;
};

Jamp.Client.prototype.toString = function ()
{
  return "Client[" + this.transport + "]";
};

Jamp.Request = function (queryId, msg, timeout)
{
  this.queryId = queryId;
  this.msg = msg;

  this.expirationTime = timeout;

  if (timeout == null) {
    this.expirationTime = new Date(new Date().getTime() + 1000 * 60 * 5);
  }

  this.isExpired = function (now)
  {
    if (now == null) {
      now = new Date();
    }

    return (now.getTime() - this.expirationTime.getTime()) > 0;
  };

  this.sent = function (client)
  {
  };

  this.completed = function (client, value)
  {
    client.removeRequest(this.queryId);
  };

  this.error = function (client, err)
  {
    console.log(err);

    client.removeRequest(this.queryId);
  };
};

Jamp.SendRequest = function (queryId, msg, timeout)
{
  Jamp.Request.call(this, queryId, msg, timeout);

  this.sent = function (client)
  {
    client.removeRequest(this.queryId);
  };
};

Jamp.QueryRequest = function (queryId, msg, callback, timeout)
{
  Jamp.Request.call(this, queryId, msg, timeout);

  this.callback = callback;

  this.completed = function (client, value)
  {
    client.removeRequest(this.queryId);

    if (this.callback !== undefined) {
      callback(value);
    }
  };

  this.error = function (client, value)
  {
    client.removeRequest(this.queryId);

    if (this.callback !== undefined && this.callback.onfail !== undefined) {
      callback.onfail(value);
    }
    else {
      console.log(value);
    }
  };
};
Jamp.HttpTransport = function (url, client)
{
  this.url = url;
  this.client = client;
  this.isClosed = false;

  return this;
};

Jamp.HttpTransport.prototype.submitRequest = function (request)
{
  if (this.isClosed)
    throw this.toString() + " was already closed.";

  var httpRequest;

  httpRequest = this.initPushRequest();

  var msg = request.msg;

  var json = msg.serialize();
  json = "[" + json + "]";

  var client = this.client;
  var transport = this;

  httpRequest.onreadystatechange = function ()
  {
    if (httpRequest.readyState != 4) {
      return;
    }

    if (httpRequest.status == "200") {

      request.sent(client);

      transport.pull(client);
    }
    else {
      request.error(client,
                    "error submitting query "
                    + httpRequest.status
                    + " "
                    + httpRequest.statusText
                    + " : "
                    + httpRequest.responseText);
    }
  };

  httpRequest.send(json);
};

Jamp.HttpTransport.prototype.pull = function (client)
{
  if(this.isClosed)
    return;

  var httpRequest = this.initPullRequest();
  this.pullRequest = httpRequest;

  httpRequest.send("[]");

  var transport = this;

  httpRequest.onreadystatechange = function ()
  {
    if (httpRequest.readyState != 4) {
      return;
    }

    if (httpRequest.status == "200") {
      var json = httpRequest.responseText;

      var list = JSON.parse(json);

      client.onMessageArray(list);

      transport.pull(client);
    }
    else {
      console.log(this,
                  "error submitting query "
                  + httpRequest.status
                  + " "
                  + httpRequest.statusText
                  + " : "
                  + httpRequest.responseText);
    }

    transport.pullRequest = undefined;
  };

  httpRequest.ontimeout = function ()
  {
    if (! transport.isClosed)
      transport.pull(client);
  };
};

Jamp.HttpTransport.prototype.initPushRequest = function ()
{
  var httpRequest = new XMLHttpRequest();
  httpRequest.withCredentials = true;

  httpRequest.open("POST", this.url, true);
  httpRequest.setRequestHeader("Content-Type", "x-application/jamp-push");

  return httpRequest;
};

Jamp.HttpTransport.prototype.initPullRequest = function ()
{
  var httpRequest = new XMLHttpRequest();
  httpRequest.withCredentials = true;

  httpRequest.open("POST", this.url, true);
  httpRequest.setRequestHeader("Content-Type", "x-application/jamp-pull");

  return httpRequest;
};

Jamp.HttpTransport.prototype.close = function ()
{
  this.isClosed = true;

  var pullRequest = this.pullRequest;

  if (pullRequest !== undefined) {
    try {
      pullRequest.abort();
    } catch (err) {
    }
  }
};

Jamp.HttpTransport.prototype.toString = function ()
{
  return "Jamp.HttpTransport[" + this.url + "]";
};
Jamp.WsTransport = function (url, client)
{
  this.client = client;
  this.url = url;

  this.conn = new Jamp.WsConnection(client, this);
  this.conn.init(this.conn);
};

Jamp.WsTransport.prototype.close = function ()
{
  this.conn.close();
};

Jamp.WsTransport.prototype.reconnect = function ()
{
  this.conn.reconnect(this.conn);
};

Jamp.WsTransport.prototype.removeRequest = function (queryId)
{
  var request = this.client.requestMap[queryId];

  delete this.client.requestMap[queryId];

  return request;
};

Jamp.WsTransport.prototype.submitRequest = function (request)
{
  this.conn.addRequest(request);

  this.conn.submitRequestLoop();
};

Jamp.WsTransport.prototype.toString = function ()
{
  return "Jamp.WsTransport[" + this.url + "]";
};

Jamp.WsConnection = function (client,
                              transport,
                              reconnectIntervalMs,
                              isReconnectOnClose,
                              isReconnectOnError)
{
  this.client = client;
  this.transport = transport;

  this.socket;
  this.isClosing = false;
  this.requestQueue = new Array();

  this.initialReconnectInterval = 5000;
  this.maxReconnectInterval = 1000 * 60 * 3;

  this.reconnectIntervalMs = this.initialReconnectInterval;
  this.reconnectDecay = 1.5;
  this.isReconnectOnClose = true;
  this.isReconnectOnError = true;
  this.isOpen = false;

  if (reconnectIntervalMs != null) {
    this.reconnectIntervalMs = reconnectIntervalMs;
  }

  if (isReconnectOnClose != true) {
    this.isReconnectOnClose = false;
  }

  if (isReconnectOnError != true) {
    this.isReconnectOnError = false;
  }
};

Jamp.WsConnection.prototype.init = function (conn)
{
  if (conn.isClosing) {
    return;
  }

  conn.socket = new WebSocket(conn.transport.url, ["jamp"]);

  conn.socket.onopen = function ()
  {
    if (conn.client.onOpen !== undefined)
      conn.client.onOpen();

    conn.isOpen = true;

    conn.reconnectIntervalMs = conn.initialReconnectInterval;

    conn.submitRequestLoop();
  };

  conn.socket.onclose = function ()
  {
    conn.isOpen = false;

    if (conn.isClosing) {
      return;
    }

    conn.reconnect(conn);
  };

  conn.socket.onerror = function ()
  {
    conn.isOpen = false;

    if (conn.isClosing) {
      return;
    }
  };

  this.socket.onmessage = function (event)
  {
    conn.client.onMessageJson(event.data, conn.client);
  }
};

Jamp.WsConnection.prototype.addRequest = function (data)
{
  if (this.isClosing) {
    throw new Error("websocket is closing");
  }

  this.requestQueue.push(data);
};

Jamp.WsConnection.prototype.submitRequestLoop = function ()
{
  if (!this.isOpen)
    return;

  while (this.socket.readyState === WebSocket.OPEN
         && this.requestQueue.length > 0
         && !this.isClosing) {
    var request = this.requestQueue[0];
    var msg = request.msg;

    var json = msg.serialize();

    this.socket.send(json);

    request.sent(this.transport);

    this.requestQueue.splice(0, 1);
  }
};

Jamp.WsConnection.prototype.reconnect = function (conn)
{
  this.close();

  this.isClosing = false;

  console.log("reconnecting in "
              + (this.reconnectIntervalMs / 1000)
              + " seconds");

  setTimeout(conn.init(conn), this.reconnectIntervalMs);

  var interval = this.reconnectIntervalMs * this.reconnectDecay;

  this.reconnectIntervalMs = Math.min(interval, this.maxReconnectInterval);
};

Jamp.WsConnection.prototype.close = function ()
{
  this.isClosing = true;

  try {
    this.socket.close();
  } catch (err) {

  }
};

Jamp.HttpRpcTransport = function (url, client)
{
  this.url = url;
  this.client = client;
  this.isClosed = false;

  return this;
};

Jamp.HttpRpcTransport.prototype.submitRequest = function (request)
{
  if (this.isClosed)
    throw this.toString() + " was already closed.";

  var httpRequest;

  httpRequest = this.initRpcRequest();

  var msg = request.msg;

  var json = msg.serialize();
  json = "[" + json + "]";

  var client = this.client;

  httpRequest.onreadystatechange = function ()
  {
    if (httpRequest.readyState != 4) {
      return;
    }

    if (httpRequest.status == "200") {
      var json = httpRequest.responseText;

      var list = JSON.parse(json);

      request.sent(client);

      client.onMessageArray(list);
    }
    else {
      request.error(client,
                    "error submitting query "
                    + httpRequest.status
                    + " "
                    + httpRequest.statusText
                    + " : "
                    + httpRequest.responseText);
    }
  };

  httpRequest.send(json);
};

Jamp.HttpRpcTransport.prototype.initRpcRequest = function ()
{
  var httpRequest = new XMLHttpRequest();
  httpRequest.withCredentials = true;

  httpRequest.open("POST", this.url, true);
  httpRequest.setRequestHeader("Content-Type", "x-application/jamp-rpc");

  return httpRequest;
};

Jamp.HttpRpcTransport.prototype.close = function ()
{
  this.isClosed = true;
};

Jamp.HttpRpcTransport.prototype.toString = function ()
{
  return "Jamp.HttpRpcTransport[" + this.url + "]";
};
