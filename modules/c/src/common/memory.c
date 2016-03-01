/*
 * Copyright (c) 1999-2003 Caucho Technology.  All rights reserved.
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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 */

#include <stdio.h>
#include <ctype.h>
#include <string.h>
#include <time.h>
#include <stdlib.h>
#ifdef WIN32
#include <windows.h>
#endif
#include "cse.h"

#define BLOCK_SIZE 32 * 1024

typedef struct pool_block_t {
  struct pool_block_t *next;

  char *buffer;
  int size;
  int offset;
} pool_block_t;

struct mem_pool_t {
  struct config_t *config;
  struct pool_block_t *block;
  void *lock;
};

mem_pool_t *
cse_create_pool(config_t *config)
{
  mem_pool_t *pool = (mem_pool_t *) cse_malloc(sizeof(mem_pool_t));
  memset(pool, 0, sizeof(mem_pool_t));
  pool->config = config;
  pool->lock = cse_create_lock(config);
  
  LOG(("%s:%d:cse_create_pool(): memory lock %p\n",
       __FILE__, __LINE__, pool->lock));
  LOG(("%s:%d:cse_create_pool(): create pool %p\n",
       __FILE__, __LINE__, pool));

  return pool;
}

void *
cse_alloc(mem_pool_t *pool, int size)
{
  void *data;
  int frag = size % 8;

  if (frag > 0)
    size += 8 - frag;

  cse_lock(pool->lock);

  if (! pool->block || pool->block->size - pool->block->offset < size) {
    pool_block_t *block = (pool_block_t *) cse_malloc(sizeof(pool_block_t));
    memset(block, 0, sizeof(pool_block_t));
    block->next = pool->block;
    pool->block = block;

    if (size > BLOCK_SIZE)
      block->size = size;
    else
      block->size = BLOCK_SIZE;

    block->buffer = cse_malloc(block->size);
  }

  data = pool->block->buffer + pool->block->offset;
  pool->block->offset += size;
  
  cse_unlock(pool->lock);

  return data;
}

char *
cse_strdup(mem_pool_t *pool, const char *str)
{
  int len = strlen(str);
  char *buf = cse_alloc(pool, len + 1);

  strcpy(buf, str);

  return buf;
}

void
cse_free_pool(mem_pool_t *pool)
{
  pool_block_t *ptr;
  pool_block_t *next;

  LOG(("%s:%d:cse_free_pool(): free pool %p\n", __FILE__, __LINE__, pool));

  cse_lock(pool->lock);

  for (ptr = pool->block; ptr; ptr = next) {
    next = ptr->next;

    cse_free(ptr->buffer);
    cse_free(ptr);
  }

  pool->block = 0;

  cse_unlock(pool->lock);
  cse_free_lock(pool->config, pool->lock);
  pool->lock = 0;

  cse_free(pool);
}
