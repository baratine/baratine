/*
 * Copyright (c) 1998-2014 Caucho Technology -- all rights reserved
 *
 * multiple writer, single reader.
 */

#include <jni.h>

#ifdef HAS_IPC

#define _GNU_SOURCE

#include <stdio.h>
#include <errno.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <string.h>
#include <fcntl.h>

#include "baratine_os.h"
#include "baratine.h"

#define HEAD_OFFER_OFFSET 0
#define HEAD_OFFSET 4
#define HEAD_LIMIT_OFFSET 8
#define TAIL_OFFSET 16

#define READ_SEM_OFFSET 20

#define DATA_OFFSET 1024

static char *
get_utf8(JNIEnv *env, jstring jaddr, char *buf, int buflen)
{
  const char *temp_string = 0;

  temp_string = (*env)->GetStringUTFChars(env, jaddr, 0);
  
  if (temp_string) {
    memcpy(buf, temp_string, buflen);
    buf[buflen - 1] = 0;
  
    (*env)->ReleaseStringUTFChars(env, jaddr, temp_string);
  }

  return buf;
}

JNIEXPORT jlong JNICALL
Java_com_caucho_vfs_JniSharedMemory_nativeOpen(JNIEnv *env,
                                               jobject obj,
                                               jbyteArray j_name,
                                               jint j_buffer_length,
                                               jboolean is_reader)
{
  int flag;
  int fd;
  int result;
  int mode = 0770;
  void *mmap_offset;
  char name[256];

  fprintf(stderr, "NAME: %p\n", j_name);
  if (j_name == 0) {
    return 0;
  }

  get_utf8(env, j_name, name, sizeof(name));
  fprintf(stderr, "NAME2: %s\n", name);

  if (is_reader) {
    shm_unlink(name);

    flag = O_RDWR|O_CREAT;
  }
  else {
    flag = O_RDWR;
  }

  fd = shm_open(name, flag, mode);

  if (fd < 0) {
    return 0;
  }

  result = ftruncate(fd, j_buffer_length);

  if (result < 0) {
    shm_unlink(name);

    return 0;
  }

  mmap_offset = mmap(0, j_buffer_length, 
                     PROT_READ|PROT_WRITE, MAP_SHARED,
                     fd, 0);

  //  shm_unlink(name);

  if (mmap_offset == MAP_FAILED) {
    return 0;
  }

  fprintf(stderr, "MMAP %p\n", mmap_offset);

  {
    char *test = mmap_offset;

    fprintf(stderr, "TEST %x\n", test[0]);
    test[0] = 0x55;
  }

  return (PTR) mmap_offset;
}

JNIEXPORT jint JNICALL
Java_com_caucho_vfs_JniSharedMemory_nativeClose(JNIEnv *env,
                                                jobject obj,
                                                long mmap_offset,
                                                int length)
{
  void *mmap = (void*) (PTR) mmap_offset;

  if (mmap != 0) {
    munmap(mmap, length);
  }

  return 1;
}

#else

JNIEXPORT jlong JNICALL
Java_com_caucho_vfs_JniSharedMemory_nativeOpen(JNIEnv *env,
                                               jobject obj,
                                               jlong j_name,
                                               jint j_buffer_length)
{
  return -1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_vfs_JniSharedMemory_nativeClose(JNIEnv *env,
                                                jobject obj,
                                                long mmap_offset,
                                                int length)
{
  return -1;
}

#endif
