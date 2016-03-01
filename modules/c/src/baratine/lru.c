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
#endif
#include <jvmti.h>

#include "baratine_os.h"
#include "baratine.h"

#define BUCKETS (1 << 16)
#define MASK (BUCKETS - 1);

typedef struct stack_item_t {
  jmethodID method;

  char *class_name;
  char *method_name;

  char *arg_string;
} stack_item_t;

typedef struct cache_item_t {
  int bucket;
  
  struct cache_item_t *prev_hash;
  struct cache_item_t *next_hash;
  
  struct cache_item_t *prev_lru;
  struct cache_item_t *next_lru;

  int state;
  int frame_count;
  
  struct stack_item_t *stack;

  jlong count;
  jlong size;

  int needs_filling;
} cache_item_t;

typedef struct lru_cache_t {
  cache_item_t **buckets;

  /* maximum allowed entries */
  int capacity;
  /* size of first section */
  int capacity1;

  /* number of items in the cache seen once */
  int size1;
  /* head of lru1 list */
  cache_item_t *head1;
  /* tail of lru1 list */
  cache_item_t *tail1;

  /* number of items in the cache seen more than once */
  int size2;
  /* head of lru2 list */
  cache_item_t *head2;
  /* tail of lru2 list */
  cache_item_t *tail2;

  cache_item_t *free_list;

  struct symbol_table_t *symbol_table;
} lru_cache_t;

static int
profile_hash_code(jvmtiStackInfo *info)
{
  jint frame_count = info->frame_count;
  jvmtiFrameInfo *frames = info->frame_buffer;
  int hash = 37;
  int i;

  for (i = 0; i < frame_count; i++) {
    long method_hash = (long) frames[i].method;
    
    hash = 65521 * hash + 137 * (int) method_hash;
    hash = 65521 * hash + 137 * (((int) method_hash) >> 16);
  }

  return hash;
}

static int
profile_equals(cache_item_t *item, jvmtiStackInfo *info)
{
  int i;
  jint frame_count = info->frame_count;
  jvmtiFrameInfo *info_frames = info->frame_buffer;
  
  if (item->frame_count != frame_count)
    return 0;

  for (i = frame_count - 1; i >= 0; i--) {
    if (item->stack[i].method != info_frames[i].method)
      return 0;
  }

  return 1;
}

struct lru_cache_t *
profile_create(jvmtiEnv *jvmti, int size)
{
  lru_cache_t *cache = 0;
  int bucket_size;
  int entry_size;
  cache_item_t *free_list = 0;
  int i;

  if ((*jvmti)->Allocate(jvmti, sizeof(lru_cache_t), (void*) &cache) != 0)
    return 0;
  
  memset(cache, 0, sizeof(lru_cache_t));

  cache->capacity = size;
  cache->capacity1 = size / 2;

  cache->symbol_table = symbol_table_create(jvmti);

  bucket_size = BUCKETS * sizeof(cache_item_t *);
  if ((*jvmti)->Allocate(jvmti, bucket_size, (void*) &cache->buckets) != 0)
    return 0;
  
  memset(cache->buckets, 0, bucket_size);

  entry_size = size * sizeof(cache_item_t);

  if ((*jvmti)->Allocate(jvmti, entry_size, (void*) &free_list) != 0)
    return 0;
  
  memset(free_list, 0, entry_size);

  for (i = 0; i < size; i++) {
    free_list[i].next_lru = cache->free_list;
    cache->free_list = &free_list[i];
  }

  return cache;
}

/**
 * Remove the last item in the LRU
 */
static void
profile_remove_tail(jvmtiEnv *jvmti, lru_cache_t *cache)
{
  cache_item_t *tail;
  
  cache_item_t *prev;
  cache_item_t *next;
  int is_tail1;

  if (cache->capacity1 <= cache->size1)
    is_tail1 = cache->tail1 != 0;
  else
    is_tail1 = cache->tail2 == 0;

  tail = is_tail1 ? cache->tail1 : cache->tail2;

  if (tail == 0)
    return;

  if (is_tail1)
    cache->size1--;
  else
    cache->size2--;

  prev = tail->prev_hash;
  next = tail->next_hash;

  if (prev != 0)
    prev->next_hash = next;
  else
    cache->buckets[tail->bucket] = next;
  
  if (next != 0)
    next->prev_hash = prev;

  prev = tail->prev_lru;

  if (prev != 0)
    prev->next_lru = 0;
  else if (tail == cache->head1)
    cache->head1 = 0;
  else if (tail == cache->head2)
    cache->head2 = 0;

  if (tail == cache->tail1)
    cache->tail1 = prev;
  else if (tail == cache->tail2)
    cache->tail2 = prev;

  /* free data */
  if (tail->stack) {
    int i;
    
    for (i = 0; i < tail->frame_count; i++) {
      tail->stack[i].arg_string = 0;
    }
    (*jvmti)->Deallocate(jvmti, (void*) tail->stack);
    tail->stack = 0;
  }

  tail->next_lru = cache->free_list;
  cache->free_list = tail;
}

/**
 * Clear the cache
 */
void
profile_clear(jvmtiEnv *jvmti, lru_cache_t *cache)
{
  cache_item_t *ptr;
  cache_item_t *next;

  for (ptr = cache->head1; ptr; ptr = next) {
    next = ptr->next_lru;
    
    if (ptr->stack)
      (*jvmti)->Deallocate(jvmti, (void*) ptr->stack);
    ptr->stack = 0;

    ptr->next_lru = cache->free_list;
    cache->free_list = ptr;
  }

  for (ptr = cache->head2; ptr; ptr = next) {
    next = ptr->next_lru;
    
    if (ptr->stack)
      (*jvmti)->Deallocate(jvmti, (void*) ptr->stack);
    ptr->stack = 0;

    ptr->next_lru = cache->free_list;
    cache->free_list = ptr;
  }

  cache->head1 = cache->tail1 = 0;
  cache->head2 = cache->tail2 = 0;
  cache->size1 = cache->size2 = 0;
  memset(cache->buckets, 0, BUCKETS * sizeof(cache_item_t *));
}

/**
 * Put item at the head of the used-twice lru list.
 * This is always called while synchronized.
 */
static void
update_lru(lru_cache_t *cache, cache_item_t *item)
{
  cache_item_t *prev = item->prev_lru;
  cache_item_t *next = item->next_lru;

  if (item->count < 2) {
    if (prev != 0)
      prev->next_lru = next;
    else
      cache->head1 = next;

    if (next != 0)
      next->prev_lru = prev;
    else
      cache->tail1 = prev;

    item->prev_lru = 0;
    if (cache->head2 != 0)
      cache->head2->prev_lru = item;
    else
      cache->tail2 = item;
      
    item->next_lru = cache->head2;
    cache->head2 = item;

    cache->size1--;
    cache->size2++;
  }
  else if (prev != 0) {
    prev->next_lru = next;

    item->prev_lru = 0;
    item->next_lru = cache->head2;

    if (cache->head2)
      cache->head2->prev_lru = item;
    cache->head2 = item;
      
    if (next != 0)
      next->prev_lru = prev;
    else
      cache->tail2 = prev;
  }
}

static void
fill_arg_value(JNIEnv *env,
               jvmtiEnv *jvmti,
               jthread thread,
               lru_cache_t *cache,
               cache_item_t *item,
               stack_item_t *stack,
               int depth)
{
  jvmtiCapabilities capabilities;
  jvmtiCapabilities set_capabilities;
  jint slot = 1;
  jvmtiFrameInfo frame_buffer[4];
  jint count;
  jobject value_ptr = 0;
  int res;
  
  res = (*jvmti)->GetCapabilities(jvmti, &capabilities);
  
  memset(&set_capabilities, 0, sizeof(capabilities));
  set_capabilities.can_access_local_variables = 1;
  set_capabilities.can_suspend = 1;

  res = (*jvmti)->AddCapabilities(jvmti, &set_capabilities);

  if (res)
    return;
  
  res = (*jvmti)->GetCapabilities(jvmti, &set_capabilities);
  if (! set_capabilities.can_access_local_variables
      || ! set_capabilities.can_suspend) {
    return;
  }

  /*
   * suspend thread, check that the location is still valid and get data
   */
  /*
  res = (*jvmti)->SuspendThread(jvmti, thread);
  if (res)
    return;
  */

  count = 0;
  res = (*jvmti)->GetStackTrace(jvmti, thread, depth, 1, frame_buffer, &count);

  if (! res && count == 1
      && frame_buffer[0].method == stack->method) {
    int res2;
    
    res = (*jvmti)->GetLocalObject(jvmti, thread, depth, slot, &value_ptr);
    
      
    res2 = (*jvmti)->GetStackTrace(jvmti, thread, depth, 1,
                                   frame_buffer, &count);

    if (res2 == 0 && count == 1 && frame_buffer[0].method == stack->method) {
      if (res == 0 && value_ptr) {
        const char *buf = 0;
        int len;

        len = (*env)->GetStringUTFLength(env, value_ptr);
        if (len > 0)
          buf = (*env)->GetStringUTFChars(env, value_ptr, 0);
        
        if (buf) {
          stack->arg_string = symbol_table_add(jvmti, cache->symbol_table, buf);

          (*env)->ReleaseStringUTFChars(env, value_ptr, buf);
        }
      }
    }
    else
      item->needs_filling = 1;
  }
  else
    item->needs_filling = 1;
  
  /* res = (*jvmti)->ResumeThread(env, thread); */
}

static void
fill_stack_entry(JNIEnv *env,
                 jvmtiEnv *jvmti,
                 lru_cache_t *cache,
                 struct symbol_table_t *symbol_table,
                 jthread thread,
                 cache_item_t *item,
                 stack_item_t *stack,
                 int depth)
{
  int res;
  jclass cl = 0;
  char *class_name = 0;
  char *method_name = 0;
  char *method_sig = 0;
  char clean_buffer[1024];
  size_t len;
  int k;
  jmethodID method = stack->method;

  res = (*jvmti)->GetMethodDeclaringClass(jvmti, method, &cl);

  if (res != 0)
    return;
	
  res = (*jvmti)->GetClassSignature(jvmti, cl, &class_name, NULL);
  if (res != 0 || class_name == 0)
    return;

  len = strlen(class_name);
  if (sizeof(clean_buffer) <= len)
    len = sizeof(clean_buffer) - 1;

  for (k = 1; k + 1 < len; k++) {
    char ch = class_name[k];

    if (ch == '/')
      clean_buffer[k - 1] = '.';
    else
      clean_buffer[k - 1] = ch;
  }
  clean_buffer[k - 1] = 0;
  
  stack->class_name = symbol_table_add(jvmti, symbol_table, clean_buffer);
      
  res = (*jvmti)->GetMethodName(jvmti, method, &method_name, NULL, NULL);
  if (res == 0 && method_name) {
    jobject value_ptr = 0;
    
    stack->method_name = symbol_table_add(jvmti, symbol_table, method_name);
    
    if (stack->class_name && stack->method_name
        && ! strcmp(stack->class_name, "com.caucho.sql.UserStatement")
        && ! strcmp(stack->method_name, "execute")) {
      fill_arg_value(env, jvmti, thread, cache, item, stack, depth);
    }
    
    (*jvmti)->Deallocate(jvmti, (void*) method_name);
  }

  (*jvmti)->Deallocate(jvmti, (void*) class_name);
}

void
profile_add_stack(JNIEnv *jniEnv, jvmtiEnv *jvmti,
		  lru_cache_t *cache,
		  jvmtiStackInfo *info,
		  jlong size)
{
  int hash;
  int count;
  cache_item_t *item;
  int i;
  
  /* remove LRU items until we're below capacity */
  while (cache->free_list == 0) {
    profile_remove_tail(jvmti, cache);
  }

  hash = profile_hash_code(info) & MASK;
  count = cache->size1 + cache->size2 + 1;

  for (item = cache->buckets[hash];
       item != 0;
       item = item->next_hash) {
    if (profile_equals(item, info)) {
      item->count++;
      item->size += size;

      update_lru(cache, item);

      if (item->needs_filling) {
        item->needs_filling = 0;
        
        for (i = 0; i < item->frame_count; i++) {
          item->stack[i].method = info->frame_buffer[i].method;

          fill_stack_entry(jniEnv, jvmti, cache, cache->symbol_table,
                           info->thread,
                           item, &item->stack[i], i);
        }
      }

      return;
    }
  }
  
  item = cache->free_list;
  cache->free_list = item->next_lru;

  memset(item, 0, sizeof(cache_item_t));

  item->state = info->state;
  item->frame_count = info->frame_count;
  (*jvmti)->Allocate(jvmti,
		     item->frame_count * sizeof(stack_item_t),
		     (void*) &item->stack);

  if (item->stack)
    memset(item->stack, 0, item->frame_count * sizeof(stack_item_t));

  for (i = 0; i < item->frame_count; i++) {
    item->stack[i].method = info->frame_buffer[i].method;

    fill_stack_entry(jniEnv, jvmti, cache, cache->symbol_table,
                     info->thread, item, &item->stack[i], i);
  }

  item->bucket = hash;
  item->next_hash = cache->buckets[hash];
  
  if (cache->buckets[hash])
    cache->buckets[hash]->prev_hash = item;

  cache->buckets[hash] = item;

  cache->size1++;

  item->next_lru = cache->head1;
  if (cache->head1 != 0)
    cache->head1->prev_lru = item;
  else
    cache->tail1 = item;
  
  cache->head1 = item;

  item->size = size;
  item->count = 1;
}

static int
profile_compare(const void *a_obj, const void *b_obj)
{
  cache_item_t *a = *(cache_item_t **) a_obj;
  cache_item_t *b = *(cache_item_t **) b_obj;

  return (int) (b->count - a->count);
}

jobject
profile_display(JNIEnv *jniEnv,
		jvmtiEnv *jvmti,
		lru_cache_t *cache,
                int max)
{
  int i;
  cache_item_t **list = 0;
  int res;
  int size = cache->size1 + cache->size2;
  int index = 0;
  jclass eltClass;
  jmethodID entryCtor;
  jmethodID entryAddStack;
  jobjectArray objResult = 0;

  res = (*jvmti)->Allocate(jvmti,
			   size * sizeof(cache_item_t *),
			   (void*) &list);
  if (res != 0 || ! list)
    return 0;

  memset(list, 0, size * sizeof(cache_item_t *));

  for (i = 0; i < BUCKETS; i++) {
    cache_item_t *ptr = cache->buckets[i];

    for (; ptr; ptr = ptr->next_hash) {
      list[index++] = ptr;
    }
  }

  eltClass = (*jniEnv)->FindClass(jniEnv, "com/caucho/v5/profile/ProfileEntry");

  if (! eltClass)
    return 0;

  entryCtor = (*jniEnv)->GetMethodID(jniEnv, eltClass, "<init>", "(IJJ)V");

  if (! entryCtor)
    return 0;

  entryAddStack = (*jniEnv)->GetMethodID(jniEnv, eltClass, "addStack", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");

  if (! entryAddStack)
    return 0;

  qsort(list, size, sizeof(cache_item_t *), profile_compare);

  if (max < size)
    size = max;
  objResult = (*jniEnv)->NewObjectArray(jniEnv, size, eltClass, 0);

  for (i = 0; i < size; i++) {
    cache_item_t *ptr = list[i];
    jobject entry;
    int j;

    entry = (*jniEnv)->NewObject(jniEnv, eltClass, entryCtor,
				 ptr->state, ptr->count, ptr->size);

    if (! entry)
      return 0;

    (*jniEnv)->SetObjectArrayElement(jniEnv, objResult, i, entry);
    
    /* printf("%d:\n", (int) ptr->count); */
    for (j = 0; j < ptr->frame_count; j++) {
      stack_item_t *stack_item = &ptr->stack[j];
      jstring jClassName = 0;
      jstring jMethodName = 0;
      jstring jArgString = 0;

      if (stack_item->class_name)
        jClassName = (*jniEnv)->NewStringUTF(jniEnv, stack_item->class_name);
      
      if (stack_item->method_name)
        jMethodName = (*jniEnv)->NewStringUTF(jniEnv, stack_item->method_name);

      if (stack_item->arg_string)
        jArgString = (*jniEnv)->NewStringUTF(jniEnv, stack_item->arg_string);
	
      (*jniEnv)->CallVoidMethod(jniEnv, entry, entryAddStack,
                                jClassName, jMethodName, jArgString);
    }
  }

  (*jvmti)->Deallocate(jvmti, (void*) list);

  return objResult;
}
	   
#endif
