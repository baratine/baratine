/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

#ifdef WIN32
#ifndef _WINSOCKAPI_ 
#define _WINSOCKAPI_
#endif 
#include <windows.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <io.h>
#else
#define _GNU_SOURCE
#include <sys/param.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/resource.h>
#include <dirent.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <unistd.h>

#ifdef EPOLL
#include <sys/epoll.h>
#endif

#ifdef POLL
#include <sys/poll.h>
#else
#include <sys/select.h>
#endif

#include <pwd.h>
#include <syslog.h>
#include <netdb.h>

#ifdef HAS_SENDFILE
#include <sys/sendfile.h>
#endif

#endif

#ifdef linux
#include <linux/version.h>
#endif

#include <sys/stat.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <signal.h>
#include <errno.h>
/* probably system-dependent */
#include <jni.h>

#include <fcntl.h>
#ifdef linux
#include <sys/uio.h>
#endif
#include "baratine_os.h"

#define STACK_BUFFER_SIZE (16 * 1024)

void
cse_log(char *fmt, ...)
{
#ifdef DEBUG  
  va_list list;

  va_start(list, fmt);
  vfprintf(stderr, fmt, list);
  va_end(list);
#endif
}

static char *
q_strdup(char *str)
{
  size_t len = strlen(str);
  char *dup = cse_malloc(len + 1);

  strcpy(dup, str);

  return dup;
}

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_nativeAllocate(JNIEnv *env,
						 jobject obj)
{
  connection_t *conn;

  conn = (connection_t *) malloc(sizeof(connection_t));
  
  memset(conn, 0, sizeof(connection_t));
  conn->fd = -1;
  conn->client_sin = (struct sockaddr *) conn->client_data;
  conn->server_sin = (struct sockaddr *) conn->server_data;

  conn->ops = &std_ops;

#ifdef WIN32
  // conn->event = WSACreateEvent();
#endif

  return (jlong) (PTR) conn;
}

static int
resin_tcp_nodelay(connection_t *conn)
{
  int fd = conn->fd;
  int flag = 1;
  int result;

  result = setsockopt(fd, IPPROTO_TCP, TCP_NODELAY,
                      (char *) &flag, sizeof(int));

  return result;
}

static int
resin_tcp_cork(connection_t *conn)
{
#ifdef TCP_CORK  
  int fd = conn->fd;
  int flag = 1;
  int result;

  if (! conn->tcp_cork || conn->is_cork || conn->ssl_sock) {
    return;
  }

  conn->is_cork = 1;
  result = setsockopt(fd, IPPROTO_TCP, TCP_CORK,
                      (char *) &flag, sizeof(int));


  return result;
#else
  return 1;
#endif
}

static int
resin_tcp_uncork(connection_t *conn)
{
#ifdef TCP_CORK  
  int fd = conn->fd;
  int flag = 0;
  int result;

  if (! conn->tcp_cork || ! conn->is_cork || conn->ssl_sock) {
    return;
  }

  conn->is_cork = 0;

  result = setsockopt(fd, IPPROTO_TCP, TCP_CORK,
                      (char *) &flag, sizeof(int));

  return result;
#else
  return 1;
#endif
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_readNative(JNIEnv *env,
					     jobject obj,
					     jlong conn_fd,
					     jbyteArray buf,
					     jint offset,
					     jint length,
					     jlong timeout_ms)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  int sublen;
  char buffer[STACK_BUFFER_SIZE];
  char *temp_buf;

  if (! conn || conn->fd < 0 || ! buf) {
    return -1;
  }

  conn->jni_env = env;

  if (length < STACK_BUFFER_SIZE)
    sublen = length;
  else
    sublen = STACK_BUFFER_SIZE;

  sublen = conn->ops->read(conn, buffer, sublen, (int) timeout_ms);

  /* Should probably have a different response for EINTR */
  if (sublen <= 0) {
    return sublen;
  }

  if (length < sublen) {
    sublen = length;
  }

  baratine_set_byte_array_region(env, buf, offset, sublen, buffer);

  return sublen;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniStream_readNonBlockNative(JNIEnv *env,
						 jobject obj,
						 jlong conn_fd,
						 jbyteArray buf,
						 jint offset,
						 jint length)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  int sublen;
  char buffer[STACK_BUFFER_SIZE];

  if (! conn || conn->fd < 0 || ! buf)
    return -1;

  conn->jni_env = env;

  if (length < STACK_BUFFER_SIZE)
    sublen = length;
  else
    sublen = STACK_BUFFER_SIZE;

  sublen = conn->ops->read_nonblock(conn, buffer, sublen);

  /* Should probably have a different response for EINTR */
  if (sublen < 0) {
    return sublen;
  }

  baratine_set_byte_array_region(env, buf, offset, sublen, buffer);

  return sublen;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_writeNative(JNIEnv *env,
					      jobject obj,
					      jlong conn_fd,
					      jbyteArray j_buf,
					      jint offset,
					      jint length)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  char buffer[STACK_BUFFER_SIZE];
  char *c_buf;
  int sublen;
  int write_length = 0;
  int result;
    
  if (! conn || conn->fd < 0 || ! j_buf) {
    return -1;
  }

  conn->jni_env = env;
  /*
  resin_tcp_cork(conn);
  */
  while (length > 0) {
    jbyte *cBuf;

    if (length < sizeof(buffer)) {
      sublen = length;
    }
    else {
      sublen = sizeof(buffer);
    }

    baratine_get_byte_array_region(env, j_buf, offset, sublen, buffer);

    result = conn->ops->write(conn, buffer, sublen);
    
    if (result == length) {
      return result + write_length;
    }
    else if (result < 0) {
      /*
      fprintf(stdout, "write-ops: write result=%d errno=%d\n", 
              result, errno);
      fflush(stdout);
      */

      return result;
    }

    length -= result;
    offset += result;
    write_length += result;
  }

  return write_length;
}

#undef HAS_SPLICE
#ifdef HAS_SPLICE

static int
write_splice(connection_t *conn,
             jlong mmap_address,
             int sublen)
{
  struct iovec io;
  int result;
  int fd = conn->fd;
  int write_len = 0;

  if (fd < 0) {
    return -1;
  }

  if (conn->ssl_sock) {
    return conn->ops->write(conn, 
                            (void*) (PTR) (mmap_address), 
                            sublen);
  }

  io.iov_base = (void*) (mmap_address);
  io.iov_len = sublen;

  if (conn->pipe[0] <= 0) {
    if (pipe(conn->pipe) < 0) {
      fprintf(stderr, "BADPIPE\n");
    }
  }

  sublen = vmsplice(conn->pipe[1], &io, 1, SPLICE_F_MOVE);

  if (sublen < 0) {
    if (errno != EAGAIN && errno != ECONNRESET && errno != EPIPE) {
      fprintf(stderr, "vmsplice addr:%lx result:%d %d\n", 
              mmap_address,
              sublen, errno);
    }
    
    return -1;
  }

  write_len = 0;

  while (write_len < sublen) {
    int delta = sublen - write_len;

    result = splice(conn->pipe[0], 0, fd, 0, delta,
                    SPLICE_F_MOVE|SPLICE_F_MORE);

    if (result <= 0) {
      if (errno != EAGAIN && errno != ECONNRESET && errno != EPIPE) {
        fprintf(stderr, "splice result:%d pipe:%d fd:%d addr:%lx errno:%d\n",
                result, conn->pipe[0], fd, mmap_address, errno);
      }

      return -1;
    }

    write_len += result;
  }

  return sublen;
}

#else

static int
write_splice(connection_t *conn,
             long mmap_address,
             int sublen)
{
  return conn->ops->write(conn, 
                          (void*) (PTR) (mmap_address), 
                          sublen);
}

#endif

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_writeMmapNative(JNIEnv *env,
                                                  jobject obj,
                                                  jlong conn_fd,
                                                  jlong mmap_address,
                                                  jlongArray mmap_blocks_arr,
                                                  jlong mmap_offset,
                                                  jlong mmap_length)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  int sublen;
  int write_length = 0;
  int result;
  long block_size = BARATINE_BLOCK_SIZE;
  int i;
  int blocks_len;
  jlong *mmap_blocks = 0;
    
  if (! conn || conn->fd < 0) {
    return -1;
  }
  
  conn->jni_env = env;

  blocks_len = (*env)->GetArrayLength(env, mmap_blocks_arr);
  mmap_blocks = (*env)->GetLongArrayElements(env, mmap_blocks_arr, 0);

  if (! mmap_blocks) {
    return -1;
  }

  resin_tcp_cork(conn);

  i = 0;
  for (; mmap_length > 0; mmap_length -= block_size, i++) {
    jint sublen = (jint) mmap_length;
    jlong sub_offset = 0;

    if (block_size < sublen) {
      sublen = block_size;
    }

    sub_offset = mmap_blocks[i] & ~(block_size - 1);

    while (sublen > 0) {
      result = write_splice(conn, mmap_address + sub_offset, sublen);

      if (result > 0) {
        write_length += result;
        sub_offset += result;
        sublen -= result;
      }
      else {
        sublen = -1;
        break;
      }
    }

    if (sublen < 0) {
      break;
    }
  }

  resin_tcp_uncork(conn);

  (*env)->ReleaseLongArrayElements(env, mmap_blocks_arr, mmap_blocks, 0);

  if (result < 0) {
    return result;
  }
  else {
    return write_length + result;
  }
}

#ifdef HAS_SENDFILE

static int
jni_open_file(JNIEnv *env,
              jbyteArray name,
              jint length)
{
  char buffer[8192];
  int fd;
  int flags;
  int offset = 0;

  if (! name || length <= 0 || sizeof(buffer) <= length) {
    return -1;
  }

  (*env)->GetByteArrayRegion(env, name, offset, length, (void*) buffer);

  buffer[length] = 0;

  flags = O_RDONLY;
  
#ifdef O_BINARY
  flags |= O_BINARY;
#endif
  
#ifdef O_LARGEFILE
  flags |= O_LARGEFILE;
#endif
 
  fd = open(buffer, flags, 0664);

  return fd;
}

static jint
caucho_sendfile_ssl(connection_t *conn, int file_fd)
{
  int len;
  char buf[8192];
  int result;

  while ((len = read(file_fd, buf, sizeof(buf))) > 0) {
    result = conn->ops->write(conn, buf, len);

    if (result < 0) {
      return result;
    }
  }

  return 1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_writeSendfileNative(JNIEnv *env,
                                                      jobject obj,
                                                      jlong conn_fd,
                                                      jbyteArray j_buf,
                                                      jint offset,
                                                      jint length,
                                                      jbyteArray name,
                                                      jint name_length,
                                                      jlong file_length)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  int sublen;
  int write_length = 0;
  int result;
  int fd;
  off_t sendfile_offset;
    
  if (! conn || conn->fd < 0 || conn->ssl_bits) {
    return -1;
  }

  resin_tcp_cork(conn);

  if (length > 0) {
    Java_com_caucho_v5_jni_JniSocketImpl_writeNative(env, obj, conn_fd,
                                                  j_buf, offset, length);
  }

  conn->jni_env = env;

  fd = jni_open_file(env, name, name_length);

  if (fd < 0) {
    /* file not found */
    return -1;
  }

  if (conn->ssl_context) {
    int result;

    result = caucho_sendfile_ssl(conn, fd);

    close(fd);

    return result;
  }
  
  resin_tcp_cork(conn);

  sendfile_offset = 0;

  result = sendfile(conn->fd, fd, &sendfile_offset, file_length);

  close(fd);

  resin_tcp_uncork(conn);

  if (result < 0) {
    if (errno != EAGAIN && errno != ECONNRESET && errno != EPIPE) {
      fprintf(stderr, "sendfile ERR %d %d\n", result, errno);
    }
    
    return result;
  }
  else {
    return write_length + result;
  }
}

#else

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_writeSendfileNative(JNIEnv *env,
                                                      jobject obj,
                                                      jlong conn_fd,
                                                      jint fd,
                                                      jlong fd_offset,
                                                      jint fd_length)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  int sublen;
  int write_length = 0;
  int result;
  off_t sendfile_offset;
  char buffer[16 * 1024];
    
  if (! conn || conn->fd < 0) {
    return -1;
  }
  
  conn->jni_env = env;

  resin_tcp_cork(conn);

  sendfile_offset = fd_offset;

  while (fd_length > 0) {
    sublen = sizeof(buffer);

    if (fd_length < sublen) {
      sublen = fd_length;
    }
    
    sublen = read(fd, buffer, sublen);

    if (sublen < 0)
      return sublen;

    result = conn->ops->write(conn, buffer, sublen);

    if (result < 0) {
      fprintf(stderr, "ERR %d %d\n", result, errno);
      return result;
    }

    write_length += sublen;
    fd_length -= sublen;
  }

  // resin_tcp_uncork(conn);

  return write_length + result;
}

#endif

JNIEXPORT jobject JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_createByteBuffer(JNIEnv *env,
                                                   jobject this,
                                                   jint length)
{
  char *buffer = malloc(length);

  return (*env)->NewDirectByteBuffer(env, buffer, length);
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_writeNativeNio(JNIEnv *env,
                                                 jobject obj,
                                                 jlong conn_fd,
                                                 jobject byte_buffer,
                                                 jint offset,
                                                 jint length)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  char *ptr;
  int sublen;
  int write_length = 0;
  int result;

  if (! conn || conn->fd < 0 || ! byte_buffer) {
    return -1;
  }
  
  /* resin_tcp_cork(conn); */
  
  conn->jni_env = env;

  ptr = (*env)->GetDirectBufferAddress(env, byte_buffer);

  if (! ptr)
    return -1;

  while (length > 0) {
    /*
    if (length < sizeof(buffer))
      sublen = length;
    else
      sublen = sizeof(buffer);
    */

    sublen = length;

    result = conn->ops->write(conn, ptr + offset, sublen);

    /*
    ptr = (*env)->GetByteArrayElements(env, buf, &is_copy);

    if (ptr) {
      result = conn->ops->write(conn, ptr + offset, sublen);

      (*env)->ReleaseByteArrayElements(env, buf, ptr, is_copy);
    }
    */
    
    if (result == length)
      return result + write_length;
    else if (result < 0) {
      /*
      fprintf(stdout, "write-ops: write result=%d errno=%d\n", 
              result, errno);
      fflush(stdout);
      */
      return result;
    }

    length -= result;
    offset += result;
    write_length += result;
  }

  return write_length;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_writeNative2(JNIEnv *env,
					       jobject obj,
					       jlong conn_fd,
					       jbyteArray buf1,
					       jint off1,
					       jint len1,
					       jbyteArray buf2,
					       jint off2,
					       jint len2)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  char buffer[2 * STACK_BUFFER_SIZE];
  int sublen;
  int buffer_offset;
  int write_length = 0;

  buffer_offset = 0;

  if (! conn || conn->fd < 0 || ! buf1 || ! buf2)
    return -1;
  
  /* resin_tcp_cork(conn); */

  conn->jni_env = env;

  while (sizeof(buffer) < len1) {
    sublen = sizeof(buffer);
    
    baratine_get_byte_array_region(env, buf1, off1, sublen, buffer);
      
    sublen = conn->ops->write(conn, buffer, sublen);

    if (sublen < 0) {
      /* XXX: probably should throw exception */
      return sublen;
    }

    len1 -= sublen;
    off1 += sublen;
    write_length += sublen;
  }

  baratine_get_byte_array_region(env, buf1, off1, len1, buffer);
  buffer_offset = len1;

  while (buffer_offset + len2 > 0) {
    int result;
    
    if (len2 < sizeof(buffer) - buffer_offset)
      sublen = len2;
    else
      sublen = sizeof(buffer) - buffer_offset;

    baratine_get_byte_array_region(env, buf2, off2, sublen,
			       buffer + buffer_offset);
      
    result = conn->ops->write(conn, buffer, buffer_offset + sublen);

    if (result < 0) {
      /* XXX: probably should throw exception */
      return result;
    }

    len2 -= sublen;
    off2 += sublen;
    write_length += sublen + buffer_offset;
    buffer_offset = 0;
  }

  return write_length;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_flushNative(JNIEnv *env,
					      jobject obj,
					      jlong conn_fd)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  int fd;

  if (! conn) {
    return -1;
  }

  fd = conn->fd;

  if (fd <= 0) {
    return -1;
  }

  resin_tcp_uncork(conn);

  /* return cse_flush_request(res); */
  return 1;
}

/**
 * Force an interrupt so listening threads will close
 */
JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_nativeCloseFd(JNIEnv *env,
						jobject obj,
						jlong conn_fd)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  int fd = -1;

  if (conn) {
    fd = conn->fd;
    conn->fd = -1;
  }

  if (fd >= 0) {
    /*
    fprintf(stdout, "CLOSE2 %d\n", fd);
    fflush(stdout);
    */
    
    closesocket(fd);
  }
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_nativeClose(JNIEnv *env,
					      jobject obj,
					      jlong conn_fd)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;

  if (conn && conn->fd >= 0) {
    conn->jni_env = env;
    
    fflush(stderr);

    conn->ops->close(conn);
  }
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_nativeCloseWrite(JNIEnv *env,
                                                   jobject obj,
                                                   jlong conn_fd)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;

  if (conn && conn->fd >= 0) {
    int result;

    result = shutdown(conn->fd, SHUT_WR);
  }
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_writeCloseNative(JNIEnv *env,
                                                   jobject obj,
                                                   jlong conn_fd,
                                                   jbyteArray buf,
                                                   jint offset,
                                                   jint length)
{
  int value;

  value = Java_com_caucho_v5_jni_JniSocketImpl_writeNative(env, obj, conn_fd,
                                                        buf, offset, length);

  Java_com_caucho_v5_jni_JniSocketImpl_nativeClose(env, obj, conn_fd);

  return value;
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_nativeFree(JNIEnv *env,
					     jobject obj,
					     jlong conn_fd)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;

  if (conn) {
    if (conn->fd >= 0) {
      conn->jni_env = env;

      conn->ops->close(conn);
    }

#ifdef WIN32
	  /*
    if (conn->event)
      WSACloseEvent(conn->event);
	  */
#endif

    conn->ops->free(conn);
    
    free(conn);
  }
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_isSecure(JNIEnv *env,
                                        jobject obj,
                                        jlong conn_fd)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;

  if (! conn)
    return 0;
  
  return conn->ssl_sock != 0 && conn->ssl_cipher != 0;
}

JNIEXPORT jstring JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_getCipher(JNIEnv *env,
                                            jobject obj,
                                            jlong conn_fd)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;

  if (! conn || ! conn->ssl_sock || ! conn->ssl_cipher)
    return 0;
  
  return (*env)->NewStringUTF(env, conn->ssl_cipher);
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_getCipherBits(JNIEnv *env,
					     jobject obj,
					     jlong conn_fd)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;

  if (! conn || ! conn->ssl_sock)
    return 0;
  else
    return conn->ssl_bits;
}

JNIEXPORT jstring JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_getInfoString(JNIEnv *env,
                                                jobject obj,
                                                jlong conn_fd,
                                                jstring j_name)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  char name[1024];
  char buffer[4096];
  int len;

  if (! conn || conn->fd < 0 || ! j_name) {
    return 0;
  }

  if (! baratine_get_utf(env, j_name, name, sizeof(name))) {
    return 0;
  }
  
  len = conn->ops->get_string(conn, name, buffer, sizeof(buffer));

  if (len <= 0) {
    return 0;
  }

  buffer[len] = 0;

  return (*env)->NewStringUTF(env, buffer);
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_nativeIsEof(JNIEnv *env,
                                              jobject obj,
                                              jlong conn_fd)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  int fd;
  int result;
  char buffer[1];
  int ms = 0;

  if (! conn)
    return 1;

  fd = conn->fd;

  if (fd < 0)
    return 1;

#ifdef MSG_DONTWAIT
  result = recv(fd, buffer, 1, MSG_DONTWAIT|MSG_PEEK);
#else
  result = 1;
#endif

  return result == 0;
}

#ifdef AI_NUMERICHOST

static struct sockaddr_in *
lookup_addr(JNIEnv *env, const char *addr_name, int port,
	    char *buffer, int *p_family, int *p_protocol,
	    socklen_t *p_sin_length)
{
  struct addrinfo hints;
  struct addrinfo *addr;
  struct sockaddr_in *sin;
  int sin_length;
  char port_name[16];
  
  memset(&hints, 0, sizeof(hints));

  hints.ai_socktype = SOCK_STREAM;
  hints.ai_family = PF_UNSPEC;
  hints.ai_flags = AI_NUMERICHOST;

  sprintf(port_name, "%d", port);

  if (getaddrinfo(addr_name, port_name, &hints, &addr)) {
    baratine_printf_exception(env, "java/net/SocketException", "can't find address %s", addr_name);
    return 0;
  }

  *p_family = addr->ai_family;
  *p_protocol = addr->ai_protocol;
  sin_length = addr->ai_addrlen;
  memcpy(buffer, addr->ai_addr, sin_length);
  sin = (struct sockaddr_in *) buffer;
  freeaddrinfo(addr);

  *p_sin_length = sin_length;

  return sin;
}

#else

static struct sockaddr_in *
lookup_addr(JNIEnv *env, char *addr_name, int port,
	    char *buffer, int *p_family, int *p_protocol, int *p_sin_length)
{
  struct sockaddr_in *sin = (struct sockaddr_in *) buffer;
  
  memset(sin, 0, sizeof(struct sockaddr_in));

  *p_sin_length = sizeof(struct sockaddr_in);
  
  sin->sin_family = AF_INET;
  *p_family = AF_INET;
  *p_protocol = 0;

  sin->sin_addr.s_addr = inet_addr(addr_name);
 
  sin->sin_port = htons((unsigned short) port);

  return sin;
}

#endif

static void
init_server_socket(JNIEnv *env, server_socket_t *ss)
{
  jclass jniServerSocketClass;
  
  jniServerSocketClass = (*env)->FindClass(env, "com/caucho/v5/jni/JniSocketImpl");

  if (jniServerSocketClass) {
    ss->_isSecure = (*env)->GetFieldID(env, jniServerSocketClass,
				       "_isSecure", "Z");
    if (! ss->_isSecure)
      baratine_throw_exception(env, "com/caucho/v5/config/ConfigException",
			    "can't load _isSecure field");
      
    /*
    ss->_localAddrBuffer = (*env)->GetFieldID(env, jniServerSocketClass,
					      "_localAddrBuffer", "[B");
    if (! ss->_localAddrBuffer)
      baratine_throw_exception(env, "com/caucho/v5/config/ConfigException",
			    "can't load _localAddrBuffer field");
    
    ss->_localAddrLength = (*env)->GetFieldID(env, jniServerSocketClass,
					      "_localAddrLength", "I");
    if (! ss->_localAddrLength)
      baratine_throw_exception(env, "com/caucho/v5/config/ConfigException",
			    "can't load _localAddrLength field");
    */
    
    ss->_localPort = (*env)->GetFieldID(env, jniServerSocketClass,
					"_localPort", "I");
    if (! ss->_localPort)
      baratine_throw_exception(env, "com/caucho/v5/config/ConfigException",
			    "can't load _localPort field");
      
    /*
    ss->_remoteAddrBuffer = (*env)->GetFieldID(env, jniServerSocketClass,
					       "_remoteAddrBuffer", "[B");
    if (! ss->_remoteAddrBuffer)
      baratine_throw_exception(env, "com/caucho/v5/config/ConfigException",
			    "can't load _remoteAddrBuffer field");
    
    ss->_remoteAddrLength = (*env)->GetFieldID(env, jniServerSocketClass,
					      "_remoteAddrLength", "I");
    if (! ss->_remoteAddrLength)
      baratine_throw_exception(env, "com/caucho/v5/config/ConfigException",
			    "can't load _remoteAddrLength field");
    */
    
    ss->_remotePort = (*env)->GetFieldID(env, jniServerSocketClass,
					 "_remotePort", "I");
    if (! ss->_remotePort)
      baratine_throw_exception(env, "com/caucho/v5/config/ConfigException",
			    "can't load _remotePort field");
      
  }
}

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_bindPort(JNIEnv *env,
						 jobject obj,
						 jstring jaddr,
						 jint port)
{
  int val = 0;
  char addr_name[256];
  const char *temp_string = 0;
  int sock;
  int family = 0;
  int protocol = 0;
  server_socket_t *ss;
  char sin_data[512];
  struct sockaddr_in *sin = (struct sockaddr_in *) sin_data;
  socklen_t sin_length = sizeof(sin_data);

#ifdef WIN32
  {
	  WSADATA data;
	  WORD version = MAKEWORD(2,2);
	  WSAStartup(version, &data);
  }
#endif
  
  addr_name[0] = 0;
  memset(sin_data, 0, sizeof(sin_data));

  if (jaddr != 0) {
    temp_string = (*env)->GetStringUTFChars(env, jaddr, 0);
  
    if (temp_string) {
      strncpy(addr_name, temp_string, sizeof(addr_name));
      addr_name[sizeof(addr_name) - 1] = 0;
  
      (*env)->ReleaseStringUTFChars(env, jaddr, temp_string);
    }
    else {
      baratine_throw_exception(env, "java/lang/NullPointerException", "missing addr");
      return 0;
    }

    lookup_addr(env, addr_name, port, sin_data,
                &family, &protocol, &sin_length);
  }
  else {
    struct sockaddr_in6 *sin6;
    
    sin6 = (struct sockaddr_in6 *) sin_data;
    sin6->sin6_family = AF_INET6;
    sin6->sin6_port = htons(port);
    family = AF_INET6;
    protocol = IPPROTO_TCP;
    sin_length = sizeof(struct sockaddr_in6);
  }
  
  if (! sin)
    return 0;

  sock = socket(family, SOCK_STREAM, 0);
  if (sock < 0) {
    return 0;
  }
  
  val = 1;
  if (setsockopt(sock, SOL_SOCKET, SO_REUSEADDR,
		 (char *) &val, sizeof(int)) < 0) {
    closesocket(sock);
    return 0;
  }

  val = 0;
#ifdef IPV6_V6ONLY
  if (family != AF_INET6) {
  }
  else if (setsockopt(sock, IPPROTO_IPV6, IPV6_V6ONLY,
                 (char *) &val, sizeof(int)) < 0) {
    fprintf(stderr, "Cannot set ipv6_v6only");
  }
#endif

  if (bind(sock, (struct sockaddr *) sin_data, sin_length) < 0) {
    int i = 5;
    int result = 0;
    
    /* somewhat of a hack to clear the old connection. */
    for (i = 5; result == 0 && i >= 0; i--) {
      int flags;
      int fd = socket(AF_INET, SOCK_STREAM, 0);

#ifdef O_NONBLOCK
      flags = fcntl(fd, F_GETFL);
      fcntl(fd, F_SETFL, O_NONBLOCK|flags);
#endif

      result = connect(fd, (struct sockaddr *) &sin_data, sizeof(sin));
      closesocket(fd);
    }

    result = -1;
    for (i = 50; result < 0 && i >= 0; i--) {
      result = bind(sock, (struct sockaddr *) sin_data, sin_length);

      if (result < 0) {
	struct timeval tv;

	tv.tv_sec = 0;
	tv.tv_usec = 100000;

	select(0, 0, 0, 0, &tv);
      }
    }

    if (result < 0) {
      closesocket(sock);
      return 0;
    }
  }

  sin_length = sizeof(sin_data);
  memset(sin_data, 0, sin_length);
  getsockname(sock, (struct sockaddr *) sin_data, &sin_length);

  /* must be 0 if the poll is missing for accept */
#if 0 && defined(O_NONBLOCK)
  /*
   * sets nonblock to ensure the timeout work in the case of multiple threads.
   */
  {
    int flags;
    int result;
    
    flags = fcntl(sock, F_GETFL);
    result = fcntl(sock, F_SETFL, O_NONBLOCK|flags);
  }
#endif

  ss = (server_socket_t *) cse_malloc(sizeof(server_socket_t));
  memset(ss, 0, sizeof(server_socket_t));

  ss->fd = sock;
  ss->port = ntohs(sin->sin_port);

  ss->conn_socket_timeout = 65000;

  ss->accept = &std_accept;
  ss->init = &std_init;
  ss->close = &std_close_ss;

#ifdef WIN32
  ss->accept_lock = CreateMutex(0, 0, 0);
  ss->ssl_lock = CreateMutex(0, 0, 0);
#endif

  init_server_socket(env, ss);
  
  return (PTR) ss;
}

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeOpenPort(JNIEnv *env,
						       jobject obj,
						       jint sock,
						       jint port)
{
  server_socket_t *ss;

#ifdef WIN32
  {
	  WSADATA data;
	  WORD version = MAKEWORD(2,2);
	  WSAStartup(version, &data);
  }
#endif

  if (sock < 0)
    return 0;

  ss = (server_socket_t *) cse_malloc(sizeof(server_socket_t));

  if (ss == 0)
    return 0;
  
  memset(ss, 0, sizeof(server_socket_t));

  ss->fd = sock;
  ss->port = port;
  
  ss->conn_socket_timeout = 65000;

  ss->accept = &std_accept;
  ss->init = &std_init;
  ss->close = &std_close_ss;

#ifdef WIN32
  ss->accept_lock = CreateMutex(0, 0, 0);
  ss->ssl_lock = CreateMutex(0, 0, 0);
#endif
  
  init_server_socket(env, ss);
  
  return (PTR) ss;
}

#ifdef WIN32

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_bindUnix(JNIEnv *env,
						 jobject obj,
						 jstring jpath)
{
  return 0;
}

#else

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_bindUnix(JNIEnv *env,
						 jobject obj,
						 jstring jpath)
{
  int val = 0;
  char path_name[256];
  const char *temp_string = 0;
  int sock;
  int family = 0;
  int protocol = 0;
  server_socket_t *ss;
  char sin_data[512];
  struct sockaddr_un *sin = (struct sockaddr_un *) sin_data;
  socklen_t sin_length = sizeof(sin_data);
  
  path_name[0] = 0;
  memset(sin_data, 0, sizeof(sin_data));

  if (jpath == 0) {
    baratine_throw_exception(env, "java/lang/NullPointerException", "missing path");
    return 0;
  }

  temp_string = (*env)->GetStringUTFChars(env, jpath, 0);
  
  if (! temp_string) {
    baratine_throw_exception(env, "java/lang/NullPointerException", "missing addr");
    return 0;
  }

  strncpy(path_name, temp_string, sizeof(path_name));
  path_name[sizeof(path_name) - 1] = 0;
  (*env)->ReleaseStringUTFChars(env, jpath, temp_string);

  sin->sun_family = AF_UNIX;
  strncpy(sin->sun_path, path_name, sizeof(sin->sun_path));
  sin_length = sizeof(struct sockaddr_un);

  sock = socket(AF_UNIX, SOCK_STREAM, 0);
  if (sock < 0) {
    return 0;
  }
  
  val = 1;
  if (setsockopt(sock, SOL_SOCKET, SO_REUSEADDR,
		 (char *) &val, sizeof(int)) < 0) {
    closesocket(sock);
    return 0;
  }

  val = 0;

  if (bind(sock, (struct sockaddr *) sin_data, sin_length) < 0) {
    int i = 5;
    int result = 0;
    
    /* somewhat of a hack to clear the old connection. */
    for (i = 5; result == 0 && i >= 0; i--) {
      int flags;
      int fd = socket(AF_UNIX, SOCK_STREAM, 0);

#ifdef O_NONBLOCK
      flags = fcntl(fd, F_GETFL);
      fcntl(fd, F_SETFL, O_NONBLOCK|flags);
#endif

      result = connect(fd, (struct sockaddr *) &sin_data, sizeof(sin));
      closesocket(fd);
    }

    result = -1;
    for (i = 50; result < 0 && i >= 0; i--) {
      result = bind(sock, (struct sockaddr *) sin_data, sin_length);

      if (result < 0) {
	struct timeval tv;

	tv.tv_sec = 0;
	tv.tv_usec = 100000;

	select(0, 0, 0, 0, &tv);
      }
    }

    if (result < 0) {
      closesocket(sock);
      return 0;
    }
  }

  sin_length = sizeof(sin_data);
  memset(sin_data, 0, sin_length);
  getsockname(sock, (struct sockaddr *) sin_data, &sin_length);

  ss = (server_socket_t *) cse_malloc(sizeof(server_socket_t));
  memset(ss, 0, sizeof(server_socket_t));

  ss->fd = sock;
  ss->port = 1;

  ss->conn_socket_timeout = 65000;

  ss->accept = &std_accept;
  ss->init = &std_init;
  ss->close = &std_close_ss;

  init_server_socket(env, ss);

  return (PTR) ss;
}

#endif

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeSetConnectionSocketTimeout(JNIEnv *env,
						       jobject obj,
						       jlong ss_fd,
						       jint timeout)
{
  server_socket_t *ss = (server_socket_t *) (PTR) ss_fd;

  if (! ss)
    return;

  if (timeout < 0)
    timeout = 600 * 1000;
  else if (timeout < 500)
    timeout = 500;
  
  ss->conn_socket_timeout = timeout;
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeSetTcpNoDelay(JNIEnv *env,
                                                            jobject obj,
                                                            jlong ss_fd,
                                                            jboolean enable)
{
  server_socket_t *ss = (server_socket_t *) (PTR) ss_fd;

  if (! ss)
    return;
  
  ss->tcp_no_delay = enable;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeIsTcpNoDelay(JNIEnv *env,
                                                           jobject obj,
                                                           jlong ss_fd)
{
  server_socket_t *ss = (server_socket_t *) (PTR) ss_fd;

  if (! ss) {
    return 0;
  }
  
  return ss->tcp_no_delay;
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeSetTcpKeepalive(JNIEnv *env,
                                                              jobject obj,
                                                              jlong ss_fd,
                                                              jboolean enable)
{
  server_socket_t *ss = (server_socket_t *) (PTR) ss_fd;

  if (! ss)
    return;
  
  ss->tcp_keepalive = enable;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeIsTcpKeepalive(JNIEnv *env,
                                                             jobject obj,
                                                             jlong ss_fd)
{
  server_socket_t *ss = (server_socket_t *) (PTR) ss_fd;

  if (! ss) {
    return 0;
  }
  
  return ss->tcp_keepalive;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeIsTcpCork(JNIEnv *env,
                                                        jobject obj,
                                                        jlong ss_fd)
{
  server_socket_t *ss = (server_socket_t *) (PTR) ss_fd;

  if (! ss) {
    return 0;
  }

  return ss->tcp_cork;
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeSetTcpCork(JNIEnv *env,
                                                        jobject obj,
                                                        jlong ss_fd,
                                                        jboolean enable)
{
  server_socket_t *ss = (server_socket_t *) (PTR) ss_fd;

  if (! ss) {
    return;
  }

#ifdef TCP_CORK
  if (! ss->ssl_config) {
    ss->tcp_cork = enable;
  }
#endif  
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeListen(JNIEnv *env,
						     jobject obj,
						     jlong ss_fd,
						     jint backlog)
{
  server_socket_t *ss = (server_socket_t *) (PTR) ss_fd;

  if (! ss || ss->fd < 0)
    return;

  if (backlog < 0)
    backlog = 0;

  listen(ss->fd, backlog);
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_getLocalPort(JNIEnv *env,
                                                  jobject obj,
                                                  jlong ss)
{
  server_socket_t *socket = (server_socket_t *) (PTR) ss;

  if (socket) {
    return socket->port;
  }
  else {
    return 0;
  }
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeGetSystemFD(JNIEnv *env,
							  jobject obj,
							  jlong ss)
{
  server_socket_t *socket = (server_socket_t *) (PTR) ss;

  if (! socket)
    return -1;
  else
    return socket->fd;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeSetSaveOnExec(JNIEnv *env,
							    jobject obj,
							    jlong ss)
{
#ifdef WIN32
  return 0;
#else
  server_socket_t *socket = (server_socket_t *) (PTR) ss;

  if (! socket)
    return 0;
  else {
    int fd = socket->fd;
    int arg = 0;
    int result = 0;

    if (fd < 0)
      return 0;

    /* sets the close on exec flag */
    arg = fcntl(fd, F_GETFD, 0);
    arg &= ~FD_CLOEXEC;

    result = fcntl(fd, F_SETFD, arg);

    return result >= 0;
  }
#endif
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_closeNative(JNIEnv *env,
                                                 jobject obj,
                                                 jlong ss)
{
  server_socket_t *socket = (server_socket_t *) (PTR) ss;

  if (! socket)
    return 0;

  socket->close(socket);

  cse_free(socket);

  return 0;
}

#if ! defined(AF_INET6)
static int
get_address(struct sockaddr *addr, char *dst, int length)
{
  struct sockaddr_in *sin = (struct sockaddr_in *) addr;

  if (! sin)
    return 0;
  
  memset(dst, 0, 10);
  dst[10] = 0xff;
  dst[11] = 0xff;
  memcpy(dst + 12, sin->sin_addr, 4);

  return 4;
}
#else

static int
get_address(struct sockaddr *addr, char *dst, int length)
{
  struct sockaddr_in *sin = (struct sockaddr_in *) addr;
  const char *result;
  
  if (sin->sin_family == AF_INET6) {
    struct sockaddr_in6 *sin6 = (struct sockaddr_in6 *) sin;
    struct in6_addr *sin6_addr = &sin6->sin6_addr;

    memcpy(dst, sin6_addr, 16);

    return 6;
  }
  else {
    memset(dst, 0, 10);
    dst[10] = 0xff;
    dst[11] = 0xff;
    memcpy(dst + 12, (char *) &sin->sin_addr, 4);

    return 4;
  }
}
#endif

static void
socket_fill_address(JNIEnv *env, jobject obj,
                    server_socket_t *ss,
                    connection_t *conn,
                    jbyteArray local_addr,
                    jbyteArray remote_addr)
{
  char temp_buf[1024];
  struct sockaddr_in *sin;
  struct sockaddr_in6 *sin6;

  if (! local_addr || ! remote_addr) {
    return;
  }

  if (ss->_isSecure) {
    jboolean is_secure = conn->ssl_sock != 0 && conn->ssl_cipher != 0;
    
    (*env)->SetBooleanField(env, obj, ss->_isSecure, is_secure);
  }

  if (local_addr) {
    /* the 16 must match JniSocketImpl 16 bytes ipv6 */
    get_address(conn->server_sin, temp_buf, 16);

    baratine_set_byte_array_region(env, local_addr, 0, 16, temp_buf);
  }

  if (ss->_localPort) {
    jint local_port;

    sin = (struct sockaddr_in *) conn->server_sin;
    if (sin->sin_family == AF_INET6) {
      sin6 = (struct sockaddr_in6 *) sin;
      local_port = ntohs(sin6->sin6_port);
    } else {
      local_port = ntohs(sin->sin_port);
    }

    (*env)->SetIntField(env, obj, ss->_localPort, local_port);
  }

  if (remote_addr) {
    /* the 16 must match JniSocketImpl 16 bytes ipv6 */
    get_address(conn->client_sin, temp_buf, 16);

    baratine_set_byte_array_region(env, remote_addr, 0, 16, temp_buf);
  }

  if (ss->_remotePort) {
    jint remote_port;

    sin = (struct sockaddr_in *) conn->client_sin;
    if (sin->sin_family == AF_INET6) {
      sin6 = (struct sockaddr_in6 *) sin;
      remote_port = ntohs(sin6->sin6_port);
    } else {
      remote_port = ntohs(sin->sin_port);
    }

    (*env)->SetIntField(env, obj, ss->_remotePort, remote_port);
  }
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_nativeAccept(JNIEnv *env,
                                               jobject obj,
                                               jlong ss_fd,
                                               jlong conn_fd,
                                               jbyteArray local_addr,
                                               jbyteArray remote_addr)
{
  server_socket_t *ss = (server_socket_t *) (PTR) ss_fd;
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  jboolean value;

  if (! ss || ! conn || ! env || ! obj) {
    return 0;
  }

  if (conn->fd >= 0) {
    baratine_throw_exception(env, "java/lang/IllegalStateException",
                          "unclosed socket in accept");
    return 0;
  }

  if (! ss->accept(ss, conn)) {
    return 0;
  }

  conn->ss = ss;

  /* avoid nativeAcceptInit */
  conn->ops->init(conn);

  socket_fill_address(env, obj, ss, conn, local_addr, remote_addr);

  return 1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_nativeAcceptInit(JNIEnv *env,
                                                   jobject obj,
                                                   jlong conn_fd,
                                                   jbyteArray local_addr,
                                                   jbyteArray remote_addr,
                                                   jbyteArray buf,
                                                   jint offset,
                                                   jint length)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  server_socket_t *ss;
  jboolean value;
  jint result = 0;

  if (! conn || ! env || ! obj) {
    return -1;
  }

  ss = conn->ss;

  if (! ss) {
    baratine_printf_exception(env,
                           "java/io/IOException",
                           "%s:%d server socket is not available in nativeAccept\n",
                           __FILE__, __LINE__);
    
    return -1;
  }

  conn->ops->init(conn);

  socket_fill_address(env, obj, ss, conn, local_addr, remote_addr);

  if (length <= 0) {
    return 0;
  }

  result = Java_com_caucho_v5_jni_JniSocketImpl_readNative(env,
                                                        obj, conn_fd,
                                                        buf, offset, 
                                                        length,
                                                        -1);

  return result;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_nativeConnect(JNIEnv *env,
						jobject obj,
						jlong conn_fd,
						jstring jhost,
						jint port)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  int val = 0;
  const char *addr_string = 0;
  int sock;
  int family = 0;
  int protocol = 0;
  server_socket_t *ss = 0;
  char sin_data[256];
  struct sockaddr_in *sin = (struct sockaddr_in *) sin_data;
  int sin_length = sizeof(sin_data);
  struct timeval timeout;

  if (! conn || ! env || ! jhost)
    return 0;

  if (conn->fd >= 0) {
    baratine_throw_exception(env, "java/lang/IllegalStateException",
                          "unclosed socket in connect");
  }

  memset(sin_data, 0, sin_length);
  addr_string = (*env)->GetStringUTFChars(env, jhost, 0);

  if (! addr_string) {
    baratine_throw_exception(env, "java/lang/NullPointerException", "missing addr");
    return 0;
  }
  
  sin = lookup_addr(env, addr_string, port, sin_data,
		      &family, &protocol, &sin_length);
  
  (*env)->ReleaseStringUTFChars(env, jhost, addr_string);

  sock = socket(family, SOCK_STREAM, 0);
  if (sock < 0) {
    return 0;
  }

  val = 1;
  //  setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (char *) &val, sizeof(int));

  if (connect(sock, (struct sockaddr *) sin, sin_length) < 0) {
    close(sock);
    
    return 0;
  }

  conn->fd = sock;

  /*
  conn->socket_timeout = ss->conn_socket_timeout;
  */

  if (conn->socket_timeout == 0) {
    conn->socket_timeout = 120000;
  }

#ifdef HAS_SOCK_TIMEOUT
  timeout.tv_sec = conn->socket_timeout / 1000;
  timeout.tv_usec = conn->socket_timeout % 1000 * 1000;
  
  if (setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO,
                 (char *) &timeout, sizeof(timeout)) == 0) {
    conn->is_recv_timeout = 1;
    conn->recv_timeout = conn->socket_timeout;
  }

  timeout.tv_sec = conn->socket_timeout / 1000;
  timeout.tv_usec = conn->socket_timeout % 1000 * 1000;
  setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO,
	     (char *) &timeout, sizeof(timeout));

#endif

  return 1;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_nativeConnectUnix(JNIEnv *env,
                                                    jobject obj,
                                                    jlong conn_fd,
                                                    jstring jpath)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  int val = 0;
  const char *path_string = 0;
  int sock;
  int family = 0;
  int protocol = 0;
  server_socket_t *ss = 0;
  char sin_data[256];
  struct sockaddr_un *sin = (struct sockaddr_un *) sin_data;
  int sin_length = sizeof(struct sockaddr_un);
  struct timeval timeout;

  if (! conn || ! env || ! jpath) {
    return 0;
  }

  if (conn->fd >= 0) {
    baratine_throw_exception(env, "java/lang/IllegalStateException",
                          "unclosed socket in connect");
  }

  memset(sin_data, 0, sin_length);
  path_string = (*env)->GetStringUTFChars(env, jpath, 0);

  if (! path_string) {
    baratine_throw_exception(env, "java/lang/NullPointerException", "missing addr");
    return 0;
  }

  sin->sun_family = AF_UNIX;
  strncpy(sin->sun_path, path_string, sizeof(sin->sun_path));

  sock = socket(AF_UNIX, SOCK_STREAM, 0);

  if (sock < 0) {
    return 0;
  }

  val = 1;
  setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (char *) &val, sizeof(int));

  if (connect(sock, (struct sockaddr *) sin, sin_length) < 0) {
    close(sock);
    
    return 0;
  }

  conn->fd = sock;

  /*
  conn->socket_timeout = ss->conn_socket_timeout;
  */

  timeout.tv_sec = conn->socket_timeout / 1000;
  timeout.tv_usec = conn->socket_timeout % 1000 * 1000;
  
  if (setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO,
                 (char *) &timeout, sizeof(timeout)) == 0) {
    conn->is_recv_timeout = 1;
    conn->recv_timeout = conn->socket_timeout;
  }

  timeout.tv_sec = conn->socket_timeout / 1000;
  timeout.tv_usec = conn->socket_timeout % 1000 * 1000;
  setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO,
	     (char *) &timeout, sizeof(timeout));

  return 1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_getClientCertificate(JNIEnv *env,
                                                    jobject obj,
                                                    jlong conn_fd,
                                                    jbyteArray buf,
                                                    jint offset,
                                                    jint length)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;
  int sublen;
  char buffer[8192];

  if (! conn || ! buf)
    return -1;

  if (length < 8192)
    sublen = length;
  else
    sublen = 8192;

  sublen = conn->ops->read_client_certificate(conn, buffer, sublen);

  /* Should probably have a different response for EINTR */
  if (sublen < 0 || length < sublen)
    return sublen;

  baratine_set_byte_array_region(env, buf, offset, sublen, buffer);

  return sublen;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniSocketImpl_getNativeFd(JNIEnv *env,
                                              jobject obj,
                                              jlong conn_fd)
{
  connection_t *conn = (connection_t *) (PTR) conn_fd;

  if (! conn)
    return -1;
  else
    return conn->fd;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeIsSendfileEnabled(JNIEnv *env,
                                                                jobject obj)
{
#ifdef HAS_SENDFILE
  return 1;
#else
  return 0;
#endif  
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniServerSocketImpl_nativeIsCorkEnabled(JNIEnv *env,
                                                            jobject obj)
{
#ifdef TCP_CORK
  return 1;
#else
  return 0;
#endif  
}
