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

