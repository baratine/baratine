/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

#ifdef WIN32
#ifndef _WINSOCKAPI_ 
#define _WINSOCKAPI_
#endif 
#include <windows.h>
#include <winsock2.h>
#include <io.h>
#else
#include <sys/param.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/resource.h>
#include <dirent.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#ifdef EPOLL
#include <sys/epoll.h>
#endif
#ifdef POLL
#include <sys/poll.h>
#else
#include <sys/select.h>
#endif
#include <pwd.h>
#include <syslog.h>
#include <netdb.h>
#endif

#ifdef linux
#include <linux/version.h>
#endif

#include <sys/stat.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <signal.h>
#include <limits.h>
#include <errno.h>
/* probably system-dependent */
#include <jni.h>

#include "baratine_os.h"
#include "baratine.h"

/* Must match JniCpuStat */
#define CPU_STAT_SIZE 12

/* Must match JniNetStat */
#define NET_STAT_SIZE 13

/* Must match JniVmStat */
#define VM_STAT_SIZE 2

typedef struct resin_stream_t {
  int fd;
  char buffer[512];
  int offset;
  int length;
} resin_stream_t;

static int g_stat_fd = -1;
static int g_net_stat_fd = -1;
static int g_vm_stat_fd = -1;
static int g_vm_page_size = -1;
static resin_stream_t g_net_stream;

JNIEXPORT double JNICALL
Java_com_caucho_v5_server_util_JniCauchoSystemImpl_nativeGetLoadAvg(JNIEnv *env,
								 jobject obj)
{
  double avg[3];

#ifndef WIN32
  
  getloadavg(avg, 3);

#else

  avg[0] = -1;

#endif
  
  return avg[0];
}

JNIEXPORT double JNICALL
Java_com_caucho_v5_server_util_JniCauchoSystemImpl_nativeGetRusage(JNIEnv *env,
								jobject obj)
{
#ifndef WIN32
  struct rusage r_usage;

  getrusage(RUSAGE_SELF, &r_usage);

  return -1;
#else

  return -1;

#endif
}

#ifdef linux

JNIEXPORT int JNICALL
Java_com_caucho_v5_health_stat_JniCpuStat_nativeJiffiesPerSecond(JNIEnv *env,
                                                               jobject obj)
{
  return sysconf(_SC_CLK_TCK);
}

JNIEXPORT int JNICALL
Java_com_caucho_v5_health_stat_JniCpuStat_nativeCpuCount(JNIEnv *env,
                                                       jobject obj)
{
  FILE *file = fopen("/proc/stat", "r");
  char line[128];
  int max_cpu = 0;

  if (! file)
    return -1;

  g_stat_fd = open("/proc/stat", O_RDONLY);

  while (fgets(line, sizeof(line), file)) {
    if (! strncmp(line, "cpu", 3)) {
      int i;
      int count = 0;
      
      for (i = 3; '0' <= line[i] && line[i] <= '9'; i++) {
        count = count * 10 + line[i] - '0';
      }

      if (i > 3 && max_cpu < count + 1)
        max_cpu = count + 1;
    }
  }
  
  return max_cpu;
}

/* cpu */

static char *
read_cpu_line(char *ptr, int max, jlong *data)
{
  int count = 0;
  int i;
  
  ptr += 3;

  if (*ptr != ' ') {
    for (; '0' <= *ptr && *ptr <= '9'; ptr++) {
      count = 10 * count + *ptr - '0';
    }

    count++;
  }

  if (max < count || *ptr != ' ') {
    return ptr;
  }

  data += count * CPU_STAT_SIZE;
  i = 1;
  
  while (1) {
    jlong value = 0;
    int ch;
    
    for (; *ptr == ' '; ptr++) {
    }

    if (! isdigit(*ptr) || CPU_STAT_SIZE <= i)
      return ptr;

    for (; '0' <= *ptr && *ptr <= '9'; ptr++) {
      value = 10 * value + *ptr - '0';
    }

    data[0] += value;

    if (i < CPU_STAT_SIZE)
      data[i] = value;
    else
      data[CPU_STAT_SIZE - 1] += value;

    i++;
  }    
}

static char *
read_context_switch_line(char *ptr, int max, jlong *data)
{
  int count = 0;
  int i;
  jlong value = 0;
  int ch;

  data[0] = 0;
  
  ptr += 4;

  if (*ptr != ' ') {
    return ptr;
  }

  for (; *ptr == ' '; ptr++) {
  }

  for (; '0' <= *ptr && *ptr <= '9'; ptr++) {
    value = 10 * value + *ptr - '0';
  }

  data[0] = value;
  
  return ptr;
}

JNIEXPORT int JNICALL
Java_com_caucho_v5_health_stat_JniCpuStat_nativeCpuSample(JNIEnv *env,
                                                        jobject obj,
                                                        jint max,
                                                        jlongArray cpuBuf,
                                                        jlongArray cswitchBuf)
{
  char data[8192];
  jlong *cpu_data;
  jlong context_switch_data = 0;
  int len;
  char *ptr;
  int size;
  
  if (g_stat_fd < 0)
    return -1;

  if (lseek(g_stat_fd, 0, SEEK_SET) != 0)
    return -1;

  len = read(g_stat_fd, data, sizeof(data));

  if (len < 0)
    return -1;

  size = ((max + 1) * CPU_STAT_SIZE) * sizeof(jlong);
  cpu_data = (jlong *) alloca(size);
  memset(cpu_data, 0, size);

  if (len < sizeof(data))
    data[len] = 0;
  else
    data[sizeof(data) - 1] = 0;

  for (ptr = data; *ptr; ptr++) {
    if (ptr[0] == 'c' && ptr[1] == 'p' && ptr[2] == 'u') {
      ptr = read_cpu_line(ptr, max, cpu_data);
    }
    else if (ptr[0] == 'c'
             && ptr[1] == 't'
             && ptr[2] == 'x'
             && ptr[3] == 't') {
      ptr = read_context_switch_line(ptr, max, &context_switch_data);
    }
  }
  
  (*env)->SetLongArrayRegion(env, cpuBuf, 0, (max + 1) * CPU_STAT_SIZE,
                             (void*) cpu_data);
  
  (*env)->SetLongArrayRegion(env, cswitchBuf, 0, 1, (void*) &context_switch_data);
  
  return 1;
}

/* netstat */

static int
stream_read(resin_stream_t *stream)
{
  if (stream->length <= stream->offset) {
    int len;

    if (stream->length < 0)
      return -1;
    
    stream->length = read(stream->fd, stream->buffer, sizeof(stream->buffer));
    stream->offset = 0;

    if (stream->length <= 0)
      return -1;
  }
  
  return stream->buffer[stream->offset++];
}

static int
stream_unread(resin_stream_t *stream)
{
  if (stream->offset > 0)
    stream->offset--;
}

static int
stream_skip_to_eol(resin_stream_t *stream)
{
  int ch;

  while ((ch = stream_read(stream)) > 0 && ch != '\n') {
  }

  if (ch < 0)
    return -1;
  else
    return ch == '\n';
}

static int
stream_skip_to_word(resin_stream_t *stream)
{
  int ch;

  while ((ch = stream_read(stream)) > 0 && (ch == ' ' || ch == '\t')) {
  }

  for (;
       ch > 0 && ch != ' ' && ch != '\n';
       ch = stream_read(stream)) {
  }

  for (;
       ch == ' ' || ch == '\t';
       ch = stream_read(stream)) {
  }

  if (ch > 0)
    stream_unread(stream);

  if (ch < 0 || ch == '\n')
    return -1;
  else
    return 1;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_health_stat_JniNetStat_nativeIsNetActive(JNIEnv *env,
                                                          jobject obj)
{
  return 1;
}

JNIEXPORT int JNICALL
Java_com_caucho_v5_health_stat_JniNetStat_nativeNetSample(JNIEnv *env,
                                                        jobject obj,
                                                        jlongArray buf)
{
  jlong net_data[NET_STAT_SIZE];
  int len;
  char *ptr;
  int size;

  if (g_net_stat_fd < 0) {
    g_net_stat_fd = open("/proc/net/tcp", O_RDONLY);
    if (g_net_stat_fd < 0)
      return -1;
  }

  if (lseek(g_net_stat_fd, 0, SEEK_SET) != 0) {
    return -1;
  }

  g_net_stream.fd = g_net_stat_fd;
  g_net_stream.length = 0;
  g_net_stream.offset = 0;

  memset(net_data, 0, sizeof(net_data));
  
  /* skip initial line */
  while (stream_skip_to_eol(&g_net_stream) > 0) {
    int value = 0;
    int ch;

    if (stream_skip_to_word(&g_net_stream) <= 0
        || stream_skip_to_word(&g_net_stream) <= 0
        || stream_skip_to_word(&g_net_stream) <= 0) {
      break;
    }

    while ((ch = stream_read(&g_net_stream)) > 0) {
      if ('0' <= ch && ch <= '9') {
        value = 16 * value + ch - '0';
      }
      else if ('a' <= ch && ch <= 'f') {
        value = 16 * value + ch - 'a' + 10;
      }
      else if ('A' <= ch && ch <= 'F') {
        value = 16 * value + ch - 'A' + 10;
      }
      else
        break;
    }

    if (value >= 0 && value < NET_STAT_SIZE)
      net_data[value]++;
  }
  
  (*env)->SetLongArrayRegion(env, buf, 0, NET_STAT_SIZE,
                             (void*) &net_data);

  return 1;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_health_stat_JniVmStat_nativeIsVmActive(JNIEnv *env,
                                                        jobject obj)
{
  if (g_vm_stat_fd < 0) {
    g_vm_stat_fd = open("/proc/self/statm", O_RDONLY);
  }

  g_vm_page_size = sysconf(_SC_PAGESIZE);
  
  return g_vm_stat_fd >= 0 && g_vm_page_size > 0;
}

static char *
read_vm_data(char *ptr, jlong *p_value)
{
  int ch;
  jlong value = 0;

  for (; *ptr == ' '; ptr++) {
  }

  for (; '0' <= *ptr && *ptr <= '9'; ptr++) {
    value = 10 * value + *ptr - '0';
  }

  *p_value = value * g_vm_page_size;

  return ptr;
}

JNIEXPORT int JNICALL
Java_com_caucho_v5_health_stat_JniVmStat_nativeVmSample(JNIEnv *env,
                                                      jobject obj,
                                                      jlongArray buf)
{
  char data[8192];
  jlong vm_data[VM_STAT_SIZE];
  int len;
  char *ptr;
  int size;
  
  if (g_vm_stat_fd < 0)
    return -1;

  if (lseek(g_vm_stat_fd, 0, SEEK_SET) != 0)
    return -1;

  len = read(g_vm_stat_fd, data, sizeof(data));

  if (len < 0)
    return -1;

  memset(vm_data, 0, sizeof(vm_data));

  if (len < sizeof(data))
    data[len] = 0;
  else
    data[sizeof(data) - 1] = 0;

  ptr = data;

  ptr = read_vm_data(ptr, &(vm_data[0]));
  ptr = read_vm_data(ptr, &(vm_data[1]));
  
  (*env)->SetLongArrayRegion(env, buf, 0, VM_STAT_SIZE,
                             (void*) &vm_data);

  return 1;
}

#else /* non-linux */

JNIEXPORT int JNICALL
Java_com_caucho_v5_health_stat_JniCpuStat_nativeJiffiesPerSecond(JNIEnv *env,
                                                               jobject obj)
{
  return 0;
}

JNIEXPORT int JNICALL
Java_com_caucho_v5_health_stat_JniCpuStat_nativeCpuCount(JNIEnv *env,
                                                       jobject obj)
{
  return -1;
}

JNIEXPORT int JNICALL
Java_com_caucho_v5_health_stat_JniCpuStat_nativeCpuSample(JNIEnv *env,
                                                        jobject obj,
                                                        jint max,
                                                        jlongArray buf)
{
  return -1;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_health_stat_JniNetStat_nativeIsNetActive(JNIEnv *env,
                                                          jobject obj)
{
  return 0;
}

JNIEXPORT int JNICALL
Java_com_caucho_v5_health_stat_JniNetStat_nativeNetSample(JNIEnv *env,
                                                        jobject obj,
                                                        jlongArray buf)
{
  return -1;
}

JNIEXPORT jboolean JNICALL
Java_com_caucho_v5_health_stat_JniVmStat_nativeIsVmActive(JNIEnv *env,
                                                        jobject obj)
{
  return 0;
}

JNIEXPORT int JNICALL
Java_com_caucho_v5_health_stat_JniVmStat_nativeVmSample(JNIEnv *env,
                                                      jobject obj,
                                                      jlongArray buf)
{
  return -1;
}

#endif
