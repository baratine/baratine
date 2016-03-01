/*
 * Copyright (c) 1998-2014 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

#define _GNU_SOURCE
#include <sys/types.h>
#ifdef WIN32
#ifndef _WINSOCKAPI_ 
#define _WINSOCKAPI_
#endif 
#include <windows.h>
#include <winsock2.h>
#else
#include <sys/time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <netdb.h>
#include <unistd.h>
#ifdef POLL
#include <sys/poll.h>
#else
#include <sys/select.h>
#endif
#endif
#include <sys/stat.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
/* probably system-dependent */
#include <jni.h>
#include <errno.h>
#include <signal.h>
#ifdef linux
#include <sys/uio.h>
#endif

#include "baratine_os.h"

int std_init(connection_t *conn);
static int std_read(connection_t *conn, char *buf, int len, int timeout);
static int std_read_nonblock(connection_t *conn, char *buf, int len);
static int std_write(connection_t *conn, char *buf, int len);
static int std_write_nonblock(connection_t *conn, char *buf, int len);
int conn_close(connection_t *conn);
static void std_free(connection_t *conn);
static int std_read_client_certificate(connection_t *conn, char *buf, int len);
static int std_get_string(connection_t *conn, char *name,
                          char *buf, int buflen);

struct connection_ops_t std_ops = {
  std_init,
  std_read,
  std_read_nonblock,
  std_write,
  std_write_nonblock,
  conn_close,
  
  std_read_client_certificate,
  std_get_string,
  std_free,
};

static int
write_exception_status(connection_t *conn, int error)
{
  if (error == EAGAIN || error == EWOULDBLOCK || error == EINTR) {
    /*
    baratine_printf_exception(conn->jni_env,
                              "com/caucho/v5/vfs/ClientDisconnectException",
                              "timeout fd=%d errno=%d\n", conn->fd, error);
    */
    
    return TIMEOUT_EXN;
  }
  else if (error == EPIPE || error == ECONNRESET) {
    /*
    if (conn->jni_env) {
      baratine_printf_exception(conn->jni_env, "com/caucho/v5/vfs/ClientDisconnectException",
			     "Client disconnect fd=%d errno=%d\n",
			     conn->fd, error);
    }
    */
    
    return DISCONNECT_EXN;
  }
  else {
    return -1;
  }
}

static int
read_exception_status(connection_t *conn, int error)
{
  if (error == EAGAIN || error == EWOULDBLOCK || error == EINTR) {
    return TIMEOUT_EXN;
  }
  else if (error == EPIPE || error == ECONNRESET) {
    return -1;
  }
  else {
    return -1;
  }
}

static int
resin_tcp_set_recv_timeout(connection_t *conn, int timeout_ms)
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
std_read_nonblock(connection_t *conn, char *buf, int len)
{
  int fd;
  int result;
  int retry = 16;

  if (! conn) {
    return -1;
  }
  
  fd = conn->fd;
  
  if (fd <= 0) {
    return -1;
  }

  do {
    result = recv(fd, buf, len, 0);
  } while (result < 0 && errno == EINTR && retry-- > 0);

  return result;
}

#ifdef POLL
int
poll_read(int fd, int ms)
{
  struct pollfd pollfd[1];
  int result;
  int rd_hup = 0;
  int retry = 16;

#ifdef POLLRDHUP
  /* the other end has hung up */
  rd_hup = POLLRDHUP;
#endif

  if (fd <= 0) {
    return -1;
  }
  
  pollfd[0].fd = fd;
  pollfd[0].events = POLLIN|POLLPRI|rd_hup;
  pollfd[0].revents = 0;

  do {
    result = poll(pollfd, 1, ms);
  } while (result < 0 && errno == EINTR && retry-- > 0);

  if (result <= 0) {
    return result;
  }
  else if ((pollfd[0].revents & rd_hup) != 0) {
    errno = ECONNRESET;
    
    return -1;
  }
  else if ((pollfd[0].revents & (POLLIN|POLLPRI)) == 0) {
    return 1;
  }
  else {
    return result;
  }
}

int
poll_write(int fd, int ms)
{
  struct pollfd pollfd[1];
  
  pollfd[0].fd = fd;
  pollfd[0].events = POLLOUT;
  pollfd[0].revents = 0;

  return poll(pollfd, 1, ms);
}
#else /* select */
int
poll_read(int fd, int ms)
{
  fd_set read_set;
  struct timeval timeout;
  int result;

  if (fd <= 0) {
    return -1;
  }

  FD_ZERO(&read_set);
  FD_SET(fd, &read_set);

  timeout.tv_sec = ms / 1000;
  timeout.tv_usec = (ms % 1000) * 1000;

  result = select(fd + 1, &read_set, 0, 0, &timeout);

  return result;
}

int
poll_write(int fd, int ms)
{
  fd_set write_set;
  struct timeval timeout;

  FD_ZERO(&write_set);
  FD_SET(fd, &write_set);

  timeout.tv_sec = ms / 1000;
  timeout.tv_usec = (ms % 1000) * 1000;

  return select(fd + 1, 0, &write_set, 0, &timeout);
}
#endif

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
std_read(connection_t *conn, char *buf, int len, int timeout)
{
  int fd;
  int result;
  int retry = 3;
  int poll_result;
  int poll_timeout;

  if (! conn) {
    return -1;
  }
  
  fd = conn->fd;
  
  if (fd <= 0 || conn->is_read_shutdown) {
    return -1;
  }

  if (timeout >= 0) {
    poll_timeout = timeout;
  }
  else {
    poll_timeout = conn->socket_timeout;
  }

  if (conn->is_recv_timeout) {
    if (conn->recv_timeout != poll_timeout) {
      conn->recv_timeout = poll_timeout;

      resin_tcp_set_recv_timeout(conn, poll_timeout);
    }
  }
  else {
    poll_result = poll_read(fd, poll_timeout);

    if (poll_result <= 0) {
      return calculate_poll_result(conn, poll_result);
    }
  }

  do {
    /* recv returns 0 on end of file */
    result = recv(fd, buf, len, 0);
    //    fprintf(stderr, "rcv %d\n", result);
    
    if (result > 0) {
      return result;
    }
    else if (result == 0) {
      /* recv returns 0 on end of file */
      return -1;
    }

    if (errno == EINTR) {
      /* EAGAIN is returned by a timeout */
      poll_result = poll_read(fd, conn->socket_timeout);

      if (poll_result <= 0) {
        return calculate_poll_result(conn, poll_result);
      }
    }
    else {
      return read_exception_status(conn, errno);
    }
  } while (retry-- >= 0);
    
  if (result > 0) {
    return result;
  }
  else if (result == 0) {
    return -1;
  }
  else {
    return read_exception_status(conn, errno);
  }
}

static int
std_write(connection_t *conn, char *buf, int len)
{
  int fd;
  int result;
  int retry = 5;
  int poll_result;
  int error;

  if (! conn)
    return -1;

  fd = conn->fd;

  if (fd <= 0) {
    return -1;
  }

  conn->sent_data = 1;

  if (! conn->is_recv_timeout && poll_write(fd, conn->socket_timeout) == 0) {
    return TIMEOUT_EXN;
  }

  do {
    result = send(fd, buf, len, 0);
    
    if (result >= 0) {
      return result;
    }

    error = errno;
    
    if (errno == EINTR || errno == EAGAIN) {
      poll_result = poll_write(fd, conn->socket_timeout);

      if (poll_result == 0) {
        return TIMEOUT_EXN;
      }
      else if (poll_result < 0 && errno != EINTR) {
	return write_exception_status(conn, errno);
      }
    }
    else {
      return write_exception_status(conn, errno);
    }
  } while (retry-- >= 0);

  return write_exception_status(conn, error);
}

static int
std_write_nonblock(connection_t *conn, char *buf, int len)
{
  int fd;
  int result;

  if (! conn)
    return -1;

  fd = conn->fd;

  if (fd <= 0)
    return -1;

#ifndef O_NONBLOCK
  if (poll_write(fd, 0) <= 0)
    return 0;
#endif  

  result = send(fd, buf, len, 0);

  return result;
}

int
conn_close(connection_t *conn)
{
  int fd;
  int pipe0;
  int pipe1;

  if (! conn) {
    return -1;
  }

  fd = conn->fd;
  conn->fd = -1;
  
  pipe0 = conn->pipe[0];
  pipe1 = conn->pipe[1];

  conn->pipe[0] = 0;
  conn->pipe[1] = 0;

  if (fd > 0) {
    closesocket(fd);
  }

  if (pipe0 > 0) {
    close(pipe0);
  }

  if (pipe1 > 0) {
    close(pipe1);
  }

  return 1;
}

static void
std_free(connection_t *conn)
{
}

int
std_accept(server_socket_t *ss, connection_t *conn)
{
  int fd;
  int sock = -1;
  char sin_data[256];
  struct sockaddr *sin = (struct sockaddr *) &sin_data;
  unsigned int sin_len;
  int poll_result;
  int result;

  if (! ss || ! conn) {
    return 0;
  }
  
  fd = ss->fd;
  if (fd <= 0) {
    return 0;
  }

  if (conn->fd > 0) {
    return 0;
  }

#ifdef WIN32
  WaitForSingleObject(ss->accept_lock, INFINITE);
  fd = ss->fd;
  if (fd < 0) {
    ReleaseMutex(ss->accept_lock);
    return 0;
  }
#endif
  
  sin_len = sizeof(conn->client_data);
  memset(conn->client_data, 0, sin_len);
  conn->client_sin = (struct sockaddr *) &conn->client_data;
  sock = accept(fd, conn->client_sin, &sin_len);

#ifdef WIN32
  ReleaseMutex(ss->accept_lock);
#endif
  
  if (sock <= 0) {
    return 0;
  }

  conn->ss = ss;
  conn->fd = sock;

  return 1;
}

int
std_init(connection_t *conn)
{
  server_socket_t *ss = conn->ss;
  int sock = conn->fd;
  struct timeval timeout;
  unsigned int sin_len;

  conn->socket_timeout = ss->conn_socket_timeout;

  if (ss->tcp_no_delay) {/* && ! ss->tcp_cork) { */
    int flag = 1;

    setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, (char *) &flag, sizeof(int));
  }

  conn->tcp_cork = 0;
#ifdef TCP_CORK
  if (ss->tcp_cork) {
    int flag = 1;
    int result;
    /*
      result = setsockopt(sock, IPPROTO_TCP, TCP_CORK, (char *) &flag, sizeof(int));*/
    conn->tcp_cork = 1;
  }
#endif  

#ifdef SO_KEEPALIVE
  if (ss->tcp_keepalive) {
    int flag = 1;

    setsockopt(sock, SOL_SOCKET, SO_KEEPALIVE,
               (char *) &flag, sizeof(int));
  }
#endif

  conn->is_recv_timeout = 0;

#ifdef HAS_SOCK_TIMEOUT
  timeout.tv_sec = conn->socket_timeout / 1000;
  timeout.tv_usec = conn->socket_timeout % 1000 * 1000;

  if (setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO,
                 (char *) &timeout, sizeof(timeout)) == 0) {
    conn->is_recv_timeout = 1;
    conn->recv_timeout = conn->socket_timeout;

    timeout.tv_sec = conn->socket_timeout / 1000;
    timeout.tv_usec = conn->socket_timeout % 1000 * 1000;

    setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO,
               (char *) &timeout, sizeof(timeout));
  }
#endif

  conn->ssl_lock = &ss->ssl_lock;
  conn->ssl_sock = 0;
  /*
  conn->ops = &std_ops;
  */
  
  /*
  conn->client_sin = (struct sockaddr *) &conn->client_data;
  memcpy(conn->client_sin, sin, sizeof(conn->client_data));
  */
  conn->is_init = 0;

  conn->server_sin = (struct sockaddr *) &conn->server_data;
  sin_len = sizeof(conn->server_data);
  getsockname(sock, conn->server_sin, &sin_len);
  /*
  conn->ssl_cipher = 0;
  conn->ssl_bits = 0;
  */

  return 1;
}

void
std_close_ss(server_socket_t *ss)
{
  int fd;
  char server_data[128];
  struct sockaddr *server_sin = (struct sockaddr *) server_data;
  unsigned int sin_len;
  int result;

   if (! ss)
    return;
  
  fd = ss->fd;
  ss->fd = -1;

  if (fd <= 0) {
    return;
  }

  sin_len = sizeof(server_data);
  
  if (! getsockname(fd, server_sin, &sin_len)) {
    int retry;

    /* probably should check for 0 socket name for local host*/

    /* connect enough times to clear the threads waiting for a connection */
    for (retry = 20; retry >= 0; retry--) {
      int sock = socket(AF_INET, SOCK_STREAM, 0);
      int flags;
      int result;

      if (sock < 0)
	break;

#ifdef O_NONBLOCK
      flags = fcntl(sock, F_GETFL);
      fcntl(sock, F_SETFL, O_NONBLOCK|flags);
#endif

      result = connect(sock, server_sin, sin_len);

      closesocket(sock);

      if (result < 0)
	break;
    }
  }

  result = closesocket(fd);
  /* fprintf(stderr, "closesocket: %d %p\n", fd, ss->context); */
}

static int
std_read_client_certificate(connection_t *conn, char *buffer, int length)
{
  return -1;
}

static int
std_get_string(connection_t *conn, char *name,
               char *buffer, int length)
{
  return -1;
}
