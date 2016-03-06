/*
 * Copyright (c) 1998-2014 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

#ifndef WIN32
#include <sys/param.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/resource.h>
#include <dirent.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <pwd.h>
#include <syslog.h>
#include <netdb.h>
#include <sys/mman.h>
#include <string.h>

#include <sys/stat.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>

#include <jni.h>
#include "baratine_os.h"
#include "baratine.h"

#else

#include <jni.h>

#endif

#ifndef WIN32

typedef struct mmap_file_t {
  int fd;
  void *address;
  size_t length;
} mmap_file_t;

/*
 * JniMemoryMappedFile
 */


JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_JniMemoryMappedFile_nativeOpen(JNIEnv *env,
						   jobject obj,
						   jbyteArray name,
						   jint name_length,
                                                   jlong file_length)
{
  char buffer[8192];
  int fd;
  int flags;
  mmap_file_t *file = 0;
  struct stat st;
  void *addr;

  if (! name
      || name_length <= 0
      || sizeof(buffer) <= name_length
      || file_length <= 0) {
    return 0;
  }

  (*env)->GetByteArrayRegion(env, name, 0, name_length, (void*) buffer);

  buffer[name_length] = 0;

  if (stat(buffer, &st)) {
    return 0;
  }

#ifdef S_ISDIR
 /* On Linux, check if the file is a directory first. */
 {
   if (S_ISDIR(st.st_mode)) {
     return 0;
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

  if (fd < 0) {
    return 0;
  }

  if (st.st_size < file_length) {
    if (ftruncate(fd, file_length)) {
      close(fd);
      return 0;
    }
  }

  addr = mmap(0, file_length, PROT_READ|PROT_WRITE, MAP_SHARED, fd, 0);

  /* fprintf(stdout, "mmap: %x %p\n", (int) file_length, addr); */

  /* fprintf(stderr, "MMAP %s %p %lx\n", buffer, addr, file_length); */

  if (! addr) {
    close(fd);
    return 0;
  }

  file = (mmap_file_t *) malloc(sizeof(mmap_file_t));
  if (! file) {
    close(fd);
    return 0;
  }

  memset((void*) file, 0, sizeof(mmap_file_t));

  file->fd = fd;
  file->address = addr;
  file->length = file_length;

  return (PTR) file;
}

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_JniMemoryMappedFile_nativeMmapAddress(JNIEnv *env,
                                                          jobject obj,
                                                          jlong v_file)
{
  mmap_file_t *file = (mmap_file_t *) (PTR) v_file;

  if (! file) {
    return 0;
  }

  return (jlong) (PTR) file->address;
  /*
  return (jlong) file->fd;
  */
  // return (jlong) file->fd;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniMemoryMappedFile_nativeClose(JNIEnv *env,
						    jobject obj,
						    jlong v_file)
{
  mmap_file_t *file = (mmap_file_t *) (PTR) v_file;

  if (file) {
    int fd = file->fd;
    void *address = file->address;

    file->fd = 0;
    file->address = 0;

    if (fd > 0) {
      /* fprintf(stderr, "UNMAP %p %lx\n", address, file->length); */
      
      munmap(address, file->length);

      close(fd);
    }

    free(file);
  }

  return 0;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniMemoryMappedFile_nativeRead(JNIEnv *env,
						   jobject obj,
						   jlong v_file,
						   jlong pos,
						   jbyteArray buf,
						   jint offset,
						   jint length)
{
  mmap_file_t *file;
  char *buffer;
  
  if (! env || ! v_file || ! buf) {
    return -1;
  }

  file = (mmap_file_t *) (PTR) v_file;

  if (offset < 0 || length < 0 || file->length < pos + length)
    return -1;

  buffer = file->address;

  if (! buffer)
    return -1;

  (*env)->SetByteArrayRegion(env, buf, offset, length, (void*) (buffer + pos));
  /* set_byte_array_region(env, buf, offset, length, buffer + pos); */

  return length;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniMemoryMappedFile_nativeWrite(JNIEnv *env,
						    jobject obj,
						    jlong v_file,
						    jlong pos,
						    jbyteArray buf,
						    jint offset,
						    jint length)
{
  mmap_file_t *file;
  char *buffer;
  
  if (v_file == 0 || buf == 0)
    return -1;

  file = (mmap_file_t *) (PTR) v_file;

  if (offset < 0 || length < 0 || file->length < offset + length)
    return -1;

  buffer = file->address;

  if (! buffer) {
    return -1;
  }

  /*
  fprintf(stderr, "get: %p offset=%d len=%d pos=%d\n",
          buffer, offset, length, (int) pos);
  */

  (*env)->GetByteArrayRegion(env, buf, offset, length,
                             (void*) (buffer + pos));

  return length;
}

JNIEXPORT int JNICALL
Java_com_caucho_v5_jni_JniMemoryMappedFile_nativeFlushToDisk(JNIEnv *env,
                                                          jobject obj,
                                                          jlong v_file)
{
  mmap_file_t *file;
  int fd;
  
  if (v_file == 0) {
    return -1;
  }

  file = (mmap_file_t *) (PTR) v_file;

  fd = file->fd;

  if (fd > 0) {
    /* fsync(fd); */
    msync(file->address, file->length, MS_SYNC|MS_INVALIDATE);
    /* fprintf(stderr, "SYNC: %p %lx\n", file->address, file->length); */
  }

  return 1;
}

#else

JNIEXPORT jlong JNICALL
Java_com_caucho_v5_jni_JniMemoryMappedFile_nativeOpen(JNIEnv *env,
						   jobject obj,
						   jbyteArray name,
						   jint name_length,
                                                   jlong file_length)
{
  return 0;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniMemoryMappedFile_nativeClose(JNIEnv *env,
						    jobject obj,
						    jlong v_file)
{
  return -1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniMemoryMappedFile_nativeRead(JNIEnv *env,
						   jobject obj,
						   jlong v_file,
						   jlong pos,
						   jbyteArray buf,
						   jint offset,
						   jint length)
{
  return -1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniMemoryMappedFile_nativeWrite(JNIEnv *env,
						    jobject obj,
						    jlong v_file,
						    jlong pos,
						    jbyteArray buf,
						    jint offset,
						    jint length)
{
  return -1;
}

JNIEXPORT jint JNICALL
Java_com_caucho_v5_jni_JniMemoryMappedFile_nativeFlushToDisk(JNIEnv *env,
                                                          jobject obj,
                                                          jlong v_file)
{
  return -1;
}

#endif

