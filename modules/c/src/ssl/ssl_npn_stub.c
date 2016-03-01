/*
 * Copyright (c) 1998-2014 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

#include <stdio.h>
#ifdef WIN32
#ifndef _WINSOCKAPI_ 
#define _WINSOCKAPI_
#endif 
#include <windows.h>
#include <winsock2.h>
#else
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <sys/time.h>
#endif
#include <stdlib.h>
#include <memory.h>
#include <errno.h>
#include <sys/types.h>

#include <fcntl.h>

#include "../baratine/baratine_os.h"
#include "baratine_ssl.h"

int
caucho_ssl_npn_get_protocol(connection_t *conn, char *buffer, int buflen)
{
  return -1;
}

int
caucho_ssl_npn_register_server(SSL_CTX *ctx, char *protocols)
{
  return 0;
}

int
caucho_ssl_npn_register_client(SSL_CTX *ctx, char *protocols)
{
  return 0;
}
