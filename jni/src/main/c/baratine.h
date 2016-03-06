/*
 * Copyright (c) 1999-2016 Caucho Technology.  All rights reserved.
 */

#ifndef BARATINE_H
#define BARATINE_H

#ifdef _JAVA_JVMTI_H_

struct lru_cache_t *profile_create(jvmtiEnv *env, int size);

void
profile_add_stack(JNIEnv *jniEnv,
                  jvmtiEnv *jvmti,
		  struct lru_cache_t *cache,
		  jvmtiStackInfo *info,
		  jlong size);

jobject
profile_display(JNIEnv *jniEnv,
		jvmtiEnv *jvmti,
		struct lru_cache_t *cache,
                int max);

void
profile_clear(jvmtiEnv *jvmti,
	      struct lru_cache_t *cache);

struct symbol_table_t *
symbol_table_create(jvmtiEnv *jvmti);

char *
symbol_table_add(jvmtiEnv *jvmti,
           struct symbol_table_t *symbol_table,
           const char *name);

#endif

jlong crc64_generate(jlong crc, char *value, int max_length);
jlong crc64_generate_bytes(jlong crc, char *buffer, int length);

#endif /* RESIN_H */
