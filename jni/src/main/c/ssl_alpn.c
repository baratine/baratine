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

/*
 * common
 */

int
caucho_ssl_npn_get_protocol(connection_t *conn, char *buffer, int buflen)
{
  const unsigned char *proto = 0;
  unsigned proto_len = 0;
  int sublen;
  SSL *ssl = conn->ssl_sock;

  if (! ssl) {
    return -1;
  }
    
  SSL_get0_next_proto_negotiated(ssl, &proto, &proto_len);

  if (proto_len <= 0) {
    return -1;
  }

  sublen = buflen < proto_len ? buflen : proto_len;

  memcpy(buffer, proto, sublen);
  buffer[sublen + 1] = 0;

  return sublen;
}

/*
 * server
 */

static int
ssl_npn_server_cb(SSL *s,
                  const unsigned char **out, unsigned int *outlen,
                  void *arg)
{
  const unsigned char *value = arg;
  
  *out = value;
  *outlen = strlen((char *) value);

  return 0;
}

int
caucho_ssl_npn_register_server(SSL_CTX *ctx, char *protocols)
{
  if (! ctx || ! protocols) {
    return -1;
  }

  SSL_CTX_set_next_protos_advertised_cb(ctx,
                                        ssl_npn_server_cb,
                                        protocols);

  return 0;
}

/*
 * client
 */

static const unsigned char *
ssl_is_npn_match(const unsigned char *pref, int pref_len,
                 const unsigned char *in, int inlen)
{
  const unsigned char *ptr = in;

  if (! ptr || ! in) {
    return 0;
  }

  while (inlen > 0) {
    int sublen = *ptr;

    if (sublen <= 0 || inlen < sublen) {
      return 0;
    }

    if (sublen == pref_len
        && ! memcmp(ptr + 1, pref, pref_len)) {
      return ptr;
    }

    ptr += sublen + 1;
    inlen -= sublen + 1;
  }

  return 0;
}

static int
ssl_npn_client_cb(SSL *ssl,
                  unsigned char **out,
                  unsigned char *outlen,
                  const unsigned char *in,
                  unsigned int inlen,
                  void *arg)
{
  unsigned char *pref_list = arg;
  int pref_len;

  if (! pref_list) {
    return SSL_TLSEXT_ERR_NOACK;
  }

  pref_len = strlen((char *) pref_list);

  while (pref_len > 0) {
    int sublen = pref_list[0];
    const unsigned char *match;

    if (sublen < 0 || pref_len < sublen) {
      break;
    }

    match = ssl_is_npn_match(pref_list + 1, sublen, in, inlen);

    if (match) {
      *out = (unsigned char *) (match + 1);
      *outlen = *match;
      
      return SSL_TLSEXT_ERR_OK;
    }

    pref_list += sublen + 1;
    pref_len -= sublen + 1;
  }

  return SSL_TLSEXT_ERR_OK;
}  

int
caucho_ssl_npn_register_client(SSL_CTX *ctx, char *protocols)
{
  if (! ctx || ! protocols) {
    return -1;
  }
  
  SSL_CTX_set_next_proto_select_cb(ctx, ssl_npn_client_cb, protocols);

  return 0;
}
