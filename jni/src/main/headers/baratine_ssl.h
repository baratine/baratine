/*
 * Copyright (c) 1999-2014 Caucho Technology.  All rights reserved.
 */

#ifndef BARATINE_SSL_H
#define BARATINE_SSL_H

typedef struct CRYPTO_dynlock_value {
  int dummy;
} CRYPTO_dynlock_value;
  

/* SSLeay stuff */
#include <openssl/ssl.h>
#include <openssl/rsa.h>       
#include <openssl/err.h>
#include <openssl/engine.h>

#include <jni.h>

#include "../baratine/baratine_os.h"


int caucho_ssl_npn_get_protocol(connection_t *conn, char *buffer, int buflen);
int caucho_ssl_npn_register_client(SSL_CTX *ctx, char *protocols);
int caucho_ssl_npn_register_server(SSL_CTX *ctx, char *protocols);

#endif

