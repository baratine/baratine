/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

#ifdef WIN32
#include <windows.h>
#else
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>
#include <sys/time.h>
#include <pwd.h>
#include <syslog.h>
#include <netdb.h>
#endif

#ifndef WIN32
#ifdef EPOLL
#include <sys/epoll.h>
#endif
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
#include <signal.h>
#include <errno.h>
/* probably system-dependent */
#include <jni.h>
#include "baratine_os.h"

#define STACK_BUFFER_SIZE (16 * 1024)

/* jboolean jvmdi_can_reload_native(JNIEnv *env, jobject obj); */
jboolean jvmti_can_reload_native(JNIEnv *env, jobject obj);

jint
jvmti_reload_native(JNIEnv *env,
		    jobject obj,
		    jclass cl,
		    jbyteArray buf,
		    jint offset,
		    jint length);

/*
jint
jvmdi_reload_native(JNIEnv *env,
		    jobject obj,
		    jclass cl,
		    jbyteArray buf,
		    jint offset,
		    jint length);
*/		    

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_loader_ClassEntry_canReloadNative(JNIEnv *env,
					          jobject obj)
{
  return (jvmti_can_reload_native(env, obj));
  /* || jvmdi_can_reload_native(env, obj)); */
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_loader_ClassEntry_reloadNative(JNIEnv *env,
					       jobject obj,
					       jclass cl,
					       jbyteArray buf,
					       jint offset,
					       jint length)
{
  int res = jvmti_reload_native(env, obj, cl, buf, offset, length);

  return res;
  
  /*
  if (res > 0)
    return res;

  return jvmdi_reload_native(env, obj, cl, buf, offset, length);
  */
}

#ifdef POLL
JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniFileStream_nativeAvailable(JNIEnv *env,
						  jobject obj,
						  jint fd)
{
  struct pollfd poll_item[1];

  if (fd < 0)
    return 0;

  poll_item[0].fd = fd;
  poll_item[0].events = POLLIN|POLLPRI;
  poll_item[0].revents = 0;

  return (poll(poll_item, 1, 0) > 0);
}
#else /* select */
JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniFileStream_nativeAvailable(JNIEnv *env,
						  jobject obj,
						  jint fd)
{
  fd_set read_set;
  struct timeval timeval;
  int result;
  
  if (fd < 0)
    return 0;

  FD_ZERO(&read_set);

  FD_SET(fd, &read_set);

  memset(&timeval, 0, sizeof(timeval));

  result = select(fd + 1, &read_set, 0, 0, &timeval);

  return result > 0;
}
#endif /* select */

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniFileStream_nativeOpenRead(JNIEnv *env,
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

 flags = O_RDONLY;
  
#ifdef O_BINARY
  flags |= O_BINARY;
#endif
  
#ifdef O_LARGEFILE
  flags |= O_LARGEFILE;
#endif
 
  fd = open(buffer, flags);

  return fd;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniFileStream_nativeOpenWrite(JNIEnv *env,
						  jobject obj,
						  jbyteArray name,
						  jint length,
						  jboolean is_append)
{
  char buffer[8192];
  int fd;
  int flags;
  int mode;

  if (! name || length <= 0 || sizeof(buffer) <= length)
    return -1;

  baratine_get_byte_array_region(env, name, 0, length, buffer);

  buffer[length] = 0;

  flags = 0;
  
#ifdef O_BINARY
  flags |= O_BINARY;
#endif
  
#ifdef O_LARGEFILE
  flags |= O_LARGEFILE;
#endif

  mode = 0664; /* S_IRUSR|S_IWUSR|S_IRGRP|S_IRGRP|S_IROTH; */

  if (is_append)
    fd = open(buffer, O_WRONLY|O_CREAT|O_APPEND|flags, mode);
  else
    fd = open(buffer, O_WRONLY|O_CREAT|O_TRUNC|flags, mode);

  if (fd < 0) {
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
      baratine_printf_exception(env, "java/io/FileNotFoundException",
			     "'%s' unable to open", buffer);
      break;
    default:
      baratine_printf_exception(env, "java/io/IOException",
			     "'%s' unknown error (errno=%d).", buffer, errno);
      break;
    }
  }

  return fd;
}

#ifdef WIN32

JNIEXPORT void JNICALL
Java_com_caucho_v5_bootjni_JniProcess_nativeChown(JNIEnv *env,
					      jobject obj,
					      jbyteArray name,
					      jint length,
					      jstring user,
					      jstring group)
{
}

#else /* WIN32 */

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_bootjni_JniProcess_nativeChown(JNIEnv *env,
					      jobject obj,
					      jbyteArray name,
					      jint length,
					      jstring user,
					      jstring group)
{
  char buffer[8192];
  char userbuf[256];
  char groupbuf[256];
  int uid = -1;
  int gid = -1;
  const char *temp_string;
  struct passwd *passwd;
  int fd;
  int flags;
  struct stat st;

  if (! name || length <= 0 || sizeof(buffer) <= length) {
    return 0;
  }

  baratine_get_byte_array_region(env, name, 0, length, buffer);

  buffer[length] = 0;

  /* Check if the file exists first. */
  if (stat(buffer, &st)) {
    return 0;
  }

  if (user) {
    temp_string = (*env)->GetStringUTFChars(env, user, 0);

    if (! temp_string) {
      return 0;
    }

    strncpy(userbuf, temp_string, sizeof(userbuf));
    userbuf[sizeof(userbuf) - 1] = 0;
  
    (*env)->ReleaseStringUTFChars(env, user, temp_string);

    passwd = getpwnam(userbuf);
    
    if (passwd == 0) {
      baratine_printf_exception(env, "java/lang/IllegalArgumentException",
			     "setuid '%s' is an unknown <user-name>.",
                             userbuf);
      
      return 0;
    }

    uid = passwd->pw_uid;
    gid = passwd->pw_gid;
  }

  if (uid > 0 || gid > 0) {
    return chown(buffer, uid, gid) >= 0;
  }
  else {
    return 1;
  }
}

#endif

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniFileStream_nativeRead(JNIEnv *env,
					     jobject obj,
					     jint fd,
					     jbyteArray buf,
					     jint offset,
					     jint length)
{
  int sublen;
  char buffer[STACK_BUFFER_SIZE];
  int read_length = 0;

  if (fd < 0 || ! buf)
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

     baratine_set_byte_array_region(env, buf, offset, result, buffer);
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
Java_com_caucho_v5_jni_JniFileStream_nativeWrite(JNIEnv *env,
					      jobject obj,
					      jint fd,
					      jbyteArray buf,
					      jint offset,
					      jint length)
{
  int sublen;
  char buffer[STACK_BUFFER_SIZE];
  int read_length = 0;

  if (fd < 0 || ! buf)
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

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_JniFileStream_nativeSkip(JNIEnv *env,
					     jobject obj,
					     jint fd,
					     jlong offset)
{
  if (fd < 0)
    return 0;

  return lseek(fd, (off_t) offset, SEEK_CUR);
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniFileStream_nativeClose(JNIEnv *env,
					      jobject obj,
					      jint fd)
{
  if (fd >= 0) {
    return close(fd);
  }
  else
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniFileStream_nativeFlushToDisk(JNIEnv *env,
						    jobject obj,
						    jint fd)
{
  if (fd >= 0) {
#ifndef WIN32
    return fsync(fd);
#else
    return -1;
#endif
  }
  else
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniFileStream_nativeSeekStart(JNIEnv *env,
						  jobject obj,
						  jint fd,
						  jlong offset)
{
  if (fd >= 0) {
    return lseek(fd, (off_t) offset, SEEK_SET);
  }
  else
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniFileStream_nativeSeekEnd(JNIEnv *env,
						jobject obj,
						jint fd,
						jlong offset)
{
  if (fd >= 0) {
    return lseek(fd, (off_t) offset, SEEK_END);
  }
  else
    return -1;
}
