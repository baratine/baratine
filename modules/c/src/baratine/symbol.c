/*
 * Copyright (c) 1998-2007 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

#ifdef HAS_JVMTI
#ifdef WIN32
#include <windows.h>
#else
#include <string.h>
#include <strings.h>
#endif
#include <jvmti.h>

#include "baratine_os.h"
#include "baratine.h"

#define BUCKETS (1 << 16)

typedef struct symbol_t {
  struct symbol_t *next;

  char *name;
} symbol_t;

typedef struct symbol_table_t {
  symbol_t *buckets[BUCKETS];
} symbol_table_t;

static int
symbol_hash(const char *string)
{
  int hash = 37;
  int i;

  for (i = 0; string[i]; i++)
    hash = 65521 * hash + string[i];

  return hash;
}

struct symbol_table_t *
symbol_table_create(jvmtiEnv *jvmti)
{
  symbol_table_t *symbol_table = 0;

  if ((*jvmti)->Allocate(jvmti,
			 sizeof(symbol_table_t),
			 (void*) &symbol_table) != 0) {
    return 0;
  }

  memset(symbol_table, 0, sizeof(symbol_table_t));

  return symbol_table;
}

char *
symbol_table_add(jvmtiEnv *jvmti,
                 symbol_table_t *symbol_table,
                 const char *name)
{
  int bucket;
  symbol_t *symbol;

  bucket = symbol_hash(name) & (BUCKETS - 1);

  for (symbol = symbol_table->buckets[bucket];
       symbol;
       symbol = symbol->next) {
    if (! strcmp(name, symbol->name))
      return symbol->name;
  }

  if ((*jvmti)->Allocate(jvmti, sizeof(symbol_t), (void*) &symbol) != 0)
    return 0;
  
  memset(symbol, 0, sizeof(symbol_t));

  if ((*jvmti)->Allocate(jvmti, strlen(name) + 1, (void*) &(symbol->name)) != 0
      && symbol->name != 0)
    return 0;

  strcpy(symbol->name, name);

  symbol->next = symbol_table->buckets[bucket];
  symbol_table->buckets[bucket] = symbol;

  return symbol->name;
}
	   
#endif
