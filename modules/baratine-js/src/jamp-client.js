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
