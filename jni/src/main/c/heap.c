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

#define TAG_SEEN (1LL << 63)

static jlong g_class_max;

typedef struct heap_item_t {
  jclass cl;
  jlong count;
  jlong self_size;
  jlong child_size;

  int charge_referrer : 1;
} heap_item_t;

static jvmtiIterationControl JNICALL
heap_root_callback(jvmtiHeapRootKind kind,
		   jlong class_tag,
		   jlong size,
		   jlong *tag_ptr,
		   void *user_data)
{
  heap_item_t *data = (heap_item_t *) user_data;

  int class_max = g_class_max;

  if (! data || class_tag <= 0 || class_max < class_tag) {
  }
  else if (! tag_ptr) {
    data[class_tag - 1].count++;
    data[class_tag - 1].self_size += size;
  }
  else if (! *tag_ptr) {
    data[class_tag - 1].count++;
    data[class_tag - 1].self_size += size;
    *tag_ptr = TAG_SEEN | class_tag;
  }

  return JVMTI_ITERATION_CONTINUE;
}

static jvmtiIterationControl JNICALL
heap_stack_ref_callback(jvmtiHeapRootKind kind,
			jlong class_tag,
			jlong size,
			jlong *tag_ptr,
			jlong thread_tag,
			jint depth,
			jmethodID method,
			jint slot,
			void *user_data)
{
  heap_item_t *data = (heap_item_t *) user_data;

  int class_max = g_class_max;
  
  if (! data || class_tag <= 0 || class_max < class_tag ) {
  }
  else if (! tag_ptr) {
    data[class_tag - 1].count++;
    data[class_tag - 1].self_size += size;
  }
  else if (! *tag_ptr) {
    data[class_tag - 1].count++;
    data[class_tag - 1].self_size += size;
    *tag_ptr = TAG_SEEN | class_tag;
  }

  return JVMTI_ITERATION_CONTINUE;
}

static jvmtiIterationControl JNICALL
heap_object_ref_callback(jvmtiObjectReferenceKind kind,
			 jlong class_tag,
			 jlong size,
			 jlong *tag_ptr,
			 jlong referrer_tag,
			 jint referrer_instance,
			 void *user_data)
{
  heap_item_t *data = (heap_item_t *) user_data;

  int class_max = g_class_max;
  
  if (! data || class_tag <= 0 || class_max < class_tag) {
  }
  else if (! tag_ptr) {
    data[class_tag - 1].count++;
    data[class_tag - 1].self_size += size;
  }
  else if (! *tag_ptr) {
    data[class_tag - 1].count++;
    data[class_tag - 1].self_size += size;

    if (data[class_tag - 1].charge_referrer && referrer_tag) {
      jlong referrer_mask = referrer_tag & ~TAG_SEEN;
	
      jlong top = (referrer_mask >> 40) & ((1 << 20) - 1);
      jlong grandparent = (referrer_mask >> 20) & ((1 << 20) - 1);
      jlong parent = (referrer_mask >> 0) & ((1 << 20) - 1);
	
      if (parent > 0
          && parent < class_max
          && parent != class_tag) {
        data[parent - 1].child_size += size;
      }

      if (grandparent > 0
          && grandparent < class_max
          && grandparent != class_tag
          && grandparent != parent) {
        data[grandparent - 1].child_size += size;
      }

      if (top > 0
          && top < class_max
          && top != class_tag
          && top != parent
          && top != grandparent) {
        data[top - 1].child_size += size;
      }

      if (top > 0) {
        top = class_tag;
      }

      *tag_ptr = TAG_SEEN | (top << 40) | (parent << 20) | class_tag;
    }
    else {
      *tag_ptr = TAG_SEEN | class_tag;
    }
  }

  return JVMTI_ITERATION_CONTINUE;
}

static int
heap_compare(const void *a_obj, const void *b_obj)
{
  heap_item_t *a = (heap_item_t *) a_obj;
  heap_item_t *b = (heap_item_t *) b_obj;
  
  jlong diff = b->self_size + b->child_size - (a->self_size + a->child_size);

  if (diff < 0) {
    return -1;
  }
  else if (diff > 0) {
    return 1;
  }
  else {
    return 0;
  }
}

jobject
Java_com_caucho_v5_profile_ProHeapDump_nativeHeapDump(JNIEnv *env,
                                                   jobject obj)
{
  JavaVM *jvm = 0;
  jvmtiEnv *jvmti = 0;
  jvmtiCapabilities capabilities;
  jvmtiCapabilities set_capabilities;
  heap_item_t *data = 0;
  int res;
  jint class_count = 0;
  jclass *classes = 0;
  int i;
  int max;
  jclass eltClass;
  jmethodID entryCtor;
  jmethodID entryAddStack;
  jobjectArray objResult = 0;

  res = (*env)->GetJavaVM(env, &jvm);
  if (res < 0 || jvm == 0) {
    return 0;
  }
  
  res = (*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1_0);

  if (res != JNI_OK || jvmti == 0) {
    return 0;
  }

  (*jvmti)->GetPotentialCapabilities(jvmti, &capabilities);
  
  memset(&set_capabilities, 0, sizeof(capabilities));
  set_capabilities.can_tag_objects = 1;

  res = (*jvmti)->AddCapabilities(jvmti, &set_capabilities);
  if (res) {
    (*jvmti)->DisposeEnvironment(jvmti);
    
    return 0;
  }

  (*jvmti)->GetCapabilities(jvmti, &capabilities);

  if (! capabilities.can_tag_objects) {
    (*jvmti)->DisposeEnvironment(jvmti);
    
    return 0;
  }

  res = (*jvmti)->GetLoadedClasses(jvmti, &class_count, &classes);
  if (res) {
    (*jvmti)->DisposeEnvironment(jvmti);
    
    return 0;
  }

  eltClass = (*env)->FindClass(env, "com/caucho/v5/profile/HeapEntry");

  if (! eltClass) {
    (*jvmti)->DisposeEnvironment(jvmti);
    
    return 0;
  }

  entryCtor = (*env)->GetMethodID(env, eltClass, "<init>", "(Ljava/lang/String;JJJ)V");

  if (! entryCtor) {
    (*jvmti)->DisposeEnvironment(jvmti);
    
    return 0;
  }

  res = (*jvmti)->Allocate(jvmti,
			   class_count * sizeof(heap_item_t),
			   (void*) &data);
  
  if (res) {
    (*jvmti)->Deallocate(jvmti, (void*) classes);
    (*jvmti)->DisposeEnvironment(jvmti);
    
    return 0;
  }

  memset(data, 0, class_count * sizeof(heap_item_t));

  for (i = 0; i < class_count; i++) {
    data[i].cl = classes[i];

    res = (*jvmti)->SetTag(jvmti, classes[i], i + 1);
    if (! res) {
      char *class_name = 0;

      res = (*jvmti)->GetClassSignature(jvmti, classes[i], &class_name, 0);
      if (! res) {
	if (class_name[0] == '[') {
	  data[i].charge_referrer = 1;
	}
	else if (! strncmp("Ljava/lang/", class_name,
			   sizeof("Ljava/lang"))) {
	  data[i].charge_referrer = 1;
	}
	else if (! strncmp("Ljava/util/", class_name,
			   sizeof("Ljava/util"))) {
	  data[i].charge_referrer = 1;
	}
	else if (! strncmp("Ljava/io/", class_name,
			   sizeof("Ljava/io"))) {
	  data[i].charge_referrer = 1;
	}
	else if (! strncmp("Ljava/nio/", class_name,
			   sizeof("Ljava/nio"))) {
	  data[i].charge_referrer = 1;
	}
	/*
	else if (! strncmp("Lcom/caucho/v5/util/", class_name,
			   sizeof("Lcom/caucho/v5/util"))) {
	  data[i].charge_referrer = 1;
	}
	*/

	(*jvmti)->Deallocate(jvmti, (void*) class_name);
      }
    }
  }

  g_class_max = class_count;
  
  res = (*jvmti)->IterateOverReachableObjects(jvmti,
					      heap_root_callback,
					      heap_stack_ref_callback,
					      heap_object_ref_callback,
					      data);

  qsort(data, class_count, sizeof(heap_item_t), heap_compare);
  
  max = 256;
  if (class_count < max) {
    max = class_count;
  }

  for (; max > 0 && data[max - 1].count == 0; max--) {
  }

  objResult = (*env)->NewObjectArray(env, max, eltClass, 0);

  if (! objResult) {
    return 0;
  }
  
  for (i = 0; i < max; i++) {
    if (data[i].self_size + data[i].child_size > 0) {
      char *class_name = 0;
      jobject jClassName = 0;

      res = (*jvmti)->GetClassSignature(jvmti, data[i].cl, &class_name, 0);
      if (! res) {
	int j, k;
	jobject entry;

	for (j = 0, k = 0; class_name[j]; j++) {
	  if (class_name[j] == '/')
	    class_name[k++] = '.';
	  else if (class_name[j] == ';')
	    class_name[k] = 0;
	  else if (class_name[j] == 'L'
		   && (j == 0 || class_name[j - 1] == '[')) {
	  }
	  else
	    class_name[k++] = class_name[j];
	}
	class_name[k] = 0;

	if (class_name[0] == 'L')
	  jClassName = (*env)->NewStringUTF(env, class_name + 1);
	else
	  jClassName = (*env)->NewStringUTF(env, class_name);

        if (jClassName) {
          entry = (*env)->NewObject(env, eltClass, entryCtor,
                                    jClassName,
                                    data[i].count,
                                    data[i].self_size,
                                    data[i].child_size);
        }

	if (entry) {
	  (*env)->SetObjectArrayElement(env, objResult, i, entry);
	}

	(*jvmti)->Deallocate(jvmti, (void*) class_name);
      }
      
    }
  }
  
  (*jvmti)->Deallocate(jvmti, (void*) data);
  (*jvmti)->Deallocate(jvmti, (void*) classes);

  (*jvmti)->DisposeEnvironment(jvmti);
  
  return objResult;
}

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved)
{
  jvmtiEnv *jvmti = 0;
  jvmtiCapabilities set_capabilities;
  int res;
  
  res = (*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1_0);

  if (res != JNI_OK || jvmti == 0)
    return 0;
  
  memset(&set_capabilities, 0, sizeof(set_capabilities));
  set_capabilities.can_tag_objects = 1;
  set_capabilities.can_redefine_classes = 1;
  set_capabilities.can_access_local_variables = 1;

  res = (*jvmti)->AddCapabilities(jvmti, &set_capabilities);

  (*jvmti)->DisposeEnvironment(jvmti);
    
  return 0;
}

#else

jobject
Java_com_caucho_v5_profile_Heap_nativeHeapDump(JNIEnv *env,
					 jobject obj)
{
  return 0;
}

#endif
