/*
 * Copyright (c) 1999-2008 Caucho Technology.  All rights reserved.
 *
 * @author Scott Ferguson
 */

#include <sys/types.h>
#ifdef WIN32
#ifndef _WINSOCKAPI_ 
#define _WINSOCKAPI_
#endif 
#include <windows.h>
#include <winsock2.h>
#else
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
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

#include "baratine_os.h"

typedef struct chunk_t {
  int bucket;
  struct chunk_t *next;
} chunk_t;

#define MEMORY_MAX 65536
#define MEMORY_ALLOC 65536

static int is_init;
static pthread_mutex_t mem_lock;
static int alloc = 0;
static chunk_t *buckets[256];

static int
get_bucket(int size)
{
  size += sizeof(chunk_t);

  if (size <= MEMORY_MAX)
    return (size + 255) / 256;
  else
    return -1;
}

static int
get_chunk_size(int size)
{
  if (size + sizeof(chunk_t) < MEMORY_MAX)
    return 256 * ((size + sizeof(chunk_t) + 255) / 256);
  else
    return 0;
}

static void
cse_init_bucket(int size, int alloc_size)
{
  char *data;
  int bucket = get_bucket(size);
  int chunk_size = get_chunk_size(size);
  int i;

  if (bucket < 0) {
    fprintf(stderr, "illegal call to cse_init_bucket size=%d\n", size);
    return;
  }
  
  data = malloc(alloc_size);
  bucket = get_bucket(size);
  chunk_size = get_chunk_size(size);
  
  if (bucket >= 1024)
    fprintf(stderr, "bad bucket size:%d bucket:%d\n", size, bucket);

  for (i = 0; i < alloc_size; i += chunk_size) {
    chunk_t *chunk = (chunk_t *) (data + i);
    chunk->bucket = bucket;
    chunk->next = buckets[bucket];
    buckets[bucket] = chunk;
  }
}

void *
cse_malloc(int size)
{
  int bucket;
  chunk_t *chunk = 0;
  void *data;

  bucket = get_bucket(size);

  if (bucket < 0) {
    chunk = (chunk_t *) malloc(size + sizeof(chunk_t));
    chunk->bucket = -1;
    
    data = ((char *) chunk) + sizeof(chunk_t);
    
    return data;
  }
  
  pthread_mutex_lock(&mem_lock);
  chunk = buckets[bucket];
  if (chunk)
    buckets[bucket] = chunk->next;
  pthread_mutex_unlock(&mem_lock);

  if (chunk) {
  }
  else if (size + sizeof(chunk_t) <= 4096) {
    pthread_mutex_lock(&mem_lock);
    cse_init_bucket(size, MEMORY_ALLOC);
    
    chunk = buckets[bucket];
    buckets[bucket] = chunk->next;
    pthread_mutex_unlock(&mem_lock);
  }
  else {
    chunk = (chunk_t *) malloc(get_chunk_size(size));

    if (chunk == 0)
      return 0;

    chunk->bucket = bucket;
  }
  
  chunk->next = 0;

  data = ((char *) chunk) + sizeof(chunk_t);

  return data;
}

void
cse_free(void *v_data)
{
  chunk_t *chunk = (chunk_t *) (((char *) v_data) - sizeof(chunk_t));
  int bucket = chunk->bucket;

  if (bucket == -1) {
    free(chunk);
  }
  else if (bucket < 256) {
    pthread_mutex_lock(&mem_lock);
    chunk->next = buckets[bucket];
    buckets[bucket] = chunk;
    pthread_mutex_unlock(&mem_lock);
  }
  else
    fprintf(stderr, "no bucket\n");
}
