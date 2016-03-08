/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

#define _GNU_SOURCE

#ifdef WIN32
#ifndef _WINSOCKAPI_ 
#define _WINSOCKAPI_
#endif 
#include <windows.h>
#include <winsock2.h>
#include <io.h>
#else
#include <sys/param.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/resource.h>
#include <dirent.h>
#include <netinet/in.h>
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
#include <limits.h>
#include <errno.h>
/* probably system-dependent */
#include <jni.h>

#include "baratine.h"

#define SELECT_MAX (128 * 1024)

typedef struct select_t {
  int max;
  int has_more;
  int has_update;

  int pipe[2];
  pthread_t thread_id;
  int epoll_fd;
  
#ifndef WIN32
  pthread_mutex_t lock;
#endif
#ifdef POLL
  struct pollfd poll_items[SELECT_MAX];
#else
  fd_set select_items;
#endif
} select_t;

/*
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
*/

static char *
q_strdup(char *str)
{
  int len = strlen(str);
  char *dup = cse_malloc(len + 1);

  strcpy(dup, str);

  return dup;
}

#define POLY64REV 0xd800000000000000LL
#define MASK      0x00ffffffffffffffLL

static jlong CRC_TABLE[256];
static int g_crc64_is_init;

static void
crc64_init()
{
  int i;
  
  if (g_crc64_is_init)
    return;

  for (i = 0; i < 256; i++) {
    jlong v = i;
    int j;

    for (j = 0; j < 8; j++) {
      int flag = (v & 1) != 0;

      jlong newV = (v >> 1) & ~(1LL << 63);

      if ((v & 0x100000000LL) != 0)
        newV |= 0x100000000LL;

      if ((v & 1) != 0)
        newV ^= POLY64REV;

      v = newV;
    }

    CRC_TABLE[i] = v;
  }

  g_crc64_is_init = 1;
}


/**
 * Calculates the next crc value.
 */
static jlong 
crc64_next(jlong crc, int ch)
{
  return ((crc >> 8) & MASK) ^ CRC_TABLE[((int) crc ^ ch) & 0xff];
}

/**
 * Calculates CRC from a string.
 */
jlong
crc64_generate(jlong crc, char *value, int max_len)
{
  int ch;
  int i;

  if (! g_crc64_is_init)
    crc64_init();

  for (i = 0; i < max_len && (ch = value[i]); i++) {
    crc = crc64_next(crc, ch);
  }

  return crc;
}

/**
 * Calculates CRC from a string.
 */
jlong
crc64_generate_bytes(jlong crc, char *buffer, int length)
{
  int ch;
  int i;

  if (! g_crc64_is_init) {
    crc64_init();
  }
  
  for (i = 0; i < length; i++) {
    crc = crc64_next(crc, buffer[i]);
  }

  return crc;
}

int
set_byte_array_region(JNIEnv *env, jbyteArray buf, jint offset, jint sublen,
		      char *buffer)
{
  jbyte *cBuf;

  /* JDK uses SetByteArrayRegion */
     
  (*env)->SetByteArrayRegion(env, buf, offset, sublen, (void*) buffer);

  /*  
  cBuf = (*env)->GetPrimitiveArrayCritical(env, buf, 0);

  if (! cBuf)
    return 0;

  memcpy(cBuf + offset, buffer, sublen);

  (*env)->ReleasePrimitiveArrayCritical(env, buf, cBuf, 0);
  */

  return 1;
}

#ifdef EPOLL
static int
zpoll_read(int fd, int ms)
{
  struct pollfd pollfd[1];
  int result;
  int rd_hup = 0;

#ifdef POLLRDHUP
  /* the other end has hung up */
  rd_hup = POLLRDHUP;
#endif  
  
  pollfd[0].fd = fd;
  pollfd[0].events = POLLIN|POLLPRI|rd_hup;
  pollfd[0].revents = 0;

  result = poll(pollfd, 1, ms);

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
#endif

/*
 * JniRandomAccessFile
 */

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniRandomAccessFile_nativeOpen(JNIEnv *env,
						   jobject obj,
						   jbyteArray name,
						   jint length)
{
  char buffer[8192];
  int fd;
  int flags;

  if (! name || length <= 0 || sizeof(buffer) <= length)
    return -1;

  baratine_get_byte_array_region(env, name, 0, length, buffer);

  buffer[length] = 0;

#ifdef S_ISDIR
 /* On Linux, check if the file is a directory first. */
 {
   struct stat st;

   if (stat(buffer, &st) || S_ISDIR(st.st_mode)) {
     return -1;
   }
 }
#endif

 flags = O_RDWR|O_CREAT;
  
#ifdef O_BINARY
  flags |= O_BINARY;
#endif
  
#ifdef O_LARGEFILE
  flags |= O_LARGEFILE;
#endif
 
  fd = open(buffer, flags, 0664);

  return fd;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniRandomAccessFile_nativeClose(JNIEnv *env,
						    jobject obj,
						    jint fd)
{
  if (fd >= 0)
    close(fd);

  return 0;
}

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_JniRandomAccessFile_nativeGetLength(JNIEnv *env,
                                                        jobject obj,
                                                        jint fd)
{
  struct stat st;

  if (fd < 0)
    return -1;

  if (fstat(fd, &st) != 0)
    return -1;

  return st.st_size;
}  

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniRandomAccessFile_nativeRead(JNIEnv *env,
						   jobject obj,
						   jint fd,
						   jlong pos,
						   jbyteArray buf,
						   jint offset,
						   jint length)
{
  int sublen;
  char buffer[STACK_BUFFER_SIZE];
  int read_length = 0;
  
  if (fd < 0)
    return -1;

  if (lseek(fd, pos, SEEK_SET) < 0)
    return -1;

  while (length > 0) {
    int result;
    
    if (length < sizeof(buffer))
      sublen = length;
    else
      sublen = sizeof(buffer);

#ifdef RESIN_DIRECT_JNI_BUFFER
   {
     jbyte *cBuf = (*env)->GetPrimitiveArrayCritical(env, buf, 0);

     if (! cBuf)
       return -1;
     
     result = read(fd, cBuf + offset, sublen);

     (*env)->ReleasePrimitiveArrayCritical(env, buf, cBuf, 0);
     
     if (result <= 0)
       return read_length == 0 ? -1 : read_length;
   }
#else
   {
     result = read(fd, buffer, sublen);

     if (result <= 0)
       return read_length == 0 ? -1 : read_length;

     set_byte_array_region(env, buf, offset, result, buffer);
  }
#endif    

    read_length += result;
    
    if (result < sublen)
      return read_length;
    
    offset += result;
    length -= result;
  }

  return read_length;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniRandomAccessFile_nativeWrite(JNIEnv *env,
						    jobject obj,
						    jint fd,
						    jlong pos,
						    jbyteArray buf,
						    jint offset,
						    jint length)
{
  int sublen;
  char buffer[STACK_BUFFER_SIZE];
  int read_length = 0;

  if (fd < 0 || buf == 0)
    return -1;

  if (lseek(fd, pos, SEEK_SET) < 0)
    return -1;

  while (length > 0) {
    int result;
    
    if (length < sizeof(buffer))
      sublen = length;
    else
      sublen = sizeof(buffer);

    baratine_get_byte_array_region(env, buf, offset, sublen, buffer);

    result = write(fd, buffer, sublen);

    if (result <= 0)
      return -1;
    
    offset += result;
    length -= result;
  }

  return 1;
}

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
    
    ss->_localPort = (*env)->GetFieldID(env, jniServerSocketClass,
					"_localPort", "I");
    if (! ss->_localPort)
      baratine_throw_exception(env, "com/caucho/v5/config/ConfigException",
			    "can't load _localPort field");
      
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
    
    ss->_remotePort = (*env)->GetFieldID(env, jniServerSocketClass,
					 "_remotePort", "I");
    if (! ss->_remotePort)
      baratine_throw_exception(env, "com/caucho/v5/config/ConfigException",
			    "can't load _remotePort field");
      
  }
}

static void
handle_alarm(int fo)
{
}

#ifdef EPOLL

/* EPOLL */

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_createNative(JNIEnv *env,
                                                          jobject obj)
{
  select_t *sel = cse_malloc(sizeof(select_t));

  if (sel == 0)
    return 0;

  memset(sel, 0, sizeof(select_t));
  sel->pipe[0] = -1;
  sel->pipe[1] = -1;
  
  return (PTR) sel;
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_initNative(JNIEnv *env,
                                                        jobject obj,
                                                        jlong manager_fd)
{
  select_t *sel = (select_t *) (PTR) manager_fd;

  if (! sel) {
    return;
  }

  if (sel->epoll_fd) {
    baratine_printf_exception(env, "java/io/IOException",
			   "duplicate init for SelectManagerJni");
  }

  sel->thread_id = pthread_self();
  sel->epoll_fd = epoll_create(SELECT_MAX);
  
  sel->max = 1;
}

JNIEXPORT int JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_addNative(JNIEnv *env,
                                                          jobject obj,
                                                          jlong manager_fd,
                                                          jint fd,
                                                          jboolean is_first)
{
  select_t *sel = (select_t *) (PTR) manager_fd;
  struct epoll_event ev;
  int is_update = 0;
  int result;
  
  if (! sel || fd < 0) {
    return -1;
  }
  
  if (sel->epoll_fd <= 0) {
    return -1;
  }

  if (is_first) {
    ev.events = EPOLLIN|EPOLLPRI|EPOLLERR|EPOLLHUP;

#ifdef EPOLLRDHUP    
    ev.events |= EPOLLRDHUP;
#endif

    ev.events |= EPOLLET;
    
    ev.data.fd = fd;

    if (epoll_ctl(sel->epoll_fd, EPOLL_CTL_ADD, fd, &ev) < 0) {
      baratine_printf_exception(env, "java/io/IOException",
                             "failed to add EPOLL for fd=%d (errno=%d)\n",
                             fd, errno);

      return -1;
    }
  }

  result = poll_read(fd, 0);

  /*
  fprintf(stderr, "READ: fd=%d %d errno=%d\n", fd, result, errno);
  */

  return result;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_removeNative(JNIEnv *env,
							  jobject obj,
							  jlong manager_fd,
							  jint fd)
{
  select_t *sel = (select_t *) (PTR) manager_fd;
  int i;
  
  if (! sel || fd < 0) {
    return -1;
  }

  if (sel->epoll_fd >= 0) {
    struct epoll_event ev;

    ev.events = EPOLLIN|EPOLLET;
    ev.data.fd = fd;

    if (epoll_ctl(sel->epoll_fd, EPOLL_CTL_DEL, fd, &ev) < 0) {
      /*
      baratine_printf_exception(env, "java/io/IOException",
			     "failed to remove EPOLL for fd=%d\n",
			     fd);
      */

      return errno > 1 ? -errno : -1;
    }
  }

  return sel->max > 0 ? fd : -1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_selectNative(JNIEnv *env,
                                                             jobject obj,
                                                             jlong manager_fd,
                                                             jlong timeout_ms,
                                                             jlongArray j_fds)
{
  select_t *sel = (select_t *) (PTR) manager_fd;
  int maxevents = 1024;
  struct epoll_event events[maxevents];
  jlong c_fds[maxevents];
  struct epoll_event ev;
  jlong *c_fds_ptr;
  jsize fd_max = 0;
  int max;
  int result;
  int i;
  int fd;
  int read_mask;
  int hup_mask;

  if (! sel || sel->max <= 0 || ! j_fds) {
    return -1;
  }

  read_mask = EPOLLIN|EPOLLPRI|EPOLLERR;
  hup_mask = EPOLLHUP;

#ifdef EPOLLRDHUP    
  hup_mask |= EPOLLRDHUP;
#endif

  memset(events, 0, sizeof(events));

  fd_max = (*env)->GetArrayLength(env, j_fds);

  if (fd_max < maxevents) {
    maxevents = fd_max;
  }

  result = epoll_wait(sel->epoll_fd, events, maxevents, timeout_ms);

  for (i = 0; i < result; i++) {
    jlong value = 0;

    if ((events[i].events & hup_mask) != 0) {
      value |= 1;
    }

    if ((events[i].events & read_mask) != 0) {
      value |= 2;
    }

    value <<= 32;

    fd = events[i].data.fd;

    value |= fd;

    c_fds[i] = value;

    /* epoll_ctl(sel->epoll_fd, EPOLL_CTL_DEL, fd, &ev); */
  }

  c_fds_ptr = (*env)->GetLongArrayElements(env, j_fds, 0);

  for (i = 0; i < result; i++) {
    c_fds_ptr[i] = c_fds[i];
  }

  (*env)->ReleaseLongArrayElements(env, j_fds, c_fds_ptr, 0);

  return result;
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_closeNative(JNIEnv *env,
                                                         jobject obj,
                                                         jlong manager_fd)
{
  select_t *sel = (select_t *) (PTR) manager_fd;

  if (sel && sel->max >= 0) {
    int result;
    
    sel->has_update = 1;
    sel->max = -1;
    result = write(sel->pipe[0], " ", 1);
    close(sel->pipe[0]);
    close(sel->pipe[1]);
    /*
    cse_free(sel);
    */
  }

#ifdef EPOLL
 {
   int epoll_fd = sel->epoll_fd;
   sel->epoll_fd = 0;

   if (epoll_fd > 0)
     close(epoll_fd);
 }
#endif
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_freeNative(JNIEnv *env,
							jobject obj,
							jlong manager_fd)
{
  select_t *sel = (select_t *) (PTR) manager_fd;

  if (sel) {
    cse_free(sel);
  }
}

#else /* ! EPOLL */

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_createNative(JNIEnv *env,
                                                          jobject obj)
{
  return 0;
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_initNative(JNIEnv *env,
                                                        jobject obj,
                                                        jlong manager_fd)
{
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_addNative(JNIEnv *env,
                                                          jobject obj,
                                                          jlong manager_fd,
                                                          jint fd,
                                                          jboolean is_first)
{
  return -1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_removeNative(JNIEnv *env,
							  jobject obj,
							  jlong manager_fd,
							  jint fd)
{
  return -1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_selectNative(JNIEnv *env,
                                                             jobject obj,
                                                             jlong manager_fd,
                                                             jlong ms,
                                                             jlongArray j_fds)
{
  return -1;
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_closeNative(JNIEnv *env,
                                                         jobject obj,
                                                         jlong manager_fd)
{
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_SelectManagerJni_freeNative(JNIEnv *env,
                                                         jobject obj,
                                                         jlong manager_fd)
{
}

#endif /* ! EPOLL */

#ifdef linux
static void
get_linux_version(char *version)
{
  FILE *file = fopen("/proc/version", "r");

  if (! file || fscanf(file, "Linux version %s", version) != 1)
    strcpy(version, "2.4.0-unknown");

  fclose(file);
}
#endif

#ifndef WIN32
JNIEXPORT jint JNICALL
Java_com_caucho_v5_util_CauchoUtil_setUserNative(JNIEnv *env,
						jobject obj,
						jstring juser,
						jstring jgroup)
{
  char userbuf[256];
  char groupbuf[256];
  char *user = 0;
  char *group = 0;
  int uid = -1;
  int gid = -1;
  const char *temp_string;
  struct passwd *passwd;
  
  if (juser != 0) {
    temp_string = (*env)->GetStringUTFChars(env, juser, 0);
  
    if (temp_string) {
      strncpy(userbuf, temp_string, sizeof(userbuf));
      userbuf[sizeof(userbuf) - 1] = 0;
      user = userbuf;
  
      (*env)->ReleaseStringUTFChars(env, juser, temp_string);
    }
  }

  if (jgroup != 0) {
    temp_string = (*env)->GetStringUTFChars(env, jgroup, 0);
  
    if (temp_string) {
      strncpy(groupbuf, temp_string, sizeof(groupbuf));
      groupbuf[sizeof(groupbuf) - 1] = 0;
      group = groupbuf;
  
      (*env)->ReleaseStringUTFChars(env, jgroup, temp_string);
    }
  }

  if (user == 0)
    return -1;

  passwd = getpwnam(user);
  if (passwd == 0)
    return -1;

  uid = passwd->pw_uid;

  if (group) {
    passwd = getpwnam(group);
    if (passwd)
      gid = passwd->pw_gid;
  }

  if (gid > 0)
    setgid(gid);

  if (uid == getuid())
    return uid;

#ifdef linux
  {
    char buf[1024];
    char version[1024];
    jclass clazz;

    get_linux_version(version);

    /*
    if (1 || strcmp(version, "2.5.0") < 0 && strcmp(version, "2.4.20-6")) {
    */
    if (1) {
      sprintf(buf, "Linux %s does not properly implement setuid for threaded processes, so the <user-name> is not properly available.  Consider using iptables or some other port mapping function to avoid the need for root.",
	      version);

      clazz = (*env)->FindClass(env, "java/io/IOException");

      if (clazz)
	(*env)->ThrowNew(env, clazz, buf);

      return -1;
    }
  }
#endif

  if (uid > 0) {
    int result;

    result = setuid(uid);
    
    if (result > 0)
      return -1;
  }

  return getuid();
}
#else /* WIN32 */
JNIEXPORT jint JNICALL
Java_com_caucho_v5_util_CauchoUtil_setUserNative(JNIEnv *env,
						jobject obj,
						jstring user,
						jstring group)
{
  return -1;
}
#endif				 

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniFilePathImpl_nativeIsEnabled(JNIEnv *env,
						    jobject obj)
{
  return 1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniFilePathImpl_nativeGetLastModified(JNIEnv *env,
							  jobject obj,
							  jbyteArray name,
							  jint length)
{
  char buffer[8192];
  struct stat st;

  if (! name || length <= 0 || sizeof(buffer) <= length)
    return -1;

  baratine_get_byte_array_region(env, name, 0, length, buffer);

  buffer[length] = 0;

#ifdef WIN32
  if (length > 0 && (buffer[length - 1] == '/' || buffer[length - 1] == '\\')) {
	  length--;
	  buffer[length] = 0;
  }
#endif

  if (stat(buffer, &st))
    return -1;
  
  return st.st_mtime;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniFilePathImpl_nativeGetLength(JNIEnv *env,
						    jobject obj,
						    jbyteArray name,
						    jint length)
{
  char buffer[8192];
#ifdef WIN32
  struct _stat64 st;
#else  
  struct stat st;
#endif  

  if (! name || length <= 0 || sizeof(buffer) <= length)
    return -1;

  baratine_get_byte_array_region(env, name, 0, length, buffer);

  buffer[length] = 0;

#ifdef WIN32
  if (length > 0 && (buffer[length - 1] == '/' || buffer[length - 1] == '\\')) {
    length--;
    buffer[length] = 0;
  }
  
  if (_stati64(buffer, &st)) {
    return -1;
  }

  return st.st_size;
#else

  if (stat(buffer, &st)) {
    return -1;
  }

  return st.st_size;
#endif  
}  

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniFilePathImpl_nativeCanRead(JNIEnv *env,
						  jclass cl,
						  jbyteArray name)
{
  char buffer[8192];
  int length;
  int result;

  if (! name)
    return 0;

  length = (*env)->GetArrayLength(env, name);
  
  if (length <= 0 || sizeof(buffer) <= length)
    return 0;

  baratine_get_byte_array_region(env, name, 0, length, buffer);

  buffer[length] = 0;

#ifndef WIN32
  result = access(buffer, R_OK) == 0;
#else
  result = access(buffer, 4) == 0;
#endif

  return result;
}

JNIEXPORT jboolean JNICALL 
Java_com_caucho_v5_jni_JniFilePathImpl_nativeStat(JNIEnv *env, 
                                               jclass cl,
					       jobject status,
                                               jbyteArray name,
                                               jboolean do_lstat)
{
  /* we can cache a method id... */
  static jmethodID file_status_init = NULL;

  /* but we can't cache classes */
  jclass file_status_class = NULL;

  char buffer[8192];
  int length;
  int result;
  struct stat st;
  jobject stat_obj;
  jboolean is_link;
  jboolean is_socket;
  jboolean is_file;
  jboolean is_block;
  jboolean is_char;
  jboolean is_dir;
  jboolean is_fifo;
  jlong block_size;
  jlong blocks;

  if (! name)
    return 0;

  length = (*env)->GetArrayLength(env, name);
  
  if (length <= 0 || sizeof(buffer) <= length)
    return 0;

  baratine_get_byte_array_region(env, name, 0, length, buffer);

  buffer[length] = 0;

#ifdef WIN32
  if (length > 0 && (buffer[length - 1] == '/' || buffer[length - 1] == '\\')) {
	  length--;
	  buffer[length] = 0;
  }
#endif

#ifdef WIN32
  result = stat(buffer, &st);
#else
  if (do_lstat) 
    result = lstat(buffer, &st);
  else 
    result = stat(buffer, &st);
#endif

  if (result != 0)
    return 0;

  file_status_class = (*env)->FindClass(env, "com/caucho/v5/vfs/FileStatus");

  if (file_status_class == NULL)
    return 0;

  if (file_status_init == NULL) {
    file_status_init = (*env)->GetMethodID(env, file_status_class, 
					   "init", 
					   "(JJIIIIJJJJJJJZZZZZZZ)V");

    if (file_status_init == NULL)
      return 0;
  }

#ifndef WIN32
  is_file = (jboolean) (S_ISREG(st.st_mode));
  is_dir = (jboolean) (S_ISDIR(st.st_mode));
  is_char = (jboolean) (S_ISCHR(st.st_mode));
  is_block = (jboolean) (S_ISBLK(st.st_mode));
  is_fifo = (jboolean) (S_ISFIFO(st.st_mode));
  is_link = (jboolean) (S_ISLNK(st.st_mode));
  is_socket = (jboolean) (S_ISSOCK(st.st_mode));
  block_size = (jlong) st.st_blksize;
  blocks = (jlong) st.st_blocks;
#else
  is_file = (jboolean) ((st.st_mode & S_IFREG) != 0);
  is_dir = (jboolean) ((st.st_mode & S_IFDIR) != 0);
  is_char = (jboolean) ((st.st_mode & S_IFCHR) != 0);
  is_block = 0;
  is_fifo = 0;
  is_link = 0;
  is_socket = 0;
  block_size = 1024;
  blocks = (st.st_size + block_size - 1) / block_size;
#endif

  (*env)->CallVoidMethod(env, status, file_status_init,
			 (jlong)st.st_dev, (jlong)st.st_ino, 
			 (jint)st.st_mode, (jint)st.st_nlink, 
			 (jint)st.st_uid, (jint)st.st_gid,
			 (jlong)st.st_rdev, (jlong)st.st_size, 
			 block_size, blocks, 
			 (jlong)st.st_atime, (jlong)st.st_mtime,
			 (jlong)st.st_ctime,
			 is_file, is_dir, is_char, is_block, is_fifo,
			 is_link, is_socket);

  return 1;
}

JNIEXPORT jboolean JNICALL 
Java_com_caucho_v5_jni_JniFilePathImpl_nativeLink(JNIEnv *env, 
                                               jclass cl, 
                                               jbyteArray name, 
                                               jbyteArray target, 
                                               jboolean hard_link)
{
  jclass ioExceptionClass;
  char name_buffer[8192];
  char target_buffer[8192];
  int name_length;
  int target_length;
  int result;

#ifdef WIN32
  return (jboolean) 0;
#else

  if (! name || ! target)
    return (jboolean) 0;
  
  name_length = (*env)->GetArrayLength(env, name);
  target_length = (*env)->GetArrayLength(env, target);
  
  if (name_length <= 0 || sizeof(name_buffer) <= name_length)
    return (jboolean) 0;

  if (target_length <= 0 || sizeof(target_buffer) <= target_length)
    return (jboolean) 0;

  baratine_get_byte_array_region(env, name, 0, name_length, name_buffer);
  baratine_get_byte_array_region(env, target, 0, target_length, target_buffer);

  name_buffer[name_length] = 0;
  target_buffer[target_length] = 0;

  if (hard_link)
    result = link(target_buffer, name_buffer);
  else
    result = symlink(target_buffer, name_buffer);

  if (result == 0)
    return (jboolean) 1;

  ioExceptionClass = (*env)->FindClass(env, "java/io/IOException");

  if (ioExceptionClass == NULL)
    return (jboolean) 0;

  (*env)->ThrowNew(env, ioExceptionClass, strerror(errno));

  return (jboolean) 0;
#endif
}

JNIEXPORT jstring JNICALL 
Java_com_caucho_v5_jni_JniFilePathImpl_nativeReadLink(JNIEnv *env, 
						   jclass cl, 
						   jbyteArray name)
{
  jclass ioExceptionClass;
  char name_buffer[8192];
  int name_length;
  char link_buffer[8192];
  int link_length;
  int result;

#ifdef WIN32
  return (jstring) 0;
#else
  if (! name)
    return (jstring) 0;
  
  name_length = (*env)->GetArrayLength(env, name);
  
  if (name_length <= 0 || sizeof(name_buffer) <= name_length)
    return (jstring) 0;

  baratine_get_byte_array_region(env, name, 0, name_length, name_buffer);

  name_buffer[name_length] = 0;

  result = readlink(name_buffer, link_buffer, sizeof(link_buffer) - 1);

  if (result < 0)
    return (jstring) 0;

  link_buffer[result] = 0;
  
  return (*env)->NewStringUTF(env, link_buffer);
#endif
}

JNIEXPORT jstring JNICALL 
Java_com_caucho_v5_jni_JniFilePathImpl_nativeRealPath(JNIEnv *env, 
						   jclass cl, 
						   jbyteArray name)
{
#ifdef WIN32
	return (jstring) 0;
#else
  jclass ioExceptionClass;
  char name_buffer[8192];
  int name_length;
  char link_buffer[PATH_MAX];
  char *result;

  if (! name)
    return (jstring) 0;
  
  name_length = (*env)->GetArrayLength(env, name);
  
  if (name_length <= 0 || sizeof(name_buffer) <= name_length)
    return (jstring) 0;

  baratine_get_byte_array_region(env, name, 0, name_length, name_buffer);

  name_buffer[name_length] = 0;

  result = realpath(name_buffer, link_buffer);

  if (result == 0)
    return (jstring) 0;
  
  return (*env)->NewStringUTF(env, link_buffer);
#endif
}


JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniFilePathImpl_nativeIsDirectory(JNIEnv *env,
						      jobject obj,
						      jbyteArray name)
{
  char buffer[8192];
  int length;
  struct stat st;

  if (! name)
    return 0;
  
  length = (*env)->GetArrayLength(env, name);
  
  if (length <= 0 || sizeof(buffer) <= length)
    return 0;

  baratine_get_byte_array_region(env, name, 0, length, buffer);

  buffer[length] = 0;

#ifdef WIN32
  if (length > 0 && (buffer[length - 1] == '/' || buffer[length - 1] == '\\')) {
	  length--;
	  buffer[length] = 0;
  }
#endif

  if (stat(buffer, &st))
    return 0;
#ifndef WIN32
  return S_ISDIR(st.st_mode);
#else
  return (st.st_mode & S_IFDIR) != 0;
#endif
}

JNIEXPORT int JNICALL
Java_com_caucho_v5_jni_JniFilePathImpl_nativeChmod(JNIEnv *env,
						jobject obj,
						jbyteArray name,
						jint length,
						jint mode)
{
  char buffer[8192];

  if (! name || length <= 0 || sizeof(buffer) <= length)
    return -1;

  baratine_get_byte_array_region(env, name, 0, length, buffer);

  buffer[length] = 0;

#ifndef WIN32
  chmod(buffer, mode);
#endif

  return 0;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_jni_JniFilePathImpl_nativeChangeOwner(JNIEnv *env,
						      jobject obj,
						      jbyteArray name,
						      jint length,
						      jstring owner)
{
  char buffer[8192];
  char userbuf[8192];
  struct passwd *passwd;
  const char *temp_string;
  int uid = -1;

  if (! name || length <= 0 || sizeof(buffer) <= length || ! owner)
    return 0;

  baratine_get_byte_array_region(env, name, 0, length, buffer);

  buffer[length] = 0;

#ifndef WIN32
  temp_string = (*env)->GetStringUTFChars(env, owner, 0);

  if (! temp_string)
    return 0;

  strncpy(userbuf, temp_string, sizeof(userbuf));
  userbuf[sizeof(userbuf) - 1] = 0;
  
  (*env)->ReleaseStringUTFChars(env, owner, temp_string);

  passwd = getpwnam(userbuf);
  if (passwd == 0)
    return -1;

  uid = passwd->pw_uid;

  return chown(buffer, uid, -1) >= 0;
#else
  return 0;
#endif
}

#ifndef WIN32

static jlong
crc64_directory(jlong crc64, char *dir_name, DIR *dir)
{
  struct dirent *entry_buffer;
  int size;

  size = pathconf(dir_name, _PC_NAME_MAX) + 1;

  if (size < 1024)
    size = 1024;

  entry_buffer = malloc(sizeof(struct dirent) + size);

  if (entry_buffer) {
    struct dirent *dp = 0;

    while (readdir_r(dir, entry_buffer, &dp) == 0 && dp) {
      crc64 = crc64_generate(crc64, dp->d_name, size);

      dp = 0;
    }
  
    free(entry_buffer);
  }

  return crc64;
}

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_JniFilePathImpl_nativeCrc64(JNIEnv *env,
                                                jobject obj,
                                                jbyteArray name,
                                                jint length)
{
  char buffer[8192];
  struct stat st;
  jlong crc64 = 1;
  struct dirent *dp;
  int fd;
  int sublen;
  DIR *dir;

  if (! name || length <= 0 || sizeof(buffer) <= length) {
    return 0;
  }

  baratine_get_byte_array_region(env, name, 0, length, buffer);

  buffer[length] = 0;

  dir = opendir(buffer);

  /* directory only for now */
  if (dir) {
    crc64 = crc64_directory(crc64, buffer, dir);

    closedir(dir);

    return crc64;
  }
  else {
    fd = open(buffer, O_RDONLY);

    if (fd < 0) {
      return 0;
    }

    while ((sublen = read(fd, buffer, sizeof(buffer))) > 0) {
      crc64 = crc64_generate_bytes(crc64, buffer, sublen);
    }

    close(fd);

    return crc64;
  }
}

#else

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_JniFilePathImpl_nativeCrc64(JNIEnv *env,
                                                jobject obj,
                                                jbyteArray name,
                                                jint length)
{
  return 0;
}

#endif

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_JniFilePathImpl_nativeTruncate(JNIEnv *env,
						   jobject obj,
						   jbyteArray name,
						   jint length)
{
  char buffer[8192];
  int result;

  if (! name || length <= 0 || sizeof(buffer) <= length)
    return;

  baratine_get_byte_array_region(env, name, 0, length, buffer);

  buffer[length] = 0;

#ifdef WIN32
  result = open(buffer, O_WRONLY|O_CREAT|O_TRUNC, 0664);
  if (result > 0)
    close(result);
#else
  result = truncate(buffer, 0);
#endif

  if (result < 0) {
    switch (errno) {
    case EISDIR:
      baratine_printf_exception(env, "java/io/IOException",
			     "'%s' is a directory", buffer);
      break;
    case EACCES:
      baratine_printf_exception(env, "java/io/IOException",
			     "'%s' permission denied", buffer);
      break;
    case ENOTDIR:
      baratine_printf_exception(env, "java/io/IOException",
			     "'%s' parent directory does not exist", buffer);
      break;
    case EMFILE:
    case ENFILE:
      baratine_printf_exception(env, "java/io/IOException",
			     "too many files open", buffer);
      break;
    case ENOENT:
      return;
      /*
      baratine_printf_exception(env, "java/io/FileNotFoundException",
			     "'%s' unable to open", buffer);
      break;
      */
    default:
      baratine_printf_exception(env, "java/io/IOException",
			     "'%s' unknown error (errno=%d).", buffer, errno);
      break;
    }
  }
}

#ifdef WIN32

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_Syslog_nativeSyslog(JNIEnv *env,
					jobject obj,
					jint priority,
					jstring msg)
{
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_Syslog_nativeOpenSyslog(JNIEnv *env,
					    jobject obj)
{
}

#else

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_Syslog_nativeOpenSyslog(JNIEnv *env,
					    jobject obj)
{
  openlog("Resin", 0, LOG_DAEMON);
}

JNIEXPORT void JNICALL
Java_com_caucho_v5_jni_Syslog_nativeSyslog(JNIEnv *env,
					jobject obj,
					jint priority,
					jstring msg)
{
  char buffer[8192];
  const char *temp_string;
  
  temp_string = (*env)->GetStringUTFChars(env, msg, 0);
  
  if (temp_string) {
    strncpy(buffer, temp_string, 8191);
    buffer[sizeof(buffer) - 1] = 0;
  
    (*env)->ReleaseStringUTFChars(env, msg, temp_string);
    
    syslog(priority, "%s", buffer);
  }
}


#endif

#ifdef WIN32
static BOOL WINAPI my_handler(DWORD dwCtrlType)
{
	return 0;
}
#endif

JNIEXPORT void JNICALL
Java_com_caucho_v5_server_util_JniCauchoSystemImpl_nativeInitBackground(JNIEnv *env,
								     jobject obj)
{
#ifdef WIN32
	SetConsoleCtrlHandler(my_handler, 1);
#endif
}
