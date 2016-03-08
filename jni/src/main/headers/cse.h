/*
 * Copyright (c) 1999-2008 Caucho Technology.  All rights reserved.
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 */

#ifndef CSE_H
#define CSE_H

#ifdef B64
#define PTR jlong
#else
#define PTR jint
#endif
/*
#ifdef WIN32
typedef time_t unsigned int;
#else
#include <time.h>
#endif
*/

#ifndef WIN32
#define O_BINARY 0
#define O_TEXT 0
#endif

#include <time.h>

typedef struct mem_pool_t mem_pool_t;

#define CONN_POOL_SIZE 128
  
typedef struct stream_t stream_t;

typedef struct srun_status_t {
  volatile int is_fail;               /* true if the connect() failed         */
  volatile time_t last_fail;          /* when the last connect() failed       */
  volatile time_t last_unavail;       /* when the last 503 was received       */
} srun_status_t;

typedef struct srun_t {
  int is_valid;
  
  char *hostname;
  struct in_addr *host;
  int port;

  int connect_timeout;       /* time the connect() call should wait  */
  int idle_timeout;          /* time an idle socket should live      */
  int fail_recover_timeout;  /* time a dead srun stays dead          */
  int read_timeout;          /* how long to wait for a read (iis)    */
  int send_buffer_size;      /* how big the send buffer is           */
  
  void *lock;                /* lock specific to the srun            */

  srun_status_t *status;

  void *ssl;                 /* ssl context                          */
  int (*open) (stream_t *);
  int (*read) (stream_t *, void *buf, int len);
  int (*write) (stream_t *, const void *buf, int len);
  int (*close) (int socket, void *ssl);

  struct conn_t {            /* pool of idle sockets (ring)          */
    struct srun_t *srun;     /* owning srun                          */
    int socket;              /* socket file descriptor               */
    void *ssl;               /* ssl context                          */
    unsigned int last_time;  /* last time the socket was used        */
  } conn_pool[CONN_POOL_SIZE];

  int conn_head;             /* head of the pool (most recent used)  */
  int conn_tail;             /* tail of the pool (least recent used) */
  
  int max_sockets;
  int is_default;

  int active_sockets;        /* current number of active connections */
} srun_t;

typedef struct depend_t {
  struct depend_t *next;

  char *path;
  int last_modified;
  int last_size;
} depend_t;

typedef struct cluster_srun_t {
  char *id;
  
  srun_t *srun;
  struct cluster_t *cluster;

  int is_valid;
  int is_backup;
  int index;
} cluster_srun_t;

typedef struct cluster_t {
  char *id;

  struct cluster_t *next;

  struct config_t *config;
  
  cluster_srun_t *srun_list;
  
  int srun_capacity;
  int srun_size;
  
  int round_robin_index;
} cluster_t;

typedef struct location_t {
  struct location_t *next;
  struct web_app_t *application;
  
  char *prefix;
  char *suffix;
  int is_exact;

  int ignore;     /* If true, a matching will defer to the web server. */
} location_t;

typedef struct web_app_t {
  struct web_app_t *next;
  
  struct resin_host_t *host;
  
  char *context_path;

  int has_data;
  
  struct location_t *locations;
} web_app_t;

typedef struct resin_host_t {
  struct resin_host_t *next;
  struct resin_host_t *canonical;

  struct config_t *config;
  struct mem_pool_t *pool;
  
  char *name;
  int port;

  int has_data;
  time_t last_update_time;
  char etag[32];  /* etag for the last configuration update. */

  cluster_t cluster;
  
  struct web_app_t *applications;

  char error_message[256];
  char config_source[256];
} resin_host_t;

typedef struct config_t {
  void *p;

  void *web_pool;

  int has_config; /* true if there's config information, i.e. not defalt */
  
  void *cache_lock;
  void *config_lock;
  void *server_lock;
  char *error;
  
  int enable_caucho_status;
  int disable_sticky_sessions;
  int disable_session_failover;

  char *path;
  char *resin_home;
  
  char work_dir[1024];
  char config_path[1024];
  char config_file[1024];
  
  char error_page[1024];
  char session_url_prefix[256];
  char alt_session_url_prefix[256];
  char session_cookie[256];

  char error_message[1024];
  char *iis_priority;
  int override_iis_authentication;

  int default_host_max;
  
  srun_t **srun_list;
  int srun_capacity;

  cluster_t *cluster_head;
  
  cluster_t config_cluster;

  resin_host_t *hosts;

  /* for direct dispatching */
  resin_host_t *manual_host;

  /* how often to check for updates */
  int update_timeout;
  time_t last_update_time;
  time_t last_file_update;
  time_t start_time;
  int update_count;
  int is_updating;
} config_t;

/* windows needs this smaller than 64k */
#define BUF_LENGTH (16 * 1024)

struct stream_t {
  struct cluster_srun_t *cluster_srun;
  void *pool;
  void *web_pool;
  int update_count;

  int socket;
  void *ssl;
  
  struct config_t *config;

  unsigned char read_buf[BUF_LENGTH + 8];
  int read_offset;
  int read_length;

  unsigned char write_buf[BUF_LENGTH + 8];
  int write_length;

  int sent_data;
};

#define HMUX_CHANNEL        'C'
#define HMUX_ACK            'A'
#define HMUX_ERROR          'E'
#define HMUX_YIELD          'Y'
#define HMUX_QUIT           'Q'
#define HMUX_EXIT           'X'

#define HMUX_DATA           'D'
#define HMUX_URL            'U'
#define HMUX_STRING         'S'
#define HMUX_HEADER         'H'
#define HMUX_META_HEADER    'M'
#define HMUX_PROTOCOL       'P'

#define CSE_NULL            '?'
#define CSE_PATH_INFO       'b'
#define CSE_PROTOCOL        'c'
#define CSE_REMOTE_USER     'd'
#define CSE_QUERY_STRING    'e'
#define CSE_SERVER_PORT     'g'
#define CSE_REMOTE_HOST     'h'
#define CSE_REMOTE_ADDR     'i'
#define CSE_REMOTE_PORT     'j'
#define CSE_REAL_PATH       'k'
#define CSE_AUTH_TYPE       'n'
#define CSE_URI             'o'
#define CSE_CONTENT_LENGTH  'p'
#define CSE_CONTENT_TYPE    'q'
#define CSE_IS_SECURE       'r'
#define CSE_SESSION_GROUP   's'
#define CSE_CLIENT_CERT     't'
#define CSE_SERVER_TYPE	    'u'

#define HMUX_METHOD         'm'
#define HMUX_FLUSH          'f'
#define HMUX_SERVER_NAME    'v'
#define HMUX_STATUS         's'
#define HMUX_CLUSTER        'c'
#define HMUX_SRUN           's'
#define HMUX_SRUN_BACKUP    'b'
#define HMUX_SRUN_SSL       'e'
#define HMUX_UNAVAILABLE    'u'
#define HMUX_WEB_APP_UNAVAILABLE 'U'

#define CSE_HEADER          'H'
#define CSE_VALUE           'V'

#define CSE_STATUS          'S'
#define CSE_SEND_HEADER     'G'

/* #define CSE_PING            'P' */
#define CSE_QUERY           'Q'

#define CSE_ACK             'A'
#define CSE_DATA            'D'
#define CSE_FLUSH           'F'
#define CSE_KEEPALIVE       'K'
#define CSE_END             'Z'
#define CSE_CLOSE           'X'

#define HMUX_DISPATCH_PROTOCOL 0x102
#define HMUX_QUERY 0x102

#ifdef DEBUG
#define LOG(x) cse_log x
#define ERR(x) cse_log x
#else
#define LOG(x)
#define ERR(x)
#endif

#ifndef WIN32
#undef closesocket
#define closesocket(x) close(x)
#endif

mem_pool_t *cse_create_pool(config_t *config);
void cse_free_pool(mem_pool_t *);

/* base malloc */
void *cse_malloc(int size);

void *cse_alloc(mem_pool_t *p, int size);
char *cse_strdup(mem_pool_t *p, const char *string);

void cse_error(config_t *config, char *format, ...);

void cse_init_config(config_t *config);
/*
void cse_update_config(config_t *config, time_t now);
*/

resin_host_t *cse_match_request(config_t *config, const char *host, int port,
				const char *url, int should_escape, time_t now);
resin_host_t *cse_match_host(config_t *config,
			     const char *host, int port,
			     time_t now);

cluster_srun_t *
cse_add_cluster_server(mem_pool_t *pool, cluster_t *cluster,
		       const char *host, int port, const char *id,
		       int index, int is_backup, int is_ssl);

cluster_srun_t *cse_add_host(mem_pool_t *pool, cluster_t *cluster,
			     const char *host, int port);
cluster_srun_t *cse_add_backup(mem_pool_t *pool, cluster_t *cluster,
			       const char *host, int port);

void cse_add_config_server(mem_pool_t *pool, config_t *config,
			   const char *host, int port);

void cse_log(char *fmt, ...);

int cse_is_srun_failed(srun_t *srun);

int cse_is_srun_recent_fail(srun_t *srun, time_t now);

void cse_fail_srun(srun_t *srun, time_t now);

time_t cse_srun_fail_time(srun_t *srun);

void cse_ok_srun(srun_t *srun);

void cse_srun_unavail(srun_t *srun, time_t now);

int cse_is_srun_unavail(srun_t *srun, time_t now);

int cse_session_from_string(char *source, char *cookie, int *backup_index);

int cse_open(stream_t *s, cluster_t *cluster, cluster_srun_t *srun,
	     void *p, int wait);
void cse_close(stream_t *s, char *msg);
void cse_close_stream(stream_t *s);
void cse_close_sockets(config_t *config);
void cse_free_idle(stream_t *s, time_t now);
int cse_flush(stream_t *s);
int cse_fill_buffer(stream_t *s);
int cse_read_byte(stream_t *s);
void cse_write_byte(stream_t *s, int ch);
void cse_write(stream_t *s, const char *buf, int length);
int cse_read_all(stream_t *s, char *buf, int len);
int cse_skip(stream_t *s, int length);
int cse_read_limit(stream_t *s, char *buf, int buflen, int readlen);

void cse_write_packet(stream_t *s, char code, const char *buf, int length);
void cse_write_string(stream_t *s, char code, const char *buf);
int cse_read_string(stream_t *s, char *buf, int length);

void cse_kill_socket_cleanup(int socket, void *pool);
void cse_set_socket_cleanup(int socket, void *pool);
int
cse_open_connection(stream_t *s, cluster_t *cluster,
		    int session_index, int backup_offset,
                    time_t request_time, void *pool);
int cse_open_any_connection(stream_t *s, cluster_t *cluster,
			    time_t now);
int cse_open_live_connection(stream_t *s, cluster_t *cluster,
			     time_t now);
void close_srun(srun_t *srun, time_t now);

void *cse_create_lock(config_t *config);
void cse_free_lock(config_t *config, void *lock);
int cse_lock(void *lock);
void cse_unlock(void *lock);

void cse_proc_lock();

void cse_proc_unlock();

void cse_close_all();

void cse_add_depend(config_t *config, char *path);

void hmux_start_channel(stream_t *s, unsigned short channel);
void hmux_write_int(stream_t *s, char code, int i);
void hmux_write_string(stream_t *s, char code, const char *value);
void hmux_write_close(stream_t *s);
int hmux_read_len(stream_t *s);
int hmux_read_string(stream_t *s, char *buffer, int len);

#endif /* CSE_H */

