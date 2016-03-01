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

#include <jni.h>

#include "../baratine/baratine_os.h"
#include "baratine_ssl.h"

static int ssl_open(connection_t *conn, int fd);
static int ssl_init(connection_t *conn);
static int ssl_read(connection_t *conn, char *buf, int len, int timeout);
static int ssl_read_nonblock(connection_t *conn, char *buf, int len);
static int ssl_write(connection_t *conn, char *buf, int len);
static int ssl_close(connection_t *conn);
static void ssl_free(connection_t *conn);
static int ssl_read_client_certificate(connection_t *conn, char *buf, int len);
static int ssl_get_string(connection_t *conn, char *name, char *buf, int len);

struct connection_ops_t ssl_ops = {
  ssl_init,
  ssl_read,
  ssl_read_nonblock,
  ssl_write,
  0,        /* write_nonblock */
  ssl_close,
  ssl_read_client_certificate,
  ssl_get_string,
  ssl_free,
};

static RSA *g_rsa_512 = 0;
static RSA *g_rsa_1024 = 0;

static int g_crypto_num_locks;
static pthread_mutex_t *g_crypto_locks;

void
cse_log(char *fmt, ...)
{
}

static char *
q_strdup(char *str)
{
  int len = strlen(str);
  char *dup = cse_malloc(len + 1);

  strcpy(dup, str);

  return dup;
}

static int
get_utf(JNIEnv *env, jstring jString, char *buffer)
{
  const char *temp_string;
  
  temp_string = (*env)->GetStringUTFChars(env, jString, 0);
  
  if (temp_string) {
    strcpy(buffer, temp_string);
  
    (*env)->ReleaseStringUTFChars(env, jString, temp_string);
  }

  return temp_string != 0;
}

static char *
strdup_utf(JNIEnv *env, jstring jString)
{
  const char *temp_string;
  char buffer[1024];

  if (jString == 0) {
    return 0;
  }
  
  temp_string = (*env)->GetStringUTFChars(env, jString, 0);
  
  if (temp_string) {
    strcpy(buffer, temp_string);
  
    (*env)->ReleaseStringUTFChars(env, jString, temp_string);
  }

  return temp_string != 0 ? strdup(buffer) : 0;
}

static int
get_utf_n(JNIEnv *env, jstring jString, char *buffer, int strlen)
{
  const char *temp_string;

  if (! jString) {
    buffer[0] = 0;
    return 0;
  }
  
  temp_string = (*env)->GetStringUTFChars(env, jString, 0);
  
  if (temp_string) {
    strncpy(buffer, temp_string, strlen);
    buffer[strlen - 1] = 0;
  
    (*env)->ReleaseStringUTFChars(env, jString, temp_string);
  }
  else {
    buffer[0] = 0;
  }

  return temp_string != 0;
}

static int
ssl_io_exception_cb(const char *str, size_t len, void *user_data)
{
  connection_t *conn = user_data;
  int error = errno;

  if (conn && conn->jni_env) {
    if (! (*conn->jni_env)->ExceptionOccurred(conn->jni_env)) {
      if (strstr(str, "ssl handshake failure"))
	baratine_throw_exception(conn->jni_env, "com/caucho/vfs/ClientDisconnectException",
			      str);
      else {
	char buf[8192];

	sprintf(buf, "errno=%d openssl='%s'", error, str);
	
	baratine_throw_exception(conn->jni_env, "java/io/IOException",
			      buf);
      }
    }
  }
  else {
    fwrite(str, len, 1, stderr);
  }

  return 0;
}

static int
ssl_config_exception_cb(const char *str, size_t len, void *user_data)
{
  JNIEnv *env = user_data;

  if (env) {
    if (! (*env)->ExceptionOccurred(env)) {
      baratine_throw_exception(env, "com/caucho/config/ConfigException", str);
    }
  }
  else {
    fwrite(str, len, 1, stderr);
  }
  
  return 0;
}

static int
write_exception_status(connection_t *conn, int error)
{
  conn->ops->close(conn);
  
  if (error == EAGAIN || error == EWOULDBLOCK) {
    return TIMEOUT_EXN;
  }
  else if (error == EINTR) {
    return INTERRUPT_EXN;
  }
  else if (error == EPIPE || errno == ECONNRESET) {
    return DISCONNECT_EXN;
  }
  else {
    return -1;
  }
}

static int
read_exception_status(connection_t *conn, int error)
{
  /* conn->ops->close(conn); */
  
  if (error == EAGAIN || error == EWOULDBLOCK) {
    return TIMEOUT_EXN;
  }
  else if (error == EINTR) {
    conn->ops->close(conn);
    
    return INTERRUPT_EXN;
  }
  else if (error == EPIPE || errno == ECONNRESET) {
    conn->ops->close(conn);
    
    return DISCONNECT_EXN;
  }
  else {
    conn->ops->close(conn);
    
    return -1;
  }
}

static int
calculate_poll_result(connection_t *conn, int poll_result)
{
  if (poll_result == 0) {
    return TIMEOUT_EXN;
  }
  else if (poll_result < 0 && errno != EINTR) {
    return read_exception_status(conn, errno);
  }
  else {
    return poll_result;
  }
}

static int
baratine_tcp_set_recv_timeout(connection_t *conn, int timeout_ms)
{
  int fd = conn->fd;
  int result = 0;
  struct timeval timeout;

#ifdef HAS_SOCK_TIMEOUT
  timeout.tv_sec = timeout_ms / 1000;
  timeout.tv_usec = timeout_ms % 1000 * 1000;
  
  result = setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO,
                      (char *) &timeout, sizeof(timeout));
#endif  

  return result;
}

static int
password_callback(char *buf, int size, int rwflag, void *userdata)
{
  strcpy(buf, userdata);

  return strlen(buf);
}

/*
 * This OpenSSL callback function is called when OpenSSL
 * verifies the client certificate against the certificate chain.
 */
static int
ssl_verify_callback(int ok, X509_STORE_CTX *ctx)
{
  SSL *ssl;
  connection_t *conn;
  ssl_config_t *config;
  
  int error_code;
  int verify_depth;

  ssl = (SSL *) X509_STORE_CTX_get_app_data(ctx);

  /* If the user data is missing, then it's a failure. */
  if (! ssl)
    return 0;

  verify_depth = X509_STORE_CTX_get_error_depth(ctx);

  conn = (connection_t *) SSL_get_app_data(ssl);

  /* If the user data is missing, then it's a failure. */
  if (! conn || ! conn->ss)
    return 0;

  config = conn->ss->ssl_config;

  /* If openssl's check was okay, then the verify is okay. */
  if (ok) {
    /* The verification depth was too deep. */
    if (config->verify_depth > 0 && config->verify_depth < verify_depth) {
      baratine_printf_exception(conn->jni_env, "java/io/IOException", 
			     "client certificate verification depth %d is deeper than the configured max %d.\n",
			     verify_depth, config->verify_depth);
      
      return 0;
    }

    return 1;
  }
  
  error_code = X509_STORE_CTX_get_error(ctx);
  
  /* optional and required require valid certificates */
  if (conn->ss->verify_client != Q_VERIFY_OPTIONAL_NO_CA) {
    return 0;
  }

  if (error_code == X509_V_ERR_DEPTH_ZERO_SELF_SIGNED_CERT
      || error_code == X509_V_ERR_SELF_SIGNED_CERT_IN_CHAIN
      || error_code == X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY
      || error_code == X509_V_ERR_CERT_UNTRUSTED
      || error_code == X509_V_ERR_UNABLE_TO_VERIFY_LEAF_SIGNATURE) {
    return 1;
  }

  return 0;
}

/*
 * This OpenSSL callback is called when OpenSSL creates a new SSL session.
 * Resin uses it to set the session cache timeout.
 */
static int
ssl_new_session_cache_callback(SSL *ssl, SSL_SESSION *session)
{
  connection_t *conn;
  ssl_config_t *config;
  
  if (! ssl || ! session)
    return 0;
  
  conn = (connection_t *) SSL_get_app_data(ssl);

  /* If the user data is missing, then it's a failure. */
  if (! conn || ! conn->ss)
    return 0;

  config = conn->ss->ssl_config;

  return 1;
}

/*
 * This OpenSSL callback is called when OpenSSL creates a new SSL session.
 * Resin uses it to set the session cache timeout.
 */

static SSL_SESSION *
ssl_get_session_cache_callback(SSL *ssl,
			       unsigned char *id, int id_len,
			       int *pCopy)
{
  return 0;
}

#ifndef WIN32

static unsigned long
ssl_thread_id()
{
  return (unsigned long) pthread_self();
}

static void
ssl_thread_lock(int mode, int n, const char *file, int line)
{
  int result;
  
  if (g_crypto_num_locks <= n) {
    result = -1;
  }
  else if (mode & CRYPTO_LOCK) {
    result = pthread_mutex_lock(&(g_crypto_locks[n]));
  }
  else {
    result = pthread_mutex_unlock(&(g_crypto_locks[n]));
  }
}

static struct CRYPTO_dynlock_value *
ssl_create_dynlock(const char *file, int line)
{
  pthread_mutex_t *mutex = malloc(sizeof(pthread_mutex_t));
  memset(mutex, 0, sizeof(pthread_mutex_t));
  pthread_mutex_init(mutex, 0);

  return (CRYPTO_dynlock_value *) mutex;
}

static void
ssl_lock_dynlock(int mode,
                 struct CRYPTO_dynlock_value *lock,
                 const char *file,
                 int line)
{
  pthread_mutex_t *mutex = (pthread_mutex_t *) lock;

  if (! mutex)
    return;

  if (mode & CRYPTO_LOCK) {
    pthread_mutex_lock(mutex);
  }
  else {
    pthread_mutex_unlock(mutex);
  }
}

static void
ssl_destroy_dynlock(struct CRYPTO_dynlock_value *lock,
                    const char *file,
                    int line)
{
  pthread_mutex_t *mutex = (pthread_mutex_t *) lock;

  if (mutex) {
    free(mutex);
  }
}

static void
ssl_init_locks()
{
  int i;
  /* pthread_mutex_t init = PTHREAD_MUTEX_INITIALIZER; */
  pthread_mutexattr_t attr;
  int memsize;

  if (g_crypto_locks)
    return;

  g_crypto_num_locks = CRYPTO_num_locks();
  memsize = g_crypto_num_locks * sizeof(g_crypto_locks[0]);
  g_crypto_locks = malloc(memsize);
  memset(g_crypto_locks, 0, memsize);

  pthread_mutexattr_init(&attr);
#ifdef PTHREAD_MUTEX_ERRORCHECK_NP  
  pthread_mutexattr_settype(&attr, PTHREAD_MUTEX_ERRORCHECK_NP);
#endif

  for (i = 0; i < g_crypto_num_locks; i++) {
    /* pthread_mutex_init(&(g_crypto_locks[i]), 0); */
    pthread_mutex_init(&(g_crypto_locks[i]), &attr);
    
    /* avoid glibc optimization issue */
    /* memcpy(&(g_crypto_locks[i]), &init, sizeof(init)); */
  }

  CRYPTO_set_id_callback(ssl_thread_id);
  CRYPTO_set_locking_callback(ssl_thread_lock);

  CRYPTO_set_dynlock_create_callback(ssl_create_dynlock);
  CRYPTO_set_dynlock_lock_callback(ssl_lock_dynlock);
  CRYPTO_set_dynlock_destroy_callback(ssl_destroy_dynlock);
}

#else

static void
ssl_init_locks()
{
}

#endif

static void
ssl_servername_cb(SSL *s, int *ad, void *arg)
{
  const char *server_name = SSL_get_servername(s, TLSEXT_NAMETYPE_host_name);
  
  /* fprintf(stderr, "ServerName: %p %s\n", arg, server_name); */
}

static SSL_CTX *
ssl_create_context(JNIEnv *env, ssl_config_t *config, ENGINE *e)
{
  SSL_CTX *ctx;
  const SSL_METHOD *meth;

  meth = SSLv23_server_method();
  
  ctx = SSL_CTX_new(meth);

  if (! ctx) {
    ERR_print_errors_cb(ssl_config_exception_cb, env);

    return 0;
  }

  SSL_CTX_set_options(ctx, SSL_OP_ALL);
  
  if (! (config->alg_flags & ALG_SSL2)) {
    SSL_CTX_set_options(ctx, SSL_OP_NO_SSLv2);
  }
  
  if (! (config->alg_flags & ALG_SSL3)) {
    SSL_CTX_set_options(ctx, SSL_OP_NO_SSLv3);
  }
  
  if (! (config->alg_flags & ALG_TLS1)) {
    SSL_CTX_set_options(ctx, SSL_OP_NO_TLSv1);
  }

#ifdef SSL_OP_NO_TLSv1_1  
  if (! (config->alg_flags & ALG_TLS1_1)) {
    SSL_CTX_set_options(ctx, SSL_OP_NO_TLSv1_1);
  }
#endif
  
#ifdef SSL_OP_NO_TLSv1_2
  if (! (config->alg_flags & ALG_TLS1_2)) {
    SSL_CTX_set_options(ctx, SSL_OP_NO_TLSv1_2);
  }
#endif  

  if (! config->key_file && ! config->engine) {
    baratine_printf_exception(env, "com/caucho/config/ConfigException", "certificate-key-field is required for <openssl>.");
    return 0;
  }
  
  if (config->password) {
    SSL_CTX_set_default_passwd_cb(ctx, password_callback);
    SSL_CTX_set_default_passwd_cb_userdata(ctx, config->password);
  }

  if (config->certificate_file) {
    if (SSL_CTX_use_certificate_file(ctx, config->certificate_file,
                                     SSL_FILETYPE_PEM) != 1) {
      baratine_printf_exception(env, "com/caucho/config/ConfigException", "OpenSSL can't open certificate-file '%s'",
                             config->certificate_file);
      /*ERR_print_errors_fp(stderr); */
      return 0;
    }
  }

  if (config->key_file) {
    if (SSL_CTX_use_PrivateKey_file(ctx,
                                    config->key_file,
                                    SSL_FILETYPE_PEM) != 1) {
      baratine_printf_exception(env, "com/caucho/config/ConfigException", "OpenSSL can't open key file '%s' or the password does not match.",
			   config->key_file);
      /*ERR_print_errors_fp(stderr); */
      return 0;
    }
  }
  else if (e != 0) {
    EVP_PKEY *pkey = 0;
    UI_METHOD *ui_method = 0;
    void *cb_data = config->password;

    pkey = ENGINE_load_private_key(e, config->engine_key,
                                   ui_method, cb_data);

    if (pkey == 0) {
      baratine_printf_exception(env, "com/caucho/config/ConfigException", "OpenSSL engine can't open a private key %s.  Check the engine and key-name files for consistency.",
                             config->engine_key ? config->engine_key : "(null)");
      return 0;
    }
    
    if (SSL_CTX_use_PrivateKey(ctx, pkey) <= 0) {
      baratine_printf_exception(env, "com/caucho/config/ConfigException", "OpenSSL engine can't use a private key %s.  Check the engine and key-name files for consistency.",
                             config->engine_key);

      return 0;
    }
  }
  else {
    baratine_printf_exception(env, "com/caucho/config/ConfigException", "OpenSSL can't open a private key.  Check the engine and key-name files for consistency.");

    return 0;
  }

  if (! SSL_CTX_check_private_key(ctx)) {
    baratine_printf_exception(env, "com/caucho/config/ConfigException", "The certificate's private key does not match the public key in '%s'.  Check the certificate and certificate-key files for consistency.",
			   config->certificate_file);
    return 0;
  }

  if (config->certificate_chain_file &&
      SSL_CTX_use_certificate_chain_file(ctx, config->certificate_chain_file) != 1) {
    /*ERR_print_errors_fp(stderr); */
    baratine_printf_exception(env, "com/caucho/config/ConfigException", "OpenSSL can't open certificate-chain-file '%s'",
			   config->certificate_file);

    return 0;
  }

  if (config->verify_client != Q_VERIFY_NONE) {
    int verifyFlags = SSL_VERIFY_NONE|SSL_VERIFY_PEER;

    if (config->verify_client == Q_VERIFY_REQUIRE)
      verifyFlags |= SSL_VERIFY_FAIL_IF_NO_PEER_CERT;

    SSL_CTX_set_verify(ctx, verifyFlags, ssl_verify_callback);
  }

  if (config->enable_session_cache) {
    unsigned char id[] = "Resin";
    
    SSL_CTX_set_session_cache_mode(ctx, SSL_SESS_CACHE_SERVER);
    SSL_CTX_set_session_id_context(ctx, id, sizeof(id));
    /*
    SSL_CTX_sess_set_cache_size(ctx, 1024);
    */
    
    if (config->session_cache_timeout > 0) {
      SSL_CTX_set_timeout(ctx, config->session_cache_timeout);
    }

    /*
    SSL_CTX_sess_set_new_cb(ctx, ssl_new_session_cache_callback);
    SSL_CTX_sess_set_get_cb(ctx, ssl_get_session_cache_callback);
    */
  }
  else
    SSL_CTX_set_session_cache_mode(ctx, SSL_SESS_CACHE_OFF);

  if (! config->cipher_suite) {
  }
  else if (! SSL_CTX_set_cipher_list(ctx, config->cipher_suite)) {
    baratine_printf_exception(env, "com/caucho/config/ConfigException", "OpenSSL can't set the cipher-suite '%s'",
			   config->cipher_suite);

    return 0;
  }

  SSL_CTX_set_options(ctx, SSL_OP_SINGLE_DH_USE);
#ifdef SSL_OP_SINGLE_ECDH_USE
  SSL_CTX_set_options(ctx, SSL_OP_SINGLE_ECDH_USE);
#endif

#ifdef SSL_OP_CIPHER_SERVER_PREFERENCE
  if (config->is_honor_cipher_order) {
    SSL_CTX_set_options(ctx, SSL_OP_CIPHER_SERVER_PREFERENCE);
  }
#endif  

#ifdef SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION
  SSL_CTX_set_options(ctx, SSL_OP_NO_SESSION_RESUMPTION_ON_RENEGOTIATION);
#endif

#ifdef SSL_OP_NO_COMPRESSION
  if (! config->is_compression) {
    SSL_CTX_set_options(ctx, SSL_OP_NO_COMPRESSION);
  }
  else {
    SSL_CTX_clear_options(ctx, SSL_OP_NO_COMPRESSION);
  }
#endif

  SSL_CTX_set_tlsext_servername_callback(ctx, ssl_servername_cb);

  /* NPN */
  if (config->next_protos) {
    caucho_ssl_npn_register_server(ctx, config->next_protos);
  }

  if (! g_rsa_512) {
    g_rsa_512 = RSA_generate_key(512, RSA_F4, NULL, NULL);
    
    if (! g_rsa_512) {
      baratine_printf_exception(env, "com/caucho/config/ConfigException", "OpenSSL can't generate a 512 bit RSA key.  On Unix, check the machine's /dev/random and /dev/urandom configuration.");

      return 0;
    }
  }

  if (! g_rsa_1024) {
    g_rsa_1024 = RSA_generate_key(1024, RSA_F4, NULL, NULL);
    
    if (! g_rsa_1024) {
      baratine_printf_exception(env, "com/caucho/config/ConfigException", "OpenSSL can't generate a 1024 bit RSA key.  On Unix, check the machine's /dev/random and /dev/urandom configuration.");

      return 0;
    }
  }
  
  if (config->ca_certificate_file || config->ca_certificate_path) {
    if (! SSL_CTX_load_verify_locations(ctx,
					config->ca_certificate_file,
					config->ca_certificate_path)) {
      baratine_printf_exception(env, "com/caucho/config/ConfigException", "OpenSSL can't find CA certificates for client authentication in '%s'",
			     config->ca_certificate_file ?
			     config->ca_certificate_file :
			     config->ca_certificate_path ?
			     config->ca_certificate_path :
			     "null");
      return 0;
    }

    if (! SSL_CTX_set_default_verify_paths(ctx)) {
      baratine_printf_exception(env, "com/caucho/config/ConfigException", "OpenSSL error setting default verify paths.");
      return 0;
    }
    
    if (config->ca_certificate_file) {
      STACK_OF(X509_NAME) *cacerts;
    
      cacerts = SSL_load_client_CA_file(config->ca_certificate_file);
      if (cacerts)
	SSL_CTX_set_client_CA_list(ctx, cacerts);
      else {
	baratine_printf_exception(env, "com/caucho/config/ConfigException", "OpenSSL can't find CA certificates for client authentication in '%s'",
			       config->ca_certificate_file);
	return 0;
      }
    }
  }
  
  if (config->ca_revocation_file || config->ca_revocation_path) {
    if (! X509_STORE_load_locations(SSL_CTX_get_cert_store(ctx),
				    config->ca_revocation_file,
				    config->ca_revocation_path)) {
      baratine_printf_exception(env, "com/caucho/config/ConfigException",
			     "Can't find CA revocation list for CRL management.\n");
      return 0;
    }
  }

#ifdef SSL_CTX_set_ecdh_auto
  SSL_CTX_set_ecdh_auto(ctx, 1);
#else
#ifdef SSL_OP_SINGLE_ECDH_USE
  SSL_CTX_set_tmp_ecdh(ctx,
                       EC_KEY_new_by_curve_name(NID_X9_62_prime256v1));
#endif
#endif

  return ctx;
}

static void
ssl_safe_free(connection_t *conn, int fd, SSL *ssl)
{
  server_socket_t *ss;
  
  if (ssl) {
    int count;

    /* clear non-blocking i/o */
#ifndef WIN32
    {
      int flags;
      flags = fcntl(fd, F_GETFL);
      fcntl(fd, F_SETFL, ~O_NONBLOCK&flags);
    }
#endif

    if (! conn || ! conn->ss || ! conn->ss->ssl_config)
      SSL_set_shutdown(ssl, SSL_RECEIVED_SHUTDOWN);
    else if (conn->ss->ssl_config->unclean_shutdown)
      SSL_set_shutdown(ssl, SSL_SENT_SHUTDOWN|SSL_RECEIVED_SHUTDOWN);
    else
      SSL_set_shutdown(ssl, SSL_RECEIVED_SHUTDOWN);

    for (count = 4; count > 0; count--) {
      int result = SSL_shutdown(ssl);
    }

    SSL_set_app_data(ssl, 0);

    ss = conn ? conn->ss : 0;

    SSL_set_fd(ssl, -1);

    /*
    if (ss && ss->fd >= 0) {
      SSL_free(ssl);
    }
    */
  }
}

static RSA *
ssl_get_temporary_RSA_key(SSL *ssl, int isExport, int keyLen)
{
  RSA *rsa = 0;

  if (isExport) {
    if (keyLen == 512)
      return g_rsa_512;
    else if (keyLen == 1024)
      return g_rsa_1024;
    else
      return g_rsa_1024;
  }
  else
    return g_rsa_1024;
}

static void
ssl_fill_ciphers(connection_t *conn)
{
  SSL *ssl;
  const SSL_CIPHER *cipher;
  int algbits;
  
  ssl = conn->ssl_sock;
  
  cipher = SSL_get_current_cipher(ssl);

  if (cipher) {
    conn->ssl_cipher = (void *) SSL_CIPHER_get_name(cipher);
    conn->ssl_bits = SSL_CIPHER_get_bits(cipher, &algbits);
  }
  else {
    conn->ssl_cipher = 0;
    conn->ssl_bits = 0;
  }
}

static int
ssl_open(connection_t *conn, int fd)
{
  int result;
  SSL_CTX *ctx = 0;
  SSL *ssl;
  int ssl_error;
  server_socket_t *ss;
  int code;

  ss = conn->ss;

  if (conn->is_init) {
    return 1;
  }

  conn->is_init = 1;
  
  if (! ss || ss->fd <= 0 || ! ss->ssl_config) {
    if (conn->jni_env) {
      baratine_printf_exception(conn->jni_env, "java/lang/IllegalStateException",
			     "ssl_open called with closed ServerSocket");
    }
    else {
      fprintf(stderr, "ssl_open called with closed ServerSocket\n");
    }
    
    return -1;
  }

  ctx = conn->ssl_context;

  if (! ctx) {
    if (conn->jni_env) {
      baratine_printf_exception(conn->jni_env, "java/lang/IllegalStateException",
			     "ssl_open is missing the SSL context");
    }
    else
      fprintf(stderr, "ssl_open is missing the SSL context\n");
    
    return -1;
  }

  ssl = conn->ssl_sock;

  if (! ssl) {
    ssl = SSL_new(ctx);
    conn->ssl_sock = ssl;
  }

  if (! ssl) {
    baratine_printf_exception(conn->jni_env, "java/io/IOException",
			   "OpenSSL can't allocate SSL socket context");
    return -1;
  }

#ifndef WIN32
  if (! conn->is_recv_timeout) {
    int flags;
    flags = fcntl(fd, F_GETFL);
    fcntl(fd, F_SETFL, O_NONBLOCK|flags);
  }
#endif
  
  SSL_set_fd(ssl, fd);
  SSL_set_app_data(ssl, conn);

  SSL_set_tmp_rsa_callback(ssl, ssl_get_temporary_RSA_key);

  do {
    result = SSL_accept(ssl);
  } while (result < 0
           && ((code = SSL_get_error(ssl, result)) == SSL_ERROR_WANT_READ
               || code == SSL_ERROR_WANT_WRITE));

  if (result > 0) {
    /* success */
    ssl_fill_ciphers(conn);

    return 1;
  }
  
  if ((ssl_error = SSL_get_error(ssl, result)) == SSL_ERROR_ZERO_RETURN) {
    /* empty request */
  }
  else if (ssl_error == SSL_ERROR_SYSCALL
	   && (errno == EPIPE || errno == ECONNRESET)) {
  }
  else {
    ERR_print_errors_cb(ssl_io_exception_cb, conn);
  }

  SSL_set_app_data(ssl, 0);
  SSL_set_fd(ssl, -1);
  
  return -1;
}

static int
ssl_init(connection_t *conn)
{
  return conn->ss->init(conn);
}

static int
ssl_read(connection_t *conn, char *buf, int len, int timeout)
{
  int fd;
  SSL *ssl;
  int result;
  int retry = 32;
  int ssl_error = 0;
  int code;
  int poll_timeout;
  int poll_result;
  int is_retry = 0;
  server_socket_t *ss;

  if (conn->fd < 0)
    return -1;

  if (! conn)
    return -1;
  
  fd = conn->fd;
  
  if (fd < 0 || conn->is_read_shutdown)
    return -1;

  ss = conn->ss;

  if (! conn->is_client && (! ss || ss->fd < 0)) {
    conn->ops->close(conn);
    
    return -1;
  }

  if (timeout >= 0) {
    poll_timeout = timeout;
  }
  else {
    poll_timeout = conn->socket_timeout;
  }

  if (timeout > 0 && conn->is_recv_timeout) {
    if (conn->recv_timeout != poll_timeout) {
      conn->recv_timeout = poll_timeout;

      baratine_tcp_set_recv_timeout(conn, poll_timeout);
    }
  }
  else if (conn->ssl_sock && SSL_pending(conn->ssl_sock) > 0) {
    /* #5120 - if pending data is available don't poll */
  }
  else {
    poll_result = poll_read(fd, poll_timeout);

    if (poll_result <= 0) {
      return calculate_poll_result(conn, poll_result);
    }
  }
  
  if (! conn->is_init && ssl_open(conn, conn->fd) <= 0) {
    ssl_close(conn);
    return -1;
  }

  ssl = conn->ssl_sock;
  if (! ssl) {
    return -1;
  }

  do {
    errno = 0;

    result = SSL_read(ssl, buf, len);

    if (result > 0) {
      return result;
    }

    ssl_error = SSL_get_error(ssl, result);

    if (ssl_error == SSL_ERROR_WANT_READ) {
      poll_result = poll_read(fd, poll_timeout);

      if (poll_result <= 0) {
	return calculate_poll_result(conn, poll_result);
      }
    }
    else if (ssl_error == SSL_ERROR_WANT_WRITE) {
    }
    else if (errno == EINTR) {
      poll_result = poll_read(fd, poll_timeout);

      if (poll_result <= 0) {
	return calculate_poll_result(conn, poll_result);
      }

      errno = EINTR;
    }
    else if (errno == EAGAIN) {
      return TIMEOUT_EXN;
    }

    is_retry++;
  } while (retry-- > 0
           && (errno == EINTR
               || ssl_error == SSL_ERROR_WANT_READ
	       || ssl_error == SSL_ERROR_WANT_WRITE));

  if (ssl_error == SSL_ERROR_ZERO_RETURN) {
    ssl_close(conn);
    /* end of file */
    return -1;
  }

  if (ssl_error == SSL_ERROR_SYSCALL) {
    return read_exception_status(conn, errno);
  }
  else if (ssl_error == SSL_ERROR_SSL
	   && (errno == EAGAIN || errno == EWOULDBLOCK
	       || errno == EPIPE || errno == ECONNRESET || errno == EINTR)) {
    /* XXX: not sure why SSL returns this instead of SSL_ERROR_SYSCALL */
    return read_exception_status(conn, errno);
  }
  else {
    int readErrno = errno;
      
#if OPENSSL_VERSION_NUMBER >= 0x00907000L
    ERR_print_errors_cb(ssl_io_exception_cb, conn);
#else
    if (! conn->jni_env) {
      fprintf(stderr, "OpenSSL read exception (code=%d, errno=%d)", SSL_get_error(ssl, result), readErrno);
    }
    else if ((*(conn->jni_env))->ExceptionOccurred(conn->jni_env)) {
      baratine_printf_exception(conn->jni_env, "java/io/IOException", "OpenSSL read exception (code=%d, errno=%d)", SSL_get_error(ssl, result), readErrno);
    }
#endif
  
    return read_exception_status(conn, readErrno);
  }
}

static int
ssl_read_nonblock(connection_t *conn, char *buf, int len)
{
  return ssl_read(conn, buf, len, 100);
}

static int
ssl_write(connection_t *conn, char *buf, int len)
{
  SSL *ssl = conn->ssl_sock;
  int fd;
  int ssl_error;
  int result;
  int retry = 10;
  int is_retry = 0;
  
  if (! conn || ! ssl) {
    return -1;
  }

  if (conn->fd < 0)
    return -1;
  
  fd = conn->fd;

  if (! conn->is_init && ssl_open(conn, conn->fd) <= 0) {
    ssl_close(conn);
    return -1;
  }

  if (len <= 0)
    return 0;

  do {
    errno = 0;

    if (is_retry > 0) {
      int timeout = conn->socket_timeout;
      int poll_result;

      poll_result = poll_write(conn->fd, timeout);

      if (poll_result > 0) {
      }
      else if (poll_result == 0) {
        /* XXX: also EINTR */
        return TIMEOUT_EXN;
      }
      else if (errno == EINTR) {
        continue;
      }
      else {
        return read_exception_status(conn, errno);
      }
    }
    
    result = SSL_write(ssl, buf, len);

    if (result > 0)
      return result;

    /*
    fprintf(stdout,
            "SSL_write_fail result=%d errno=%d buffer=%p len=%d retry=%d\n",
            result, errno, buf, len, is_retry);
    fflush(stdout);
    */

    ssl_error = SSL_get_error(ssl, result);

    /*
    fprintf(stdout,
            "SSL_write_fail ssl_error=%d result=%d errno=%d buffer=%p len=%d retry=%d\n",
            ssl_error, result, errno, buf, len, is_retry);
    fflush(stdout);
    */

    is_retry++;
  } while (retry-- > 0
	   && (ssl_error == SSL_ERROR_WANT_READ
               || ssl_error == SSL_ERROR_WANT_WRITE));

  conn->ops->close(conn);
  
  if (ssl_error == SSL_ERROR_SYSCALL) {
    return write_exception_status(conn, errno);
  }
  else if (ssl_error == SSL_ERROR_SSL
	   && (errno == EAGAIN || errno == EWOULDBLOCK
	       || errno == EPIPE || errno == ECONNRESET || errno == EINTR)) {
    /* XXX: not sure why SSL returns this instead of SSL_ERROR_SYSCALL */
    return write_exception_status(conn, errno);
  }
  else {
    int writeErrno = errno;

    ERR_print_errors_cb(ssl_io_exception_cb, conn);
    
    return write_exception_status(conn, writeErrno);
  }
}

static int
ssl_close(connection_t *conn)
{
  int fd;
  SSL *ssl;

  if (! conn) {
    return 0;
  }

  fd = conn->fd;
  conn->fd = -1;
  
  ssl = conn->ssl_sock;
  conn->ssl_sock = 0;

  conn->ssl_cipher = 0;
  conn->ssl_bits = 0;

  if (ssl) {
    SSL_set_app_data(ssl, 0);
    ssl_safe_free(conn, fd, ssl);
  }

  if (fd > 0) {
    closesocket(fd);
  }

  conn_close(conn);

  /* fprintf(stderr, "ssl_close %d\n", fd); */

  return 0;
}

static void
ssl_free(connection_t *conn)
{
  int fd;
  SSL *ssl;

  if (! conn) {
    return;
  }

  ssl = conn->ssl_sock;
  conn->ssl_sock = 0;

  if (ssl) {
    SSL_free(ssl);
  }
}

/**
 * Sets certificate chain stuff.
 */
static int
set_certificate_chain(SSL_CTX *ctx, ssl_config_t *config)
{
  /* Not sure how this is supposed to work. */
  return 0;
}

static int
ssl_get_string(connection_t *conn, char *name,
               char *buffer, int length)
{
  if (! conn || conn->fd < 0) {
    return -1;
  }
  
  if (! strcmp(name, "negotiated-protocol")) {
    return caucho_ssl_npn_get_protocol(conn, buffer, length);
  }
  
  return -1;
}

static int
ssl_read_client_certificate(connection_t *conn, char *buffer, int length)
{
  BIO *bio;
  int n = -1;
  X509 *cert;
  
  if (! conn)
    return -1;
  
  if (! conn->is_init && ssl_open(conn, conn->fd) <= 0) {
    ssl_close(conn);
    return -1;
  }

  cert = SSL_get_peer_certificate(conn->ssl_sock);

  if (! cert)
    return -1;

  if ((bio = BIO_new(BIO_s_mem())) != NULL) {
    PEM_write_bio_X509(bio, cert);
    n = BIO_pending(bio);

    if (n <= length)
      n = BIO_read(bio, buffer, n);
  
    BIO_free(bio);
  }

  X509_free(cert);
    
  return n;
}

static int
engine_command_length(char *cmd)
{
  int len = 0;

  while (*cmd && *cmd != 1 && len < 255) {
    cmd++;
    len++;
  }

  return len;
}

static int
ssl_engine_commands(JNIEnv *env,
                    ENGINE *engine,
                    ssl_config_t *config)
{
  char command[256];
  unsigned int cmd_len;
  char arg[256];
  unsigned int arg_len;
  char *engine_commands;

  if (! config) {
    return 0;
  }

  engine_commands = config->engine_commands;
  
  if (! engine_commands) {
    return 1;
  }
  
  while (*engine_commands == 1) {
    int sublen;
    int result;
    
    engine_commands++;

    sublen = engine_command_length(engine_commands);

    if (sublen < 0) {
      break;
    }
    
    memcpy(command, engine_commands, sublen);
    command[sublen] = 0;
    engine_commands += sublen;

    if (*engine_commands != 1) {
      break;
    }
    
    engine_commands++;

    sublen = engine_command_length(engine_commands);

    if (sublen < 0) {
      break;
    }
    memcpy(arg, engine_commands, sublen);
    arg[sublen] = 0;
    engine_commands += sublen;

    if (arg[0]) {
      result = ENGINE_ctrl_cmd_string(engine, command, arg, 0);
    }
    else {
      result = ENGINE_ctrl_cmd_string(engine, command, 0, 0);
    }
  
    if (! result) {
      ERR_print_errors_cb(ssl_config_exception_cb, env);
      ENGINE_free(engine);
      
      /*
      baratine_printf_exception(env, "com/caucho/config/ConfigException", "'%s' is an unknown engine-command for OpenSSL engine '%s'.", command,
                             config->engine);
      */
      return 0;
    }
  }

  return 1;
}

static ENGINE *
ssl_engine_init(JNIEnv *env, ssl_config_t *config)
{
  ENGINE *engine;
  
  if (! config->engine || ! *config->engine) {
    return 0;
  }

  if (! strcmp(config->engine, "builtin")) {
    return 0;
  }

  if (! (engine = ENGINE_by_id(config->engine))) {
    baratine_printf_exception(env, "com/caucho/config/ConfigException",
			   "<openssl> ENGINE_by_id cannot load engine %s", config->engine);
    
    return 0;
  }

  if (! ssl_engine_commands(env, engine, config)) {
    return 0;
  }

  if (! ENGINE_init(engine)) {
    ERR_print_errors_cb(ssl_config_exception_cb, env);
    ENGINE_free(engine);
      /*
      baratine_printf_exception(env, "com/caucho/config/ConfigException", "'%s' is an unknown crypto-device for OpenSSL.", config->engine);
      */
    return 0;
  }
  
  if (! ENGINE_set_default(engine, ENGINE_METHOD_ALL)) {
    ERR_print_errors_cb(ssl_config_exception_cb, env);
    ENGINE_free(engine);
    /*
    baratine_printf_exception(env, "com/caucho/config/ConfigException",
			   "<openssl> ENGINE_by_id cannot initialize engine %s", config->engine);
    */                        

    return 0;
  }

  return engine;
}

/**
 * Needs to match ssl_stub.c
 */
JNIEXPORT jlong JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_initConfig(JNIEnv *env,
					      jobject obj,
					      jstring jCertificateFile,
					      jstring jKeyFile,
					      jstring jPassword,
					      jstring jCertificateChainFile,
					      jstring jCaCertificatePath,
					      jstring jCaCertificateFile,
					      jstring jCaRevocationPath,
					      jstring jCaRevocationFile,
					      jstring jCipherSuite,
                                              jboolean jIsHonorCipherOrder,
                                              jboolean jIsCompression,
					      jint protocol,
					      jboolean uncleanShutdown)
{
  int val = 0;
  int sock;
  ssl_config_t *config;
  const char *string;
  char certificate_file[1024];
  char key_file[1024];
  char password[1024];

#ifndef OPENSSL_THREADS
  baratine_printf_exception(env, "com/caucho/config/ConfigException",
			 "Baratine requires a threaded version of OpenSSL.  OpenSSL must be compiled with OPENSSL_THREADS for Baratine to use it.");
  return 0;
#endif

  certificate_file[0] = 0;
  key_file[0] = 0;
  password[0] = 0;

  if (jCertificateFile &&
      ! get_utf(env, jCertificateFile, certificate_file)) {
    baratine_printf_exception(env, "com/caucho/config/ConfigException",
			   "<openssl> requires a certificate-file attribute");

    return 0;
  }

  if (jKeyFile && ! get_utf(env, jKeyFile, key_file)) {
    baratine_printf_exception(env, "com/caucho/config/ConfigException",
			   "<openssl> requires a certificate-key-file attribute");
    return 0;
  }

  if (jPassword && ! get_utf(env, jPassword, password)) {
    baratine_printf_exception(env, "com/caucho/config/ConfigException",
			   "<openssl> requires a password attribute");
    return 0;
  }

  config = cse_malloc(sizeof(ssl_config_t));
  memset(config, 0, sizeof(ssl_config_t));
  
  pthread_mutex_init(&config->ssl_lock, 0);

  config->jni_env = env;

  if (certificate_file[0]) {
    config->certificate_file = q_strdup(certificate_file);
  }

  if (key_file[0]) {
    config->key_file = q_strdup(key_file);
  }

  if (password[0]) {
    config->password = q_strdup(password);
  }
  
  config->alg_flags = protocol;

  config->certificate_chain_file = strdup_utf(env, jCertificateChainFile);
  
  config->ca_certificate_path = strdup_utf(env, jCaCertificatePath);
  config->ca_certificate_file = strdup_utf(env, jCaCertificateFile);
  
  config->ca_revocation_path = strdup_utf(env, jCaRevocationPath);
  config->ca_revocation_file = strdup_utf(env, jCaRevocationFile);
  
  config->cipher_suite = strdup_utf(env, jCipherSuite);
  config->is_honor_cipher_order = jIsHonorCipherOrder;
  config->is_compression = jIsCompression;
  
  config->unclean_shutdown = uncleanShutdown;

  /* init_ssl_engine(env, config); */
  
  /* memory leak with the config. */
  
  return (jlong) (PTR) config;
}

/**
 * Sets verification configuration.
 */
JNIEXPORT void JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_setVerify(JNIEnv *env,
					     jobject obj,
					     jlong p_config,
					     jstring jVerifyClient,
					     jint jVerifyDepth)
{
  ssl_config_t *config = (ssl_config_t *) (PTR) p_config;
  const char *string;
  
  if (! config)
    return;

  if (jVerifyClient) {
    string = (*env)->GetStringUTFChars(env, jVerifyClient, 0);

    if (! string) {
    }
    else if (! strcmp(string, "optional_no_ca"))
      config->verify_client = Q_VERIFY_OPTIONAL_NO_CA;
    else if (! strcmp(string, "optional-no-ca"))
      config->verify_client = Q_VERIFY_OPTIONAL_NO_CA;
    else if (! strcmp(string, "optional"))
      config->verify_client = Q_VERIFY_OPTIONAL;
    else if (! strcmp(string, "require"))
      config->verify_client = Q_VERIFY_REQUIRE;
  
    (*env)->ReleaseStringUTFChars(env, jVerifyClient, string);
    
    if (! string) {
      baratine_printf_exception(env, "java/lang/IllegalStateException",
			     "verify-client attribute is unavailable");

      return;
    }
  }

  config->verify_depth = jVerifyDepth;
}

/**
 * Needs to match ssl_stub.c
 */
JNIEXPORT void JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_setSessionCache(JNIEnv *env,
						   jobject obj,
						   jlong p_config,
						   jboolean enable,
						   jint timeout)
{
  ssl_config_t *config = (ssl_config_t *) (PTR) p_config;
  
  if (! config)
    return;

  config->enable_session_cache = enable;
  config->session_cache_timeout = timeout;
}

/**
 * Sets the engine
 */
JNIEXPORT void JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_setEngine(JNIEnv *env,
                                             jobject obj,
                                             jlong p_config,
					     jstring jEngine,
					     jstring jEngineCommands,
                                             jstring jEngineKey)
{
  ssl_config_t *config = (ssl_config_t *) (PTR) p_config;
  
  if (! config) {
    return;
  }

  config->engine = strdup_utf(env, jEngine);
  config->engine_commands = strdup_utf(env, jEngineCommands);
  config->engine_key = strdup_utf(env, jEngineKey);
}

/**
 * Sets the next protocols.
 */
JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_setNextProtocols(JNIEnv *env,
                                                    jobject obj,
                                                    jlong p_config,
                                                    jstring j_protos)
{
  ssl_config_t *config = (ssl_config_t *) (PTR) p_config;
  
  if (! config || ! j_protos) {
    return 0;
  }

  config->next_protos = strdup_utf(env, j_protos);

  return 1;
}

/**
 * Needs to match ssl_stub.c
 */
JNIEXPORT void JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_nativeInit(JNIEnv *env,
					      jobject obj,
					      jlong p_ss,
					      jlong p_config)
{
  server_socket_t *ss = (server_socket_t *) (PTR) p_ss;
  ssl_config_t *config = (ssl_config_t *) (PTR) p_config;

  if (! ss) {
    baratine_throw_exception(env, "java/lang/IllegalStateException",
			  "server socket must have valid values.");
    return;
  }
  else if (! config) {
    baratine_throw_exception(env, "java/lang/IllegalStateException",
			  "config must have valid values.");
    return;
  }

  ss->ssl_config = config;
  ss->tcp_cork = 0;

  if (! ss->context) {
    ss->engine = ssl_engine_init(env, config);
    
    ss->context = ssl_create_context(env, config, ss->engine);
  }
}

/**
 * Needs to match ssl_stub.c
 */
JNIEXPORT jlong JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_open(JNIEnv *env,
					jobject obj,
					jlong p_conn,
					jlong p_config)
{
  connection_t *conn = (connection_t *) (PTR) p_conn;
  ssl_config_t *config = (ssl_config_t *) (PTR) p_config;

  if (! conn || ! config) {
    return 0;
  }

  if (! conn->ss) {
    return 0;
  }

  /*
   * Because OpenSSL still appears to have some threading issues under
   * heavy load, we create a separate SSL context when possible.
   *
   * When the client verification is enabled and the session cache
   * is enabled, then we need to share the context.
   */
  if (conn->ssl_context) {
  }
  else if (conn->ss->context
           && (1 || (config->verify_client && config->enable_session_cache))) {
    conn->ssl_context = conn->ss->context;
  }
  else {
    ENGINE *e = conn->ss ? conn->ss->engine : 0;
    
    conn->ssl_context = ssl_create_context(env, config, e);
  }

  conn->ops = &ssl_ops;
  conn->ssl_lock = &config->ssl_lock;

  return (jlong) (PTR) conn;
}

/*
 * client
 */

/**
 * Needs to match ssl_stub.c
 */
JNIEXPORT jlong JNICALL
Java_com_caucho_v5_vfs_OpenSSLClientFactory_openClient(JNIEnv *env,
                                                    jobject obj,
                                                    jlong p_conn,
                                                    jstring j_server_name,
                                                    jstring j_next_protos)
{
  connection_t *conn = (connection_t *) (PTR) p_conn;
  SSL_CTX *ctx;
  SSL *ssl;
  int err;
  const SSL_METHOD *meth;
  char server_name[256];
  char *next_protos = 0;

  if (! conn || conn->fd < 0) {
    return 0;
  }

  get_utf_n(env, j_server_name, server_name, sizeof(server_name));

  /* XXX: memory leak */
  next_protos = strdup_utf(env, j_next_protos);

  meth = TLSv1_client_method();
  
  ctx = SSL_CTX_new(meth);
  
  /* SSL_CTX_set_session_cache_mode(ctx, SSL_SESS_CACHE_CLIENT); */
  /* SSL_CTX_set_session_cache_mode(ctx, SSL_SESS_CACHE_OFF); */

  if (! ctx) {
    return 0;
  }

  ssl = SSL_new(ctx);

  if (! ssl) {
    return 0;
  }

  if (server_name[0]) {
    SSL_set_tlsext_host_name(ssl, server_name);
  }

  if (next_protos) {
    caucho_ssl_npn_register_client(ctx, next_protos);
  }
  
  SSL_set_fd(ssl, conn->fd);
  
  err = SSL_connect(ssl);

  if (err < 0) {
    SSL_set_fd(ssl, -1);
    // SSL_free(ssl);
    
    return 0;
  }

  conn->ssl_sock = ssl;
  conn->is_init = 1;
  conn->is_client = 1;

  conn->ops = &ssl_ops;
  /*
  conn->ssl_lock = &config->ssl_lock;
  */

  return (jlong) (PTR) conn;
}

/**
 * Needs to match ssl_stub.c
 */
JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_initSystemNative(JNIEnv *env)
{
  SSL_load_error_strings();
  SSL_library_init();
  ENGINE_load_builtin_engines();
  ENGINE_register_all_complete();
  
  ssl_init_locks();

  return 1;
}

/**
 * Needs to match ssl_stub.c
 */
JNIEXPORT void JNICALL
Java_com_caucho_v5_vfs_OpenSSLFactory_closeSystemNative(JNIEnv *env)
{
  ENGINE_cleanup();
}
