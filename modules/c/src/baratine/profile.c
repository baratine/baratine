/*
 * Copyright (c) 1998-2007 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

#ifdef WIN32
#include <windows.h>
#include <io.h>
#else
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>
#include <sys/time.h>
#include <pwd.h>
#include <syslog.h>
#include <netdb.h>
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

#ifdef HAS_JVMTI
#include <jvmti.h>

#include "baratine_os.h"
#include "baratine.h"

static jboolean
jvmti_can_reload_native(JNIEnv *env)
{
  JavaVM *jvm = 0;
  jvmtiEnv *jvmti = 0;
  jvmtiCapabilities capabilities;
  int res;

  res = (*env)->GetJavaVM(env, &jvm);
  if (res < 0 || jvm == 0)
    return 0;
  
  res = (*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1_0);

  if (res != JNI_OK || jvmti == 0)
    return 0;

  (*jvmti)->GetCapabilities(jvmti, &capabilities);

  (*jvmti)->DisposeEnvironment(jvmti);

  return capabilities.can_redefine_classes;
}

static void
profile_get_threads(jvmtiEnv *jvmti)
{
  jint thread_count;
  jthread *threads;
  int i;

  (*jvmti)->GetAllThreads(jvmti, &thread_count, &threads);

  if (! threads) {
    return;
  }

  for (i = 0; i < thread_count; i++) {
    jvmtiThreadInfo info;
    
    jthread thread = threads[i];

    if ((*jvmti)->GetThreadInfo(jvmti, thread, &info) == JVMTI_ERROR_NONE) {
      (*jvmti)->Deallocate(jvmti, (void*) info.name);
    }
  }

  (*jvmti)->Deallocate(jvmti, (void*) threads);
}

static void
profile_get_all_threads(jvmtiEnv *jvmti, int max_depth)
{
  int res;
  int i;
  jvmtiStackInfo *stack_info = 0;
  jint thread_count;

  res = (*jvmti)->GetAllStackTraces(jvmti, max_depth,
				    &stack_info, &thread_count);

  if (res != JVMTI_ERROR_NONE) {
    return;
  }
  for (i = 0; i < thread_count; i++) {
    jvmtiStackInfo *info = &stack_info[i];
    jthread thread = info->thread;
    jint state = info->state;
    jint frame_count = info->frame_count;
    jvmtiFrameInfo *frames = info->frame_buffer;
    int j;
    
    for (j = 0; j < frame_count; j++) {
      jclass cl;
      char *className = 0;
      char *methodName = 0;
      
      (*jvmti)->GetMethodDeclaringClass(jvmti, frames[j].method, &cl);
      (*jvmti)->GetClassSignature(jvmti, cl, &className, NULL);
      
      (*jvmti)->GetMethodName(jvmti, frames[j].method, &methodName,
			      NULL, NULL);
      
      printf("  %d: %s.%s\n", j, className, methodName);

      (*jvmti)->Deallocate(jvmti, (void*) className);
      (*jvmti)->Deallocate(jvmti, (void*) methodName);
    }
  }

  (*jvmti)->Deallocate(jvmti, (void*) stack_info);
}

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_profile_ProfileTask_nativeCreateProfile(JNIEnv *env,
                                                        jobject obj,
                                                        jint size)
{
  JavaVM *jvm = 0;
  jvmtiEnv *jvmti = 0;
  int res;
  struct lru_cache_t *cache;

  res = (*env)->GetJavaVM(env, &jvm);
  if (res < 0 || jvm == 0)
    return 0;
  
  res = (*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1_0);

  if (res != JNI_OK || jvmti == 0)
    return 0;

  cache = profile_create(jvmti, size);
  
  return (jlong) (PTR) cache;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_profile_ProfileTask_nativeProfile(JNIEnv *env,
                                                  jobject obj,
                                                  jlong p_cache,
                                                  jint max_depth)
{
  JavaVM *jvm = 0;
  jvmtiEnv *jvmti = 0;
  int res;
  struct lru_cache_t *cache;
  int i;
  jvmtiStackInfo *stack_info = 0;
  jint thread_count;

  res = (*env)->GetJavaVM(env, &jvm);
  if (res < 0 || jvm == 0)
    return 0;
  
  res = (*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1_0);

  if (res != JNI_OK || jvmti == 0)
    return 0;
  
  cache = (struct lru_cache_t *) (PTR) p_cache;

  if (cache == 0)
    return 0;

  res = (*jvmti)->GetAllStackTraces(jvmti, max_depth,
				    &stack_info, &thread_count);

  if (res != JVMTI_ERROR_NONE) {
    (*jvmti)->DisposeEnvironment(jvmti);
  
    return 0;
  }

  for (i = 0; i < thread_count; i++) {
    jvmtiStackInfo *info = &stack_info[i];

    profile_add_stack(env, jvmti, cache, info, 1);
  }

  (*jvmti)->Deallocate(jvmti, (void*) stack_info);

  (*jvmti)->DisposeEnvironment(jvmti);
  
  return 1;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_profile_ProfileTask_nativeClear(JNIEnv *env,
                                                jobject obj,
                                                jlong p_cache)
{
  JavaVM *jvm = 0;
  jvmtiEnv *jvmti = 0;
  int res;
  struct lru_cache_t *cache;
  int i;
  jvmtiStackInfo *stack_info = 0;
  jint thread_count;

  res = (*env)->GetJavaVM(env, &jvm);
  if (res < 0 || jvm == 0)
    return 0;
  
  res = (*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1_0);

  if (res != JNI_OK || jvmti == 0)
    return 0;
  
  cache = (struct lru_cache_t *) (PTR) p_cache;

  if (cache == 0)
    return 0;

  profile_clear(jvmti, cache);

  (*jvmti)->DisposeEnvironment(jvmti);
  
  return 1;
}

JNIEXPORT jobjectArray JNICALL
Java_com_caucho_v5_profile_ProfileTask_nativeDisplay(JNIEnv *env,
                                                  jobject obj,
                                                  jlong p_cache)
{
  JavaVM *jvm = 0;
  jvmtiEnv *jvmti = 0;
  int res;
  struct lru_cache_t *cache;
  int i;
  jvmtiStackInfo *stack_info = 0;
  jint thread_count;
  int max_display = 128;

  res = (*env)->GetJavaVM(env, &jvm);
  if (res < 0 || jvm == 0)
    return 0;
  
  res = (*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1_0);

  if (res != JNI_OK || jvmti == 0)
    return 0;
  
  cache = (struct lru_cache_t *) (PTR) p_cache;

  if (cache == 0)
    return 0;

  return profile_display(env, jvmti, cache, max_display);
}

#else

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_profile_ProfileTask_nativeCreateProfile(JNIEnv *env,
                                                        jobject obj,
                                                        jint size)
{
  return 0;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_profile_ProfileTask_nativeProfile(JNIEnv *env,
                                                  jobject obj,
                                                  jlong cache)
{
  return 0;
}

JNIEXPORT jobjectArray JNICALL
Java_com_caucho_v5_profile_ProfileTask_nativeDisplay(JNIEnv *env,
                                                  jobject obj,
                                                  jlong cache)
{
  return 0;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_profile_ProfileTask_nativeClear(JNIEnv *env,
                                                jobject obj,
                                                jlong cache)
{
  return 0;
}
#endif
