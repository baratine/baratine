/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
 *
 * Caucho Technology permits modification and use of this file in
 * source and binary form ("the Software") subject to the Caucho
 * Developer Source License 1.1 ("the License") which accompanies
 * this file.  The License is also available at
 *   http://www.caucho.com/download/cdsl1-1.xtp
 *
 * In addition to the terms of the License, the following conditions
 * must be met:
 *
 * 1. Each copy or derived work of the Software must preserve the copyright
 *    notice and this notice unmodified.
 *
 * 2. Each copy of the Software in source or binary form must include 
 *    an unmodified copy of the License in a plain ASCII text file named
 *    LICENSE.
 *
 * 3. Caucho reserves all rights to its names, trademarks and logos.
 *    In particular, the names "Resin" and "Caucho" are trademarks of
 *    Caucho and may not be used to endorse products derived from
 *    this software.  "Resin" and "Caucho" may not appear in the names
 *    of products derived from this software.
 *
 * This Software is provided "AS IS," without a warranty of any kind. 
 * ALL EXPRESS OR IMPLIED REPRESENTATIONS AND WARRANTIES, INCLUDING ANY
 * IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED.
 *
 * CAUCHO TECHNOLOGY AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES
 * SUFFERED BY LICENSEE OR ANY THIRD PARTY AS A RESULT OF USING OR
 * DISTRIBUTING SOFTWARE. IN NO EVENT WILL CAUCHO OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL,
 * CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND
 * REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF THE USE OF OR
 * INABILITY TO USE SOFTWARE, EVEN IF HE HAS BEEN ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGES.      
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
#endif

#ifdef HAS_JVMTI

jboolean
jvmti_can_reload_native(JNIEnv *env, jobject obj)
{
  JavaVM *jvm = 0;
  jvmtiEnv *jvmti = 0;
  jvmtiCapabilities capabilities;
  jvmtiCapabilities set_capabilities;
  int res;

  res = (*env)->GetJavaVM(env, &jvm);
  if (res < 0 || jvm == 0)
    return 0;
  
  res = (*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1_0);

  if (res != JNI_OK || jvmti == 0)
    return 0;

  memset(&set_capabilities, 0, sizeof(capabilities));
  set_capabilities.can_redefine_classes = 1;

  (*jvmti)->AddCapabilities(jvmti, &set_capabilities);
  
  (*jvmti)->GetCapabilities(jvmti, &capabilities);

  (*jvmti)->RelinquishCapabilities(jvmti, &set_capabilities);

  (*jvmti)->DisposeEnvironment(jvmti);

  return capabilities.can_redefine_classes;
}

jint
jvmti_reload_native(JNIEnv *env,
		    jobject obj,
		    jclass cl,
		    jbyteArray buf,
		    jint offset,
		    jint length)
{
  JavaVM *jvm = 0;
  jvmtiEnv *jvmti = 0;
  int res;
  jvmtiClassDefinition defs[1];
  jbyte *class_def;
  jvmtiCapabilities capabilities;

  if (cl == 0 || buf == 0 || env == 0)
    return 0;

  res = (*env)->GetJavaVM(env, &jvm);
  if (res < 0 || jvm == 0)
    return 0;
  
  res = (*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1_0);

  if (res != JNI_OK || jvmti == 0)
    return 0;

  memset(&capabilities, 0, sizeof(capabilities));
  capabilities.can_redefine_classes = 1;
  (*jvmti)->AddCapabilities(jvmti, &capabilities);
  
  defs[0].klass = cl;
  defs[0].class_byte_count = length;
  class_def = (*env)->GetByteArrayElements(env, buf, 0);
  defs[0].class_bytes = (unsigned char *) (class_def + offset);

  if (defs[0].class_bytes) {
    res = (*jvmti)->RedefineClasses(jvmti, 1, defs);

    (*env)->ReleaseByteArrayElements(env, buf, class_def, 0);
  }
  
  (*jvmti)->RelinquishCapabilities(jvmti, &capabilities);
  
  (*jvmti)->DisposeEnvironment(jvmti);
  
  return res;
}

#else

jboolean
jvmti_can_reload_native(JNIEnv *env, jobject obj)
{
  return 0;
}

jint
jvmti_reload_native(JNIEnv *env,
		    jobject obj,
		    jclass cl,
		    jbyteArray buf,
		    jint offset,
		    jint length)
{
  return 0;
}

#endif
