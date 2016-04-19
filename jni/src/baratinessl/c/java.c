/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

#ifdef WIN32
#ifndef _WINSOCKAPI_ 
#define _WINSOCKAPI_
#endif 
#include <windows.h>
#include <winsock2.h> 
#else
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#include <sys/time.h>
#include <pwd.h>
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

#include "../../baratine/c/baratine.h"

char *
q_strdup(char *value)
{
  if (value == 0) {
    return 0;
  }
  else {
    return strdup(value);
  }
}

void
baratine_throw_exception(JNIEnv *env, const char *cl, const char *buf)
{
  jclass clazz;

  if (env && ! (*env)->ExceptionCheck(env)) {
    clazz = (*env)->FindClass(env, cl);

    if (clazz && ! (*env)->ExceptionCheck(env)) {
      (*env)->ThrowNew(env, clazz, buf);
      return;
    }
  }

  fprintf(stderr, "%s\n", buf);
}

void
baratine_printf_exception(JNIEnv *env, const char *cl, const char *fmt, ...)
{
  char buf[8192];
  va_list list;
  jclass clazz;

  va_start(list, fmt);

  vsprintf(buf, fmt, list);

  va_end(list);

  if (env && *env && ! (*env)->ExceptionOccurred(env)) {
    clazz = (*env)->FindClass(env, cl);

    if (clazz && ! (*env)->ExceptionOccurred(env)) {
      (*env)->ThrowNew(env, clazz, buf);
      return;
    }
  }
  
  fprintf(stderr, "%s\n", buf);
}

char *
baratine_get_utf(JNIEnv *env, jstring jaddr, char *buf, int buflen)
{
  const char *temp_string = 0;

  temp_string = (*env)->GetStringUTFChars(env, jaddr, 0);
  
  if (temp_string) {
    strncpy(buf, temp_string, buflen);
    buf[buflen - 1] = 0;
  
    (*env)->ReleaseStringUTFChars(env, jaddr, temp_string);
  }

  return buf;
}

int
baratine_get_byte_array_region(JNIEnv *env,
                               jbyteArray buf,
                               jint offset,
                               jint sublen,
                               char *c_buf)
{
  (*env)->GetByteArrayRegion(env, buf, offset, sublen, (void*) c_buf);

  return 1;
}

int
baratine_set_byte_array_region(JNIEnv *env,
			    jbyteArray j_buf,
			    jint offset,
			    jint sublen,
			    char *c_buf)
{
  /* JDK uses SetByteArrayRegion */
  (*env)->SetByteArrayRegion(env, j_buf, offset, sublen, (void*) c_buf);

  /*
  jbyte *cBuf = (*env)->GetPrimitiveArrayCritical(env, j_buf, 0);
  if (! cBuf)
    return 0;

  memcpy(cBuf + offset, c_buf, sublen);

  (*env)->ReleasePrimitiveArrayCritical(env, j_buf, cBuf, 0);
  */
  
  return 1;
}
