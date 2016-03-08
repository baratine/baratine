/*
 * Copyright (c) 1999-2016 Caucho Technology.  All rights reserved.
 */

#ifndef BARATINE_H
#define BARATINE_H

#include <jni.h>

#undef BARATINE_DIRECT_JNI_BUFFER

#define PTR jlong

#undef closesocket
#define closesocket(x) close(x)

#include <pthread.h>

typedef struct connection_t connection_t;

typedef struct connection_ops_t {
  int (*init) (connection_t *conn);
  int (*read) (connection_t *conn, char *buf, int len, int timeout);
  int (*read_nonblock) (connection_t *conn, char *buf, int len);
  int (*write) (connection_t *conn, char *buf, int len);
  int (*write_nonblock) (connection_t *conn, char *buf, int len);
  int (*close) (connection_t *conn);
  
  int (*read_client_certificate) (connection_t *conn, char *buf, int len);
  
  int (*get_string) (connection_t *conn, char *name,
                     char *buf, int buflen);
  
  void (*free) (connection_t *conn);
} connection_ops_t;

struct connection_t {
  struct server_socket_t *ss;
  
  int id;

  JNIEnv *jni_env;
  
  void *ssl_context;
  connection_ops_t *ops;

  int fd;
  int is_init;
  void *ssl_sock;
  int is_client;

  pthread_mutex_t *ssl_lock;
  int socket_timeout;
  int sent_data;

  int is_recv_timeout;
  int recv_timeout;
  int is_read_shutdown;
  int tcp_cork;
  int is_cork;

  char server_data[128];
  struct sockaddr *server_sin;
  char client_data[128];
  struct sockaddr *client_sin;

  char *ssl_cipher;
  int ssl_bits;

  int pipe[2];

#ifdef WIN32
  //WSAEVENT event;
#endif
};

typedef struct server_socket_t server_socket_t;

typedef struct resin_t {
  int count;
  int (*get_server_socket)(struct resin_t *);
} resin_t;


typedef struct ssl_config_t {
  JNIEnv *jni_env;
  
  char *certificate_file;
  char *key_file;
  
  char *certificate_chain_file;
  char *ca_certificate_path;
  char *ca_certificate_file;
  char *ca_revocation_path;
  char *ca_revocation_file;
  
  char *engine;
  char *engine_commands;
  char *engine_key;
  
  char *password;
  int alg_flags;

  int enable_session_cache;
  int session_cache_timeout;

  int unclean_shutdown;

  int verify_client;
  int verify_depth;

  char *cipher_suite;
  int is_honor_cipher_order;

  int is_compression;

  void *crl;

  char *next_protos;
  
  pthread_mutex_t ssl_lock;
} ssl_config_t;

struct server_socket_t {
  ssl_config_t *ssl_config;
  
  int conn_socket_timeout;
  int tcp_no_delay;
  int tcp_keepalive;
  int tcp_cork;
  
  int fd;

  int port;

  pthread_mutex_t ssl_lock;
  pthread_mutex_t accept_lock;
  int verify_client;

  /* ssl engine */
  void *engine;
  /* ssl context */
  void *context;
  
  int (*accept) (server_socket_t *ss, connection_t *conn);
  int (*init) (connection_t *conn);
  void (*close) (server_socket_t *ss);
  int server_index;

  /* JniSocketImpl fields */
  jfieldID _localAddrBuffer;
  jfieldID _localAddrLength;
  jfieldID _localPort;
  
  jfieldID _remoteAddrBuffer;
  jfieldID _remoteAddrLength;
  jfieldID _remotePort;
  
  jfieldID _isSecure;
};

#define ALG_SSL2 0x01
#define ALG_SSL3 0x02
#define ALG_TLS1 0x04
#define ALG_TLS1_1 0x08
#define ALG_TLS1_2 0x10

#define Q_VERIFY_NONE 0
#define Q_VERIFY_OPTIONAL_NO_CA 1
#define Q_VERIFY_OPTIONAL 2
#define Q_VERIFY_REQUIRE 3

/* memory.c */
void cse_mem_init();
void cse_free(void *);
void *cse_malloc(int size);

/* std.c */
extern struct connection_ops_t std_ops;

int std_accept(server_socket_t *ss, connection_t *conn);
int std_init(connection_t *conn);
void std_close_ss(server_socket_t *ss);

int conn_close(connection_t *conn);

/* ssl.c */
int ssl_create(server_socket_t *ss, ssl_config_t *config);
connection_ops_t *ssl_get_ops();

/* java.c */
void baratine_printf_exception(JNIEnv *env,
                               const char *cl,
                               const char *fmt, ...);
void baratine_throw_exception(JNIEnv *env, const char *cl, const char *buf);
char *baratine_get_utf(JNIEnv *env, jstring jstring, char *buf, int buflen);

#define INTERRUPT_EXN -2
#define DISCONNECT_EXN -3
#define TIMEOUT_EXN -4

#define STACK_BUFFER_SIZE (16 * 1024)

#ifndef EWOULDBLOCK
#define EWOULDBLOCK EAGAIN
#endif

#ifdef _JAVA_JVMTI_H_

struct lru_cache_t *profile_create(jvmtiEnv *env, int size);

void
profile_add_stack(JNIEnv *jniEnv,
                  jvmtiEnv *jvmti,
		  struct lru_cache_t *cache,
		  jvmtiStackInfo *info,
		  jlong size);

jobject
profile_display(JNIEnv *jniEnv,
		jvmtiEnv *jvmti,
		struct lru_cache_t *cache,
                int max);

void
profile_clear(jvmtiEnv *jvmti,
	      struct lru_cache_t *cache);

struct symbol_table_t *
symbol_table_create(jvmtiEnv *jvmti);

char *
symbol_table_add(jvmtiEnv *jvmti,
           struct symbol_table_t *symbol_table,
           const char *name);

#endif

/*
jlong crc64_generate(jlong crc, char *value);
*/

int 
baratine_set_byte_array_region(JNIEnv *env,
                            jbyteArray j_buf, 
                            jint offset,
                            jint sublen,
                            char *c_buf);
int
baratine_get_byte_array_region(JNIEnv *env,
                             jbyteArray j_buf,
                             jint offset,
                             jint sublen,
                             char *c_buffer);

int poll_read(int fd, int ms);
int poll_write(int fd, int ms);

#define BARATINE_BLOCK_SIZE (8 * 1024)

#endif /* BARATINE_H */
